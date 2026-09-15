package com.paperbox.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// 设计稿颜色
private val BarBackground = Color(0xFFF2F2F2)
private val UnselectedColor = Color(0xFF000000)
private val SelectedGreen = Color(0xFF00AA5B)
private val EllipseGreen = Color(0x768DF547) // #8DF547 at 46% opacity

data class BottomNavItem(
    val route: String,
    val label: String,
    val iconType: IconType
)

enum class IconType {
    QUOTE,      // 报价
    SIZE_GUIDE, // 规格
    MATERIALS,  // 素材
    ANALYSIS,   // 对账
    PROFILE     // 个人
}

@Composable
fun BottomNavBar(
    items: List<BottomNavItem>,
    selectedRoute: String,
    onItemSelected: (String) -> Unit
) {
    val textMeasurer = rememberTextMeasurer()

    val labelStyle = TextStyle(
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(BarBackground, RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
            .padding(horizontal = 0.dp, vertical = 6.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            items.forEach { item ->
                val selected = item.route == selectedRoute
                val animatedColor by animateColorAsState(
                    targetValue = if (selected) SelectedGreen else UnselectedColor,
                    animationSpec = tween(200),
                    label = "tabColor"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onItemSelected(item.route) },
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp)
                    ) {
                        val canvasWidth = size.width
                        val canvasHeight = size.height

                        // 选中态：绘制绿色椭圆背景（仅素材tab）
                        if (selected && item.iconType == IconType.MATERIALS) {
                            drawOval(
                                color = EllipseGreen,
                                topLeft = Offset(
                                    canvasWidth * 0.08f,
                                    canvasHeight * 0.0f
                                ),
                                size = Size(
                                    canvasWidth * 0.84f,
                                    canvasHeight * 0.72f
                                )
                            )
                        }

                        // 图标：占画布宽度的 ~42%，居中
                        val iconSize = canvasWidth * 0.42f
                        val iconLeft = (canvasWidth - iconSize) / 2f
                        val iconTop = canvasHeight * 0.02f

                        when (item.iconType) {
                            IconType.QUOTE -> drawQuoteIcon(
                                animatedColor, iconLeft, iconTop, iconSize
                            )
                            IconType.SIZE_GUIDE -> drawSizeGuideIcon(
                                animatedColor, iconLeft, iconTop, iconSize
                            )
                            IconType.MATERIALS -> drawMaterialsIcon(
                                animatedColor, iconLeft, iconTop, iconSize
                            )
                            IconType.ANALYSIS -> drawAnalysisIcon(
                                animatedColor, iconLeft, iconTop, iconSize
                            )
                            IconType.PROFILE -> drawProfileIcon(
                                animatedColor, iconLeft, iconTop, iconSize
                            )
                        }

                        // 标签文字：垂直居中在图标下方区域
                        val measured = textMeasurer.measure(item.label, labelStyle)
                        val textX = (canvasWidth - measured.size.width) / 2f
                        val textY = canvasHeight * 0.72f + (canvasHeight * 0.28f - measured.size.height) / 2f

                        drawText(
                            textLayoutResult = measured,
                            topLeft = Offset(textX, textY),
                            color = animatedColor
                        )
                    }
                }
            }
        }
    }
}

// ==================== 图标绘制 ====================

/** 报价：计算器图标 */
private fun DrawScope.drawQuoteIcon(
    color: Color, left: Float, top: Float, size: Float
) {
    val path = Path().apply {
        addRoundRect(
            RoundRect(
                Rect(left + size * 0.1f, top, left + size * 0.9f, top + size),
                CornerRadius(size * 0.12f, size * 0.12f)
            )
        )
    }
    drawPath(path, color)

    // 内部显示屏区域
    drawRoundRect(
        color = Color.White,
        topLeft = Offset(left + size * 0.22f, top + size * 0.12f),
        size = Size(size * 0.56f, size * 0.25f),
        cornerRadius = CornerRadius(size * 0.04f)
    )

    // 按钮网格 2x3
    val btnW = size * 0.15f
    val btnH = size * 0.1f
    val startX = left + size * 0.22f
    val startY = top + size * 0.48f
    val gapX = size * 0.2f
    val gapY = size * 0.16f

    for (row in 0..2) {
        for (col in 0..1) {
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(startX + col * gapX, startY + row * gapY),
                size = Size(btnW, btnH),
                cornerRadius = CornerRadius(size * 0.02f)
            )
        }
    }
}

