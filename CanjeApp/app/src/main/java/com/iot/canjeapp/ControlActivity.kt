package com.iot.canjeapp

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.iot.canjeapp.bluetooth.BluetoothManager
import com.iot.canjeapp.databinding.ActivityControlBinding

/**
 * Pantalla 5 · Control remoto de los actuadores del punto de canje.
 *
 * Comandos enviados (un carácter, ver firmware canje_iot.ino):
 *   LED de validación   '1' ON  / '0' OFF
 *   Buzzer de aviso     'A' ON  / 'X' OFF
 *   Compuerta (servo)   'U' ABRIR / 'D' CERRAR
 * El Arduino confirma cada orden con una línea ">> ..." que se muestra en pantalla.
 * Los controles se deshabilitan si no hay conexión Bluetooth.
 */
class ControlActivity : AppCompatActivity(), BluetoothManager.Listener {

    private lateinit var binding: ActivityControlBinding

    /** true mientras la app cambia un switch por código (no debe reenviar comandos). */
    private var updatingSwitch = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!SessionManager.requireLogin(this)) return
        binding = ActivityControlBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.switchLed.setOnCheckedChangeListener { _, isChecked ->
            if (updatingSwitch) return@setOnCheckedChangeListener
            if (sendOrWarn(if (isChecked) '1' else '0')) {
                updateOnOff(binding.tvLedStatus, isChecked)
            } else {
                setSwitchSilently(binding.switchLed, !isChecked)
            }
        }

        binding.switchAlarm.setOnCheckedChangeListener { _, isChecked ->
            if (updatingSwitch) return@setOnCheckedChangeListener
            if (sendOrWarn(if (isChecked) 'A' else 'X')) {
                updateOnOff(binding.tvAlarmStatus, isChecked)
            } else {
                setSwitchSilently(binding.switchAlarm, !isChecked)
            }
        }

        binding.btnUp.setOnClickListener {
            if (sendOrWarn('U')) binding.tvMotorStatus.setText(R.string.motor_open)
        }
        binding.btnDown.setOnClickListener {
            if (sendOrWarn('D')) binding.tvMotorStatus.setText(R.string.motor_closed)
        }

        binding.btnPrevious.setOnClickListener { finish() }
        binding.btnLogout.setOnClickListener { SessionManager.logoutAndGoToLogin(this) }
    }

    override fun onStart() {
        super.onStart()
        if (SessionManager.isLoggedIn) BluetoothManager.addListener(this)
    }

    override fun onStop() {
        BluetoothManager.removeListener(this)
        super.onStop()
    }

    private fun setSwitchSilently(sw: android.widget.CompoundButton, checked: Boolean) {
        updatingSwitch = true
        sw.isChecked = checked
        updatingSwitch = false
    }

    private fun sendOrWarn(command: Char): Boolean {
        val sent = BluetoothManager.send(command)
        if (!sent) Toast.makeText(this, R.string.bt_not_connected_warning, Toast.LENGTH_SHORT).show()
        return sent
    }

    private fun updateOnOff(view: android.widget.TextView, on: Boolean) {
        view.setText(if (on) R.string.status_on else R.string.status_off)
        view.setTextColor(
            ContextCompat.getColor(this, if (on) R.color.accent_green else R.color.text_secondary)
        )
    }

    // ------------------------------------------------------------ BluetoothManager.Listener

    override fun onStateChanged(state: BluetoothManager.State, deviceName: String?) {
        val connected = state == BluetoothManager.State.CONNECTED
        binding.tvBtStatusControl.text = when (state) {
            BluetoothManager.State.CONNECTED -> getString(R.string.bt_connected, deviceName ?: "HC-05")
            BluetoothManager.State.CONNECTING -> getString(R.string.bt_connecting)
            BluetoothManager.State.DISCONNECTED -> getString(R.string.bt_disconnected)
        }
        binding.tvBtStatusControl.setTextColor(
            ContextCompat.getColor(this, if (connected) R.color.accent_green else R.color.error)
        )
        binding.tvBtWarning.visibility = if (connected) View.GONE else View.VISIBLE
        listOf(binding.switchLed, binding.switchAlarm, binding.btnUp, binding.btnDown)
            .forEach { it.isEnabled = connected }
    }

    override fun onLineReceived(line: String) {
        if (line.startsWith(">>")) {
            binding.tvLastAck.text = getString(R.string.last_ack, line.removePrefix(">>").trim())
        }
    }

    override fun onError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
