package com.codblackops.mobile.backend

/** All known backends; the first available one is used. */
object BackendRegistry {
    val all: List<GameBackend> by lazy { listOf(DolphinBackend(), StaticRecompBackend()) }

    fun active(): GameBackend = all.firstOrNull { it.unavailableReason() == null } ?: all.first()
}
