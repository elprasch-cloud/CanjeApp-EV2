package com.iot.canjeapp

import android.app.Activity
import android.content.Intent

/**
 * Sesión del usuario autenticado (control de acceso local).
 *
 * La sesión vive solo en memoria: si el sistema destruye el proceso, el usuario
 * debe autenticarse de nuevo (falla segura, ISO/IEC 27400 — control de acceso).
 * Toda pantalla protegida llama a [requireLogin] en onCreate().
 */
object SessionManager {

    var currentUser: String? = null
        private set

    val isLoggedIn: Boolean get() = currentUser != null

    fun login(username: String) {
        currentUser = username
    }

    fun logout() {
        currentUser = null
    }

    /**
     * Si no hay sesión activa, envía al Login, limpia la pila de pantallas y
     * devuelve false para que la Activity llamadora no siga construyéndose.
     */
    fun requireLogin(activity: Activity): Boolean {
        if (isLoggedIn) return true
        val intent = Intent(activity, LoginActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        activity.startActivity(intent)
        activity.finish()
        return false
    }

    /** Cierra sesión, corta el Bluetooth y vuelve al Login sin historial. */
    fun logoutAndGoToLogin(activity: Activity) {
        logout()
        com.iot.canjeapp.bluetooth.BluetoothManager.disconnect()
        val intent = Intent(activity, LoginActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        activity.startActivity(intent)
        activity.finish()
    }
}
