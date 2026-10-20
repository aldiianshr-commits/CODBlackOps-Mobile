@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.codblackops.mobile.ui

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.codblackops.mobile.core.disc.BlackOpsCatalog
import com.codblackops.mobile.core.save.BackupPolicy
import com.codblackops.mobile.diag.AppLog
import com.codblackops.mobile.dolphin.DolphinBridge
import com.codblackops.mobile.prefs.AppPrefs
import com.codblackops.mobile.storage.AppPaths
import com.codblackops.mobile.storage.SaveManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

@Composable
fun SavesScreen(state: LauncherState) {
    val ctx = state.activity
    val scope = rememberCoroutineScope()
    var autoBackup by remember { mutableStateOf(AppPrefs.autoBackupOnExit(ctx)) }
    var includeStates by remember { mutableStateOf(AppPrefs.includeSaveStates(ctx)) }
    val ids = state.blackOpsIds()
    var statuses by remember { mutableStateOf<Map<String, SaveManager.Status>>(emptyMap()) }
    var busy by remember { mutableStateOf(false) }
    var restoreFor by remember { mutableStateOf<SaveManager.Status?>(null) }
    var deleteFor by remember { mutableStateOf<String?>(null) }
    val tick = state.refreshTick

    suspend fun reload() {
        if (!DolphinBridge.isReady()) return
        statuses = withContext(Dispatchers.IO) {
            ids.associateWith { id ->
                try {
                    SaveManager.status(ctx, id)
                } catch (t: Throwable) {
                    null
                }
            }.filterValues { it != null }.mapValues { it.value!! }
        }
    }

    LaunchedEffect(ids.joinToString(), tick) { reload() }

    fun runTask(label: String, block: () -> String) {
        if (busy) return
        busy = true
        scope.launch {
            val msg = withContext(Dispatchers.IO) {
                try {
                    block()
                } catch (t: Throwable) {
                    AppLog.e("$label gagal", t)
                    "$label gagal: ${t.message}"
                }
            }
            reload()
            busy = false
            state.snackbar.showSnackbar(msg)
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text("Save data", style = MaterialTheme.typography.headlineSmall)
        NoteText(
            "Save Black Ops disimpan di NAND Wii emulasi milik Dolphin. Launcher menyalinnya ke " +
                "${AppPaths.saveDir.path}/<ID game>/data setelah setiap sesi dan dapat membuat backup zip di folder Backups.",
        )
        SectionCard("Otomatis") {
            SwitchRow("Backup otomatis setelah bermain", autoBackup, "Menyimpan maksimal ${BackupPolicy.DEFAULT_KEEP} backup per game.") {
                autoBackup = it
                AppPrefs.setAutoBackupOnExit(ctx, it)
            }
            SwitchRow("Sertakan save state Dolphin dalam backup", includeStates) {
                includeStates = it
                AppPrefs.setIncludeSaveStates(ctx, it)
            }
            NoteText("Jika NAND masih kosong saat game dimulai, save dari folder Save diimpor otomatis.")
        }
        if (!DolphinBridge.isReady()) {
            NoteText("Menunggu backend Dolphin siap…", CodYellow)
        } else if (ids.isEmpty()) {
            NoteText("Belum ada Black Ops yang terdeteksi. Pindai game terlebih dahulu di tab Game.")
        }
        for (id in ids) {
            val st = statuses[id]
            SectionCard("${BlackOpsCatalog.regionName(id)} — $id") {
                if (st == null) {
                    NoteText("Status belum dimuat")
                } else {
                    InfoRow("NAND", if (st.nandFileCount == 0) "Belum ada save" else "${st.nandFileCount} file, ${formatBytes(st.nandBytes)}")
                    if (st.nandLastModified > 0) InfoRow("Terakhir diubah", DateFormat.getDateTimeInstance().format(Date(st.nandLastModified)))
                    InfoRow("Save state", "${st.stateFiles} file")
                    InfoRow("Folder Save", st.targetDescription ?: "Tidak tersedia (beri izin folder atau pilih SAF)")
                    InfoRow("Salinan di folder Save", if (st.externalHasData) "Ada" else "Tidak ada")
                    InfoRow("Backup", "${st.backups.size}")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(enabled = !busy, onClick = { runTask("Backup") { SaveManager.backup(ctx, id, "manual") } }) { Text("Backup sekarang") }
                        OutlinedButton(enabled = !busy, onClick = { runTask("Salin") { SaveManager.mirrorToExternal(ctx, id) } }) {
                            Text("Salin ke folder Save")
                        }
                        OutlinedButton(enabled = !busy, onClick = {
                            runTask("Impor") { SaveManager.importFromExternal(ctx, id, onlyIfNandEmpty = false) }
                        }) { Text("Impor dari folder Save") }
                        OutlinedButton(enabled = !busy && st.backups.isNotEmpty(), onClick = { restoreFor = st }) { Text("Pulihkan…") }
                        TextButton(enabled = !busy && st.nandFileCount > 0, onClick = { deleteFor = id }) { Text("Hapus save") }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    val rs = restoreFor
    if (rs != null) {
        AlertDialog(
            onDismissRequest = { restoreFor = null },
            title = { Text("Pulihkan backup ${rs.gameId}") },
            text = {
                LazyColumn(Modifier.height(300.dp)) {
                    items(rs.backups) { b ->
                        TextButton(onClick = {
                            restoreFor = null
                            runTask("Pemulihan") { SaveManager.restore(ctx, rs.gameId, b.fileName) }
                        }) {
                            Text(
                                DateFormat.getDateTimeInstance().format(Date(b.timeMs)) + (b.tag?.let { " ($it)" } ?: ""),
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { restoreFor = null }) { Text("Batal") } },
        )
    }
    val del = deleteFor
    if (del != null) {
        AlertDialog(
            onDismissRequest = { deleteFor = null },
            title = { Text("Hapus save $del?") },
            text = { Text("Save di NAND akan dihapus. Backup otomatis dibuat terlebih dahulu.") },
            confirmButton = {
                TextButton(onClick = {
                    deleteFor = null
                    runTask("Hapus") { SaveManager.deleteNandSave(ctx, del) }
                }) { Text("Hapus") }
            },
            dismissButton = { TextButton(onClick = { deleteFor = null }) { Text("Batal") } },
        )
    }
}
