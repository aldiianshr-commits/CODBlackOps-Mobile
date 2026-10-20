package com.codblackops.mobile.emulation

import android.content.Context
import android.os.Build
import android.os.PowerManager
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.codblackops.mobile.diag.AppLog

/** Logs Android thermal status changes and reports severe throttling (Android 10+). */
class ThermalMonitor(private val context: Context, private val onSevere: (Int) -> Unit) {
    private var listener: Any? = null

    fun start() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        startQ()
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun startQ() {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val l = PowerManager.OnThermalStatusChangedListener { status ->
            AppLog.i("Status termal berubah: $status")
            if (status >= PowerManager.THERMAL_STATUS_SEVERE) onSevere(status)
        }
        pm.addThermalStatusListener(ContextCompat.getMainExecutor(context), l)
        listener = l
    }

    fun stop() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        stopQ()
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun stopQ() {
        val l = listener as? PowerManager.OnThermalStatusChangedListener ?: return
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        pm.removeThermalStatusListener(l)
        listener = null
    }
}
