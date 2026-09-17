package com.paperbox.app.ui.media

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

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
    val scope = rememberCoroutineScope()

    // 通知外部缩放状态变化
    LaunchedEffect(scale) {
        onZoomChanged?.invoke(scale > 1.1f)
    }

    // 点击/长按/双击手势（替代 clickable，避免手势冲突）
    val tapModifier = Modifier.pointerInput(onTap, onLongPress) {
        detectTapGestures(
            onTap = { onTap?.invoke() },
            onLongPress = { onLongPress?.invoke() },
            onDoubleTap = {
                scope.launch {
                    if (scale > 1.1f) {
                        scale = 1f
                        offsetX = 0f
                        offsetY = 0f
                    } else {
                        scale = 2f
                    }
                }
            }
        )
    }

    // 缩放+平移手势（仅在缩放状态下启用，避免拦截 HorizontalPager 的滑动）
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 5f)
        if (scale > 1.1f) {
            offsetX += panChange.x
            offsetY += panChange.y
        } else {
            offsetX = 0f
            offsetY = 0f
        }
    }

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
            // 仅在缩放时启用 transformable，1x 时让 HorizontalPager 处理滑动
            .then(
                if (scale > 1.1f) Modifier.transformable(state = transformState)
                else Modifier
            )
            .then(tapModifier),
        alignment = Alignment.Center
    )
}
