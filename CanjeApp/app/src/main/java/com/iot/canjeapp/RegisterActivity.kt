package com.iot.canjeapp

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.iot.canjeapp.databinding.ActivityRegisterBinding
import com.iot.canjeapp.db.DatabaseHelper
import com.iot.canjeapp.security.CredentialPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Pantalla 2b · Registro de usuarios locales aplicando la política de credenciales. */
class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnRegister.setOnClickListener { attemptRegister() }
        binding.btnBackLogin.setOnClickListener { finish() }   // vuelve al Login
    }

    private fun attemptRegister() {
        binding.tilRegUsername.error = null
        binding.tilRegPassword.error = null
        binding.tilRegConfirm.error = null

        val username = CredentialPolicy.normalizeUsername(binding.etRegUsername.text?.toString().orEmpty())
        val password = binding.etRegPassword.text?.toString().orEmpty()
        val confirm = binding.etRegConfirmPassword.text?.toString().orEmpty()

        if (username.isEmpty() || password.isEmpty() || confirm.isEmpty()) {
            Toast.makeText(this, R.string.fields_empty, Toast.LENGTH_SHORT).show()
            return
        }
        if (!CredentialPolicy.isValidUsername(username)) {
            binding.tilRegUsername.error = getString(R.string.username_invalid)
            return
        }
        CredentialPolicy.passwordProblem(password)?.let {
            binding.tilRegPassword.error = it
            return
        }
        if (password != confirm) {
            binding.tilRegConfirm.error = getString(R.string.passwords_mismatch)
            return
        }

        binding.btnRegister.isEnabled = false
        lifecycleScope.launch {
            val created = withContext(Dispatchers.IO) {
                DatabaseHelper.get(this@RegisterActivity).registerUser(username, password)
            }
            binding.btnRegister.isEnabled = true
            if (created) {
                Toast.makeText(this@RegisterActivity, R.string.register_success, Toast.LENGTH_LONG).show()
                finish()
            } else {
                binding.tilRegUsername.error = getString(R.string.register_failed)
            }
        }
    }
}
