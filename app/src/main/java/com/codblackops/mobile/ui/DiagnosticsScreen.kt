@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.codblackops.mobile.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.net.Uri
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codblackops.mobile.core.config.DiagnosticsConfig
import com.codblackops.mobile.core.config.LaunchConfigBuilder
import com.codblackops.mobile.core.input.WiiControl
import com.codblackops.mobile.diag.AppLog
import com.codblackops.mobile.diag.DeviceInfo
import com.codblackops.mobile.dolphin.DolphinBridge
import com.codblackops.mobile.dolphin.GpuDrivers
import com.codblackops.mobile.prefs.AppPrefs
import com.codblackops.mobile.storage.AppPaths
import com.codblackops.mobile.storage.SafTree
import com.codblackops.mobile.storage.StorageAccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.dolphinemu.dolphinemu.features.input.model.InputOverrider
import org.dolphinemu.dolphinemu.features.input.model.controlleremu.EmulatedController
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CheckResult(val name: String, val ok: Boolean?, val detail: String)

@Composable
fun DiagnosticsScreen(state: LauncherState) {
    val ctx = state.activity
    val scope = rememberCoroutineScope()
    var diag by remember { mutableStateOf(AppPrefs.diagnostics(ctx)) }
    var results by remember { mutableStateOf<List<CheckResult>>(emptyList()) }
    var testing by remember { mutableStateOf(false) }
    var logTick by remember { mutableIntStateOf(0) }
    val device = remember(logTick) { DeviceInfo.collect(ctx) }
    val launcherLog = remember(logTick) { AppLog.tail(150) }
    val dolphinLog = remember(logTick) {
        DolphinBridge.userDirectory()?.let { AppLog.tailOf(File(it, "Logs/dolphin.log"), 150) } ?: ""
    }

    fun setDiag(change: (DiagnosticsConfig) -> Unit) {
        val n = DiagnosticsConfig.decode(diag.encode())
        change(n)
        diag = n
        AppPrefs.setDiagnostics(ctx, n)
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text("Diagnostik", style = MaterialTheme.typography.headlineSmall)

        SectionCard("Perangkat") {
            device.summary().lines().forEach { NoteText(it) }
            if (device.isXiaomiPad6) NoteText("Xiaomi Pad 6 terdeteksi: profil tata letak tablet dipakai sebagai default.", CodGreen)
        }

        SectionCard("Backend") {
            InfoRow("Dolphin", DolphinBridge.version())
            InfoRow("Direktori pengguna", DolphinBridge.userDirectory() ?: "belum siap")
            val sys = remember { GpuDrivers.systemDriver(ctx) }
            InfoRow("Driver GPU sistem", sys?.let { "${it.vendor} ${it.driverVersion}" } ?: "tidak diketahui")
            InfoRow("Driver kustom", GpuDrivers.installedDriver()?.name ?: "tidak ada")
            InfoRow("Dukungan driver kustom", if (GpuDrivers.supportsCustomDrivers()) "ya" else "tidak")
        }

        SectionCard("Log diagnostik Dolphin", "Log grafis/CPU ditulis Dolphin ke Logs/dolphin.log saat game berjalan.") {
            SwitchRow("Tulis log Dolphin ke file", diag.logToFile) { v -> setDiag { it.logToFile = v } }
            ChoiceChips("Tingkat log", DiagnosticsConfig.Verbosity.values().toList(), diag.verbosity, { it.label }) { v ->
                setDiag { it.verbosity = v }
            }
            NoteText("FPS dan frame time di layar diatur di tab Grafis (OSD Dolphin).")
        }

        SectionCard("Uji sistem", "Pemeriksaan nyata terhadap backend, input, penyimpanan dan sensor.") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = !testing, onClick = {
                    testing = true
                    scope.launch {
                        results = runSelfTest(ctx, state)
                        testing = false
                        logTick++
                    }
                }) { Text(if (testing) "Menguji…" else "Jalankan uji") }
                OutlinedButton(onClick = {
                    scope.launch {
                        val msg = withContext(Dispatchers.IO) { exportLogs(ctx, device) }
                        state.snackbar.showSnackbar(msg)
                    }
                }) { Text("Ekspor log") }
                OutlinedButton(onClick = { logTick++ }) { Text("Muat ulang log") }
            }
            for (r in results) {
                Row(Modifier.fillMaxWidth()) {
                    val (label, color) = when (r.ok) {
                        true -> "LULUS" to CodGreen
                        false -> "GAGAL" to CodRed
                        null -> "INFO" to CodYellow
                    }
                    Text(label, color = color, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(56.dp))
                    Column(Modifier.weight(1f)) {
                        Text(r.name, style = MaterialTheme.typography.bodySmall)
                        NoteText(r.detail)
                    }
                }
            }
        }

        SectionCard("Log launcher (150 baris terakhir)") { LogBox(launcherLog) }
        SectionCard("Log Dolphin (150 baris terakhir)") { LogBox(dolphinLog.ifEmpty { "Belum ada log. Aktifkan log lalu jalankan game." }) }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun LogBox(text: String) {
    Column(
        Modifier.fillMaxWidth().heightIn(max = 280.dp).verticalScroll(rememberScrollState())
            .horizontalScroll(rememberScrollState()),
    ) {
        Text(text, fontFamily = FontFamily.Monospace, fontSize = 10.sp, lineHeight = 13.sp)
    }
}

