package com.paperbox.app.ui.media

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

@Composable
fun ZoomableImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(0.5f, 5f)
        offsetX += panChange.x
        offsetY += panChange.y
    }

    // 棋盘格背景（透明 PNG 可见）
    Canvas(modifier = Modifier.fillMaxSize()) {
        val tileSize = 24.dp.toPx()
        val cols = (size.width / tileSize).toInt() + 1
        val rows = (size.height / tileSize).toInt() + 1
        for (row in 0..rows) {
            for (col in 0..cols) {
                val isLight = (row + col) % 2 == 0
                drawRect(
                    color = if (isLight) androidx.compose.ui.graphics.Color(0xFFE0E0E0)
                            else androidx.compose.ui.graphics.Color(0xFFC0C0C0),
                    topLeft = Offset(col * tileSize, row * tileSize),
                    size = Size(tileSize, tileSize)
                )
            }
        }
    }

    AsyncImage(
        model = model,
        contentDescription = contentDescription,
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                translationX = offsetX,
                translationY = offsetY
            )
            .transformable(state = transformState)
            .pointerInput(Unit) {
                detectTapGestures(
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
                    }
                )
            },
        alignment = Alignment.Center
    )
}
