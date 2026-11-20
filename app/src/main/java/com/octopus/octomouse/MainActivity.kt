package com.octopus.octomouse

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.Executors
import kotlin.math.roundToInt

class MainActivity : Activity() {
    private var adapter: BluetoothAdapter? = null
    private var hid: BluetoothHidDevice? = null
    private var connected: BluetoothDevice? = null
    private var registered = false
    private lateinit var status: TextView
    private lateinit var devices: Spinner
    private lateinit var connectButton: Button
    private lateinit var disconnectButton: Button
    private val executor = Executors.newSingleThreadExecutor()
    private val bonded = mutableListOf<BluetoothDevice>()

    private val reportDescriptor = byteArrayOf(
        0x05,0x01,0x09,0x02,0xA1.toByte(),0x01,0x09,0x01,0xA1.toByte(),0x00,
        0x05,0x09,0x19,0x01,0x29,0x03,0x15,0x00,0x25,0x01,0x95.toByte(),0x03,0x75,0x01,0x81.toByte(),0x02,
        0x95.toByte(),0x01,0x75,0x05,0x81.toByte(),0x01,0x05,0x01,0x09,0x30,0x09,0x31,0x09,0x38,
        0x15,0x81.toByte(),0x25,0x7F,0x75,0x08,0x95.toByte(),0x03,0x81.toByte(),0x06,0xC0.toByte(),0xC0.toByte()
    )

