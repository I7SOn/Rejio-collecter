package com.elixirtracker

import kotlinx.coroutines.flow.MutableStateFlow

object ElixirState {
    val enemyElixir = MutableStateFlow(5f)
    val myElixir = MutableStateFlow(5f)
    val unitsSeen = MutableStateFlow(0)
    val elapsed = MutableStateFlow(0L)
    val isOvertime = MutableStateFlow(false)
    val running = MutableStateFlow(false)
}
