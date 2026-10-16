package com.example.appuidemo.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import coil.compose.AsyncImage
import kotlin.math.abs

private const val MIN_SCALE = 1f
private const val MAX_SCALE = 5f

/**
 * 可双指缩放 / 拖动查看细节的图片。
 *
 * 交互分工：
 * - 双指 → 缩放 + 拖动
 * - 已放大后的单指拖动 → 平移查看
 * - **未放大**时的单指上下拖动 → 交给 [onDismissDrag]，由调用方实现「下滑关闭」
 */
@Composable
fun ZoomableImage(
    model: Any?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    onDismissDrag: ((deltaY: Float) -> Unit)? = null,
    onGestureEnd: (() -> Unit)? = null,
) {
    var scale by remember { mutableFloatStateOf(MIN_SCALE) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current

    fun maxOffset(size: IntSize, currentScale: Float): Offset {
        if (size == IntSize.Zero) return Offset.Zero
        val maxX = (size.width * (currentScale - 1f)) / 2f
        val maxY = (size.height * (currentScale - 1f)) / 2f
        return Offset(maxX.coerceAtLeast(0f), maxY.coerceAtLeast(0f))
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                containerSize = IntSize(size.width.toInt(), size.height.toInt())
            }
            .pointerInput(Unit) {
                detectZoomPanAndDismiss(
                    currentScale = { scale },
                    onTransform = { zoom, pan ->
                        val newScale = (scale * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
                        scale = newScale
                        val limit = with(density) { maxOffset(containerSize, newScale) }
                        offset = Offset(
                            (offset.x + pan.x).coerceIn(-limit.x, limit.x),
                            (offset.y + pan.y).coerceIn(-limit.y, limit.y)
                        )
                    },
                    onDismissDrag = onDismissDrag ?: {}
                )
                onGestureEnd?.invoke()
            }
    ) {
        AsyncImage(
            model = model,
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
        )
    }
}

private suspend fun PointerInputScope.detectZoomPanAndDismiss(
    currentScale: () -> Float,
    onTransform: (zoom: Float, pan: Offset) -> Unit,
    onDismissDrag: (deltaY: Float) -> Unit,
) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)

        var zoomAccum = 1f
        var panAccum = Offset.Zero
        var pastTouchSlop = false
        var multiTouch = false
        val touchSlop = viewConfiguration.touchSlop

        do {
            val event = awaitPointerEvent()
            if (event.changes.size >= 2) multiTouch = true

            val zoomChange = event.calculateZoom()
            val panChange = event.calculatePan()

            if (!pastTouchSlop) {
                zoomAccum *= zoomChange
                panAccum += panChange
                val zoomMotion = abs(1f - zoomAccum) * event.calculateCentroidSize(useCurrent = false)
                if (zoomMotion > touchSlop || panAccum.getDistance() > touchSlop) {
                    pastTouchSlop = true
                }
            }

            if (pastTouchSlop) {
                val isZoomGesture = multiTouch || zoomChange != 1f || currentScale() > MIN_SCALE
                if (isZoomGesture) {
                    onTransform(zoomChange, panChange)
                } else {
                    // 单指 + 未放大 → 交给「下滑关闭」
                    onDismissDrag(panChange.y)
                }
                event.changes.forEach { if (it.positionChanged()) it.consume() }
            }
        } while (event.changes.any { it.pressed })
    }
}
