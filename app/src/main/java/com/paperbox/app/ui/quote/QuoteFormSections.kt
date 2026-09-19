package com.paperbox.app.ui.quote

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paperbox.app.domain.model.MaterialKey

// ══════════════════════════════════════════════════════════════
//  📦 尺寸 / 📊 生产 —— 标题 + 输入框同在一行
// ══════════════════════════════════════════════════════════════

@Composable
internal fun QuoteDimensionSection(
    state: QuoteUiState,
    onLength: (String) -> Unit,
    onWidth: (String) -> Unit,
    onHeight: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SectionLabel("📦 尺寸")
        QuoteField("长", state.lengthText, onLength, Modifier.weight(1f))
        QuoteField("宽", state.widthText, onWidth, Modifier.weight(1f))
        QuoteField("高", state.heightText, onHeight, Modifier.weight(1f))
    }
}

@Composable
internal fun QuoteProductionSection(
    state: QuoteUiState,
    onQuantity: (String) -> Unit,
    onProfit: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SectionLabel("📊 生产")
        QuoteField(
            label = "数量",
            value = state.quantityText,
            onValueChange = onQuantity,
            modifier = Modifier.weight(1f),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next)
        )
        QuoteField(
            label = "利润",
            value = state.profitText,
            onValueChange = onProfit,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = QuoteTitle,
        maxLines = 1
    )
}

/**
 * 36dp 高的行内输入框：左边标签、右边数值。设计稿里这一格就是「标签 + 数值」两块，
 * 所以把标签放进 decorationBox，让 BasicTextField 铺满整格 ——
 * 否则只有右半格能点，点标签没反应。
 */
@Composable
internal fun QuoteField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions(
        keyboardType = KeyboardType.Decimal,
        imeAction = ImeAction.Next
    )
) {
    // 光标和选择手柄默认取 MaterialTheme 的蓝色，跟整页的绿不搭
    val selectionColors = TextSelectionColors(
        handleColor = QuoteGreen,
        backgroundColor = QuoteGreen.copy(alpha = 0.25f)
    )

    Box(
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
            .border(1.dp, QuoteFieldStroke, RoundedCornerShape(8.dp))
    ) {
        CompositionLocalProvider(LocalTextSelectionColors provides selectionColors) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxSize(),
                textStyle = TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = QuoteTitle,
                    textAlign = TextAlign.End
                ),
                singleLine = true,
                keyboardOptions = keyboardOptions,
                cursorBrush = SolidColor(QuoteGreen),
                decorationBox = { innerTextField ->
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = label,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = QuoteMuted,
                            maxLines = 1
                        )
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            if (value.isEmpty()) {
                                Text(
                                    text = "0",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = QuoteTitle,
                                    textAlign = TextAlign.End
                                )
                            }
                            innerTextField()
                        }
                    }
                }
            )
        }
    }
}

// ══════════════════════════════════════════════════════════════
//  🎨 材质选择
// ══════════════════════════════════════════════════════════════

@Composable
internal fun QuoteMaterialSection(
    state: QuoteUiState,
    onSelect: (MaterialKey) -> Unit
) {
    val description = state.materialConfigs
        .find { it.key == state.form.materialKey }
        ?.label
        .orEmpty()

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("材质：", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = QuoteAccent)
            Text(
                text = description,
                fontSize = 12.sp,
                color = QuoteMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MaterialChipOptions.forEach { option ->
                val selected = option.key == state.form.materialKey
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selected) QuoteGreen else QuoteTabIdleBg)
                        .clickable { onSelect(option.key) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .background(option.dot, CircleShape)
                    )
                    Text(
                        text = option.label,
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (selected) Color.White else QuoteGray66,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
