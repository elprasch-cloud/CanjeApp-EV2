package com.iot.canjeapp.security

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Derivación de contraseñas con PBKDF2-HMAC-SHA256 + sal aleatoria por usuario.
 *
 * Por qué no "SHA-256" a secas: un hash rápido sin sal permite ataques de
 * diccionario y tablas arcoíris si alguien extrae la base SQLite. PBKDF2 con
 * sal única e iteraciones hace cada intento costoso (OWASP MASVS-CRYPTO,
 * ISO/IEC 27400 — protección de credenciales). Importante: esto es un HASH,
 * no un cifrado; la contraseña original no se puede recuperar.
 */
object PasswordHasher {

    private const val ALGORITHM = "PBKDF2WithHmacSHA256"   // disponible desde API 26
    private const val ITERATIONS = 60_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_BYTES = 16

    fun newSalt(): String {
        val salt = ByteArray(SALT_BYTES)
        SecureRandom().nextBytes(salt)
        return Base64.encodeToString(salt, Base64.NO_WRAP)
    }

    fun hash(password: CharArray, saltB64: String): String {
        val salt = Base64.decode(saltB64, Base64.NO_WRAP)
        val spec = PBEKeySpec(password, salt, ITERATIONS, KEY_LENGTH_BITS)
        try {
            val key = SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
            return Base64.encodeToString(key, Base64.NO_WRAP)
        } finally {
            spec.clearPassword()
        }
    }

    /** Comparación en tiempo constante para no filtrar información por tiempos. */
    fun verify(password: CharArray, saltB64: String, expectedHashB64: String): Boolean {
        val actual = Base64.decode(hash(password, saltB64), Base64.NO_WRAP)
        val expected = Base64.decode(expectedHashB64, Base64.NO_WRAP)
        return MessageDigest.isEqual(actual, expected)
    }
}
