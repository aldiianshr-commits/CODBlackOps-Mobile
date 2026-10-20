@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.codblackops.mobile.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.codblackops.mobile.core.config.GraphicsConfig
import com.codblackops.mobile.diag.AppLog
import com.codblackops.mobile.diag.DeviceInfo
import com.codblackops.mobile.dolphin.DolphinBridge
import com.codblackops.mobile.dolphin.GpuDrivers
import com.codblackops.mobile.prefs.AppPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Profile target picker shared by the Graphics and Performance tabs. */
@Composable
fun ProfileTargetPicker(state: LauncherState, target: String?, onChange: (String?) -> Unit) {
    val ids = state.blackOpsIds()
    val options = listOf<String?>(null) + ids
    ChoiceChips("Profil", options, target, { it ?: "Global (semua game)" }) { onChange(it) }
    if (target != null) {
        val own = AppPrefs.hasGameProfile(state.activity, target)
        NoteText(
            if (own) {
                "Game $target memakai profil khusus."
            } else {
                "Game $target belum punya profil khusus dan memakai profil global. Mengubah opsi di sini membuat profil khusus."
            },
        )
        if (own) {
            TextButton(onClick = {
                AppPrefs.clearGameProfile(state.activity, target)
                state.refreshTick++
            }) { Text("Hapus profil khusus $target") }
        }
    }
}

