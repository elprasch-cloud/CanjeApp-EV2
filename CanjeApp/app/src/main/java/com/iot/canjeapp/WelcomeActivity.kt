package com.iot.canjeapp

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.iot.canjeapp.databinding.ActivityWelcomeBinding

/** Pantalla 1 · Título del proyecto e integrantes. Única pantalla pública junto al login. */
class WelcomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWelcomeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWelcomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnNext.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }
    }
}
