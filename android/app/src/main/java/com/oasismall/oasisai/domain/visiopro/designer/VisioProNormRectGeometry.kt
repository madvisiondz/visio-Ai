package com.oasismall.oasisai.domain.visiopro.designer

import kotlin.math.abs

enum class NormRectHandle {
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT,
}

data class SnapResult(
    val snappedHorizontal: Boolean = false,
    val snappedVertical: Boolean = false,
)

fun VisioProNormRect.resize(
    handle: NormRectHandle,
    dx: Float,
    dy: Float,
    minSize: Float = 0.04f,
): VisioProNormRect {
    var l = left
    var t = top
    var r = right
    var b = bottom
    when (handle) {
        NormRectHandle.TOP_LEFT -> {
            l = (l + dx).coerceIn(0f, r - minSize)
            t = (t + dy).coerceIn(0f, b - minSize)
        }
        NormRectHandle.TOP_RIGHT -> {
            r = (r + dx).coerceIn(l + minSize, 1f)
            t = (t + dy).coerceIn(0f, b - minSize)
        }
        NormRectHandle.BOTTOM_LEFT -> {
            l = (l + dx).coerceIn(0f, r - minSize)
            b = (b + dy).coerceIn(t + minSize, 1f)
        }
        NormRectHandle.BOTTOM_RIGHT -> {
            r = (r + dx).coerceIn(l + minSize, 1f)
            b = (b + dy).coerceIn(t + minSize, 1f)
        }
    }
    return VisioProNormRect(l, t, r, b)
}

fun VisioProNormRect.alignCenterHorizontal(): VisioProNormRect {
    val w = right - left
    val cx = 0.5f
    return copy(left = cx - w / 2f, right = cx + w / 2f)
}

fun VisioProNormRect.alignCenterVertical(): VisioProNormRect {
    val h = bottom - top
    val cy = 0.5f
    return copy(top = cy - h / 2f, bottom = cy + h / 2f)
}

fun VisioProNormRect.translateSnapped(
    dx: Float,
    dy: Float,
    snapThreshold: Float = 0.012f,
    enableSnap: Boolean = true,
): Pair<VisioProNormRect, SnapResult> {
    val moved = translate(dx, dy)
    if (!enableSnap) return moved to SnapResult()

    val w = moved.right - moved.left
    val h = moved.bottom - moved.top
    val cx = (moved.left + moved.right) / 2f
    val cy = (moved.top + moved.bottom) / 2f

    var snappedH = false
    var snappedV = false
    var l = moved.left
    var t = moved.top

    if (abs(cx - 0.5f) < snapThreshold) {
        l = 0.5f - w / 2f
        snappedH = true
    }
    if (abs(cy - 0.5f) < snapThreshold) {
        t = 0.5f - h / 2f
        snappedV = true
    }

    return VisioProNormRect(l, t, l + w, t + h) to SnapResult(snappedH, snappedV)
}

fun VisioProDesignLayerKind.isSpatial(): Boolean = when (this) {
    VisioProDesignLayerKind.PHOTO,
    VisioProDesignLayerKind.DESIGNATION,
    VisioProDesignLayerKind.PRICE,
    VisioProDesignLayerKind.CODE,
    -> true
    else -> false
}
