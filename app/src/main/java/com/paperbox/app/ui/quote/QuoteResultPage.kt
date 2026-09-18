package com.paperbox.app.ui.quote

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paperbox.app.domain.model.ChargeLine
import com.paperbox.app.domain.model.QuoteComputation

/**
 * 报价结果页 —— 由浮动按钮「生成报价」推入的独立整页（不是弹窗）。
 *
 * 无状态：只吃 QuoteComputation 和回调，状态全在共用的 QuoteViewModel 里。
 */
@Composable
internal fun QuoteResultPage(
    state: QuoteUiState,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onClearTraceCode: () -> Unit,
    onClearError: () -> Unit
) {
    val result = state.result ?: return

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        // ── 顶栏（和报价页同一套绿色）──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(QuoteHeaderGreen)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .height(62.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("报价结果", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                Text("✕", color = Color.White, fontSize = 18.sp)
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            QuoteSummaryCard(state, result)
            QuoteBreakdownCard(state, result)

            // ── 保存 ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (state.isLoading) QuoteGreen.copy(alpha = 0.6f) else QuoteGreen)
                    .clickable(enabled = !state.isLoading, onClick = onSave),
                contentAlignment = Alignment.Center
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("保存记录", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            state.traceCode?.let { code ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFF1F8F3))
                        .border(1.dp, QuoteGreen.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("追溯码", fontSize = 12.sp, color = QuoteGray66)
                        Text(
                            code,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = QuoteGreen
                        )
                    }
                    TextButton(onClick = onClearTraceCode) {
                        Text("收起", fontSize = 12.sp, color = QuoteMuted)
                    }
                }
            }

            state.errorMessage?.let { error ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFFFEAEA))
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(error, color = Color(0xFFD32F2F), fontSize = 13.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = onClearError) {
                        Text("关闭", fontSize = 13.sp)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
        }

        // 这一页是整屏推入的，没有底部导航栏，得自己让开系统手势条
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}

@Composable
private fun QuoteSummaryCard(state: QuoteUiState, result: QuoteComputation) {
    val isSpotMode = state.selectedSpotProduct != null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(QuoteRowBg)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("规格", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = QuoteGray66)

        Text(
            text = "${trimNumber(state.form.length)} × ${trimNumber(state.form.width)} × " +
                "${trimNumber(state.form.height)} cm",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = QuoteTitle
        )

        if (isSpotMode) {
            // 现货模式：显示现货信息 + 加工面积（工艺费需要）
            val rows = listOf(
                "数量" to "${state.form.orderQuantity} 个",
                "来源" to "现货",
                "单价" to "${moneyPlain(result.materialUnitPrice)} 元/个",
                "加工面积" to "${num(result.areaM2, 4)} m²"
            )
            rows.forEach { (label, value) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(label, fontSize = 13.sp, color = QuoteMuted)
                    Text(value, fontSize = 13.sp, color = QuoteTitle, fontWeight = FontWeight.Medium)
                }
            }
        } else {
            // 普通模式：面积计价
            val rows = listOf(
                "数量" to "${state.form.orderQuantity} 个",
                "材质" to result.materialLabel,
                "单价" to "${moneyPlain(result.materialUnitPrice)} 元/m²",
                "加工面积" to "${num(result.areaM2, 4)} m²",
                "总重" to "${num(result.totalWeight, 2)} kg"
            )
            rows.forEach { (label, value) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(label, fontSize = 13.sp, color = QuoteMuted)
                    Text(value, fontSize = 13.sp, color = QuoteTitle, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun QuoteBreakdownCard(state: QuoteUiState, result: QuoteComputation) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(QuoteRowBg)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("费用明细", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = QuoteGray66)

        result.chargeLines.forEach { QuoteLineRow(it) }

        HorizontalDivider(color = QuoteFieldStroke)

        QuoteLineRow(ChargeLine("小计", result.subtotal))

        if (state.form.extraFeeEnabled && state.form.extraFee > 0) {
            QuoteLineRow(ChargeLine("工厂加价", state.form.extraFee))
        }
        if (result.profitAmount != 0.0) {
            QuoteLineRow(ChargeLine("利润", result.profitAmount))
        }
        if (result.specialFeesSum > 0) {
            QuoteLineRow(
                ChargeLine(
                    "附加费",
                    result.specialFeesSum,
                    state.form.specialFees.filter { it.enabled }.joinToString("、") { it.name }
                )
            )
        }

        HorizontalDivider(color = QuoteFieldStroke)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("总价", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = QuoteTitle)
            Text(
                money(result.finalAmount),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = QuoteGreen
            )
        }

        if (state.form.orderQuantity > 0) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("折合单价", fontSize = 12.sp, color = QuoteMuted)
                Text(
                    "${money(result.finalAmount / state.form.orderQuantity)} / 个",
                    fontSize = 12.sp,
                    color = QuoteGray66
                )
            }
        }
    }
}

@Composable
private fun QuoteLineRow(line: ChargeLine) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(line.name, fontSize = 13.sp, color = QuoteGray66)
            Text(
                money(line.amount),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = QuoteTitle
            )
        }
        if (line.detail.isNotEmpty()) {
            Text(line.detail, fontSize = 11.sp, color = QuoteMuted)
        }
    }
}
