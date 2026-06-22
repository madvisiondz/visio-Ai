package com.oasismall.oasisai.domain.paray

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Lightweight bridge from NavController and feature hooks to [ParayWorkflowObserver]. */
class ParayWorkflowTracker(
    private val observer: ParayWorkflowObserver,
    private val scope: CoroutineScope,
) {
    @Volatile private var currentScreen: String? = null
    @Volatile private var lastScreen: String? = null
    @Volatile private var lastLastScreen: String? = null
    @Volatile private var enteredAt: Long = 0L

    fun onDestinationChanged(route: String?) {
        val destination = ParayWorkflowScreens.normalize(route) ?: return
        if (destination == currentScreen) return

        val now = System.currentTimeMillis()
        val leaving = currentScreen
        val durationMs = if (leaving != null && enteredAt > 0L) now - enteredAt else 0L
        val previous = lastScreen
        val previousPrevious = lastLastScreen

        if (leaving != null) {
            scope.launch {
                observer.recordScreenVisit(
                    screen = leaving,
                    durationMs = durationMs,
                    previous = previous,
                    previousPrevious = previousPrevious,
                    destination = destination,
                )
            }
        }

        lastLastScreen = lastScreen
        lastScreen = currentScreen
        currentScreen = destination
        enteredAt = now
    }

    fun recordFeature(feature: ParayWorkflowFeature) {
        scope.launch { observer.recordFeature(feature) }
    }
}
