package com.codblackops.mobile.backend

import androidx.fragment.app.FragmentActivity
import com.codblackops.mobile.storage.ScannedGame

/**
 * A way of running Call of Duty: Black Ops (Wii) on the device. The launcher, scanner, controls
 * and settings are written against this interface so a future static-recompilation runtime can be
 * added next to the Dolphin emulation backend without touching the rest of the app.
 */
interface GameBackend {
    val id: String
    val displayName: String

    /** Plain-language explanation of what kind of backend this is (emulation vs recompilation). */
    val technique: String

    /** Null when the backend can run games on this device/build, otherwise the reason it cannot. */
    fun unavailableReason(): String?

    /** Starts the game. Throws [LaunchException] with a user-readable message on failure. */
    suspend fun launch(activity: FragmentActivity, game: ScannedGame)
}

class LaunchException(message: String, cause: Throwable? = null) : Exception(message, cause)
