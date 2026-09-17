package com.paperbox.app.ui.media

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import coil.compose.AsyncImage

@Composable
fun ZoomableImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    onTap: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
    onZoomChanged: ((Boolean) -> Unit)? = null
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    // 通知外部缩放状态变化
    androidx.compose.runtime.LaunchedEffect(scale) {
        onZoomChanged?.invoke(scale > 1.1f)
    }

    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(0.5f, 5f)
        offsetX += panChange.x
        offsetY += panChange.y
    }

    // clickable 处理点击（专为滚动容器设计，不阻塞 HorizontalPager）
    // transformable 始终启用（双指缩放随时可用）
    AsyncImage(
        model = model,
        contentDescription = contentDescription,
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                translationX = offsetX,
                translationY = offsetY
            )
            .transformable(state = transformState)
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null
            ) { onTap?.invoke() },
        alignment = Alignment.Center
    )
}
