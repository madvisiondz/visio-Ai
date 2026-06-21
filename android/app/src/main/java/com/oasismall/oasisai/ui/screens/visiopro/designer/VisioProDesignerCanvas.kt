package com.oasismall.oasisai.ui.screens.visiopro.designer

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.oasismall.oasisai.domain.visiopro.designer.NormRectHandle
import com.oasismall.oasisai.domain.visiopro.designer.VisioProDesignLayerKind
import com.oasismall.oasisai.domain.visiopro.designer.VisioProDesignerDocument
import com.oasismall.oasisai.domain.visiopro.designer.VisioProNormRect
import com.oasismall.oasisai.domain.visiopro.designer.isSpatial

private val CheckerLight = Color(0xFFECECEC)
private val CheckerDark = Color(0xFFD4D4D4)
private val GuideColor = Color(0xFFFF4081)

@Composable
fun DesignerCanvasStage(
    document: VisioProDesignerDocument,
    preview: Bitmap?,
    isLoading: Boolean,
    selectedLayer: VisioProDesignLayerKind,
    spatialLayers: List<VisioProDesignLayerKind>,
    showLayerFrames: Boolean,
    showSnapGuideH: Boolean,
    showSnapGuideV: Boolean,
    snapHint: String?,
    zoom: Float,
    onSelectLayer: (VisioProDesignLayerKind) -> Unit,
    onMoveLayer: (VisioProDesignLayerKind, Float, Float) -> Unit,
    onResizeLayer: (VisioProDesignLayerKind, NormRectHandle, Float, Float) -> Unit,
    onGestureStart: () -> Unit,
    onGestureEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val aspect = document.canvasWidth.toFloat() / document.canvasHeight.coerceAtLeast(1).toFloat()
    var viewScale by remember { mutableFloatStateOf(1f) }
    var viewPan by remember { mutableStateOf(Offset.Zero) }
    val clampedZoom = zoom.coerceIn(0.35f, 2.5f)

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
    ) {
        Column {
            if (snapHint != null) {
                Text(
                    snapHint,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Medium,
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .then(
                            if (aspect >= 1f) {
                                Modifier.fillMaxWidth(0.94f * clampedZoom).aspectRatio(aspect)
                            } else {
                                Modifier.height((260 * clampedZoom).dp).aspectRatio(aspect)
                            },
                        )
                        .graphicsLayer {
                            scaleX = viewScale
                            scaleY = viewScale
                            translationX = viewPan.x
                            translationY = viewPan.y
                        }
                        .clip(RoundedCornerShape(10.dp))
                        .background(CheckerLight)
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoomChange, _ ->
                                viewScale = (viewScale * zoomChange).coerceIn(0.6f, 3.5f)
                                viewPan += pan
                            }
                        }
                        .pointerInput(Unit) {
                            detectTapGestures(onDoubleTap = {
                                viewScale = 1f
                                viewPan = Offset.Zero
                            })
                        },
                ) {
                    CheckerboardBackground()
                    preview?.let { bmp ->
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Aperçu preset",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.FillBounds,
                        )
                    }
                    if (isLoading) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.06f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                        }
                    }
                    if (showSnapGuideH || showSnapGuideV) {
                        Canvas(Modifier.fillMaxSize()) {
                            if (showSnapGuideV) {
                                val x = size.width / 2f
                                drawLine(GuideColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.5f)
                            }
                            if (showSnapGuideH) {
                                val y = size.height / 2f
                                drawLine(GuideColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.5f)
                            }
                        }
                    }
                    if (showLayerFrames) {
                        BoxWithConstraints(Modifier.fillMaxSize()) {
                            val density = LocalDensity.current
                            val wPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
                            val hPx = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)
                            spatialLayers.forEach { layer ->
                                val rect = document.rectForLayer(layer) ?: return@forEach
                                LayerOverlay(
                                    layer = layer,
                                    rect = rect,
                                    selected = selectedLayer == layer,
                                    boxWidthPx = wPx,
                                    boxHeightPx = hPx,
                                    onSelect = { onSelectLayer(layer) },
                                    onGestureStart = onGestureStart,
                                    onGestureEnd = onGestureEnd,
                                    onDrag = { dx, dy -> onMoveLayer(layer, dx / wPx, dy / hPx) },
                                    onResize = { handle, dx, dy ->
                                        onResizeLayer(layer, handle, dx / wPx, dy / hPx)
                                    },
                                )
                            }
                        }
                    }
                }
            }
            Text(
                "Pincez pour zoomer · double-tap pour réinitialiser la vue",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CheckerboardBackground() {
    Column(Modifier.fillMaxSize()) {
        repeat(6) { row ->
            Row(Modifier.weight(1f)) {
                repeat(6) { col ->
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .background(if ((row + col) % 2 == 0) CheckerLight else CheckerDark),
                    )
                }
            }
        }
    }
}