/** 规格：尺子/测量图标 */
private fun DrawScope.drawSizeGuideIcon(
    color: Color, left: Float, top: Float, size: Float
) {
    // 外框
    val frame = Path().apply {
        addRoundRect(
            RoundRect(
                Rect(left + size * 0.1f, top + size * 0.12f, left + size * 0.9f, top + size * 0.88f),
                CornerRadius(size * 0.1f, size * 0.1f)
            )
        )
    }
    drawPath(frame, color)

    // 内部镂空
    drawRoundRect(
        color = Color.White,
        topLeft = Offset(left + size * 0.18f, top + size * 0.2f),
        size = Size(size * 0.64f, size * 0.6f),
        cornerRadius = CornerRadius(size * 0.06f)
    )

    // 中间水平线
    drawLine(
        color = color,
        start = Offset(left + size * 0.28f, top + size * 0.5f),
        end = Offset(left + size * 0.72f, top + size * 0.5f),
        strokeWidth = size * 0.04f
    )

    // 中间圆点
    drawCircle(
        color = color,
        radius = size * 0.06f,
        center = Offset(left + size * 0.5f, top + size * 0.5f)
    )
}

/** 素材：图片/媒体图标 */
private fun DrawScope.drawMaterialsIcon(
    color: Color, left: Float, top: Float, size: Float
) {
    // 外框
    val outer = Path().apply {
        addRoundRect(
            RoundRect(
                Rect(left + size * 0.1f, top + size * 0.1f, left + size * 0.9f, top + size * 0.75f),
                CornerRadius(size * 0.1f, size * 0.1f)
            )
        )
    }
    drawPath(outer, color)

    // 内部镂空
    drawRoundRect(
        color = Color.White,
        topLeft = Offset(left + size * 0.18f, top + size * 0.18f),
        size = Size(size * 0.64f, size * 0.49f),
        cornerRadius = CornerRadius(size * 0.06f)
    )

    // 底部横条
    drawRoundRect(
        color = color,
        topLeft = Offset(left + size * 0.25f, top + size * 0.78f),
        size = Size(size * 0.5f, size * 0.1f),
        cornerRadius = CornerRadius(size * 0.03f)
    )
}

/** 对账：对话气泡图标 */
private fun DrawScope.drawAnalysisIcon(
    color: Color, left: Float, top: Float, size: Float
) {
    // 气泡主体
    val bubble = Path().apply {
        addRoundRect(
            RoundRect(
                Rect(left + size * 0.1f, top, left + size * 0.9f, top + size * 0.7f),
                CornerRadius(size * 0.15f, size * 0.15f)
            )
        )
    }
    drawPath(bubble, color)

    // 气泡尾巴（左下三角）
    val tail = Path().apply {
        moveTo(left + size * 0.25f, top + size * 0.7f)
        lineTo(left + size * 0.15f, top + size * 0.92f)
        lineTo(left + size * 0.4f, top + size * 0.7f)
        close()
    }
    drawPath(tail, color)

    // 气泡内文字线（用圆角矩形模拟圆角线条）
    drawRoundRect(
        color = Color.White,
        topLeft = Offset(left + size * 0.28f, top + size * 0.22f),
        size = Size(size * 0.44f, size * 0.06f),
        cornerRadius = CornerRadius(size * 0.03f)
    )
    drawRoundRect(
        color = Color.White,
        topLeft = Offset(left + size * 0.28f, top + size * 0.39f),
        size = Size(size * 0.32f, size * 0.06f),
        cornerRadius = CornerRadius(size * 0.03f)
    )
}

/** 个人：用户头像图标 */
private fun DrawScope.drawProfileIcon(
    color: Color, left: Float, top: Float, size: Float
) {
    // 头部圆形
    drawCircle(
        color = color,
        radius = size * 0.18f,
        center = Offset(left + size * 0.5f, top + size * 0.28f)
    )

    // 身体（半圆形）
    val body = Path().apply {
        addArc(
            oval = Rect(
                left + size * 0.15f, top + size * 0.5f,
                left + size * 0.85f, top + size * 1.1f
            ),
            startAngleDegrees = 180f,
            sweepAngleDegrees = 180f
        )
    }
    drawPath(body, color)
}
