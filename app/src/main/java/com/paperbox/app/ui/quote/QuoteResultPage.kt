package com.paperbox.app.ui.quote

import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll

import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.drawToBitmap
import com.paperbox.app.R
import com.paperbox.app.domain.model.ChargeLine
import com.paperbox.app.domain.model.QuoteComputation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 报价结果页 —— 由浮动按钮「生成报价」推入的独立整页（不是弹窗）。
 *
 * 无状态：只吃 QuoteComputation 和回调，状态全在共用的 QuoteViewModel 里。
 */
@Composable
internal fun QuoteResultPage(
    state: QuoteUiState,
    onBack: () -> Unit,
    onClearTraceCode: () -> Unit,
    onClearError: () -> Unit,
    onToggleLanguage: () -> Unit
) {
    val result = state.result ?: return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val documentBounds = remember { mutableStateOf<Rect?>(null) }

    fun captureDocument() {
        val bounds = documentBounds.value ?: return
        scope.launch {
            try {
                val fullBitmap = withContext(Dispatchers.Main) {
                    view.drawToBitmap(Bitmap.Config.ARGB_8888)
                }
                val left = bounds.left.toInt().coerceAtLeast(0)
                val top = bounds.top.toInt().coerceAtLeast(0)
                val right = bounds.right.toInt().coerceAtMost(fullBitmap.width)
                val bottom = bounds.bottom.toInt().coerceAtMost(fullBitmap.height)
                val w = (right - left).coerceAtLeast(1)
                val h = (bottom - top).coerceAtLeast(1)
                val cropped = Bitmap.createBitmap(fullBitmap, left, top, w, h)

                saveBitmapToGallery(context, cropped)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "已保存到相册", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "截图失败：${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

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
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧返回按钮
            IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                Text("←", color = Color.White, fontSize = 20.sp)
            }
            // 中间标题
            Text(
                "报价结果",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
            // 右侧中英文切换按钮
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.2f))
                    .clickable { onToggleLanguage() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (state.isEnglish) "中" else "EN",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            // 下载截图按钮
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.2f))
                    .clickable { captureDocument() },
                contentAlignment = Alignment.Center
            ) {
                Text("⬇", color = Color.White, fontSize = 18.sp)
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 0.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            QuoteDocumentSection(state, result, state.isEnglish) { rect ->
                documentBounds.value = rect
            }
            QuoteSummaryCard(state, result)
            QuoteBreakdownCard(state, result)

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

// ══════════════════════════════════════════════════════════════
//  报价单文档区域（新设计：报价卡片.pen）
// ══════════════════════════════════════════════════════════════

// 设计稿色值
private val CardGreen = Color(0xFF1B8016)
private val CardGreenLight = Color(0xFFB3F2B9)
private val CardGray = Color(0xFFE3E3E3)
private val CardDescBg = Color(0xFFF5F5F5)
private val CardRowAlt = Color(0xFFF8FDF8)
private val CardTotalBg = Color(0xFFF0F7F0)
private val CardTextGray = Color(0xFF999999)
private val CardTextDesc = Color(0xFF666666)
private val CardTextDark = Color(0xFF333333)

@Composable
private fun QuoteDocumentSection(
    state: QuoteUiState,
    result: QuoteComputation,
    isEnglish: Boolean = false,
    onDocumentBounds: (Rect?) -> Unit = {}
) {
    val now = Date()
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
    val enabledFees = state.form.specialFees.filter { it.enabled }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coordinates ->
                onDocumentBounds(coordinates.boundsInWindow())
            }
            .clip(RoundedCornerShape(12.dp))
            .background(CardGray)
    ) {
        // ── 标题区域（绿色背景上方）──
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(CardDescBg)
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(if (isEnglish) "QUOTATION" else "报 价 单", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = CardGreen)
            Text("QUOTATION", fontSize = 11.sp, color = CardTextGray)
        }

        // ── Header: 绿色顶栏 ──
        CardHeader(state, dateFormat.format(now), isEnglish)

        // ── DescArea: 描述文字 ──
        CardDescArea(isEnglish)

        // ── ColumnBar: 表头 ──
        CardColumnBar(isEnglish)

        // ── DataArea: 数据行 + 合计 ──
        CardDataArea(state, result, enabledFees, isEnglish)

        // ── BottomSection: 联系信息 + 二维码 ──
        CardBottomSection(isEnglish)
    }
}

