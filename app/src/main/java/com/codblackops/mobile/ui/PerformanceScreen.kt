@file:OptIn(ExperimentalMaterial3Api::class)

package com.codblackops.mobile.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.codblackops.mobile.core.config.PerformanceConfig
import com.codblackops.mobile.diag.DeviceInfo
import com.codblackops.mobile.prefs.AppPrefs

@Composable
fun PerformanceScreen(state: LauncherState) {
    val ctx = state.activity
    var target by remember { mutableStateOf<String?>(null) }
    val tick = state.refreshTick
    var p by remember(target, tick) { mutableStateOf(AppPrefs.performance(ctx, target)) }
    val device = remember { DeviceInfo.collect(ctx) }

    fun update(change: (PerformanceConfig) -> Unit) {
        val n = p.copy()
        change(n)
        p = n
        AppPrefs.setPerformance(ctx, target, n)
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text("Performa", style = MaterialTheme.typography.headlineSmall)
        NoteText(
            "Target utama: Xiaomi Pad 6 (Snapdragon 870, Adreno 650). Profil mengatur opsi CPU/GPU Dolphin dan mode " +
                "kinerja Android; profil Hemat Baterai juga membatasi pengaturan grafis.",
        )
        SectionCard("Profil") { ProfileTargetPicker(state, target) { target = it } }

        for (profile in PerformanceConfig.Profile.values()) {
            val selected = profile == p.profile
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { update { it.profile = profile } },
                colors = CardDefaults.cardColors(
                    containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                ),
                border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(profile.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(profile.description, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        SectionCard("CPU") {
            ChoiceChips("Core CPU", PerformanceConfig.CpuCore.values().toList(), p.cpuCore, { it.label }) { c ->
                update { it.cpuCore = c }
            }
            NoteText(
                "JIT ARM64 menerjemahkan kode PowerPC Broadway ke ARM64 saat runtime (rekompilasi dinamis). " +
                    "Ini bukan rekompilasi statis; lihat tab Tentang.",
            )
            SliderRow(
                "Clock CPU emulasi", p.cpuClockPercent.toFloat(), 50f..150f, steps = 19,
                format = { "${it.toInt()}%" },
            ) { v -> update { it.cpuClockPercent = (v / 5f).toInt() * 5 } }
            NoteText(
                "Di bawah 100% mengurangi beban perangkat tetapi bisa memperlambat game; di atas 100% bisa " +
                    "mengurangi patah-patah pada bagian berat, dengan konsumsi daya lebih besar.",
            )
        }

        SectionCard("Suhu & memori") {
            SwitchRow(
                "Pelindung suhu", p.thermalGuard,
                "Saat Android melaporkan suhu berat, resolusi internal diturunkan ke 1x untuk sisa sesi.",
            ) { v -> update { it.thermalGuard = v } }
            SwitchRow(
                "Peringatan memori rendah", p.memoryGuard,
                "Memperingatkan sebelum mulai bila RAM tersedia < 1,2 GB untuk mencegah crash kehabisan memori.",
            ) { v -> update { it.memoryGuard = v } }
            InfoRow("RAM", "${device.availRamMb} MB tersedia / ${device.totalRamMb} MB")
            InfoRow("Status termal", device.thermalLabel())
            InfoRow("Sustained performance", if (device.sustainedPerformanceSupported) "Didukung" else "Tidak didukung")
            InfoRow("SoC", device.soc)
        }
        Spacer(Modifier.height(24.dp))
    }
}
