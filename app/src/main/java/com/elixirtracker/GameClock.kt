package com.elixirtracker

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class GameClock {

    companion object {
        const val MATCH_DURATION_MS = 180_000L
        const val DOUBLE_ELIXIR_START_MS = 120_000L
        const val NORMAL_RATE_MS = 2_800f
        const val DOUBLE_RATE_MS = 1_400f
    }

    private var matchStartAt = 0L
    private var running = false

    private val _elapsed = MutableStateFlow(0L)
    val elapsed: StateFlow<Long> = _elapsed

    private val _rate = MutableStateFlow(NORMAL_RATE_MS)
    val rate: StateFlow<Float> = _rate

    fun start() {
        matchStartAt = System.currentTimeMillis()
        running = true
    }

    fun stop() {
        running = false
    }

    fun reset() {
        matchStartAt = System.currentTimeMillis()
        _elapsed.value = 0
        _rate.value = NORMAL_RATE_MS
    }

    fun tick() {
        if (!running) return
        val now = System.currentTimeMillis()
        val e = now - matchStartAt
        _elapsed.value = e
        _rate.value = if (e >= DOUBLE_ELIXIR_START_MS) DOUBLE_RATE_MS else NORMAL_RATE_MS
    }

    fun isOvertime(): Boolean = _elapsed.value >= DOUBLE_ELIXIR_START_MS
    fun isFinished(): Boolean = _elapsed.value >= MATCH_DURATION_MS + 60_000L
}
