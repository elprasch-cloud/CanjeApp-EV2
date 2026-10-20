package com.iot.canjeapp.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.iot.canjeapp.security.PasswordHasher

/**
 * Persistencia local (SQLite) de la aplicación.
 *
 * Tablas:
 *  - usuarios : credenciales (hash PBKDF2 + sal), puntos acumulados y
 *               contador de intentos fallidos / bloqueo temporal.
 *  - lecturas : telemetría del HC-SR04 recibida por Bluetooth.
 *  - canjes   : códigos de un solo uso generados al detectar presencia.
 *
 * Todas las consultas son parametrizadas (sin concatenar texto del usuario),
 * lo que evita inyección SQL.
 */
class DatabaseHelper(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {

    companion object {
        // Nombre nuevo: evita conflictos con el esquema anterior (solo SHA-256)
        private const val DB_NAME = "canjeapp_v2.db"
        private const val DB_VERSION = 1

        const val MAX_FAILED_ATTEMPTS = 3
        const val LOCK_DURATION_MS = 30_000L
        const val POINTS_PER_REDEEM = 10
        private const val MAX_STORED_READINGS = 500

        // Cuentas iniciales de demostración (una por integrante).
        // Cumplen la política de CredentialPolicy. Cambiar antes de producción.
        val DEMO_USERS = listOf("lindsay.soto", "matias.vargas")
        const val DEMO_PASSWORD = "Canje#2026"

        @Volatile private var instance: DatabaseHelper? = null
        fun get(context: Context): DatabaseHelper =
            instance ?: synchronized(this) {
                instance ?: DatabaseHelper(context).also { instance = it }
            }
    }

    sealed class AuthResult {
        object Success : AuthResult()
        data class Invalid(val attemptsLeft: Int?) : AuthResult()
        data class Locked(val secondsLeft: Long) : AuthResult()
    }

    data class Redeem(val code: String, val distanceCm: Int, val timestamp: Long)

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE usuarios (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                username TEXT NOT NULL UNIQUE,
                password_hash TEXT NOT NULL,
                salt TEXT NOT NULL,
                puntos INTEGER NOT NULL DEFAULT 0,
                intentos_fallidos INTEGER NOT NULL DEFAULT 0,
                bloqueado_hasta INTEGER NOT NULL DEFAULT 0,
                creado_en INTEGER NOT NULL
            )"""
        )
        db.execSQL(
            """CREATE TABLE lecturas (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                distancia_cm INTEGER NOT NULL,
                en_rango INTEGER NOT NULL,
                timestamp INTEGER NOT NULL
            )"""
        )
        db.execSQL(
            """CREATE TABLE canjes (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                username TEXT NOT NULL,
                codigo TEXT NOT NULL UNIQUE,
                distancia_cm INTEGER NOT NULL,
                puntos INTEGER NOT NULL,
                timestamp INTEGER NOT NULL
            )"""
        )
        DEMO_USERS.forEach { insertUser(db, it, DEMO_PASSWORD) }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Versión inicial del esquema v2: sin migraciones pendientes.
    }

    // ------------------------------------------------------------------ usuarios

    private fun insertUser(db: SQLiteDatabase, username: String, password: String): Boolean {
        val salt = PasswordHasher.newSalt()
        val values = ContentValues().apply {
            put("username", username)
            put("password_hash", PasswordHasher.hash(password.toCharArray(), salt))
            put("salt", salt)
            put("creado_en", System.currentTimeMillis())
        }
        return db.insert("usuarios", null, values) != -1L
    }

    /** Registra un usuario. Devuelve false si el nombre ya existe. */
    fun registerUser(username: String, password: String): Boolean =
        insertUser(writableDatabase, username, password)

    /** Autentica con bloqueo temporal tras [MAX_FAILED_ATTEMPTS] intentos fallidos. */
    fun authenticate(username: String, password: String): AuthResult {
        val db = writableDatabase
        val now = System.currentTimeMillis()

        db.rawQuery(
            "SELECT password_hash, salt, intentos_fallidos, bloqueado_hasta FROM usuarios WHERE username = ?",
            arrayOf(username)
        ).use { c ->
            if (!c.moveToFirst()) {
                // Usuario inexistente: se calcula un hash igualmente para que el
                // tiempo de respuesta no revele qué usuarios existen.
                PasswordHasher.hash(password.toCharArray(), PasswordHasher.newSalt())
                return AuthResult.Invalid(attemptsLeft = null)
            }
            val hash = c.getString(0)
            val salt = c.getString(1)
            val failed = c.getInt(2)
            val lockedUntil = c.getLong(3)

            if (lockedUntil > now) {
                return AuthResult.Locked((lockedUntil - now + 999) / 1000)
            }

            if (PasswordHasher.verify(password.toCharArray(), salt, hash)) {
                db.execSQL(
                    "UPDATE usuarios SET intentos_fallidos = 0, bloqueado_hasta = 0 WHERE username = ?",
                    arrayOf(username)
                )
                return AuthResult.Success
            }

            val newFailed = failed + 1
            return if (newFailed >= MAX_FAILED_ATTEMPTS) {
                db.execSQL(
                    "UPDATE usuarios SET intentos_fallidos = 0, bloqueado_hasta = ? WHERE username = ?",
                    arrayOf<Any>(now + LOCK_DURATION_MS, username)
                )
                AuthResult.Locked(LOCK_DURATION_MS / 1000)
            } else {
                db.execSQL(
                    "UPDATE usuarios SET intentos_fallidos = ? WHERE username = ?",
                    arrayOf<Any>(newFailed, username)
                )
                AuthResult.Invalid(attemptsLeft = MAX_FAILED_ATTEMPTS - newFailed)
            }
        }
    }

    fun getPoints(username: String): Int =
        readableDatabase.rawQuery(
            "SELECT puntos FROM usuarios WHERE username = ?", arrayOf(username)
        ).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }

    // ------------------------------------------------------------------ lecturas

    fun insertReading(distanceCm: Int, inRange: Boolean) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("distancia_cm", distanceCm)
            put("en_rango", if (inRange) 1 else 0)
            put("timestamp", System.currentTimeMillis())
        }
        db.insert("lecturas", null, values)
        // Retención acotada: se conservan solo las últimas lecturas
        db.execSQL(
            "DELETE FROM lecturas WHERE id <= (SELECT MAX(id) FROM lecturas) - ?",
            arrayOf<Any>(MAX_STORED_READINGS)
        )
    }

    fun countReadings(): Int =
        readableDatabase.rawQuery("SELECT COUNT(*) FROM lecturas", null)
            .use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }

    // ------------------------------------------------------------------ canjes

    /** Guarda el código de un solo uso y suma los puntos al usuario (transacción). */
    fun registerRedeem(username: String, code: String, distanceCm: Int): Boolean {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val values = ContentValues().apply {
                put("username", username)
                put("codigo", code)
                put("distancia_cm", distanceCm)
                put("puntos", POINTS_PER_REDEEM)
                put("timestamp", System.currentTimeMillis())
            }
            if (db.insert("canjes", null, values) == -1L) return false
            db.execSQL(
                "UPDATE usuarios SET puntos = puntos + ? WHERE username = ?",
                arrayOf<Any>(POINTS_PER_REDEEM, username)
            )
            db.setTransactionSuccessful()
            return true
        } finally {
            db.endTransaction()
        }
    }

    fun lastRedeems(username: String, limit: Int = 5): List<Redeem> =
        readableDatabase.rawQuery(
            "SELECT codigo, distancia_cm, timestamp FROM canjes WHERE username = ? ORDER BY id DESC LIMIT $limit",
            arrayOf(username)
        ).use { c ->
            buildList {
                while (c.moveToNext()) add(Redeem(c.getString(0), c.getInt(1), c.getLong(2)))
            }
        }
}
