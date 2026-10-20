package com.iot.canjeapp.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.UUID
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Conexión Bluetooth clásica (SPP/RFCOMM) con el módulo HC-05/HC-06 del Arduino.
 *
 * Es un singleton para que la MISMA conexión se comparta entre la pantalla de
 * sensores (que la abre) y la pantalla de control (que envía comandos).
 * Las pantallas se suscriben con [addListener] en onStart y se desuscriben en
 * onStop; los eventos llegan siempre en el hilo principal (UI).
 */
object BluetoothManager {

    /** UUID estándar del Serial Port Profile (SPP). */
    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    enum class State { DISCONNECTED, CONNECTING, CONNECTED }

    interface Listener {
        fun onStateChanged(state: State, deviceName: String?)
        fun onLineReceived(line: String)
        fun onError(message: String) {}
    }

    private val listeners = CopyOnWriteArraySet<Listener>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var socket: BluetoothSocket? = null
    private var readJob: Job? = null

    @Volatile var state: State = State.DISCONNECTED
        private set
    @Volatile var deviceName: String? = null
        private set

    val isConnected: Boolean get() = state == State.CONNECTED

    fun addListener(l: Listener) {
        listeners.add(l)
        l.onStateChanged(state, deviceName)          // estado actual inmediato
    }

    fun removeListener(l: Listener) {
        listeners.remove(l)
    }

    fun adapter(context: Context): BluetoothAdapter? =
        context.getSystemService(android.bluetooth.BluetoothManager::class.java)?.adapter

    /** En Android 12+ (API 31) se exige BLUETOOTH_CONNECT en tiempo de ejecución. */
    fun hasConnectPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED

    /** Dispositivos ya vinculados en Ajustes (no se escanea: mínimo privilegio). */
    @SuppressLint("MissingPermission")
    fun bondedDevices(context: Context): List<BluetoothDevice> {
        if (!hasConnectPermission(context)) return emptyList()
        return adapter(context)?.bondedDevices?.toList().orEmpty()
    }

    @SuppressLint("MissingPermission")
    fun displayName(context: Context, device: BluetoothDevice): String =
        if (hasConnectPermission(context)) device.name ?: device.address else device.address

    @SuppressLint("MissingPermission")
    fun connect(context: Context, device: BluetoothDevice) {
        if (!hasConnectPermission(context) || state != State.DISCONNECTED) return
        val name = displayName(context, device)
        setState(State.CONNECTING, name)

        scope.launch {
            try {
                val s = try {
                    device.createRfcommSocketToServiceRecord(SPP_UUID).also { it.connect() }
                } catch (secureFail: IOException) {
                    // Algunos HC-05/06 solo aceptan el canal RFCOMM "inseguro"
                    device.createInsecureRfcommSocketToServiceRecord(SPP_UUID).also { it.connect() }
                }
                if (state != State.CONNECTING) {     // el usuario canceló / cerró sesión
                    try { s.close() } catch (ignored: IOException) { }
                    return@launch
                }
                socket = s
                setState(State.CONNECTED, name)
                startReading(s)
            } catch (e: Exception) {   // IOException o SecurityException (permiso revocado)
                closeSocket()
                setState(State.DISCONNECTED, null)
                postError("No se pudo conectar con $name")
            }
        }
    }

    private fun startReading(s: BluetoothSocket) {
        readJob = scope.launch {
            try {
                val reader = s.inputStream.bufferedReader(Charsets.US_ASCII)
                while (isActive) {
                    val line = reader.readLine() ?: break        // null = enlace cerrado
                    val clean = line.trim()
                    if (clean.isNotEmpty()) {
                        mainHandler.post { listeners.forEach { it.onLineReceived(clean) } }
                    }
                }
            } catch (lost: IOException) {
                // Enlace perdido (Arduino apagado / fuera de alcance)
            }
            if (state == State.CONNECTED) {
                closeSocket()
                setState(State.DISCONNECTED, null)
                postError("Conexión Bluetooth perdida")
            }
        }
    }

    /** Envía un comando de un carácter. Devuelve false si no hay conexión. */
    fun send(command: Char): Boolean {
        val s = socket ?: return false
        if (state != State.CONNECTED) return false
        scope.launch {
            try {
                s.outputStream.write(command.code)
                s.outputStream.flush()
            } catch (writeError: IOException) {
                postError("No se pudo enviar el comando '$command'")
            }
        }
        return true
    }

    fun disconnect() {
        // Primero el estado: así el hilo lector no lo informa como "conexión perdida"
        if (state != State.DISCONNECTED) setState(State.DISCONNECTED, null)
        readJob?.cancel()
        readJob = null
        closeSocket()
    }

    private fun closeSocket() {
        try { socket?.close() } catch (ignored: IOException) { }
        socket = null
    }

    private fun setState(newState: State, name: String?) {
        state = newState
        deviceName = name
        mainHandler.post { listeners.forEach { it.onStateChanged(newState, name) } }
    }

    private fun postError(msg: String) {
        mainHandler.post { listeners.forEach { it.onError(msg) } }
    }
}