@Composable
private fun CardHeader(state: QuoteUiState, dateStr: String, isEnglish: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardGreen)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // LogoBox
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.foundation.Image(
                painter = painterResource(id = R.drawable.company_logo),
                contentDescription = "公司Logo",
                modifier = Modifier.size(48.dp),
                contentScale = ContentScale.Crop
            )
        }

        // 公司信息
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(if (isEnglish) "Xiaoyu Packaging" else "广州小鱼包装", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(if (isEnglish) "Professional Packaging Solutions" else "专业包装解决方案", fontSize = 9.sp, color = CardGreenLight)
        }

        // 右侧日期 + 编号
        Column(
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (isEnglish) "Date: " else "报价日期：", fontSize = 9.sp, color = Color.White)
                Text(dateStr, fontSize = 9.sp, color = Color.White)
            }
            state.traceCode?.let { code ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (isEnglish) "Order No. " else "工单编号：", fontSize = 9.sp, color = Color.White)
                    Text(code, fontSize = 9.sp, color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun CardDescArea(isEnglish: Boolean = false) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardDescBg)
            .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            if (isEnglish) "Thank you for your trust. Please find our quotation below:" else "感谢您对本公司的信任，以下是我们为您提供的报价，请您参考：",
            fontSize = 9.sp,
            color = CardTextDesc
        )
    }
}

@Composable
private fun CardColumnBar(isEnglish: Boolean = false) {
    val dividerColor = Color.White.copy(alpha = 0.4f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(30.dp)
            .background(CardGreen)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CardHeaderText(if (isEnglish) "No." else "序号", 36.dp, TextAlign.Center, true, dividerColor)
        CardHeaderText(if (isEnglish) "Product" else "产品名称", 90.dp, TextAlign.Center, true, dividerColor)
        CardHeaderText(if (isEnglish) "Spec" else "规格", 85.dp, TextAlign.Center, true, dividerColor)
        CardHeaderText(if (isEnglish) "Qty" else "数量", 45.dp, TextAlign.Center, true, dividerColor)
        CardHeaderText(if (isEnglish) "Price" else "单价", 45.dp, TextAlign.Center, true, dividerColor)
        CardHeaderText(if (isEnglish) "Amount" else "金额", 54.dp, TextAlign.Center, false, dividerColor)
    }
}

@Composable
private fun RowScope.CardHeaderText(
    text: String, width: Dp, align: TextAlign,
    showEndDivider: Boolean = false, dividerColor: Color = Color.Transparent
) {
    Text(
        text,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        textAlign = align,
        modifier = Modifier
            .width(width)
            .drawBehind {
                if (showEndDivider) {
                    drawLine(
                        color = dividerColor,
                        start = Offset(size.width, 0f),
                        end = Offset(size.width, size.height),
                        strokeWidth = 1.dp.toPx()
                    )
                }
            }
    )
}

@Composable
private fun RowScope.CardDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(20.dp)
            .background(Color(0xFF999999))
    )
}

@Composable
private fun CardDataArea(
    state: QuoteUiState,
    result: QuoteComputation,
    enabledFees: List<com.paperbox.app.domain.model.SpecialFee>,
    isEnglish: Boolean = false
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
    ) {
        // 主品行
        val unitPrice = if (state.form.orderQuantity > 0)
            result.finalAmount / state.form.orderQuantity else 0.0
        val spec = "${trimNumber(state.form.length)}×${trimNumber(state.form.width)}×${trimNumber(state.form.height)}mm"

        CardDataRow(
            index = 1,
            name = result.materialLabel,
            spec = spec,
            quantity = "${state.form.orderQuantity}",
            unitPrice = moneyPlain(unitPrice),
            amount = money(result.finalAmount),
            isAlt = true
        )

        // 附加费行
        enabledFees.forEachIndexed { index, fee ->
            CardDataRow(
                index = index + 2,
                name = fee.name,
                spec = "-",
                quantity = "-",
                unitPrice = money(fee.amount),
                amount = money(fee.amount),
                isAlt = (index + 2) % 2 == 0
            )
        }

        // 合计行
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .background(CardTotalBg)
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(if (isEnglish) "Total: " else "合计金额：", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CardTextDark)
            Text(money(result.finalAmount), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = CardGreen)
        }
    }
}

