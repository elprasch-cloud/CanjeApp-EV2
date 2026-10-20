package com.iot.canjeapp

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.iot.canjeapp.bluetooth.BluetoothManager
import com.iot.canjeapp.databinding.ActivitySensorBinding
import com.iot.canjeapp.db.DatabaseHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pantalla 4 · Monitoreo del sensor HC-SR04.
 *
 * Flujo: Arduino --"Distancia: N cm"--> Bluetooth --> esta pantalla
 *   1. Muestra la distancia y si el usuario está EN RANGO (2–30 cm).
 *   2. Guarda cada lectura en SQLite (tabla lecturas).
 *   3. Al pasar de FUERA a EN RANGO genera un código de un solo uso,
 *      lo guarda en la tabla canjes y suma puntos al usuario.
 *   4. Consulta y muestra los últimos canjes desde SQLite.
 */
class SensorActivity : AppCompatActivity(), BluetoothManager.Listener {

    companion object {
        const val MIN_VALID_CM = 2               // límite inferior del HC-SR04
        const val REDEEM_THRESHOLD_CM = 30       // distancia máxima para canjear
        const val REDEEM_COOLDOWN_MS = 10_000L   // evita códigos repetidos por rebote
        private val DISTANCE_REGEX = Regex("""Distancia:\s*(-?\d+)\s*cm""")
        private const val CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // sin 0/O ni 1/I
    }

    private lateinit var binding: ActivitySensorBinding
    private lateinit var db: DatabaseHelper
    private lateinit var username: String
    private val random = SecureRandom()

    private var wasInRange = false
    private var lastRedeemAt = 0L

