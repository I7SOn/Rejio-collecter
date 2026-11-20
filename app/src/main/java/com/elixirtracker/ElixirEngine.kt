package com.elixirtracker

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class ElixirEngine {

    companion object {
        const val STARTING_ELIXIR = 5f
        const val MAX_ELIXIR = 10f
        const val MIN_ELIXIR = 0f
        const val DEFAULT_UNIT_COST = 4f
    }

    private val clock = GameClock()

    private val _enemyElixir = MutableStateFlow(STARTING_ELIXIR)
    val enemyElixir: StateFlow<Float> = _enemyElixir

    private val _unitsSeen = MutableStateFlow(0)
    val unitsSeen: StateFlow<Int> = _unitsSeen

    private var lastTickAt = 0L
    private var accumulatedSpend = 0f
    private var unitCount = 0

    fun start() {
        clock.reset()
        clock.start()
        lastTickAt = System.currentTimeMillis()
        accumulatedSpend = 0f
        unitCount = 0
        _enemyElixir.value = STARTING_ELIXIR
        _unitsSeen.value = 0
        ElixirState.running.value = true
    }

    fun stop() {
        clock.stop()
        ElixirState.running.value = false
    }

    fun reset() {
        accumulatedSpend = 0f
        unitCount = 0
        _enemyElixir.value = STARTING_ELIXIR
        _unitsSeen.value = 0
        clock.reset()
    }

    fun tick() {
        clock.tick()
        val raw = STARTING_ELIXIR +
                (clock.elapsed.value / clock.rate.value) -
                accumulatedSpend
        _enemyElixir.value = raw.coerceIn(MIN_ELIXIR, MAX_ELIXIR)

        ElixirState.enemyElixir.value = _enemyElixir.value
        ElixirState.myElixir.value =
            (STARTING_ELIXIR + clock.elapsed.value / clock.rate.value)
                .coerceIn(MIN_ELIXIR, MAX_ELIXIR)
        ElixirState.unitsSeen.value = unitCount
        ElixirState.elapsed.value = clock.elapsed.value
        ElixirState.isOvertime.value = clock.isOvertime()
    }

    fun onEnemyUnitDeployed(cost: Float = DEFAULT_UNIT_COST) {
        accumulatedSpend += cost
        unitCount++
        _unitsSeen.value = unitCount
        tick()
    }

    fun isFinished(): Boolean = clock.isFinished()
    fun elapsedMs(): Long = clock.elapsed.value
}
