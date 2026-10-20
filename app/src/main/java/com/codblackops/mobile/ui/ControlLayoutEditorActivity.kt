@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.codblackops.mobile.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.codblackops.mobile.core.input.ControlLayout
import com.codblackops.mobile.core.input.ControlSettings
import com.codblackops.mobile.core.input.HudElementId
import com.codblackops.mobile.emulation.FpsTouchOverlayView
import com.codblackops.mobile.prefs.AppPrefs
import java.util.Locale

/** Drag / pinch editor for the touch HUD (separate layouts for Campaign and Multiplayer). */
class ControlLayoutEditorActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_SET = "layoutSet"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let {
            it.hide(WindowInsetsCompat.Type.systemBars())
            it.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        val set = try {
            ControlSettings.LayoutSet.valueOf(intent.getStringExtra(EXTRA_SET) ?: "")
        } catch (_: IllegalArgumentException) {
            ControlSettings.LayoutSet.CAMPAIGN
        }
        val settings = AppPrefs.controls(this)
        val working = AppPrefs.layout(this, set).copy()
        val view = FpsTouchOverlayView(this, null, settings, working, editMode = true)

        setContent {
            CodTheme {
                var selected by remember { mutableStateOf<HudElementId?>(null) }
                var tick by remember { mutableIntStateOf(0) }
                var panelVisible by remember { mutableStateOf(true) }
                view.onSelectionChanged = { selected = it; tick++ }
                view.onLayoutEdited = { tick++ }

                Box(Modifier.fillMaxSize().background(Color(0xFF202428))) {
                    AndroidView(factory = { view }, modifier = Modifier.fillMaxSize())
                    Column(
                        Modifier.align(Alignment.TopCenter).padding(8.dp).widthIn(max = 560.dp)
                            .background(Color(0xE6171A1D), RoundedCornerShape(12.dp)).padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        val setName = if (set == ControlSettings.LayoutSet.CAMPAIGN) "Campaign" else "Multiplayer"
                        Text("Edit tata letak – $setName", style = MaterialTheme.typography.titleSmall)
                        if (panelVisible) {
                            val id = selected
                            if (id == null) {
                                NoteText("Sentuh tombol untuk memilih. Geser untuk memindah, cubit dua jari untuk mengubah ukuran. Tombol bergaris putus-putus sedang disembunyikan.")
                            } else {
                                val e = remember(id, tick) { working.get(id) }
                                Text(id.description(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                                SliderRow("Ukuran", e.sizeDp, ControlLayout.MIN_SIZE_DP..ControlLayout.MAX_SIZE_DP,
                                    format = { "${it.toInt()} dp" }) { v ->
                                    working.set(id, e.cx, e.cy, v, e.opacity, e.visible)
                                    view.invalidate()
                                    tick++
                                }
                                SwitchRow("Pakai transparansi global", e.opacity < 0f) { useGlobal ->
                                    working.set(id, e.cx, e.cy, e.sizeDp, if (useGlobal) -1f else settings.hudOpacity, e.visible)
                                    view.invalidate()
                                    tick++
                                }
                                if (e.opacity >= 0f) {
                                    SliderRow("Transparansi", e.opacity, 0.1f..1f,
                                        format = { String.format(Locale.ROOT, "%.0f%%", it * 100) }) { v ->
                                        working.set(id, e.cx, e.cy, e.sizeDp, v, e.visible)
                                        view.invalidate()
                                        tick++
                                    }
                                }
                                SwitchRow("Tampilkan tombol ini", e.visible) { v ->
                                    working.set(id, e.cx, e.cy, e.sizeDp, e.opacity, v)
                                    view.invalidate()
                                    tick++
                                }
                            }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                AppPrefs.setLayout(this@ControlLayoutEditorActivity, set, working)
                                finish()
                            }) { Text("Simpan") }
                            OutlinedButton(onClick = {
                                val p = ControlLayout.preset(AppPrefs.layoutPreset(this@ControlLayoutEditorActivity), set)
                                for (h in HudElementId.values()) {
                                    val pe = p.get(h)
                                    working.set(h, pe.cx, pe.cy, pe.sizeDp, pe.opacity, pe.visible)
                                }
                                view.invalidate()
                                tick++
                            }) { Text("Reset") }
                            TextButton(onClick = { panelVisible = !panelVisible }) {
                                Text(if (panelVisible) "Ciutkan panel" else "Buka panel")
                            }
                            TextButton(onClick = { finish() }) { Text("Batal") }
                        }
                    }
                }
            }
        }
    }
}
