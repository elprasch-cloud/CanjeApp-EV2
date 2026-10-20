package com.iot.canjeapp

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.iot.canjeapp.databinding.ActivityLoginBinding
import com.iot.canjeapp.db.DatabaseHelper
import com.iot.canjeapp.db.DatabaseHelper.AuthResult
import com.iot.canjeapp.security.CredentialPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Pantalla 2 · Control de acceso local.
 * Valida usuario/contraseña contra SQLite (hash PBKDF2 + sal) con bloqueo
 * temporal tras 3 intentos fallidos. Es el único camino hacia las pantallas
 * protegidas (Proyecto, Monitoreo y Control).
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var db: DatabaseHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        db = DatabaseHelper.get(this)

        binding.btnLogin.setOnClickListener { attemptLogin() }
        binding.btnGoRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
        binding.btnPrevious.setOnClickListener {
            startActivity(
                Intent(this, WelcomeActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
            finish()
        }
    }

    private fun attemptLogin() {
        binding.tilUsername.error = null
        binding.tilPassword.error = null

        val username = CredentialPolicy.normalizeUsername(binding.etUsername.text?.toString().orEmpty())
        val password = binding.etPassword.text?.toString().orEmpty()

        if (username.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, R.string.fields_empty, Toast.LENGTH_SHORT).show()
            return
        }

        setLoading(true)
        // PBKDF2 es costoso a propósito: se ejecuta fuera del hilo de UI
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { db.authenticate(username, password) }
            setLoading(false)
            when (result) {
                is AuthResult.Success -> onLoginSuccess(username)
                is AuthResult.Invalid -> {
                    binding.etPassword.setText("")
                    binding.tilPassword.error = result.attemptsLeft
                        ?.let { getString(R.string.login_failed_attempts, it) }
                        ?: getString(R.string.login_failed)
                }
                is AuthResult.Locked -> {
                    binding.etPassword.setText("")
                    binding.tilPassword.error =
                        getString(R.string.login_locked, result.secondsLeft.toInt())
                }
            }
        }
    }

    private fun onLoginSuccess(username: String) {
        SessionManager.login(username)
        Toast.makeText(this, getString(R.string.login_success, username), Toast.LENGTH_SHORT).show()
        // Se limpia la pila: "atrás" ya no puede volver al login con la sesión abierta
        startActivity(
            Intent(this, ProjectActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        finish()
    }

    private fun setLoading(loading: Boolean) {
        binding.btnLogin.isEnabled = !loading
        binding.btnLogin.text = getString(if (loading) R.string.login_checking else R.string.btn_login)
    }
}