private suspend fun runSelfTest(ctx: Context, state: LauncherState): List<CheckResult> {
    val out = ArrayList<CheckResult>()
    fun add(name: String, ok: Boolean?, detail: String) {
        out.add(CheckResult(name, ok, detail))
        AppLog.i("UJI ${if (ok == true) "LULUS" else if (ok == false) "GAGAL" else "INFO"} $name: $detail")
    }
    val nativeProblem = com.codblackops.mobile.backend.DolphinBackend().unavailableReason()
    add("Library native Dolphin (libmain.so)", nativeProblem == null, nativeProblem ?: DolphinBridge.version())
    if (nativeProblem != null) return out
    val ready = DolphinBridge.awaitReady(ctx, 30_000)
    add("Inisialisasi direktori Dolphin", ready, DolphinBridge.userDirectory() ?: "tidak siap")
    if (!ready) return out
    val idle = DolphinBridge.isEmulationIdle()
    add("Emulasi tidak sedang berjalan", idle, if (idle) "OK" else "Tutup game sebelum menguji")

    if (idle) {
        withContext(Dispatchers.Main) {
            // Config pipeline: write the global launch config and read every value back.
            try {
                val writes = LaunchConfigBuilder.build(
                    AppPrefs.graphics(ctx, null), AppPrefs.performance(ctx, null), AppPrefs.diagnostics(ctx), null,
                )
                DolphinBridge.applyConfig(writes)
                val mismatches = writes.filter { w -> DolphinBridge.readActive(w) != normalized(w) }
                add(
                    "Tulis & baca konfigurasi Dolphin",
                    mismatches.isEmpty(),
                    if (mismatches.isEmpty()) "${writes.size} nilai cocok" else "Tidak cocok: " + mismatches.joinToString { it.location() },
                )
            } catch (t: Throwable) {
                add("Tulis & baca konfigurasi Dolphin", false, t.message ?: t.javaClass.simpleName)
            }
            // Controller extension (Classic / Nunchuk)
            try {
                val scheme = AppPrefs.controls(ctx).activeScheme()
                DolphinBridge.configureWiimote(scheme)
                val selected = EmulatedController.getSelectedWiimoteAttachment(0)
                add("Attachment Wii Remote 1", selected == scheme.dolphinExtension, "${scheme.label} → extension $selected")
            } catch (t: Throwable) {
                add("Attachment Wii Remote 1", false, t.message ?: t.javaClass.simpleName)
            }
            // InputOverrider hook used by the touch HUD / gyro / gamepad
            try {
                InputOverrider.registerWii(0)
                InputOverrider.setControlState(0, WiiControl.WIIMOTE_IR_X, 0.0)
                InputOverrider.clearControlState(0, WiiControl.WIIMOTE_IR_X)
                InputOverrider.unregisterWii(0)
                add("InputOverrider (JNI)", true, "registerWii/setControlState/unregisterWii berhasil")
            } catch (t: Throwable) {
                add("InputOverrider (JNI)", false, t.message ?: t.javaClass.simpleName)
            }
        }
    }

    val scan = state.scan
    if (scan != null) {
        val ready2 = scan.games.count { it.compatibility.canLaunch() }
        add("File game siap", if (ready2 > 0) true else null, "$ready2 dari ${scan.games.size} file valid untuk Black Ops Wii")
    }
    val folderOk = withContext(Dispatchers.IO) {
        if (!StorageAccess.hasFolderAccess(ctx)) null else try {
            AppPaths.ensureDirs()
            val f = File(AppPaths.logsDir, ".write-test")
            f.writeText("ok")
            val ok = f.readText() == "ok"
            f.delete()
            ok
        } catch (t: Throwable) {
            false
        }
    }
    add("Tulis folder ${AppPaths.root.path}", folderOk, if (folderOk == null) "Izin belum diberikan (SAF bisa dipakai)" else "")
    val dev = DeviceInfo.collect(ctx)
    add("Vulkan", if (dev.vulkanLevel >= 0) true else null, "level ${dev.vulkanLevel}, versi ${dev.vulkanVersion}")
    val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    add("Sensor gyroscope", if (sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null) true else null, "untuk mode Gyroscope")
    add("Driver GPU kustom", null, if (GpuDrivers.supportsCustomDrivers()) "didukung" else "tidak didukung")
    return out
}

