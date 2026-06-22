package com.oasismall.oasisai.ui.components.paray

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oasismall.oasisai.domain.paray.ParayActivityState
import kotlinx.coroutines.flow.MutableStateFlow

@Composable
fun ParayPresenceLed(modifier: Modifier = Modifier) {
    val monitor = LocalParayActivityMonitor.current
    val state by (monitor?.state ?: MutableStateFlow(ParayActivityState.IDLE))
        .collectAsStateWithLifecycle()
    ParayActivityLed(
        state = state,
        modifier = modifier.padding(end = 4.dp),
    )
}
