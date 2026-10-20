@file:OptIn(ExperimentalMaterial3Api::class)

package com.codblackops.mobile.ui

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.codblackops.mobile.core.disc.BlackOpsCatalog
import com.codblackops.mobile.diag.AppLog
import com.codblackops.mobile.dolphin.DolphinBridge
import com.codblackops.mobile.prefs.AppPrefs
import com.codblackops.mobile.storage.GameScanner
import com.codblackops.mobile.storage.SaveManager
import com.codblackops.mobile.storage.ScanResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.dolphinemu.dolphinemu.activities.EmulationActivity

/** Home screen of "Call of Duty Black Ops Mobile". */
class LauncherActivity : AppCompatActivity() {

    /** Set by the gamepad-mapping dialog to capture the next button press. */
    var keyCaptureListener: ((KeyEvent) -> Boolean)? = null

    val resumeTick = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLog.init(this)
        AppLog.i("Launcher dibuka")
        DolphinBridge.startInit(this)
        setContent {
            CodTheme {
                LauncherApp(this)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Dolphin ignores launch requests until its own launcher calls this; we are that launcher.
        EmulationActivity.stopIgnoringLaunchRequests()
        resumeTick.intValue++
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val l = keyCaptureListener
        if (l != null && l(event)) return true
        return super.dispatchKeyEvent(event)
    }
}

/** UI-wide state shared between the tabs. */
class LauncherState(val activity: LauncherActivity) {
    var scan by mutableStateOf<ScanResult?>(null)
    var scanning by mutableStateOf(false)
    var busyMessage by mutableStateOf<String?>(null)
    val snackbar = SnackbarHostState()
    var refreshTick by mutableIntStateOf(0)

    /** Black Ops game IDs known from the last scan (plus the last played one). */
    fun blackOpsIds(): List<String> {
        val ids = LinkedHashSet<String>()
        scan?.games?.forEach { g -> g.gameId?.let { if (BlackOpsCatalog.isBlackOpsWii(it)) ids.add(it) } }
        AppPrefs.lastPlayedGameId(activity)?.let { if (BlackOpsCatalog.isBlackOpsWii(it)) ids.add(it) }
        return ids.toList()
    }
}

enum class LauncherTab(val label: String, val icon: ImageVector) {
    GAMES("Game", Icons.Filled.SportsEsports),
    CONTROLS("Kontrol", Icons.Filled.Gamepad),
    GRAPHICS("Grafis", Icons.Filled.Tune),
    PERFORMANCE("Performa", Icons.Filled.Speed),
    SAVES("Save", Icons.Filled.Save),
    DIAGNOSTICS("Diagnostik", Icons.Filled.BugReport),
    ABOUT("Tentang", Icons.Filled.Info),
}

@Composable
fun LauncherApp(activity: LauncherActivity) {
    val state = remember { LauncherState(activity) }
    var tab by remember { mutableStateOf(LauncherTab.GAMES) }
    val scope = rememberCoroutineScope()
    val resume = activity.resumeTick.intValue

    // Scan once at start and whenever the user returns (e.g. after granting permission).
    LaunchedEffect(resume) {
        if (!state.scanning) {
            state.scanning = true
            try {
                DolphinBridge.awaitReady(activity, timeoutMs = 20_000)
                state.scan = GameScanner.scan(activity)
            } catch (t: Throwable) {
                AppLog.e("Pindai gagal", t)
            } finally {
                state.scanning = false
            }
        }
        // Mirror/back up saves of the session that just ended.
        val pending = AppPrefs.pendingSaveSync(activity)
        if (pending != null) {
            var waited = 0
            while (!(DolphinBridge.isReady() && DolphinBridge.isEmulationIdle()) && waited < 15_000) {
                delay(250)
                waited += 250
            }
            val msg = withContext(Dispatchers.IO) {
                try {
                    SaveManager.afterSession(activity, pending)
                } catch (t: Throwable) {
                    AppLog.e("Sinkron save setelah sesi gagal", t)
                    "Sinkron save gagal: ${t.message}"
                }
            }
            AppPrefs.setPendingSaveSync(activity, null)
            state.refreshTick++
            scope.launch { state.snackbar.showSnackbar(msg) }
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(state.snackbar) }) { padding ->
        Row(Modifier.fillMaxSize().padding(padding)) {
            NavigationRail(containerColor = MaterialTheme.colorScheme.background) {
                for (t in LauncherTab.values()) {
                    NavigationRailItem(
                        selected = t == tab,
                        onClick = { tab = t },
                        icon = { Icon(t.icon, contentDescription = t.label) },
                        label = { Text(t.label) },
                    )
                }
            }
            Box(Modifier.weight(1f).fillMaxSize()) {
                when (tab) {
                    LauncherTab.GAMES -> GamesScreen(state)
                    LauncherTab.CONTROLS -> ControlsScreen(state)
                    LauncherTab.GRAPHICS -> GraphicsScreen(state)
                    LauncherTab.PERFORMANCE -> PerformanceScreen(state)
                    LauncherTab.SAVES -> SavesScreen(state)
                    LauncherTab.DIAGNOSTICS -> DiagnosticsScreen(state)
                    LauncherTab.ABOUT -> AboutScreen()
                }
            }
        }
    }
}
