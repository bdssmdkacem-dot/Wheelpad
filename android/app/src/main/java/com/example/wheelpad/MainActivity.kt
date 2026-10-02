package com.example.wheelpad

import android.content.Context
import android.content.pm.ActivityInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.roundToInt
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.runtime.snapshots.SnapshotStateMap

// Button bits (must match pc_server.py)
const val NITRO = 1; const val HORN = 2; const val CAM = 4; const val GEAR_UP = 8; const val GEAR_DN = 16

class MainActivity : ComponentActivity(), SensorEventListener {
    private val prefs by lazy { getSharedPreferences("wheelpad", Context.MODE_PRIVATE) }

    // ---- Settings (persisted) ----
    var ip by mutableStateOf("192.168.1.10")
    var maxAngle by mutableFloatStateOf(45f)   // degrees for full lock
    var deadzone by mutableFloatStateOf(3f)    // degrees
    var smoothing by mutableFloatStateOf(0.3f) // 0 = raw, 0.9 = very smooth
    var invert by mutableStateOf(false)
    var status by mutableStateOf("")
    var btnScale by mutableFloatStateOf(1f)    // button size multiplier
    var btnAlpha by mutableFloatStateOf(0.85f) // button opacity
    var editMode by mutableStateOf(false)
    val layout: SnapshotStateMap<String, Offset> = mutableStateMapOf() // saved positions (fractions of screen)

    class Btn(val id: String, val label: String, val color: Color,
              val fx: Float, val fy: Float, val w: Int, val h: Int, val onHold: (Boolean) -> Unit)

    private val buttons by lazy {
        listOf(
            Btn("brake", "BRAKE", Color(0xFFC62828), 0.03f, 0.50f, 140, 140) { brake = it },
            Btn("gas", "GAS", Color(0xFF2E7D32), 0.80f, 0.50f, 140, 140) { gas = it },
            Btn("gdn", "GEAR -", Color(0xFF455A64), 0.03f, 0.10f, 110, 80) { bit(GEAR_DN, it) },
            Btn("gup", "GEAR +", Color(0xFF455A64), 0.82f, 0.10f, 110, 80) { bit(GEAR_UP, it) },
            Btn("nitro", "NITRO", Color(0xFF1565C0), 0.32f, 0.70f, 100, 80) { bit(NITRO, it) },
            Btn("horn", "HORN", Color(0xFF6A1B9A), 0.45f, 0.70f, 100, 80) { bit(HORN, it) },
            Btn("cam", "CAM", Color(0xFF00695C), 0.58f, 0.70f, 100, 80) { bit(CAM, it) },
        )
    }

    // ---- Live state ----
    private var angle = 0f
    private var offset = 0f
    var steer by mutableFloatStateOf(0f)
    var gas by mutableStateOf(false)
    var brake by mutableStateOf(false)
    var mask by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        load()

        lifecycleScope.launch(Dispatchers.IO) {
            val socket = DatagramSocket()
            var lastIp = ""; var addr: InetAddress? = null
            while (isActive) {
                try {
                    if (ip != lastIp) { addr = InetAddress.getByName(ip); lastIp = ip }
                    val msg = "%.3f,%d,%d,%d".format(steer, if (gas) 1 else 0, if (brake) 1 else 0, mask)
                        .toByteArray()
                    socket.send(DatagramPacket(msg, msg.size, addr, 5005))
                } catch (_: Exception) { lastIp = "" }
                delay(16) // ~60 Hz
            }
        }

