package com.codblackops.mobile.backend

import androidx.fragment.app.FragmentActivity
import com.codblackops.mobile.storage.ScannedGame

/**
 * Placeholder for a future static recompilation (PowerPC Broadway -> ARM64) backend.
 *
 * Research summary (see README): CPU-only static recompilers for GameCube/Wii code now exist
 * (e.g. DolRecomp, which translates DOL/REL code to C) and single-game PC ports are in progress,
 * but none ships a runtime for the Wii GPU (GX/Hollywood), DSP audio, IOS, disc and Wii Remote
 * hardware, and none targets Black Ops. Until such a runtime exists this backend reports itself
 * unavailable instead of pretending to run anything.
 */
class StaticRecompBackend : GameBackend {
    override val id = "static-recomp"
    override val displayName = "Rekompilasi statis (belum tersedia)"
    override val technique =
        "Menerjemahkan kode PowerPC Broadway game ke ARM64 sebelum dijalankan (ahead-of-time), " +
            "ditambah runtime pengganti hardware Wii."

    override fun unavailableReason(): String =
        "Belum ada runtime rekompilasi statis untuk Call of Duty: Black Ops (Wii). Rekompiler CPU " +
            "saja tidak cukup; dibutuhkan implementasi GX/Hollywood, DSP, IOS dan input Wii yang " +
            "belum ada. Gunakan backend Dolphin."

    override suspend fun launch(activity: FragmentActivity, game: ScannedGame) {
        throw LaunchException(unavailableReason())
    }
}
