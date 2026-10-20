@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.codblackops.mobile.ui

import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.InputDevice
import android.view.KeyEvent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.codblackops.mobile.core.input.Bindings
import com.codblackops.mobile.core.input.ControlLayout
import com.codblackops.mobile.core.input.ControlScheme
import com.codblackops.mobile.core.input.ControlSettings
import com.codblackops.mobile.core.input.GameAction
import com.codblackops.mobile.core.input.GamepadMapping
import com.codblackops.mobile.core.input.GyroProcessor
import com.codblackops.mobile.core.input.WiiInput
import com.codblackops.mobile.prefs.AppPrefs
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun ControlsScreen(state: LauncherState) {
    val ctx = state.activity
    val scope = rememberCoroutineScope()
    var s by remember { mutableStateOf(AppPrefs.controls(ctx)) }
    var bindings by remember { mutableStateOf(AppPrefs.bindings(ctx)) }
    var pad by remember { mutableStateOf(AppPrefs.gamepad(ctx)) }
    var preset by remember { mutableStateOf(AppPrefs.layoutPreset(ctx)) }
    var showCalibration by remember { mutableStateOf(false) }
    var captureFor by remember { mutableStateOf<GameAction?>(null) }
    var bindingScheme by remember { mutableStateOf(s.activeScheme()) }

    fun update(change: (ControlSettings) -> Unit) {
        val n = s.copy()
        change(n)
        s = n
        AppPrefs.setControls(ctx, n)
    }

    fun pct(v: Float) = String.format(Locale.ROOT, "%.0f%%", v * 100)
    fun mult(v: Float) = String.format(Locale.ROOT, "%.2fx", v)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text("Kontrol", style = MaterialTheme.typography.headlineSmall)
        NoteText(
            "Setiap tombol di layar menekan tombol Wii Remote/Classic Controller emulasi melalui InputOverrider " +
                "Dolphin. Black Ops (Wii) mengizinkan semua tombol diubah di Options > Controls; samakan dengan tabel " +
                "pemetaan di bawah.",
        )

        SectionCard("Mode kontrol") {
            ChoiceChips(null, ControlSettings.Mode.values().toList(), s.mode, { it.label }) { m -> update { it.mode = m } }
            NoteText(s.mode.description)
            if (s.mode == ControlSettings.Mode.GYROSCOPE) {
                ChoiceChips(
                    "Controller yang dilihat game",
                    ControlScheme.values().toList(), s.gyroScheme, { it.label },
                ) { sc -> update { it.gyroScheme = sc } }
                NoteText(
                    if (s.gyroScheme == ControlScheme.WIIMOTE_NUNCHUK) {
                        "Gyro menggerakkan pointer IR seperti Wii Remote sungguhan."
                    } else {
                        "Gyro menggerakkan analog kanan Classic Controller."
                    },
                )
            }
            InfoRow("Controller aktif", s.activeScheme().label)
        }

        SectionCard("Kamera & bidik (Touch FPS)") {
            ChoiceChips(
                "Kamera kanan",
                ControlSettings.LookStyle.values().toList(), s.lookStyle,
                { if (it == ControlSettings.LookStyle.SWIPE) "Geser layar" else "Analog kanan" },
            ) { v -> update { it.lookStyle = v } }
            SliderRow("Sensitivitas horizontal", s.lookSensX, 0.1f..4f, format = ::mult) { v -> update { it.lookSensX = v } }
            SliderRow("Sensitivitas vertikal", s.lookSensY, 0.1f..4f, format = ::mult) { v -> update { it.lookSensY = v } }
            SliderRow("Akselerasi kamera", s.lookAcceleration, 0f..1.5f, format = ::pct) { v -> update { it.lookAcceleration = v } }
            SliderRow("Dead zone", s.lookDeadzone, 0f..0.4f, format = ::pct) { v -> update { it.lookDeadzone = v } }
            SliderRow("Anti dead zone (lawan dead zone game)", s.lookAntiDeadzone, 0f..0.5f, format = ::pct) { v ->
                update { it.lookAntiDeadzone = v }
            }
            SliderRow("Penghalusan", s.lookSmoothing, 0f..0.9f, format = ::pct) { v -> update { it.lookSmoothing = v } }
            SwitchRow("Invert Y", s.invertY) { v -> update { it.invertY = v } }
        }

        SectionCard("Wii Pointer") {
            ChoiceChips(
                "Cara membidik",
                ControlSettings.PointerMode.values().toList(), s.pointerMode,
                { if (it == ControlSettings.PointerMode.RELATIVE) "Relatif (geser)" else "Absolut (sentuh = posisi)" },
            ) { v -> update { it.pointerMode = v } }
            SliderRow("Sensitivitas pointer", s.pointerSens, 0.1f..4f, format = ::mult) { v -> update { it.pointerSens = v } }
            SwitchRow(
                "Pusatkan pointer saat jari diangkat", s.pointerRecenterOnRelease,
                "Black Ops memutar kamera saat pointer berada di tepi layar; memusatkan menghentikan putaran.",
            ) { v -> update { it.pointerRecenterOnRelease = v } }
        }

        SectionCard("Gyroscope") {
            val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            val hasGyro = sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null
            InfoRow("Sensor gyro", if (hasGyro) "Tersedia" else "Tidak tersedia di perangkat ini")
            SwitchRow("Gyro tambahan di mode Touch FPS / Wii Pointer", s.gyroAssist, enabled = hasGyro) { v -> update { it.gyroAssist = v } }
            SliderRow("Sensitivitas gyro horizontal", s.gyroSensX, 0.1f..5f, format = ::mult) { v -> update { it.gyroSensX = v } }
            SliderRow("Sensitivitas gyro vertikal", s.gyroSensY, 0.1f..5f, format = ::mult) { v -> update { it.gyroSensY = v } }
            SliderRow("Dead zone gyro (rad/s)", s.gyroDeadzone, 0f..0.2f, format = { String.format(Locale.ROOT, "%.3f", it) }) { v ->
                update { it.gyroDeadzone = v }
            }
            ChoiceChips(
                "Sumbu putar horizontal",
                ControlSettings.GyroAxis.values().toList(), s.gyroAxis,
                { if (it == ControlSettings.GyroAxis.YAW) "Yaw (putar kiri-kanan)" else "Roll (seperti setir)" },
            ) { v -> update { it.gyroAxis = v } }
            SwitchRow("Balik horizontal", s.gyroInvertX) { v -> update { it.gyroInvertX = v } }
            SwitchRow("Balik vertikal", s.gyroInvertY) { v -> update { it.gyroInvertY = v } }
            InfoRow(
                "Kalibrasi (bias)",
                String.format(Locale.ROOT, "%.4f / %.4f / %.4f", s.gyroBiasX, s.gyroBiasY, s.gyroBiasZ),
            )
            OutlinedButton(onClick = { showCalibration = true }, enabled = hasGyro) { Text("Kalibrasi gyro") }
        }

        SectionCard("Perilaku tombol") {
            ChoiceChips(
                "Tombol tembak", ControlSettings.FireMode.values().toList(), s.fireMode,
                { if (it == ControlSettings.FireMode.HOLD) "Tahan (otomatis)" else "Tap (satu tembakan per sentuh)" },
            ) { v -> update { it.fireMode = v } }
            SwitchRow("Geser tombol tembak untuk membidik", s.fireDragAim) { v -> update { it.fireDragAim = v } }
            ChoiceChips(
                "Lari", ControlSettings.PressMode.values().toList(), s.sprintMode,
                { if (it == ControlSettings.PressMode.HOLD) "Tahan" else "Tekan (kunci, lepas otomatis saat berhenti)" },
            ) { v -> update { it.sprintMode = v } }
            ChoiceChips(
                "Bidik (ADS)", ControlSettings.PressMode.values().toList(), s.adsMode,
                { if (it == ControlSettings.PressMode.HOLD) "Tahan" else "Tekan (kunci)" },
            ) { v -> update { it.adsMode = v } }
            SliderRow(
                "Durasi tahan tombol Interaksi", s.interactHoldMs.toFloat(), 200f..1500f, steps = 25,
                format = { "${it.toInt()} ms" },
            ) { v -> update { it.interactHoldMs = v.toInt() } }
        }

        SectionCard("HUD & tata letak") {
            SliderRow("Transparansi HUD", s.hudOpacity, 0.1f..1f, format = ::pct) { v -> update { it.hudOpacity = v } }
            SliderRow("Ukuran HUD", s.hudScale, 0.5f..1.8f, format = ::mult) { v -> update { it.hudScale = v } }
            SliderRow("Dead zone analog kiri", s.moveDeadzone, 0f..0.5f, format = ::pct) { v -> update { it.moveDeadzone = v } }
            SwitchRow("Analog kiri mengambang (muncul di titik sentuh)", s.floatingMoveStick) { v -> update { it.floatingMoveStick = v } }
            SwitchRow("Getar saat tombol ditekan", s.haptics) { v -> update { it.haptics = v } }
            SwitchRow("Tampilkan status (mode, gyro, lari)", s.showStatusChip) { v -> update { it.showStatusChip = v } }
            SwitchRow("Sembunyikan HUD saat gamepad dipakai", s.autoHideHudOnGamepad) { v -> update { it.autoHideHudOnGamepad = v } }
            ChoiceChips(
                "Tata letak awal", ControlSettings.LayoutSet.values().toList(), s.layoutSet,
                { if (it == ControlSettings.LayoutSet.CAMPAIGN) "Campaign" else "Multiplayer / Zombies" },
            ) { v -> update { it.layoutSet = v } }
            ChoiceChips("Profil perangkat", ControlLayout.Preset.values().toList(), preset, { it.label }) { p ->
                preset = p
                AppPrefs.setLayoutPreset(ctx, p)
            }
            NoteText("Mengganti profil perangkat hanya berlaku untuk tata letak yang belum diedit atau setelah di-reset.")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (set in ControlSettings.LayoutSet.values()) {
                    Button(onClick = {
                        ctx.startActivity(
                            Intent(ctx, ControlLayoutEditorActivity::class.java)
                                .putExtra(ControlLayoutEditorActivity.EXTRA_SET, set.name),
                        )
                    }) { Text(if (set == ControlSettings.LayoutSet.CAMPAIGN) "Edit Campaign" else "Edit Multiplayer") }
                }
                OutlinedButton(onClick = {
                    for (set in ControlSettings.LayoutSet.values()) AppPrefs.resetLayout(ctx, set)
                    scope.launch { state.snackbar.showSnackbar("Tata letak dikembalikan ke ${preset.label}") }
                }) { Text("Reset tata letak") }
            }
        }

        SectionCard("Pemetaan ke tombol Wii", "Tombol layar & gamepad menekan input Wii berikut.") {
            ChoiceChips(null, ControlScheme.values().toList(), bindingScheme, { it.label }) { bindingScheme = it }
            for (a in GameAction.values().filter { !it.isLauncherAction() }) {
                BindingRow(a, bindings.get(bindingScheme, a), bindingScheme) { input ->
                    val n = Bindings.decode(bindings.encode())
                    n.set(bindingScheme, a, input)
                    bindings = n
                    AppPrefs.setBindings(ctx, n)
                }
            }
            TextButton(onClick = {
                val n = Bindings.decode(bindings.encode())
                n.resetScheme(bindingScheme)
                bindings = n
                AppPrefs.setBindings(ctx, n)
            }) { Text("Kembalikan default ${bindingScheme.label}") }
        }

        SectionCard("Gamepad Bluetooth / USB") {
            val pads = connectedGamepads()
            InfoRow("Terhubung", if (pads.isEmpty()) "Tidak ada" else pads.joinToString(", "))
            NoteText("Analog kiri = gerak, analog kanan = kamera/bidik, R2 = tembak, L2 = bidik (bisa diubah di bawah).")
            ChoiceChips(
                "Sumbu analog kanan", ControlSettings.RightStickAxes.values().toList(), s.rightStickAxes,
                { if (it == ControlSettings.RightStickAxes.Z_RZ) "Z / RZ (umum, Xbox/8BitDo)" else "RX / RY" },
            ) { v -> update { it.rightStickAxes = v } }
            SliderRow("Dead zone gamepad", s.gamepadDeadzone, 0f..0.5f, format = ::pct) { v -> update { it.gamepadDeadzone = v } }
            SliderRow("Sensitivitas kamera gamepad", s.gamepadLookSens, 0.1f..3f, format = ::mult) { v -> update { it.gamepadLookSens = v } }
            SwitchRow("Invert Y gamepad", s.gamepadInvertY) { v -> update { it.gamepadInvertY = v } }
            for (a in GameAction.values()) {
                val key = pad.keyFor(a)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(a.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(
                        if (key < 0) "—" else KeyEvent.keyCodeToString(key).removePrefix("KEYCODE_"),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    TextButton(onClick = { captureFor = a }) { Text("Ubah") }
                }
            }
            TextButton(onClick = {
                val d = GamepadMapping.defaults()
                pad = d
                AppPrefs.setGamepad(ctx, d)
            }) { Text("Kembalikan pemetaan gamepad default") }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (showCalibration) {
        GyroCalibrationDialog(onDone = { bias ->
            showCalibration = false
            if (bias != null) {
                update {
                    it.gyroBiasX = bias[0]
                    it.gyroBiasY = bias[1]
                    it.gyroBiasZ = bias[2]
                }
                scope.launch { state.snackbar.showSnackbar("Kalibrasi gyro tersimpan") }
            }
        })
    }

    val cap = captureFor
    if (cap != null) {
        DisposableEffect(cap) {
            state.activity.keyCaptureListener = { e ->
                val isPad = (e.source and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
                    (e.source and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK ||
                    (e.source and InputDevice.SOURCE_DPAD) == InputDevice.SOURCE_DPAD
                if (isPad && e.action == KeyEvent.ACTION_UP && e.keyCode != KeyEvent.KEYCODE_BACK) {
                    val n = GamepadMapping.decode(pad.encode())
                    n.unbindAction(cap)
                    n.bind(e.keyCode, cap)
                    pad = n
                    AppPrefs.setGamepad(ctx, n)
                    captureFor = null
                    true
                } else {
                    isPad
                }
            }
            onDispose { state.activity.keyCaptureListener = null }
        }
        AlertDialog(
            onDismissRequest = { captureFor = null },
            title = { Text("Pemetaan gamepad") },
            text = { Text("Tekan tombol pada gamepad untuk: ${cap.label}") },
            confirmButton = {
                TextButton(onClick = {
                    val n = GamepadMapping.decode(pad.encode())
                    n.unbindAction(cap)
                    pad = n
                    AppPrefs.setGamepad(ctx, n)
                    captureFor = null
                }) { Text("Hapus pemetaan") }
            },
            dismissButton = { TextButton(onClick = { captureFor = null }) { Text("Batal") } },
        )
    }
}

@Composable
private fun BindingRow(action: GameAction, current: WiiInput, scheme: ControlScheme, onSelect: (WiiInput) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(action.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Box {
            TextButton(onClick = { open = true }) { Text(current.label) }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                for (input in WiiInput.values().filter { it.availableIn(scheme) }) {
                    DropdownMenuItem(text = { Text(input.label) }, onClick = {
                        open = false
                        onSelect(input)
                    })
                }
            }
        }
    }
}

private fun connectedGamepads(): List<String> {
    val out = ArrayList<String>()
    for (id in InputDevice.getDeviceIds()) {
        val d = InputDevice.getDevice(id) ?: continue
        val s = d.sources
        val isPad = (s and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
            (s and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
        if (isPad && !d.isVirtual) out.add(d.name)
    }
    return out
}

/** Collects ~2 s of gyro samples with the device lying still and returns the bias. */
@Composable
private fun GyroCalibrationDialog(onDone: (FloatArray?) -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var progress by remember { mutableIntStateOf(0) }
    val processor = remember { GyroProcessor().apply { beginCalibration() } }
    val target = 200
    DisposableEffect(Unit) {
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (processor.calibrationSampleCount() < target) {
                    processor.addCalibrationSample(event.values[0], event.values[1], event.values[2])
                    progress = processor.calibrationSampleCount()
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (sensor != null) sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose { sm.unregisterListener(listener) }
    }
    AlertDialog(
        onDismissRequest = { onDone(null) },
        title = { Text("Kalibrasi gyro") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Letakkan perangkat di permukaan datar dan jangan disentuh.")
                LinearProgressIndicator(progress = { progress / target.toFloat() }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(enabled = progress >= target, onClick = { onDone(processor.finishCalibration()) }) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = { onDone(null) }) { Text("Batal") } },
    )
}
