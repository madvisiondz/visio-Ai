package com.oasismall.oasisai.domain.paray

/** PARAY living-presence activity — driven by observer events, not polling. */
enum class ParayActivityState {
    IDLE,
    OBSERVING,
    PROCESSING,
    DISCOVERY,
    LEARNING,
    WARNING,
}
