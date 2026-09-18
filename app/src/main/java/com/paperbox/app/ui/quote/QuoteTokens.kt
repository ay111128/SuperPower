package com.paperbox.app.ui.quote

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp as lerpDp
import com.paperbox.app.domain.model.MaterialKey
import java.util.Locale

// ══════════════════════════════════════════════════════════════
//  设计稿色值（设计/首页报价页面UI重构.pen）
//  注意：pen 里的颜色是 #RRGGBBAA，Kotlin 是 0xAARRGGBB，阴影色要换位
// ══════════════════════════════════════════════════════════════

internal val QuoteGreen = Color(0xFF1B8A3E)          // 主色：选中态 / 开关 / 价格强调
internal val QuoteHeaderGreen = Color(0xFF007A12)    // 顶栏
internal val QuoteAccent = Color(0xFF00C417)         // 「材质：」「工艺：」「附加费：」标签
internal val QuoteTitle = Color(0xFF1A1A1A)          // 区块标题 / 数值
internal val QuoteGray66 = Color(0xFF666666)
internal val QuoteGray85 = Color(0xFF858585)         // 工艺子项文字
internal val QuoteGray55 = Color(0xFF555555)         // 附加费子项文字
internal val QuoteMuted = Color(0xFFAAAAAA)          // 字段标签 / 说明文字
internal val QuoteRowBg = Color(0xFFFAFAFA)          // 选项行 / 结果卡片底色
internal val QuoteFieldStroke = Color(0xFFEEEEEE)    // 输入框描边
internal val QuoteCardStroke = Color(0xFFF0F0F0)     // 结果卡片描边
internal val QuoteTabIdleBg = Color(0xFFF5F5F5)      // 未选中 chip / tab
internal val QuotePrice = Color(0xFFE85D3A)          // 现货价格
internal val QuoteTrackBg = Color(0xFFE0E0E0)        // 滑轨底色（关态开关也用它）

// 阴影 —— pen 的 color 字段是 #RRGGBBAA
internal val QuoteThumbShadow = Color(0x30000000)
internal val QuoteCardShadow = Color(0x0D000000)
internal val QuoteSliderThumbShadow = Color(0x20000000)
internal val QuoteFabShadow = Color(0x441B8A3E)

// ══════════════════════════════════════════════════════════════
//  格式化的数字
// ══════════════════════════════════════════════════════════════

// 必须带 Locale —— 否则欧语区会渲染成 ¥2,80
internal fun money(value: Double): String = "¥" + String.format(Locale.CHINA, "%.2f", value)

internal fun moneyPlain(value: Double): String = String.format(Locale.CHINA, "%.2f", value)

internal fun num(value: Double, digits: Int = 2): String =
    String.format(Locale.CHINA, "%.${digits}f", value)

