package com.oasismall.oasisai.domain.paray

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Exposes PARAY's current activity for the LED indicator.
 * Event-driven only — no background polling.
 */
class ParayActivityMonitor(
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(ParayActivityState.IDLE)
    val state: StateFlow<ParayActivityState> = _state.asStateFlow()

    private var returnIdleJob: Job? = null

    fun pulse(activity: ParayActivityState, holdMs: Long = holdDuration(activity)) {
        if (activity == ParayActivityState.IDLE) {
            returnIdleJob?.cancel()
            _state.value = ParayActivityState.IDLE
            return
        }
        returnIdleJob?.cancel()
        _state.value = activity
        returnIdleJob = scope.launch {
            delay(holdMs)
            if (_state.value == activity) {
                _state.value = ParayActivityState.IDLE
            }
        }
    }

    private fun holdDuration(activity: ParayActivityState): Long = when (activity) {
        ParayActivityState.OBSERVING -> 1_200L
        ParayActivityState.PROCESSING -> 900L
        ParayActivityState.DISCOVERY -> 1_600L
        ParayActivityState.LEARNING -> 2_000L
        ParayActivityState.WARNING -> 1_800L
        ParayActivityState.IDLE -> 0L
    }
}