    private val callback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registeredNow: Boolean) {
            registered = registeredNow
            runOnUiThread { updateStatus(if (registeredNow) "Ready to connect" else "Bluetooth HID unavailable") }
        }
        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
            if (state == BluetoothProfile.STATE_CONNECTED) connected = device
            if (state == BluetoothProfile.STATE_DISCONNECTED && connected == device) connected = null
            runOnUiThread {
                updateStatus(when (state) {
                    BluetoothProfile.STATE_CONNECTED -> "Connected: ${safeName(device)}"
                    BluetoothProfile.STATE_CONNECTING -> "Connecting…"
                    BluetoothProfile.STATE_DISCONNECTED -> "Disconnected"
                    else -> "Bluetooth status changed"
                })
                refreshButtons()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        if (Build.VERSION.SDK_INT >= 31 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 42)
        } else initializeBluetooth()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 28, 24, 20)
            setBackgroundColor(0xFF0B1020.toInt())
        }
        val title = TextView(this).apply {
            text = "Octo Mouse"
            textSize = 28f
            setTextColor(0xFFF4F7FF.toInt())
            gravity = Gravity.CENTER_VERTICAL
        }
        status = TextView(this).apply {
            text = "Checking Bluetooth…"
            textSize = 14f
            setTextColor(0xFF8FA7CE.toInt())
            setPadding(0, 8, 0, 18)
        }
        devices = Spinner(this)
        val scan = Button(this).apply { text = "Refresh paired devices"; setOnClickListener { refreshDevices() } }
        connectButton = Button(this).apply { text = "Connect"; setOnClickListener { connectSelected() } }
        disconnectButton = Button(this).apply { text = "Disconnect"; setOnClickListener { connected?.let { hid?.disconnect(it) } } }
        val padLabel = TextView(this).apply {
            text = "TOUCHPAD"
            textSize = 12f
            setTextColor(0xFF8FA7CE.toInt())
            setPadding(0, 22, 0, 8)
        }
        val pad = TouchSurface(this) { dx, dy -> sendMouse(dx, dy, 0) }.apply {
            setBackgroundColor(0xFF17233B.toInt())
        }
        val padParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        val clicks = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(Button(context).apply { text = "Left click"; setOnClickListener { click(1) } }, LinearLayout.LayoutParams(0, 56, 1f))
            addView(Button(context).apply { text = "Right click"; setOnClickListener { click(2) } }, LinearLayout.LayoutParams(0, 56, 1f))
        }
        root.addView(title)
        root.addView(status)
        root.addView(devices)
        root.addView(scan)
        root.addView(connectButton)
        root.addView(disconnectButton)
        root.addView(padLabel)
        root.addView(pad, padParams)
        root.addView(clicks)
        setContentView(root)
        refreshButtons()
    }

    private fun initializeBluetooth() {
        adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null) { updateStatus("This device has no Bluetooth adapter"); return }
        if (adapter?.isEnabled != true) { updateStatus("Turn on Bluetooth, then reopen this screen"); return }
        adapter?.getProfileProxy(this, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                if (profile != BluetoothProfile.HID_DEVICE) return
                hid = proxy as BluetoothHidDevice
                val sdp = BluetoothHidDeviceAppSdpSettings(
                    "Octo Mouse", "Bluetooth mouse from your phone", "Octopus205",
                    BluetoothHidDevice.SUBCLASS1_MOUSE, reportDescriptor
                )
                hid?.registerApp(sdp, null, null, executor, callback)
                refreshDevices()
            }
            override fun onServiceDisconnected(profile: Int) {
                hid = null
                registered = false
                connected = null
                updateStatus("Bluetooth HID service disconnected")
                refreshButtons()
            }
        }, BluetoothProfile.HID_DEVICE)
        updateStatus("Preparing Bluetooth mouse…")
    }

    private fun refreshDevices() {
        try {
            bonded.clear()
            bonded.addAll(adapter?.bondedDevices?.sortedBy { safeName(it) } ?: emptyList())
            val labels = if (bonded.isEmpty()) listOf("Pair a computer or tablet in Android Bluetooth settings first")
                else bonded.map { "${safeName(it)}  •  ${it.address}" }
            devices.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
        } catch (_: SecurityException) { updateStatus("Bluetooth permission is required") }
    }

    private fun connectSelected() {
        val device = bonded.getOrNull(devices.selectedItemPosition) ?: run {
            toast("Pair the target device in Bluetooth settings first"); return
        }
        if (!registered) { toast("HID is not ready on this phone"); return }
        val ok = try { hid?.connect(device) == true } catch (_: SecurityException) { false }
        updateStatus(if (ok) "Connection requested…" else "Could not start connection")
    }

    private fun sendMouse(dx: Int, dy: Int, wheel: Int) {
        val device = connected ?: return
        val report = byteArrayOf(0, dx.coerceIn(-127,127).toByte(), dy.coerceIn(-127,127).toByte(), wheel.coerceIn(-127,127).toByte())
        try { hid?.sendReport(device, 0, report) } catch (_: SecurityException) { }
    }

    private fun click(button: Int) {
        val device = connected ?: run { toast("Connect to a device first"); return }
        try {
            hid?.sendReport(device, 0, byteArrayOf(button.toByte(),0,0,0))
            hid?.sendReport(device, 0, byteArrayOf(0,0,0,0))
        } catch (_: SecurityException) { }
    }

    private fun safeName(device: BluetoothDevice): String = try { device.name ?: "Bluetooth device" } catch (_: SecurityException) { "Bluetooth device" }
    private fun updateStatus(message: String) { if (::status.isInitialized) status.text = message; refreshButtons() }
    private fun refreshButtons() {
        if (::connectButton.isInitialized) connectButton.isEnabled = registered && connected == null && bonded.isNotEmpty()
        if (::disconnectButton.isInitialized) disconnectButton.isEnabled = connected != null
    }
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 42 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) initializeBluetooth()
        else updateStatus("Bluetooth permission was not granted")
    }

    override fun onDestroy() {
        try { hid?.unregisterApp(); adapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, hid) } catch (_: Exception) { }
        executor.shutdown()
        super.onDestroy()
    }

    private class TouchSurface(context: Context, val move: (Int, Int) -> Unit) : View(context) {
        private var lastX = 0f
        private var lastY = 0f
        private var hasPoint = false
        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { lastX = event.x; lastY = event.y; hasPoint = true; return true }
                MotionEvent.ACTION_MOVE -> if (hasPoint) {
                    val dx = ((event.x - lastX) * 1.4f).roundToInt()
                    val dy = ((event.y - lastY) * 1.4f).roundToInt()
                    if (dx != 0 || dy != 0) move(dx, dy)
                    lastX = event.x; lastY = event.y
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { hasPoint = false; performClick(); return true }
            }
            return true
        }
        override fun performClick(): Boolean { super.performClick(); return true }
    }
}