@Composable
fun GraphicsScreen(state: LauncherState) {
    val ctx = state.activity
    val scope = rememberCoroutineScope()
    var target by remember { mutableStateOf<String?>(null) }
    val tick = state.refreshTick
    var g by remember(target, tick) { mutableStateOf(AppPrefs.graphics(ctx, target)) }
    val device = remember { DeviceInfo.collect(ctx) }
    val customSupported = remember { GpuDrivers.supportsCustomDrivers() }
    var driverTick by remember { mutableIntStateOf(0) }
    val installed = remember(driverTick) { GpuDrivers.installedDriver() }
    var remoteDrivers by remember { mutableStateOf<List<GpuDrivers.RemoteDriver>?>(null) }
    var driverBusy by remember { mutableStateOf(false) }

    fun update(change: (GraphicsConfig) -> Unit, keepPreset: Boolean = false) {
        val n = g.copy()
        change(n)
        if (!keepPreset) n.preset = GraphicsConfig.Preset.CUSTOM
        g = n
        AppPrefs.setGraphics(ctx, target, n)
    }

    val pickDriver = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            driverBusy = true
            val msg = withContext(Dispatchers.IO) {
                try {
                    val r = ctx.contentResolver.openInputStream(uri)?.use { GpuDrivers.install(it) }
                    if (r == null) "File tidak bisa dibuka" else GpuDrivers.describe(r)
                } catch (t: Throwable) {
                    AppLog.e("Instal driver gagal", t)
                    "Instal driver gagal: ${t.message}"
                }
            }
            driverBusy = false
            driverTick++
            state.snackbar.showSnackbar(msg)
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text("Pengaturan grafis", style = MaterialTheme.typography.headlineSmall)
        NoteText("Semua nilai ditulis ke konfigurasi Dolphin (GFX.ini / Dolphin.ini) tepat sebelum game dimulai.")

        SectionCard("Profil") { ProfileTargetPicker(state, target) { target = it } }

        SectionCard("Preset") {
            ChoiceChips(
                null, GraphicsConfig.Preset.values().toList(), g.preset,
                {
                    when (it) {
                        GraphicsConfig.Preset.VERY_LOW -> "Very Low"
                        GraphicsConfig.Preset.LOW -> "Low"
                        GraphicsConfig.Preset.MEDIUM -> "Medium"
                        GraphicsConfig.Preset.HIGH -> "High"
                        GraphicsConfig.Preset.ULTRA -> "Ultra"
                        else -> "Kustom"
                    }
                },
                enabled = { it != GraphicsConfig.Preset.CUSTOM },
            ) { p -> update({ it.applyPreset(p) }, keepPreset = true) }
            NoteText("Very Low/Low: 480p • Medium: 720p + FXAA • High: 720p + MSAA 2x • Ultra: 1080p + MSAA 4x + AF 16x")
            OutlinedButton(onClick = {
                val d = GraphicsConfig.defaults()
                g = d
                AppPrefs.setGraphics(ctx, target, d)
            }) { Text("Reset to Default") }
        }

        SectionCard("Resolusi & frame") {
            ChoiceChips(
                "Resolusi internal", GraphicsConfig.Resolution.values().toList(), g.resolution,
                { "${it.label} (${it.detail})" }, enabled = { it.isSupported() },
            ) { r -> update({ it.resolution = r }) }
            NoteText(GraphicsConfig.REASON_900P, CodYellow)
            ChoiceChips(
                "Batas FPS", GraphicsConfig.FpsLimit.values().toList(), g.fpsLimit, { "${it.fps} FPS" },
                enabled = { it.isSupported() },
            ) { f -> update({ it.fpsLimit = f }) }
            NoteText(GraphicsConfig.REASON_FPS, CodYellow)
            SwitchRow("V-Sync", g.vsync, "Mengurangi tearing, menambah sedikit latensi.") { v -> update({ it.vsync = v }) }
            SwitchRow("Tampilkan FPS & frame time (OSD Dolphin)", g.showFps) { v -> update({ it.showFps = v }, keepPreset = true) }
        }

        SectionCard("Tekstur & bayangan") {
            ChoiceChips(
                "Kualitas tekstur", GraphicsConfig.TextureQuality.values().toList(), GraphicsConfig.TextureQuality.HIGH,
                { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } }, enabled = { false },
            ) { }
            NoteText(GraphicsConfig.REASON_TEXTURE, CodYellow)
            SwitchRow(
                "Muat paket tekstur kustom (HD)", g.customTextures,
                "Letakkan paket di folder Load/Textures/<ID game>/ pada direktori pengguna Dolphin.",
            ) { v -> update({ it.customTextures = v }) }
            ChoiceChips(
                "Kualitas bayangan", GraphicsConfig.ShadowQuality.values().toList(), GraphicsConfig.ShadowQuality.MEDIUM,
                { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } }, enabled = { false },
            ) { }
            NoteText(GraphicsConfig.REASON_SHADOW, CodYellow)
        }

        SectionCard("Anti-aliasing & filter") {
            ChoiceChips("Anti-aliasing", GraphicsConfig.AntiAliasing.values().toList(), g.antiAliasing, { it.label }) { a ->
                update({ it.antiAliasing = a })
            }
            NoteText("FXAA = shader post-processing bawaan Dolphin (ringan). MSAA berat pada resolusi tinggi.")
            ChoiceChips("Anisotropic filtering", GraphicsConfig.Anisotropy.values().toList(), g.anisotropy, { it.label }) { a ->
                update({ it.anisotropy = a })
            }
        }

        SectionCard("Renderer & driver") {
            val hasVulkan = device.vulkanLevel >= 0
            ChoiceChips(
                "Backend grafis", GraphicsConfig.Backend.values().toList(), g.backend, { it.label },
                enabled = { it != GraphicsConfig.Backend.VULKAN || hasVulkan },
            ) { b -> update({ it.backend = b }, keepPreset = true) }
            if (!hasVulkan) NoteText("Perangkat tidak melaporkan dukungan Vulkan.", CodYellow)
            SwitchRow("Shader cache", g.shaderCache, "Menyimpan shader yang sudah dikompilasi untuk mengurangi stutter.") { v ->
                update({ it.shaderCache = v }, keepPreset = true)
            }
            ChoiceChips(
                "Driver GPU", GraphicsConfig.GpuDriver.values().toList(), g.gpuDriver,
                { if (it == GraphicsConfig.GpuDriver.SYSTEM) "System Driver" else "Turnip (Mesa)" },
                enabled = { it == GraphicsConfig.GpuDriver.SYSTEM || customSupported },
            ) { d -> update({ it.gpuDriver = d }, keepPreset = true) }
            if (!customSupported) {
                NoteText(
                    "Perangkat/driver ini tidak mendukung pemuatan driver kustom lewat libadrenotools (umumnya hanya GPU Adreno).",
                    CodYellow,
                )
            } else {
                InfoRow("Driver kustom terpasang", installed?.let { "${it.name} ${it.driverVersion}" } ?: "Tidak ada")
                if (g.gpuDriver == GraphicsConfig.GpuDriver.TURNIP && installed == null) {
                    NoteText("Turnip dipilih tetapi belum ada paket terpasang; game akan memakai driver sistem.", CodYellow)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = DolphinBridge.isReady() && !driverBusy, onClick = {
                        driverBusy = true
                        scope.launch {
                            try {
                                remoteDrivers = GpuDrivers.fetchTurnipDrivers()
                            } catch (t: Throwable) {
                                state.snackbar.showSnackbar("Gagal mengambil daftar driver: ${t.message}")
                            } finally {
                                driverBusy = false
                            }
                        }
                    }) { Text(if (driverBusy) "Memproses…" else "Unduh Turnip") }
                    OutlinedButton(enabled = DolphinBridge.isReady() && !driverBusy, onClick = {
                        pickDriver.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                    }) { Text("Pilih paket .zip") }
                    if (installed != null) {
                        TextButton(onClick = {
                            GpuDrivers.uninstall()
                            driverTick++
                        }) { Text("Hapus driver kustom") }
                    }
                }
                NoteText("Paket driver dimuat oleh Dolphin melalui libadrenotools. Jika game crash atau grafis rusak, kembali ke System Driver.")
            }
        }

        SectionCard("Layar") {
            ChoiceChips("Aspect ratio", GraphicsConfig.Aspect.values().toList(), g.aspect, { it.label }) { a ->
                update({ it.aspect = a }, keepPreset = true)
            }
            NoteText("Black Ops Wii dirender 16:9 (mode layar lebar Wii diaktifkan). 16:10 mengisi layar Xiaomi Pad 6 penuh.")
            SwitchRow("Fullscreen", g.fullscreen, "Sembunyikan status/navigation bar dan gunakan area notch.") { v ->
                update({ it.fullscreen = v }, keepPreset = true)
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    val list = remoteDrivers
    if (list != null) {
        AlertDialog(
            onDismissRequest = { remoteDrivers = null },
            title = { Text("Driver Turnip (AdrenoToolsDrivers)") },
            text = {
                if (list.isEmpty()) {
                    Text("Tidak ada paket Turnip ditemukan.")
                } else {
                    LazyColumn(Modifier.height(320.dp)) {
                        items(list) { d ->
                            TextButton(onClick = {
                                remoteDrivers = null
                                driverBusy = true
                                scope.launch {
                                    val msg = try {
                                        GpuDrivers.describe(GpuDrivers.downloadAndInstall(ctx, d))
                                    } catch (t: Throwable) {
                                        AppLog.e("Unduh driver gagal", t)
                                        "Unduh driver gagal: ${t.message}"
                                    }
                                    driverBusy = false
                                    driverTick++
                                    state.snackbar.showSnackbar(msg)
                                }
                            }) {
                                Column {
                                    Text(d.assetName)
                                    NoteText("${d.releaseName} • ${formatBytes(d.sizeBytes)}")
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { remoteDrivers = null }) { Text("Tutup") } },
        )
    }
}
