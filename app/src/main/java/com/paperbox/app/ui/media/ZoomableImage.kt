package com.paperbox.app.ui.media

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import coil.compose.AsyncImage
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 可缩放图片。
 *
 * 手势规则（修复捏合与 Pager 翻页冲突）：
 *  - 单指：不消费任何事件 → 完全交给 HorizontalPager 翻页
 *  - 第二根手指落下（且 Pager 还没消费手势）→ 接管并消费全部事件 → 捏合缩放，Pager 收不到
 *  - 接管后直到所有手指抬起才放手（捏合后松一根手指还能继续单指平移）
 *  - 检测器常驻，绝不随 scale 增删 modifier（原实现在手势进行中拆检测器 = 冲突根源）
 */
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

    val currentOnZoomChanged by rememberUpdatedState(onZoomChanged)
    // 双击缩放动画任务：新手势接管时取消，避免动画与捏合并发写 scale
    var zoomAnimJob by remember { mutableStateOf<Job?>(null) }

    // 迟滞阈值：从放大态缩回跌破它 → 吸附归位（消灭 1.1 附近检测器反复挂拆的抖动）
    val snapThreshold = 1.05f

    // 点击/长按/双击手势
    val tapModifier = Modifier.pointerInput(onTap, onLongPress) {
        detectTapGestures(
            onTap = { onTap?.invoke() },
            onLongPress = { onLongPress?.invoke() },
            onDoubleTap = {
                zoomAnimJob?.cancel()
                val target = if (scale > 1.5f) 1f else 2f
                if (target > 1f) currentOnZoomChanged?.invoke(true)
                zoomAnimJob = scope.launch {
                    val startScale = scale
                    val startX = offsetX
                    val startY = offsetY
                    // 放大时锚点保持；缩回时位移一并收敛到中心
                    val targetX = if (target == 1f) 0f else offsetX
                    val targetY = if (target == 1f) 0f else offsetY
                    animate(
                        initialValue = 0f,
                        targetValue = 1f,
                        animationSpec = tween(durationMillis = 200)
                    ) { t, _ ->
                        scale = startScale + (target - startScale) * t
                        offsetX = startX + (targetX - startX) * t
                        offsetY = startY + (targetY - startY) * t
                    }
                    currentOnZoomChanged?.invoke(scale > 1f)
                }
            }
        )
    }

    // 常驻捏合/平移手势检测
    val gestureModifier = Modifier.pointerInput(Unit) {
        // awaitPointerEventScope / awaitPointerEvent 是接口成员，无需 import
        awaitPointerEventScope {
            while (true) {
                // 等第一根手指按下（down 事件由它消费），记录按下点用于 slop 判断
                val down = awaitFirstDown(requireUnconsumed = false)

                var claimed = false
                var decided = false

                // 手势进行中：直到所有手指抬起
                while (true) {
                    val event = awaitPointerEvent()
                    if (event.changes.count { change -> change.pressed } == 0) break

                    if (!decided) {
                        val pressedChange = event.changes.firstOrNull { change -> change.pressed }
                        when {
                            // Pager（或其他人）已消费该手势 → 归它，我们整轮退出
                            event.changes.any { change -> change.isConsumed } -> {
                                decided = true
                                claimed = false
                            }
                            // 第二根手指落下且无人消费 → 接管（捏合缩放）
                            event.changes.count { change -> change.pressed } >= 2 -> {
                                decided = true
                                claimed = true
                                zoomAnimJob?.cancel()
                            }
                            // 已放大 + 单指移过 touchSlop → 接管（平移看细节）。
                            // 必须等过 slop：没过就接管会消费掉点击的微动，放大态点击切换会失灵
                            scale > 1f && pressedChange != null &&
                                (pressedChange.position - down.position).getDistance() >
                                    viewConfiguration.touchSlop -> {
                                decided = true
                                claimed = true
                                zoomAnimJob?.cancel()
                            }
                        }
                        // 1x 单指未消费：继续等，可能第二根手指马上落下
                    }

                    if (claimed) {
                        val (newScale, newOffsetX, newOffsetY) = nextTransform(
                            scale = scale,
                            offsetX = offsetX,
                            offsetY = offsetY,
                            zoom = event.calculateZoom(),
                            pan = event.calculatePan(),
                            centroid = event.calculateCentroid(),
                            center = Offset(size.width / 2f, size.height / 2f),
                            snapThreshold = snapThreshold
                        )
                        scale = newScale
                        offsetX = newOffsetX
                        offsetY = newOffsetY
                        // 同步通知（boolean 未翻转时不触发父级重组）
                        currentOnZoomChanged?.invoke(scale > 1f)
                        // 捏合期间消费全部事件 → Pager 永远收不到，不再打架
                        event.changes.forEach { change -> change.consume() }
                    }
                }

                // 手指全抬：微缩带（1 ~ 阈值）吸附归位，恢复翻页
                if (scale > 1f && scale < snapThreshold) {
                    scale = 1f
                    offsetX = 0f
                    offsetY = 0f
                }
                currentOnZoomChanged?.invoke(scale > 1f)
            }
        }
    }

    AsyncImage(
        model = model,
        contentDescription = contentDescription,
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            // lambda 形式：每帧在绘制阶段读状态，捏合零重组
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offsetX
                translationY = offsetY
            }
            .then(gestureModifier)
            .then(tapModifier),
        alignment = Alignment.Center
    )
}

/**
 * 捏合变换一步，返回新的 (scale, offsetX, offsetY)。
 *
 * 以两指中心为锚点缩放（绕图片中心缩放会让图往旁边滑走，不跟手）：
 *   o' = z·o + pan + (1-z)·(centroid - center)
 * 其中 z = newScale / oldScale。
 */
private fun nextTransform(
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    zoom: Float,
    pan: Offset,
    centroid: Offset,
    center: Offset,
    snapThreshold: Float
): Triple<Float, Float, Float> {
    var newScale = (scale * zoom).coerceIn(1f, 5f)
    // 从放大态缩回并跌破阈值 → 直接吸附回 1x，不在阈值附近来回抖
    if (newScale < snapThreshold && scale >= snapThreshold) newScale = 1f
    // 已回到 1x：位移归零
    if (newScale <= 1f) return Triple(1f, 0f, 0f)

    val z = newScale / scale
    val newOffsetX = z * offsetX + pan.x + (1f - z) * (centroid.x - center.x)
    val newOffsetY = z * offsetY + pan.y + (1f - z) * (centroid.y - center.y)
    return Triple(newScale, newOffsetX, newOffsetY)
}
