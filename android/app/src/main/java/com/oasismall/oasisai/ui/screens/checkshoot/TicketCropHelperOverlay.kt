package com.oasismall.oasisai.ui.screens.checkshoot

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.oasismall.oasisai.domain.paray.ParayTicketCropLearnStore
import kotlin.math.hypot
import kotlin.math.max

data class TicketCropHelperState(
    val frameBitmap: android.graphics.Bitmap,
    val cropNorm: ParayTicketCropLearnStore.NormalizedCrop,
    val frameQuality: Float = 0f,
)

private val CropYellow = Color(0xFFFFE500)
private const val MIN_CROP_FRAC = 0.14f

private enum class CropHandle {
    None,
    Move,
    TopLeft,
    TopRight,
    BottomLeft,
    BottomRight,
    Top,
    Bottom,
    Left,
    Right,
}

@Composable
fun TicketCropHelperOverlay(
    state: TicketCropHelperState,
    onConfirm: (ParayTicketCropLearnStore.NormalizedCrop) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var crop by remember(state) { mutableStateOf(state.cropNorm) }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var activeHandle by remember { mutableStateOf(CropHandle.None) }
    val displayBitmap = remember(state.frameBitmap) {
        state.frameBitmap.copy(Bitmap.Config.ARGB_8888, false)
    }
    DisposableEffect(displayBitmap) {
        onDispose {
            if (!displayBitmap.isRecycled) displayBitmap.recycle()
        }
    }
    val image = remember(displayBitmap) { displayBitmap.asImageBitmap() }
    val aspect = displayBitmap.width.toFloat() / displayBitmap.height.coerceAtLeast(1)
    val handleRadiusPx = with(LocalDensity.current) { 28.dp.toPx() }
    val scroll = rememberScrollState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.94f)),
    ) {
        Row(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .background(Color.Black.copy(alpha = 0.88f))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f),
            ) {
                Text("Cancel")
            }
            Button(
                onClick = { onConfirm(crop) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = CropYellow, contentColor = Color.Black),
            ) {
                Text("Scan ticket", fontWeight = FontWeight.Bold)
            }
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(top = 72.dp)
                .navigationBarsPadding()
                .verticalScroll(scroll)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Adjust ticket crop",
                color = CropYellow,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Drag inside the box to move. Drag corners or edges to resize width and height with your fingers.",
                color = Color.White.copy(alpha = 0.88f),
                style = MaterialTheme.typography.bodySmall,
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspect)
                    .background(Color.DarkGray, RoundedCornerShape(8.dp))
                    .onSizeChanged { viewSize = it },
            ) {
                androidx.compose.foundation.Image(
                    bitmap = image,
                    contentDescription = "Adjust crop",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
                if (viewSize.width > 0 && viewSize.height > 0) {
                    val rectPx = normToPx(crop.toRectF(), viewSize)
                    Canvas(
                        Modifier
                            .fillMaxSize()
                            .pointerInput(viewSize, handleRadiusPx) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        activeHandle = hitTestHandle(
                                            offset,
                                            normToPx(crop.toRectF(), viewSize),
                                            handleRadiusPx,
                                        )
                                    },
                                    onDragEnd = { activeHandle = CropHandle.None },
                                    onDragCancel = { activeHandle = CropHandle.None },
                                ) { change, drag ->
                                    change.consume()
                                    if (viewSize.width == 0 || viewSize.height == 0) return@detectDragGestures
                                    if (activeHandle == CropHandle.None) {
                                        activeHandle = hitTestHandle(
                                            change.position,
                                            normToPx(crop.toRectF(), viewSize),
                                            handleRadiusPx,
                                        )
                                    }
                                    val dx = drag.x / viewSize.width
                                    val dy = drag.y / viewSize.height
                                    crop = applyCropDrag(crop, activeHandle, dx, dy)
                                }
                            },
                    ) {
                        drawDimOutside(rectPx, size)
                        drawRect(
                            color = CropYellow,
                            topLeft = Offset(rectPx.left, rectPx.top),
                            size = Size(rectPx.width, rectPx.height),
                            style = Stroke(width = 3f),
                        )
                        drawHandleCorners(rectPx, handleRadiusPx * 0.55f)
                    }
                }
            }
            Text(
                "Tip: Scan ticket is always at the top — no need to scroll down.",
                color = Color.White.copy(alpha = 0.55f),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

private fun hitTestHandle(touch: Offset, rect: Rect, radius: Float): CropHandle {
    val corners = listOf(
        CropHandle.TopLeft to Offset(rect.left, rect.top),
        CropHandle.TopRight to Offset(rect.right, rect.top),
        CropHandle.BottomLeft to Offset(rect.left, rect.bottom),
        CropHandle.BottomRight to Offset(rect.right, rect.bottom),
    )
    corners.forEach { (handle, pt) ->
        if (distance(touch, pt) <= radius) return handle
    }
    if (distanceToSegment(touch, Offset(rect.left, rect.top), Offset(rect.right, rect.top)) <= radius) {
        return CropHandle.Top
    }
    if (distanceToSegment(touch, Offset(rect.left, rect.bottom), Offset(rect.right, rect.bottom)) <= radius) {
        return CropHandle.Bottom
    }
    if (distanceToSegment(touch, Offset(rect.left, rect.top), Offset(rect.left, rect.bottom)) <= radius) {
        return CropHandle.Left
    }
    if (distanceToSegment(touch, Offset(rect.right, rect.top), Offset(rect.right, rect.bottom)) <= radius) {
        return CropHandle.Right
    }
    if (rect.contains(touch)) return CropHandle.Move
    return CropHandle.None
}

private fun applyCropDrag(
    crop: ParayTicketCropLearnStore.NormalizedCrop,
    handle: CropHandle,
    dx: Float,
    dy: Float,
): ParayTicketCropLearnStore.NormalizedCrop {
    var l = crop.left
    var t = crop.top
    var r = crop.right
    var b = crop.bottom
    when (handle) {
        CropHandle.Move -> {
            val w = r - l
            val h = b - t
            l = (l + dx).coerceIn(0f, 1f - w)
            t = (t + dy).coerceIn(0f, 1f - h)
            r = l + w
            b = t + h
        }
        CropHandle.TopLeft -> {
            l = (l + dx).coerceIn(0f, r - MIN_CROP_FRAC)
            t = (t + dy).coerceIn(0f, b - MIN_CROP_FRAC)
        }
        CropHandle.TopRight -> {
            r = (r + dx).coerceIn(l + MIN_CROP_FRAC, 1f)
            t = (t + dy).coerceIn(0f, b - MIN_CROP_FRAC)
        }
        CropHandle.BottomLeft -> {
            l = (l + dx).coerceIn(0f, r - MIN_CROP_FRAC)
            b = (b + dy).coerceIn(t + MIN_CROP_FRAC, 1f)
        }
        CropHandle.BottomRight -> {
            r = (r + dx).coerceIn(l + MIN_CROP_FRAC, 1f)
            b = (b + dy).coerceIn(t + MIN_CROP_FRAC, 1f)
        }
        CropHandle.Top -> t = (t + dy).coerceIn(0f, b - MIN_CROP_FRAC)
        CropHandle.Bottom -> b = (b + dy).coerceIn(t + MIN_CROP_FRAC, 1f)
        CropHandle.Left -> l = (l + dx).coerceIn(0f, r - MIN_CROP_FRAC)
        CropHandle.Right -> r = (r + dx).coerceIn(l + MIN_CROP_FRAC, 1f)
        CropHandle.None -> return crop
    }
    return ParayTicketCropLearnStore.NormalizedCrop(
        left = l.coerceIn(0f, 1f),
        top = t.coerceIn(0f, 1f),
        right = r.coerceIn(0f, 1f),
        bottom = b.coerceIn(0f, 1f),
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawDimOutside(rect: Rect, canvas: Size) {
    val dim = Color.Black.copy(alpha = 0.48f)
    drawRect(dim, topLeft = Offset.Zero, size = Size(canvas.width, rect.top))
    drawRect(dim, topLeft = Offset(0f, rect.bottom), size = Size(canvas.width, canvas.height - rect.bottom))
    drawRect(dim, topLeft = Offset(0f, rect.top), size = Size(rect.left, rect.height))
    drawRect(dim, topLeft = Offset(rect.right, rect.top), size = Size(canvas.width - rect.right, rect.height))
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHandleCorners(rect: Rect, radius: Float) {
    val handles = listOf(
        Offset(rect.left, rect.top),
        Offset(rect.right, rect.top),
        Offset(rect.left, rect.bottom),
        Offset(rect.right, rect.bottom),
        Offset(rect.center.x, rect.top),
        Offset(rect.center.x, rect.bottom),
        Offset(rect.left, rect.center.y),
        Offset(rect.right, rect.center.y),
    )
    handles.forEach { center ->
        drawCircle(color = CropYellow, radius = radius, center = center)
        drawCircle(color = Color.Black, radius = radius, center = center, style = Stroke(width = 2f))
    }
}

private fun normToPx(norm: RectF, size: IntSize): Rect {
    val w = size.width.toFloat()
    val h = size.height.toFloat()
    return Rect(
        left = norm.left * w,
        top = norm.top * h,
        right = norm.right * w,
        bottom = norm.bottom * h,
    )
}

private fun distance(a: Offset, b: Offset): Float = hypot(a.x - b.x, a.y - b.y)

private fun distanceToSegment(p: Offset, a: Offset, b: Offset): Float {
    val dx = b.x - a.x
    val dy = b.y - a.y
    if (dx == 0f && dy == 0f) return distance(p, a)
    val t = ((p.x - a.x) * dx + (p.y - a.y) * dy) / (dx * dx + dy * dy)
    val clamped = t.coerceIn(0f, 1f)
    val proj = Offset(a.x + clamped * dx, a.y + clamped * dy)
    return distance(p, proj)
}

private val Rect.width get() = max(0f, right - left)
private val Rect.height get() = max(0f, bottom - top)
