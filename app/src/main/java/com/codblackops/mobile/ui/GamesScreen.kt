@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.codblackops.mobile.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.codblackops.mobile.backend.BackendRegistry
import com.codblackops.mobile.backend.LaunchException
import com.codblackops.mobile.core.disc.Compatibility
import com.codblackops.mobile.diag.AppLog
import com.codblackops.mobile.diag.DeviceInfo
import com.codblackops.mobile.dolphin.DolphinBridge
import com.codblackops.mobile.prefs.AppPrefs
import com.codblackops.mobile.storage.AppPaths
import com.codblackops.mobile.storage.GameScanner
import com.codblackops.mobile.storage.ScannedGame
import com.codblackops.mobile.storage.StorageAccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.dolphinemu.dolphinemu.utils.DirectoryInitialization

fun rescan(state: LauncherState, scope: CoroutineScope) {
    if (state.scanning) return
    scope.launch {
        state.scanning = true
        try {
            state.scan = GameScanner.scan(state.activity)
        } catch (t: Throwable) {
            AppLog.e("Pindai gagal", t)
            state.snackbar.showSnackbar("Pindai gagal: ${t.message}")
        } finally {
            state.scanning = false
        }
    }
}

@Composable
fun GamesScreen(state: LauncherState) {
    val activity = state.activity
    val scope = rememberCoroutineScope()
    val dirState by DirectoryInitialization.getDolphinDirectoriesState().observeAsState()
    val ready = dirState == DirectoryInitialization.DirectoryInitializationState.DOLPHIN_DIRECTORIES_INITIALIZED
    val backend = remember { BackendRegistry.active() }
    var pendingLaunch by remember { mutableStateOf<ScannedGame?>(null) }
    var lowMemoryInfo by remember { mutableStateOf<DeviceInfo?>(null) }

    val legacyPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        rescan(state, scope)
    }
    val pickTree = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            if (StorageAccess.persistTreePermission(activity, uri)) {
                AppPrefs.setSafTreeUri(activity, uri.toString())
                AppLog.i("Folder SAF dipilih: $uri")
                rescan(state, scope)
            } else {
                scope.launch { state.snackbar.showSnackbar("Izin folder tidak bisa disimpan") }
            }
        }
    }

    fun doLaunch(game: ScannedGame) {
        scope.launch {
            state.busyMessage = "Menyiapkan ${game.title}…"
            try {
                backend.launch(activity, game)
            } catch (e: LaunchException) {
                AppLog.e("Peluncuran gagal: ${e.message}", e.cause)
                state.snackbar.showSnackbar(e.message ?: "Peluncuran gagal")
            } catch (t: Throwable) {
                AppLog.e("Peluncuran gagal", t)
                state.snackbar.showSnackbar("Peluncuran gagal: ${t.message}")
            } finally {
                state.busyMessage = null
            }
        }
    }

    fun requestLaunch(game: ScannedGame) {
        val perf = AppPrefs.performance(activity, game.gameId)
        val info = DeviceInfo.collect(activity)
        if (perf.memoryGuard && (info.availRamMb < 1200 || info.lowMemory)) {
            pendingLaunch = game
            lowMemoryInfo = info
        } else {
            doLaunch(game)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            Spacer(Modifier.height(12.dp))
            Text("Call of Duty Black Ops Mobile", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "Launcher untuk Call of Duty: Black Ops versi Nintendo Wii",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            SectionCard("Backend") {
                InfoRow("Aktif", backend.displayName)
                InfoRow("Status", if (ready) "Siap (${DolphinBridge.version()})" else "Menginisialisasi…")
                NoteText(backend.technique)
                val others = BackendRegistry.all.filter { it !== backend }
                for (o in others) NoteText("${o.displayName}: ${o.unavailableReason() ?: "tersedia"}")
            }
        }
        item {
            SectionCard("Lokasi game", "Letakkan file ISO / WBFS / RVZ / WIA / GCZ / CISO milik Anda sendiri.") {
                val hasAccess = StorageAccess.hasFolderAccess(activity)
                InfoRow("Folder default", AppPaths.root.path)
                InfoRow("Akses", if (hasAccess) "Diizinkan" else "Belum diizinkan")
                InfoRow("Folder save", AppPaths.saveDir.path)
                val tree = AppPrefs.safTreeUri(activity)
                InfoRow("Folder SAF", tree?.let { Uri.decode(it) } ?: "Belum dipilih")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!hasAccess) {
                        Button(onClick = {
                            if (StorageAccess.needsAllFilesAccess()) {
                                activity.startActivity(StorageAccess.allFilesAccessIntent(activity))
                            } else {
                                legacyPermission.launch(StorageAccess.legacyPermissions)
                            }
                        }) { Text("Izinkan akses folder") }
                    }
                    OutlinedButton(onClick = { pickTree.launch(null) }) { Text("Pilih folder (SAF)") }
                    if (tree != null) {
                        TextButton(onClick = {
                            AppPrefs.setSafTreeUri(activity, null)
                            rescan(state, scope)
                        }) { Text("Lupakan folder SAF") }
                    }
                    OutlinedButton(onClick = { rescan(state, scope) }, enabled = !state.scanning) {
                        Text(if (state.scanning) "Memindai…" else "Pindai ulang")
                    }
                }
                state.scan?.notes?.forEach { NoteText(it, CodYellow) }
            }
        }
        val scan = state.scan
        if (scan == null || state.scanning) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Memindai file game…")
                }
            }
        } else if (scan.games.isEmpty()) {
            item {
                SectionCard("Belum ada game", "Tidak ada file image Wii di folder yang dipindai.") {
                    NoteText("Salin dump Call of Duty: Black Ops (Wii) milik Anda ke ${AppPaths.root.path} atau pilih folder lain dengan SAF, lalu tekan Pindai ulang.")
                    NoteText("File dipindai: ${scan.scannedFiles}")
                }
            }
        } else {
            items(scan.games, key = { it.location }) { game ->
                GameCard(game, ready && state.busyMessage == null, onPlay = { requestLaunch(game) }, state = state)
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    state.busyMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text("Memulai game") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                    Spacer(Modifier.width(16.dp))
                    Text(msg)
                }
            },
        )
    }

    val pg = pendingLaunch
    val mem = lowMemoryInfo
    if (pg != null && mem != null) {
        AlertDialog(
            onDismissRequest = { pendingLaunch = null },
            title = { Text("Memori rendah") },
            text = {
                Text(
                    "RAM tersedia hanya ${mem.availRamMb} MB dari ${mem.totalRamMb} MB. Tutup aplikasi lain " +
                        "atau pilih profil Hemat Baterai / resolusi 480p untuk mengurangi risiko crash.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingLaunch = null
                    doLaunch(pg)
                }) { Text("Tetap jalankan") }
            },
            dismissButton = { TextButton(onClick = { pendingLaunch = null }) { Text("Batal") } },
        )
    }
}

