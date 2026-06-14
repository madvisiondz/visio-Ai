package com.oasismall.oasisai.util

data class TaskProgress(
    val label: String,
    val percent: Int,
) {
    val normalizedPercent: Int = percent.coerceIn(0, 100)
    val fraction: Float = normalizedPercent / 100f
}