/** 去掉多余的小数后缀：10.0 → "10"，10.5 → "10.5" */
internal fun trimNumber(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

// ══════════════════════════════════════════════════════════════
//  材质 chip / 现货分类 tab 的静态数据
// ══════════════════════════════════════════════════════════════

internal data class MaterialChipOption(
    val key: MaterialKey,
    val label: String,
    val dot: Color
)

/**
 * 顺序和短名都按设计稿来 —— 不能用 MaterialKey.entries，
 * 枚举顺序是大/小/白卡/白牛皮，和稿子上的牛皮纸1/牛皮纸2/白牛皮/白卡不一样。
 */
internal val MaterialChipOptions = listOf(
    MaterialChipOption(MaterialKey.KRAFT_SMALL, "牛皮纸1", Color(0xFFC4956A)),
    MaterialChipOption(MaterialKey.KRAFT_LARGE, "牛皮纸2", Color(0xFFA87850)),
    MaterialChipOption(MaterialKey.WHITE_KRAFT, "白牛皮", Color(0xFFE8E8E8)),
    MaterialChipOption(MaterialKey.WHITE_CARD, "白卡", Color(0xFFF5F0E8))
)

internal data class SpotTabOption(
    val category: String,
    val label: String,
    val dot: Color,
    val dotStroke: Color
)

/** 分类 key 必须是后端返回的英文值，中文只用于显示 */
internal val SpotTabOptions = listOf(
    SpotTabOption("kraft", "牛皮色", Color(0xFFC4956A), Color(0xFFFFFFFF)),
    SpotTabOption("white", "白色", Color(0xFFE8E8E8), Color(0xFFDDDDDD)),
    SpotTabOption("color", "彩色", Color(0xFF7EB8E0), Color(0xFFDDDDDD))
)

// ══════════════════════════════════════════════════════════════
//  通用控件
// ══════════════════════════════════════════════════════════════

/**
 * 设计稿的开关：34×21、圆角 11、白色滑块 17dp（关态 x=2，开态 x=15）。
 *
 * 动画只用 animateFloatAsState —— 它在 animation-core 里，material3/foundation
 * 会传递依赖进来。animateColorAsState / AnimatedVisibility 在
 * androidx.compose.animation:animation 里，build.gradle.kts 没声明，别用。
 */
@Composable
internal fun QuoteToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val progress by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        label = "quoteToggle"
    )

    Box(
        modifier = modifier
            .size(width = 34.dp, height = 21.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(lerp(QuoteTrackBg, QuoteGreen, progress))
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange
            )
    ) {
        Box(
            modifier = Modifier
                .offset(x = lerpDp(2.dp, 15.dp, progress), y = 2.dp)
                .size(17.dp)
                .shadow(1.5.dp, CircleShape, ambientColor = QuoteThumbShadow, spotColor = QuoteThumbShadow)
                .background(Color.White, CircleShape)
        )
    }
}

/**
 * 匹配容差滑块 —— 手写的，不用 M3 的 Slider。
 * M3 Slider 内部拿 requiredSizeIn 把触控高度锁在 44dp 左右，压不到设计稿的 32dp。
 *
 * 按下即定位、拖动实时更新、抬手才回调 onValueChangeFinished（避免每帧重算匹配）。
 */
@Composable
internal fun QuoteSlider(
    value: Double,
    valueRange: ClosedFloatingPointRange<Double>,
    onValueChange: (Double) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val span = (valueRange.endInclusive - valueRange.start).takeIf { it > 0 } ?: 1.0
    val fraction = ((value - valueRange.start) / span).coerceIn(0.0, 1.0)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp)
            .pointerInput(valueRange) {
                awaitEachGesture {
                    val thumbPx = THUMB_SIZE.toPx()
                    val travel = (size.width - thumbPx).coerceAtLeast(1f)

                    fun valueAt(x: Float): Double {
                        val f = ((x - thumbPx / 2f) / travel).coerceIn(0f, 1f)
                        return valueRange.start + f * span
                    }

                    val down = awaitFirstDown(requireUnconsumed = false)
                    onValueChange(valueAt(down.position.x))
                    down.consume()

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        onValueChange(valueAt(change.position.x))
                        change.consume()
                    }
                    onValueChangeFinished()
                }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        // 轨底
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(QuoteTrackBg, RoundedCornerShape(2.dp))
        )
        // 已选区间
        Box(
            Modifier
                .fillMaxWidth(fraction.toFloat())
                .height(4.dp)
                .background(QuoteGreen, RoundedCornerShape(2.dp))
        )
        // 滑块 —— 左右各留半个滑块宽，边缘才不会露出去
        Box(
            Modifier
                .offset(x = (maxWidth - THUMB_SIZE) * fraction.toFloat())
                .size(THUMB_SIZE)
                .shadow(3.dp, CircleShape, ambientColor = QuoteSliderThumbShadow, spotColor = QuoteSliderThumbShadow)
                .background(Color.White, CircleShape)
                .border(2.dp, QuoteGreen, CircleShape)
        )
    }
}

private val THUMB_SIZE = 24.dp