/** Expected string of [DolphinBridge.readActive] for a write. */
private fun normalized(w: com.codblackops.mobile.core.config.ConfigWrite): String = when (w.type) {
    com.codblackops.mobile.core.config.ConfigWrite.Type.INT -> w.intValue.toString()
    com.codblackops.mobile.core.config.ConfigWrite.Type.BOOL -> w.boolValue.toString()
    com.codblackops.mobile.core.config.ConfigWrite.Type.FLOAT -> w.floatValue.toString()
    com.codblackops.mobile.core.config.ConfigWrite.Type.STRING -> w.stringValue ?: ""
    null -> ""
}

private fun exportLogs(ctx: Context, device: DeviceInfo): String {
    val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date())
    val files = ArrayList<Pair<String, File>>()
    AppLog.logFile()?.let { files.add("launcher.log" to it) }
    AppLog.logFile()?.parentFile?.let { File(it, "launcher.old.log") }?.takeIf { it.isFile }?.let { files.add("launcher.old.log" to it) }
    DolphinBridge.userDirectory()?.let { File(it, "Logs/dolphin.log") }?.takeIf { it.isFile }?.let { files.add("dolphin.log" to it) }
    val summary = device.summary() + "\nDolphin: " + DolphinBridge.version() + "\n"
    if (StorageAccess.hasFolderAccess(ctx) && AppPaths.ensureDirs()) {
        val dir = File(AppPaths.logsDir, stamp).apply { mkdirs() }
        for ((name, f) in files) f.copyTo(File(dir, name), overwrite = true)
        File(dir, "device.txt").writeText(summary)
        return "Log diekspor ke ${dir.path}"
    }
    val tree = AppPrefs.safTreeUri(ctx)?.let { Uri.parse(it) }
    if (tree != null && StorageAccess.treeIsWritable(ctx, tree)) {
        val saf = SafTree(ctx, tree)
        val logs = saf.ensureDir(saf.rootId, AppPaths.LOGS_NAME) ?: return "Gagal membuat folder Logs"
        val dir = saf.ensureDir(logs.documentId, stamp) ?: return "Gagal membuat folder log"
        for ((name, f) in files) saf.createFile(dir.documentId, name, "text/plain")?.use { o -> f.inputStream().use { it.copyTo(o) } }
        saf.createFile(dir.documentId, "device.txt", "text/plain")?.use { it.write(summary.toByteArray()) }
        return "Log diekspor ke folder SAF/Logs/$stamp"
    }
    return "Tidak ada folder yang bisa ditulis. Beri izin folder atau pilih folder SAF."
}
