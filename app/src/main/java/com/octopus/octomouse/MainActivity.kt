package com.octopus.octomouse

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.HapticFeedbackConstants
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
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
    private var sensitivity = 1.5f
    private lateinit var status: TextView
    private lateinit var statusPill: TextView
    private lateinit var devices: Spinner
    private lateinit var connectButton: Button
    private lateinit var disconnectButton: Button
    private lateinit var leftButton: Button
    private lateinit var rightButton: Button
    private lateinit var middleButton: Button
    private lateinit var pad: TouchSurface
    private lateinit var sensitivityValue: TextView
    private val executor = Executors.newSingleThreadExecutor()
    private val bonded = mutableListOf<BluetoothDevice>()

    private val bg = Color.rgb(9, 14, 27)
    private val card = Color.rgb(18, 27, 47)
    private val raised = Color.rgb(27, 40, 66)
    private val blue = Color.rgb(52, 130, 246)
    private val textMain = Color.rgb(240, 245, 255)
    private val textMuted = Color.rgb(151, 169, 199)
    private val green = Color.rgb(54, 211, 153)

    private val reportDescriptor = byteArrayOf(
        0x05,0x01,0x09,0x02,0xA1.toByte(),0x01,0x09,0x01,0xA1.toByte(),0x00,
        0x05,0x09,0x19,0x01,0x29,0x03,0x15,0x00,0x25,0x01,0x95.toByte(),0x03,0x75,0x01,0x81.toByte(),0x02,
        0x95.toByte(),0x01,0x75,0x05,0x81.toByte(),0x01,0x05,0x01,0x09,0x30,0x09,0x31,0x09,0x38,
        0x15,0x81.toByte(),0x25,0x7F,0x75,0x08,0x95.toByte(),0x03,0x81.toByte(),0x06,0xC0.toByte(),0xC0.toByte()
    )

    private val callback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registeredNow: Boolean) {
            registered = registeredNow
            runOnUiThread {
                updateStatus(if (registeredNow) "Mouse service is ready. Pair from the other device." else "Bluetooth mouse service is unavailable")
            }
        }

        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
            if (state == BluetoothProfile.STATE_CONNECTED) connected = device
            if (state == BluetoothProfile.STATE_DISCONNECTED && connected == device) connected = null
            runOnUiThread {
                updateStatus(when (state) {
                    BluetoothProfile.STATE_CONNECTED -> "Connected to ${safeName(device)}"
                    BluetoothProfile.STATE_CONNECTING -> "Connecting to ${safeName(device)}…"
                    BluetoothProfile.STATE_DISCONNECTED -> "Disconnected"
                    else -> "Bluetooth connection status changed"
                })
                refreshButtons()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        buildUi()
        if (Build.VERSION.SDK_INT >= 31 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 42)
        } else initializeBluetooth()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    private fun rounded(color: Int, radius: Int = 22, stroke: Int = 0): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            if (stroke != 0) setStroke(dp(1), stroke)
        }

    private fun label(value: String, size: Float = 14f, color: Int = textMain, bold: Boolean = false): TextView =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

    private fun sectionTitle(value: String): TextView = label(value, 16f, textMain, true).apply {
        setPadding(0, dp(2), 0, dp(12))
    }

    private fun makeButton(value: String, color: Int = raised, accent: Boolean = false): Button =
        Button(this).apply {
            text = value
            textSize = 14f
            isAllCaps = false
            setTextColor(if (accent) Color.WHITE else textMain)
            background = rounded(if (accent) blue else color, 18)
            minHeight = dp(54)
            minimumHeight = dp(54)
            stateListAnimator = null
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }

    private fun cardLayout(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(18), dp(18), dp(18))
        background = rounded(card, 24)
    }

    private fun addGap(parent: LinearLayout, height: Int) {
        parent.addView(View(this), LinearLayout.LayoutParams(1, dp(height)))
    }

    private fun buildUi() {
        val scroll = ScrollView(this).apply {
            fillViewport = true
            clipToPadding = false
            isVerticalScrollBarEnabled = true
            setPadding(dp(16), dp(14), dp(16), dp(28))
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(18))
        }
        header.addView(label("OCTOPUS  •  WIRELESS CONTROL", 11f, blue, true))
        header.addView(label("Octo Mouse", 30f, textMain, true))
        header.addView(label("Your phone becomes a Bluetooth mouse", 14f, textMuted))
        root.addView(header)

        val connectionCard = cardLayout()
        val connectionHeading = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        connectionHeading.addView(sectionTitle("Bluetooth connection"), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        statusPill = label("CHECKING", 10f, textMuted, true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(7), dp(10), dp(7))
            background = rounded(raised, 30)
        }
        connectionHeading.addView(statusPill)
        connectionCard.addView(connectionHeading)
        status = label("Preparing Bluetooth…", 13f, textMuted)
        connectionCard.addView(status)
        addGap(connectionCard, 12)
        connectionCard.addView(label("PAIRED DEVICES", 11f, textMuted, true))
        devices = Spinner(this).apply {
            background = rounded(raised, 14)
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }
        connectionCard.addView(devices, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)))
        addGap(connectionCard, 10)
        val pairRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val pairSettings = makeButton("Pair new device")
        pairSettings.setOnClickListener { startActivity(Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)) }
        pairRow.addView(pairSettings, LinearLayout.LayoutParams(0, dp(54), 1f).apply { marginEnd = dp(8) })
        val discoverable = makeButton("Make visible", blue, true)
        discoverable.setOnClickListener { requestDiscoverable() }
        pairRow.addView(discoverable, LinearLayout.LayoutParams(0, dp(54), 1f))
        connectionCard.addView(pairRow)
        addGap(connectionCard, 9)
        val actionRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val refresh = makeButton("Refresh list")
        refresh.setOnClickListener { refreshDevices() }
        actionRow.addView(refresh, LinearLayout.LayoutParams(0, dp(50), 1f).apply { marginEnd = dp(8) })
        connectButton = makeButton("Connect", blue, true)
        connectButton.setOnClickListener { connectSelected() }
        actionRow.addView(connectButton, LinearLayout.LayoutParams(0, dp(50), 1f))
        connectionCard.addView(actionRow)
        addGap(connectionCard, 8)
        disconnectButton = makeButton("Disconnect")
        disconnectButton.setOnClickListener { connected?.let { hid?.disconnect(it) } }
        connectionCard.addView(disconnectButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)))
        root.addView(connectionCard)
        addGap(root, 16)

        val controlCard = cardLayout()
        controlCard.addView(sectionTitle("Mouse control"))
        controlCard.addView(label("Move one finger to move the pointer. Tap to click.", 12f, textMuted))
        addGap(controlCard, 12)
        pad = TouchSurface(this) { dx, dy, wheel -> sendMouse((dx * sensitivity).roundToInt(), (dy * sensitivity).roundToInt(), wheel) }.apply {
            background = rounded(raised, 22, 0xFF334A70.toInt())
            contentDescription = "Touchpad. Drag to move, tap to left click, two-finger tap for right click, two-finger drag to scroll."
        }
        val padFrame = FrameLayout(this).apply {
            background = rounded(raised, 22)
            addView(pad, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(238)))
            addView(label("TOUCHPAD", 10f, textMuted, true).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                setPadding(0, 0, 0, dp(12))
            }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        controlCard.addView(padFrame, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(238)))
        addGap(controlCard, 12)
        controlCard.addView(label("Dedicated buttons • press and hold to drag", 12f, textMuted))
        addGap(controlCard, 9)
        val clickRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        leftButton = makeButton("◉  Left click", blue, true)
        leftButton.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { buttonDown(1); v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP); true }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { buttonUp(); true }
                else -> true
            }
        }
        rightButton = makeButton("◎  Right click")
        rightButton.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { buttonDown(2); v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP); true }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { buttonUp(); true }
                else -> true
            }
        }
        clickRow.addView(leftButton, LinearLayout.LayoutParams(0, dp(66), 1f).apply { marginEnd = dp(8) })
        clickRow.addView(rightButton, LinearLayout.LayoutParams(0, dp(66), 1f))
        controlCard.addView(clickRow)
        addGap(controlCard, 8)
        middleButton = makeButton("Middle click")
        middleButton.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { buttonDown(4); true }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { buttonUp(); true }
                else -> true
            }
        }
        controlCard.addView(middleButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)))
        root.addView(controlCard)
        addGap(root, 16)

        val settingsCard = cardLayout()
        settingsCard.addView(sectionTitle("Preferences"))
        val speedHeader = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        speedHeader.addView(label("Pointer sensitivity", 14f, textMain, true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        sensitivityValue = label("1.5×", 13f, blue, true)
        speedHeader.addView(sensitivityValue)
        settingsCard.addView(speedHeader)
        val speed = SeekBar(this).apply {
            max = 35
            progress = 10
            progressTintList = android.content.res.ColorStateList.valueOf(blue)
            thumbTintList = android.content.res.ColorStateList.valueOf(blue)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    sensitivity = 0.5f + progress / 10f
                    sensitivityValue.text = String.format("%.1f×", sensitivity)
                    getSharedPreferences("octo_mouse", MODE_PRIVATE).edit().putFloat("sensitivity", sensitivity).apply()
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        sensitivity = getSharedPreferences("octo_mouse", MODE_PRIVATE).getFloat("sensitivity", 1.5f).coerceIn(0.5f, 4f)
        speed.progress = ((sensitivity - 0.5f) * 10f).roundToInt()
        settingsCard.addView(speed)
        addGap(settingsCard, 8)
        settingsCard.addView(label("Gestures", 13f, textMain, true))
        settingsCard.addView(label("• One-finger drag: move pointer\n• One-finger tap: left click\n• Two-finger tap: right click\n• Two-finger drag: scroll vertically", 12f, textMuted).apply { setLineSpacing(dp(3).toFloat(), 1f) })
        root.addView(settingsCard)
        addGap(root, 16)
        root.addView(label("Octo Mouse • Bluetooth HID", 11f, textMuted).apply { gravity = Gravity.CENTER; setPadding(0, dp(6), 0, dp(10)) })

        scroll.addView(root)
        setContentView(scroll)
        refreshButtons()
        refreshDevices()
    }

    private fun requestDiscoverable() {
        try {
            startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
                putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 180)
            })
            toast("Allow visibility, then select this phone from the other device.")
        } catch (_: Exception) {
            toast("Open Bluetooth settings and make this phone visible.")
            startActivity(Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS))
        }
    }

    private fun initializeBluetooth() {
        adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null) { updateStatus("This phone has no Bluetooth adapter"); return }
        if (adapter?.isEnabled != true) {
            updateStatus("Turn Bluetooth on, then tap Refresh")
            return
        }
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
            }
        }, BluetoothProfile.HID_DEVICE)
        updateStatus("Preparing Bluetooth mouse service…")
    }

    private fun refreshDevices() {
        try {
            if (adapter == null) adapter = BluetoothAdapter.getDefaultAdapter()
            if (adapter?.isEnabled == true && hid == null) {
                initializeBluetooth()
                return
            }
            bonded.clear()
            bonded.addAll(adapter?.bondedDevices?.sortedBy { safeName(it) } ?: emptyList())
            val labels = if (bonded.isEmpty()) listOf("No paired devices yet — use Pair new device")
                else bonded.map { "${safeName(it)}  •  ${it.address}" }
            devices.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
            if (adapter?.isEnabled != true) updateStatus("Bluetooth is off. Turn it on and refresh.")
        } catch (_: SecurityException) { updateStatus("Allow Nearby devices permission to use Bluetooth") }
    }

    private fun connectSelected() {
        val device = bonded.getOrNull(devices.selectedItemPosition) ?: run {
            toast("Pair the target device first."); openBluetoothSettings(); return
        }
        if (!registered) { toast("The phone's Bluetooth HID service is not ready."); return }
        val ok = try { hid?.connect(device) == true } catch (_: SecurityException) { false }
        updateStatus(if (ok) "Connection requested…" else "Could not connect. Remove the old pairing on both devices and pair again.")
    }

    private fun sendMouse(dx: Int, dy: Int, wheel: Int) {
        val device = connected ?: return
        val report = byteArrayOf(0, dx.coerceIn(-127,127).toByte(), dy.coerceIn(-127,127).toByte(), wheel.coerceIn(-127,127).toByte())
        try { hid?.sendReport(device, 0, report) } catch (_: SecurityException) { }
    }

    private fun buttonDown(button: Int) {
        val device = connected ?: run { toast("Connect to a device first."); return }
        try { hid?.sendReport(device, 0, byteArrayOf(button.toByte(),0,0,0)) } catch (_: SecurityException) { }
    }

    private fun buttonUp() {
        val device = connected ?: return
        try { hid?.sendReport(device, 0, byteArrayOf(0,0,0,0)) } catch (_: SecurityException) { }
    }

    private fun safeName(device: BluetoothDevice): String = try { device.name ?: "Bluetooth device" } catch (_: SecurityException) { "Bluetooth device" }

    private fun updateStatus(message: String) {
        if (::status.isInitialized) {
            status.text = message
            val connectedNow = connected != null
            statusPill.text = when {
                connectedNow -> "CONNECTED"
                registered -> "READY"
                else -> "WAITING"
            }
            statusPill.setTextColor(if (connectedNow) green else if (registered) blue else textMuted)
            statusPill.background = rounded(if (connectedNow) 0xFF123A35.toInt() else raised, 30)
        }
        refreshButtons()
    }

    private fun refreshButtons() {
        if (::connectButton.isInitialized) connectButton.isEnabled = registered && connected == null && bonded.isNotEmpty()
        if (::disconnectButton.isInitialized) disconnectButton.isEnabled = connected != null
        if (::leftButton.isInitialized) leftButton.isEnabled = connected != null
        if (::rightButton.isInitialized) rightButton.isEnabled = connected != null
        if (::middleButton.isInitialized) middleButton.isEnabled = connected != null
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

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

    private class TouchSurface(context: Context, val move: (Int, Int, Int) -> Unit) : View(context) {
        private var lastX = 0f
        private var lastY = 0f
        private var downX = 0f
        private var downY = 0f
        private var downAt = 0L
        private var moved = false
        private var twoFinger = false
        private var lastMultiY = 0f

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x; downY = event.y
                    lastX = event.x; lastY = event.y
                    downAt = event.eventTime
                    moved = false
                    twoFinger = false
                    return true
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    if (event.pointerCount >= 2) {
                        twoFinger = true
                        lastMultiY = (event.getY(0) + event.getY(1)) / 2f
                    }
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (twoFinger && event.pointerCount >= 2) {
                        val centerY = (event.getY(0) + event.getY(1)) / 2f
                        val delta = (lastMultiY - centerY).roundToInt()
                        if (kotlin.math.abs(delta) > 1) move(0, 0, (delta * 0.7f).roundToInt().coerceIn(-12, 12))
                        lastMultiY = centerY
                        moved = moved || kotlin.math.abs(centerY - downY) > 12
                    } else if (!twoFinger) {
                        val dx = ((event.x - lastX) * 1.5f).roundToInt()
                        val dy = ((event.y - lastY) * 1.5f).roundToInt()
                        if (dx != 0 || dy != 0) move(dx, dy, 0)
                        if (kotlin.math.abs(event.x - downX) + kotlin.math.abs(event.y - downY) > 12) moved = true
                        lastX = event.x; lastY = event.y
                    }
                    return true
                }
                MotionEvent.ACTION_POINTER_UP -> {
                    if (twoFinger && event.pointerCount == 2 && !moved && event.eventTime - downAt < 280) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved && event.eventTime - downAt < 280) {
                        if (twoFinger) moveButtonClick(2) else moveButtonClick(1)
                    }
                    performClick()
                    return true
                }
                MotionEvent.ACTION_CANCEL -> return true
            }
            return true
        }

        private fun moveButtonClick(button: Int) {
            // The parent supplies mouse motion; a tap is handled through the activity's touch callback below.
            (context as? MainActivity)?.sendTapFromPad(button)
        }

        override fun performClick(): Boolean { super.performClick(); return true }
    }

    private fun sendTapFromPad(button: Int) {
        buttonDown(button)
        buttonUp()
    }
}
