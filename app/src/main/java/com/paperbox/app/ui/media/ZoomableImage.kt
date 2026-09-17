package com.paperbox.app.ui.media

import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.input.pointer.pointerInput
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

    // transformable 放前面，让多指手势优先被它处理
    // pointerInput 放后面处理单指点击
    val gestureModifier = (if (scale > 1.1f) Modifier.transformable(state = transformState) else Modifier)
        .pointerInput(Unit) {
            detectTapGestures(
                onTap = { onTap?.invoke() },
                onDoubleTap = { tapOffset ->
                    if (scale > 1.5f) {
                        scale = 1f
                        offsetX = 0f
                        offsetY = 0f
                    } else {
                        scale = 2.5f
                        offsetX = (size.width / 2f - tapOffset.x) * 1.5f
                        offsetY = (size.height / 2f - tapOffset.y) * 1.5f
                    }
                },
                onLongPress = { onLongPress?.invoke() }
            )
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
            .then(gestureModifier),
        alignment = Alignment.Center
    )
}
