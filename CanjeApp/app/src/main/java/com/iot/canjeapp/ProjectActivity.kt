package com.iot.canjeapp

import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.iot.canjeapp.databinding.ActivityProjectBinding

/**
 * Pantalla 3 · Descripción del proyecto, sensor, actuadores y comunicación.
 * Es la primera pantalla protegida: "atrás" aquí significa cerrar sesión.
 */
class ProjectActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProjectBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!SessionManager.requireLogin(this)) return
        binding = ActivityProjectBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnNext.setOnClickListener {
            startActivity(Intent(this, SensorActivity::class.java))
        }
        binding.btnPrevious.setOnClickListener { confirmLogout() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = confirmLogout()
        })
    }

    private fun confirmLogout() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.logout_title)
            .setMessage(R.string.logout_message)
            .setPositiveButton(R.string.btn_logout) { _, _ -> SessionManager.logoutAndGoToLogin(this) }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }
}