    // Permiso BLUETOOTH_CONNECT (Android 12+)
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startConnectFlow()
            else Toast.makeText(this, R.string.bt_permission_denied, Toast.LENGTH_LONG).show()
        }

    // Solicitud al usuario para encender el Bluetooth
    private val enableBtLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (BluetoothManager.adapter(this)?.isEnabled == true) showDevicePicker()
            else Toast.makeText(this, R.string.bt_disabled, Toast.LENGTH_SHORT).show()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!SessionManager.requireLogin(this)) return
        binding = ActivitySensorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = DatabaseHelper.get(this)
        username = SessionManager.currentUser!!
        binding.tvSessionUser.text = getString(R.string.session_user, username)
        binding.tvRedeemInfo.text = getString(R.string.redeem_waiting, REDEEM_THRESHOLD_CM)

        binding.btnConnectBt.setOnClickListener {
            if (BluetoothManager.state == BluetoothManager.State.DISCONNECTED) startConnectFlow()
            else BluetoothManager.disconnect()
        }
        binding.btnPrevious.setOnClickListener { finish() }
        binding.btnNext.setOnClickListener {
            startActivity(Intent(this, ControlActivity::class.java))
        }

        refreshFromDatabase()
    }

    override fun onStart() {
        super.onStart()
        if (SessionManager.isLoggedIn) BluetoothManager.addListener(this)
    }

    override fun onStop() {
        BluetoothManager.removeListener(this)
        super.onStop()
    }

    // ------------------------------------------------------------ Bluetooth

    private fun startConnectFlow() {
        val adapter = BluetoothManager.adapter(this)
        if (adapter == null) {
            Toast.makeText(this, R.string.bt_not_supported, Toast.LENGTH_LONG).show()
            return
        }
        if (!BluetoothManager.hasConnectPermission(this)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
            }
            return
        }
        if (!adapter.isEnabled) {
            enableBtLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            return
        }
        showDevicePicker()
    }

    private fun showDevicePicker() {
        val devices = BluetoothManager.bondedDevices(this)
        if (devices.isEmpty()) {
            Toast.makeText(this, R.string.bt_no_devices, Toast.LENGTH_LONG).show()
            return
        }
        val names = devices.map { BluetoothManager.displayName(this, it) }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.bt_select_device)
            .setItems(names) { _, which -> BluetoothManager.connect(this, devices[which]) }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    override fun onStateChanged(state: BluetoothManager.State, deviceName: String?) {
        when (state) {
            BluetoothManager.State.CONNECTED -> {
                binding.tvBtStatus.text = getString(R.string.bt_connected, deviceName ?: "HC-05")
                binding.tvBtStatus.setTextColor(ContextCompat.getColor(this, R.color.accent_green))
                binding.btnConnectBt.setText(R.string.btn_disconnect_bt)
            }
            BluetoothManager.State.CONNECTING -> {
                binding.tvBtStatus.setText(R.string.bt_connecting)
                binding.tvBtStatus.setTextColor(ContextCompat.getColor(this, R.color.accent_amber))
                binding.btnConnectBt.setText(R.string.btn_cancel)
            }
            BluetoothManager.State.DISCONNECTED -> {
                binding.tvBtStatus.setText(R.string.bt_disconnected)
                binding.tvBtStatus.setTextColor(ContextCompat.getColor(this, R.color.error))
                binding.btnConnectBt.setText(R.string.btn_connect_bt)
                binding.tvDistance.setText(R.string.value_default_distance)
                binding.tvPresence.setText(R.string.value_default)
                wasInRange = false
            }
        }
    }

    override fun onError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    // ------------------------------------------------------------ Telemetría

    override fun onLineReceived(line: String) {
        val match = DISTANCE_REGEX.find(line) ?: return   // las respuestas ">>" se muestran en Control
        val distance = match.groupValues[1].toIntOrNull() ?: return

        val valid = distance >= MIN_VALID_CM
        val inRange = valid && distance <= REDEEM_THRESHOLD_CM

        binding.tvDistance.text =
            if (valid) getString(R.string.value_distance, distance) else getString(R.string.value_no_echo)
        binding.tvPresence.setText(if (inRange) R.string.presence_in else R.string.presence_out)
        binding.tvPresence.setTextColor(
            ContextCompat.getColor(this, if (inRange) R.color.accent_green else R.color.text_primary)
        )

        val now = SystemClock.elapsedRealtime()
        val shouldRedeem = inRange && !wasInRange && now - lastRedeemAt >= REDEEM_COOLDOWN_MS
        wasInRange = inRange
        if (shouldRedeem) lastRedeemAt = now

        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                if (valid) db.insertReading(distance, inRange)
                if (shouldRedeem) registerRedeem(distance)
            }
            refreshFromDatabase()
        }
    }

    /** Genera un código XXXX-XXXX con SecureRandom y lo registra (reintenta si colisiona). */
    private fun registerRedeem(distance: Int): String? {
        repeat(3) {
            val code = newCode()
            if (db.registerRedeem(username, code, distance)) return code
        }
        return null
    }

    private fun newCode(): String {
        val chars = CharArray(8) { CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)] }
        return String(chars, 0, 4) + "-" + String(chars, 4, 4)
    }

    // ------------------------------------------------------------ Consulta SQLite

    private fun refreshFromDatabase() {
        lifecycleScope.launch {
            val (readings, points, redeems) = withContext(Dispatchers.IO) {
                Triple(db.countReadings(), db.getPoints(username), db.lastRedeems(username))
            }
            binding.tvReadings.text = readings.toString()
            binding.tvPoints.text = points.toString()

            val last = redeems.firstOrNull()
            if (last != null) {
                binding.tvRedeemCode.text = last.code
                binding.tvRedeemInfo.text =
                    getString(R.string.redeem_generated, DatabaseHelper.POINTS_PER_REDEEM)
            }

            val fmt = SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault())
            binding.tvHistory.text =
                if (redeems.isEmpty()) getString(R.string.history_empty)
                else redeems.joinToString("\n") {
                    "${it.code}  ${fmt.format(Date(it.timestamp))}  ${it.distanceCm} cm"
                }
        }
    }
}