@Composable
private fun CardDataRow(
    index: Int,
    name: String,
    spec: String,
    quantity: String,
    unitPrice: String,
    amount: String,
    isAlt: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .background(if (isAlt) CardRowAlt else Color.White)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val cellDividerColor = Color(0xFFCCCCCC)
            Text("$index", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CardTextDark,
                textAlign = TextAlign.Center, modifier = Modifier.width(36.dp).drawBehind {
                    drawLine(cellDividerColor, Offset(size.width, 0f), Offset(size.width, size.height), 1.dp.toPx())
                })
            Text(name, fontSize = 10.sp, color = CardTextDark,
                textAlign = TextAlign.Center, modifier = Modifier.width(90.dp).drawBehind {
                    drawLine(cellDividerColor, Offset(size.width, 0f), Offset(size.width, size.height), 1.dp.toPx())
                })
            Text(spec, fontSize = 10.sp, color = CardTextDark,
                textAlign = TextAlign.Center, modifier = Modifier.width(85.dp).drawBehind {
                    drawLine(cellDividerColor, Offset(size.width, 0f), Offset(size.width, size.height), 1.dp.toPx())
                })
            Text(quantity, fontSize = 10.sp, color = CardTextDark,
                textAlign = TextAlign.Center, modifier = Modifier.width(45.dp).drawBehind {
                    drawLine(cellDividerColor, Offset(size.width, 0f), Offset(size.width, size.height), 1.dp.toPx())
                })
            Text(unitPrice, fontSize = 10.sp, color = CardTextDark,
                textAlign = TextAlign.Center, modifier = Modifier.width(45.dp).drawBehind {
                    drawLine(cellDividerColor, Offset(size.width, 0f), Offset(size.width, size.height), 1.dp.toPx())
                })
            Text(amount, fontSize = 10.sp, color = CardGreen,
                textAlign = TextAlign.Center, modifier = Modifier.width(54.dp))
        }
        // 底部分割线
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .align(Alignment.BottomCenter)
                .background(Color(0xFFE0E0E0))
        )
    }
}

@Composable
private fun CardBottomSection(isEnglish: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardGreen)
            .padding(start = 16.dp, top = 2.dp, end = 16.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 联系信息
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(if (isEnglish) "Contact Us" else "联系我们", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
            CardInfoRow(if (isEnglish) "Contact: " else "联系人：", if (isEnglish) "Ms. Wu" else "吴小姐")
            CardInfoRow(if (isEnglish) "Tel: " else "电话：", "13570315323")
            CardInfoRow(if (isEnglish) "WeChat: " else "微信：", "xbrody")
            CardInfoRow(if (isEnglish) "Address: " else "地址：", if (isEnglish) "No.35 Fuyuan Rd, Xintang, Zengcheng, Guangzhou" else "广东省广州市增城区新塘镇富源路35号")
        }

        // 二维码
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.foundation.Image(
                painter = painterResource(id = R.drawable.wechat_qr),
                contentDescription = "微信二维码",
                modifier = Modifier.size(110.dp),
                contentScale = ContentScale.Crop
            )
        }
    }
}

@Composable
private fun CardInfoRow(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = TextStyle(fontSize = 9.sp, lineHeight = 9.sp), color = CardGreenLight)
        Text(value, style = TextStyle(fontSize = 9.sp, lineHeight = 9.sp), color = Color.White)
    }
}

/**
 * 将 Bitmap 保存到系统相册（Android 10+ 用 MediaStore，不需要存储权限）。
 */
private suspend fun saveBitmapToGallery(context: android.content.Context, bitmap: Bitmap) {
    withContext(Dispatchers.IO) {
        val filename = "报价单_${System.currentTimeMillis()}.jpg"
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/小鱼包装")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val uri = context.contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues
        ) ?: return@withContext
        try {
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                context.contentResolver.update(uri, contentValues, null, null)
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "报价单已保存到相册", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "保存失败：${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
