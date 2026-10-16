package com.example.appuidemo.ui.zoom

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.appuidemo.data.model.LibraryItem
import com.example.appuidemo.ui.components.ZoomableImage
import com.example.appuidemo.ui.components.rememberImageModel

/**
 * 放大图页面（页面 2，全屏浮层）。
 *
 * 四种退出方式：点击任意处、下滑、返回键、右上角关闭按钮。
 * 放大状态下拖动改为平移查看，避免与下滑冲突。
 */
@Composable
fun ZoomScreen(
    item: LibraryItem,
    onClose: () -> Unit
) {
    val density = LocalDensity.current
    var dragOffset by remember { mutableFloatStateOf(0f) }

    val dismissThresholdPx = with(density) { 120.dp.toPx() }

    BackHandler(onBack = onClose)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(onClick = onClose)
    ) {
        ZoomableImage(
            model = rememberImageModel(item.filePath),
            contentDescription = "放大图",
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = dragOffset
                    alpha = (1f - dragOffset / (dismissThresholdPx * 4f)).coerceIn(0.35f, 1f)
                },
            onDismissDrag = { delta ->
                dragOffset = (dragOffset + delta).coerceAtLeast(0f)
            },
            onGestureEnd = {
                if (dragOffset > dismissThresholdPx) onClose() else dragOffset = 0f
            }
        )

        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(8.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "关闭",
                tint = Color.White
            )
        }
    }
}
