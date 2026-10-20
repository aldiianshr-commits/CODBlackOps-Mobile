package com.codblackops.mobile.diag

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager

/** Snapshot of device facts relevant to emulation performance. */
data class DeviceInfo(
    val model: String,
    val device: String,
    val soc: String,
    val android: String,
    val totalRamMb: Long,
    val availRamMb: Long,
    val lowMemory: Boolean,
    val vulkanLevel: Int,
    val vulkanVersion: String,
    val thermalStatus: Int,
    val sustainedPerformanceSupported: Boolean,
) {
    val isXiaomiPad6: Boolean
        get() = device.equals("pipa", ignoreCase = true) || model.contains("Pad 6", ignoreCase = true)

    fun thermalLabel(): String = when (thermalStatus) {
        -1 -> "tidak tersedia"
        0 -> "Normal"
        1 -> "Ringan"
        2 -> "Sedang"
        3 -> "Berat"
        4 -> "Kritis"
        5 -> "Darurat"
        6 -> "Shutdown"
        else -> "Status $thermalStatus"
    }

    companion object {
        fun collect(context: Context): DeviceInfo {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val mi = ActivityManager.MemoryInfo()
            am.getMemoryInfo(mi)
            val pm = context.packageManager
            var vkLevel = -1
            var vkVersion = "tidak ada"
            for (f in pm.systemAvailableFeatures) {
                when (f.name) {
                    PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL -> vkLevel = f.version
                    PackageManager.FEATURE_VULKAN_HARDWARE_VERSION -> {
                        val v = f.version
                        vkVersion = "${v ushr 22}.${(v ushr 12) and 0x3FF}.${v and 0xFFF}"
                    }
                }
            }
            val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val thermal = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) power.currentThermalStatus else -1
            val soc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                "${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}"
            } else {
                Build.HARDWARE
            }
            return DeviceInfo(
                model = "${Build.MANUFACTURER} ${Build.MODEL}",
                device = Build.DEVICE,
                soc = soc,
                android = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                totalRamMb = mi.totalMem / (1024 * 1024),
                availRamMb = mi.availMem / (1024 * 1024),
                lowMemory = mi.lowMemory,
                vulkanLevel = vkLevel,
                vulkanVersion = vkVersion,
                thermalStatus = thermal,
                sustainedPerformanceSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
                    power.isSustainedPerformanceModeSupported,
            )
        }
    }

    fun summary(): String = buildString {
        appendLine("Perangkat: $model ($device)")
        appendLine("SoC: $soc")
        appendLine(android)
        appendLine("RAM: $availRamMb MB tersedia dari $totalRamMb MB${if (lowMemory) " (MEMORI RENDAH)" else ""}")
        appendLine("Vulkan: level $vulkanLevel, versi $vulkanVersion")
        appendLine("Status termal: ${thermalLabel()}")
        append("Sustained performance mode: ${if (sustainedPerformanceSupported) "didukung" else "tidak didukung"}")
    }
}