        setContent { MaterialTheme(colorScheme = darkColorScheme()) { Surface(Modifier.fillMaxSize()) { Screen() } } }
    }

    override fun onResume() {
        super.onResume()
        val sm = getSystemService(SENSOR_SERVICE) as SensorManager
        sm.registerListener(this, sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_GAME)
    }

    override fun onPause() {
        super.onPause()
        (getSystemService(SENSOR_SERVICE) as SensorManager).unregisterListener(this)
        save()
    }

    // Landscape: wheel rotation = angle of gravity in the device x/y plane
    override fun onSensorChanged(e: SensorEvent) {
        val raw = Math.toDegrees(atan2(e.values[1], e.values[0]).toDouble()).toFloat()
        angle += (1f - smoothing) * (raw - angle)
        var a = (angle - offset) * if (invert) -1f else 1f
        if (abs(a) < deadzone) a = 0f
        steer = (a / maxAngle).coerceIn(-1f, 1f)
    }
    override fun onAccuracyChanged(s: Sensor?, a: Int) {}

    private fun load() = prefs.run {
        ip = getString("ip", ip)!!; maxAngle = getFloat("max", maxAngle); deadzone = getFloat("dead", deadzone)
        smoothing = getFloat("smooth", smoothing); invert = getBoolean("inv", invert)
        btnScale = getFloat("scale", btnScale); btnAlpha = getFloat("alpha", btnAlpha)
        for (b in buttons) getString("pos_" + b.id, null)?.split(",")?.let {
            if (it.size == 2) layout[b.id] = Offset(it[0].toFloat(), it[1].toFloat())
        }
    }
    private fun save() = prefs.edit().apply {
        putString("ip", ip); putFloat("max", maxAngle); putFloat("dead", deadzone)
        putFloat("smooth", smoothing); putBoolean("inv", invert)
        putFloat("scale", btnScale); putFloat("alpha", btnAlpha)
        for (b in buttons) {
            val p = layout[b.id]
            if (p != null) putString("pos_" + b.id, "${p.x},${p.y}") else remove("pos_" + b.id)
        }
    }.apply()

    // Broadcast on the LAN; the PC app answers with its address
    fun findPc() {
        status = "Searching..."
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                DatagramSocket().use { sock ->
                    sock.broadcast = true
                    sock.soTimeout = 2500
                    val q = "WHEELPAD_DISCOVER".toByteArray()
                    sock.send(DatagramPacket(q, q.size, InetAddress.getByName("255.255.255.255"), 5005))
                    val r = DatagramPacket(ByteArray(64), 64)
                    sock.receive(r)
                    if (String(r.data, 0, r.length).startsWith("WHEELPAD_HERE")) {
                        ip = r.address.hostAddress ?: ip
                        status = "Found PC: $ip"
                    }
                }
            } catch (e: Exception) {
                status = "PC not found (check Wi-Fi / firewall)"
            }
        }
    }

    // ---------------- UI ----------------
    fun bit(b: Int, down: Boolean) { mask = if (down) mask or b else mask and b.inv() }

    @Composable
    fun BtnView(b: Btn, maxW: Float, maxH: Float) {
        val d = LocalDensity.current.density
        val wDp = b.w * btnScale; val hDp = b.h * btnScale
        val wPx = wDp * d; val hPx = hDp * d
        val p = layout[b.id] ?: Offset(b.fx, b.fy)
        val shape = RoundedCornerShape(20.dp)
        Box(
            Modifier
                .offset { IntOffset((p.x * maxW).roundToInt(), (p.y * maxH).roundToInt()) }
                .size(wDp.dp, hDp.dp)
                .background(b.color.copy(alpha = btnAlpha), shape)
                .then(if (editMode) Modifier.border(2.dp, Color.White, shape) else Modifier)
                .pointerInput(editMode, btnScale) {
                    if (editMode) {
                        detectDragGestures { change, drag ->
                            change.consume()
                            val cur = layout[b.id] ?: Offset(b.fx, b.fy)
                            layout[b.id] = Offset(
                                (cur.x + drag.x / maxW).coerceIn(0f, max(0f, 1f - wPx / maxW)),
                                (cur.y + drag.y / maxH).coerceIn(0f, max(0f, 1f - hPx / maxH))
                            )
                        }
                    } else {
                        detectTapGestures(onPress = { b.onHold(true); tryAwaitRelease(); b.onHold(false) })
                    }
                },
            contentAlignment = Alignment.Center
        ) { Text(b.label, style = MaterialTheme.typography.titleMedium) }
    }

    @Composable
    fun Screen() {
        var settings by remember { mutableStateOf(false) }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val d = LocalDensity.current.density
            val maxW = maxWidth.value * d
            val maxH = maxHeight.value * d

            for (b in buttons) BtnView(b, maxW, maxH)

            // Top panel: status, actions, settings
            Column(
                Modifier.align(Alignment.TopCenter).width(300.dp).heightIn(max = maxHeight)
                    .background(Color(0xCC000000), RoundedCornerShape(16.dp))
                    .verticalScroll(rememberScrollState()).padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("Steer: %.0f%%".format(steer * 100), style = MaterialTheme.typography.titleMedium)
                LinearProgressIndicator(progress = { (steer + 1f) / 2f }, Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(onClick = { offset = angle }, contentPadding = PaddingValues(8.dp)) { Text("Calibrate") }
                    OutlinedButton(onClick = { settings = !settings; if (!settings) save() },
                        contentPadding = PaddingValues(8.dp)) { Text("Settings") }
                    Button(onClick = {
                        editMode = !editMode
                        if (editMode) { gas = false; brake = false; mask = 0 } else save()
                    }, contentPadding = PaddingValues(8.dp)) { Text(if (editMode) "Done" else "Edit") }
                }
                if (editMode) {
                    Text("Drag buttons to move them", style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { layout.clear(); btnScale = 1f; btnAlpha = 0.85f }) { Text("Reset layout") }
                }
                if (settings || editMode) {
                    Text("Button size: %.0f%%".format(btnScale * 100))
                    Slider(btnScale, { btnScale = it }, valueRange = 0.6f..1.6f)
                    Text("Button opacity: %.0f%%".format(btnAlpha * 100))
                    Slider(btnAlpha, { btnAlpha = it }, valueRange = 0.3f..1f)
                }
                if (settings) {
                    OutlinedTextField(ip, { ip = it }, label = { Text("PC IP") }, singleLine = true)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = { findPc() }) { Text("Find PC") }
                        Spacer(Modifier.width(8.dp))
                        Text(status, style = MaterialTheme.typography.bodySmall)
                    }
                    Text("Max angle: ${maxAngle.toInt()}°")
                    Slider(maxAngle, { maxAngle = it }, valueRange = 15f..90f)
                    Text("Deadzone: ${deadzone.toInt()}°")
                    Slider(deadzone, { deadzone = it }, valueRange = 0f..15f)
                    Text("Smoothing: %.0f%%".format(smoothing * 100))
                    Slider(smoothing, { smoothing = it }, valueRange = 0f..0.9f)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Invert steering", Modifier.weight(1f)); Switch(invert, { invert = it })
                    }
                }
            }
        }
    }
}