@Composable
private fun GameCard(game: ScannedGame, canStart: Boolean, onPlay: () -> Unit, state: LauncherState) {
    val c = game.compatibility
    val color = when (c.status) {
        Compatibility.Status.READY -> CodGreen
        Compatibility.Status.READY_WITH_WARNING, Compatibility.Status.PENDING_BACKEND_CHECK -> CodYellow
        else -> CodRed
    }
    val statusLabel = when (c.status) {
        Compatibility.Status.READY -> "SIAP"
        Compatibility.Status.READY_WITH_WARNING -> "SIAP (PERINGATAN)"
        Compatibility.Status.PENDING_BACKEND_CHECK -> "MENUNGGU VALIDASI"
        Compatibility.Status.WRONG_GAME -> "GAME LAIN"
        Compatibility.Status.BACKEND_REJECTED -> "DITOLAK BACKEND"
        else -> "TIDAK VALID"
    }
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(game.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    listOfNotNull(game.gameId, game.regionLabel, game.formatLabel, formatBytes(game.sizeBytes), game.source.label)
                        .joinToString("  •  "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(game.fileName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = color.copy(alpha = 0.18f), shape = RoundedCornerShape(6.dp)) {
                        Text(statusLabel, color = color, style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                    }
                    if (game.gameId != null && AppPrefs.hasGameProfile(state.activity, game.gameId!!)) {
                        Spacer(Modifier.width(8.dp))
                        Text("Profil khusus", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
                NoteText(c.message ?: "")
            }
            Spacer(Modifier.width(12.dp))
            Button(onClick = onPlay, enabled = canStart && c.canLaunch()) { Text("MAIN") }
        }
    }
}