@Composable
private fun LayerOverlay(
    layer: VisioProDesignLayerKind,
    rect: VisioProNormRect,
    selected: Boolean,
    boxWidthPx: Float,
    boxHeightPx: Float,
    onSelect: () -> Unit,
    onGestureStart: () -> Unit,
    onGestureEnd: () -> Unit,
    onDrag: (dx: Float, dy: Float) -> Unit,
    onResize: (NormRectHandle, dx: Float, dy: Float) -> Unit,
) {
    val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
    val density = LocalDensity.current
    val offsetX = with(density) { (rect.left * boxWidthPx).toDp() }
    val offsetY = with(density) { (rect.top * boxHeightPx).toDp() }
    val width = with(density) { ((rect.right - rect.left) * boxWidthPx).toDp() }
    val height = with(density) { ((rect.bottom - rect.top) * boxHeightPx).toDp() }

    Box(
        modifier = Modifier
            .offset(x = offsetX, y = offsetY)
            .size(width = width, height = height),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = color.copy(alpha = if (selected) 0.95f else 0.4f),
                    shape = RoundedCornerShape(4.dp),
                )
                .background(color.copy(alpha = if (selected) 0.12f else 0.05f))
                .clickable { onSelect() }
                .pointerInput(layer) {
                    detectDragGestures(
                        onDragStart = {
                            onSelect()
                            onGestureStart()
                        },
                        onDragEnd = { onGestureEnd() },
                        onDragCancel = { onGestureEnd() },
                    ) { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount.x, dragAmount.y)
                    }
                },
            contentAlignment = Alignment.TopStart,
        ) {
            Text(
                layer.labelFr,
                modifier = Modifier.padding(4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            )
        }
        if (selected && layer.isSpatial()) {
            listOf(
                Alignment.TopStart to NormRectHandle.TOP_LEFT,
                Alignment.TopEnd to NormRectHandle.TOP_RIGHT,
                Alignment.BottomStart to NormRectHandle.BOTTOM_LEFT,
                Alignment.BottomEnd to NormRectHandle.BOTTOM_RIGHT,
            ).forEach { (align, handle) ->
                ResizeHandleDot(
                    alignment = align,
                    color = color,
                    onGestureStart = onGestureStart,
                    onGestureEnd = onGestureEnd,
                ) { dx, dy -> onResize(handle, dx, dy) }
            }
        }
    }
}

@Composable
private fun BoxScope.ResizeHandleDot(
    alignment: Alignment,
    color: Color,
    onGestureStart: () -> Unit,
    onGestureEnd: () -> Unit,
    onDrag: (dx: Float, dy: Float) -> Unit,
) {
    Box(
        modifier = Modifier
            .align(alignment)
            .offset(
                x = if (alignment == Alignment.TopEnd || alignment == Alignment.BottomEnd) 6.dp else (-6).dp,
                y = if (alignment == Alignment.BottomStart || alignment == Alignment.BottomEnd) 6.dp else (-6).dp,
            )
            .size(18.dp)
            .clip(CircleShape)
            .background(color)
            .border(2.dp, Color.White, CircleShape)
            .pointerInput(alignment) {
                detectDragGestures(
                    onDragStart = { onGestureStart() },
                    onDragEnd = { onGestureEnd() },
                    onDragCancel = { onGestureEnd() },
                ) { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.x, dragAmount.y)
                }
            },
    )
}

private fun VisioProDesignerDocument.rectForLayer(layer: VisioProDesignLayerKind): VisioProNormRect? =
    when (layer) {
        VisioProDesignLayerKind.PHOTO -> photoRect
        VisioProDesignLayerKind.DESIGNATION -> designationRect
        VisioProDesignLayerKind.PRICE -> priceRect
        VisioProDesignLayerKind.CODE -> codeRect
        else -> null
    }
