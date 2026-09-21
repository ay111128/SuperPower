package com.paperbox.app.ui.quote

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import com.paperbox.app.domain.model.SpotMatch

/**
 * 📦 现货匹配卡片。
 *
 * 卡片开关打开后进入现货计价模式：双击下方匹配结果中的某个尺寸，
 * 该尺寸的价格直接作为计价单价。
 */
@Composable
internal fun SpotMatchCard(
    state: QuoteUiState,
    onToggleEnabled: (Boolean) -> Unit,
    onToleranceChange: (Double) -> Unit,
    onToleranceCommit: () -> Unit,
    onSelectCategory: (String) -> Unit,
    onOpenSizeGuide: () -> Unit,
    onSelectSpot: (SpotMatch) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(16.dp),
                ambientColor = QuoteCardShadow,
                spotColor = QuoteCardShadow
            )
            .background(Color.White, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── 标题行 ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("📦 现货匹配", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = QuoteTitle)

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onOpenSizeGuide)
                        .padding(horizontal = 5.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Calculate,
                        contentDescription = null,
                        tint = QuoteGreen,
                        modifier = Modifier.size(14.dp)
                    )
                    Text("现货计价", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = QuoteGreen)
                }

                QuoteToggle(checked = state.spotMatchEnabled, onCheckedChange = onToggleEnabled)
            }
        }

        SpotMatchBody(
            state = state,
            onToleranceChange = onToleranceChange,
            onToleranceCommit = onToleranceCommit,
            onSelectCategory = onSelectCategory,
            onSelectSpot = onSelectSpot
        )
    }
}

@Composable
private fun SpotMatchBody(
    state: QuoteUiState,
    onToleranceChange: (Double) -> Unit,
    onToleranceCommit: () -> Unit,
    onSelectCategory: (String) -> Unit,
    onSelectSpot: (SpotMatch) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
            // ── 匹配容差 ──
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("匹配容差", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = QuoteGray66)
                    Text(
                        "±${trimNumber(state.spotTolerance)}cm",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = QuoteGreen
                    )
                }
                QuoteSlider(
                    value = state.spotTolerance,
                    valueRange = QuoteUiState.MIN_TOLERANCE..QuoteUiState.MAX_TOLERANCE,
                    onValueChange = onToleranceChange,
                    onValueChangeFinished = onToleranceCommit
                )
            }

            // ── 分类 tab ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SpotTabOptions.forEach { tab ->
                    val selected = tab.category == state.spotCategory
                    val count = state.spotCounts[tab.category] ?: 0
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) QuoteGreen else QuoteTabIdleBg)
                            .clickable { onSelectCategory(tab.category) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(12.dp)
                                .background(tab.dot, CircleShape)
                                // 选中的 tab 底色是绿的，色点描边得用白色才看得出来
                                .border(1.dp, if (selected) Color.White else tab.dotStroke, CircleShape)
                        )
                        Text(
                            text = "${tab.label}($count)",
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (selected) Color.White else QuoteGray66,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // ── 匹配结果 ──
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when {
                    state.form.length <= 0 || state.form.width <= 0 || state.form.height <= 0 ->
                        Text("填好长宽高后显示匹配结果", fontSize = 12.sp, color = QuoteMuted)

                    state.spotMatches.isEmpty() ->
                        Text("没有匹配的现货尺寸，可以试试放宽容差", fontSize = 12.sp, color = QuoteMuted)

                    else -> {
                        if (state.spotMatchEnabled) {
                            Text(
                                "双击尺寸可直接按现货价格计价",
                                fontSize = 11.sp,
                                color = QuoteMuted
                            )
                        }
                        state.spotMatches.forEach { match ->
                            SpotResultRow(
                                match = match,
                                isSelected = match.size == state.selectedSpotProduct?.size,
                                onDoubleClick = { onSelectSpot(match) }
                            )
                        }
                    }
                }
            }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SpotResultRow(
    match: SpotMatch,
    isSelected: Boolean,
    onDoubleClick: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val displaySize = match.size.replace('x', '×').replace('X', '×').replace('*', '×')

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) QuoteGreen.copy(alpha = 0.08f) else QuoteRowBg)
            .border(
                width = 1.dp,
                color = if (isSelected) QuoteGreen else QuoteCardStroke,
                shape = RoundedCornerShape(10.dp)
            )
            .combinedClickable(
                onClick = {},
                onDoubleClick = onDoubleClick,
                onLongClick = {
                    clipboardManager.setText(AnnotatedString(displaySize))
                    Toast.makeText(context, "已复制：$displaySize", Toast.LENGTH_SHORT).show()
                }
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = displaySize,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isSelected) QuoteGreen else QuoteTitle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = money(match.price),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = if (isSelected) QuoteGreen else QuotePrice
        )
    }
}
