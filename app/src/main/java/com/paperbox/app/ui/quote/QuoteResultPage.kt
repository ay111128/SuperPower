package com.paperbox.app.ui.quote

import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.Dp
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
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
            QuoteDocumentSection(state, result)
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QuoteDocumentSection(state: QuoteUiState, result: QuoteComputation) {
    val now = Date()
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
    val enabledFees = state.form.specialFees.filter { it.enabled }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val documentBounds = remember { mutableStateOf<Rect?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coordinates ->
                documentBounds.value = coordinates.boundsInWindow()
            }
            .clip(RoundedCornerShape(12.dp))
            .background(CardGray)
            .combinedClickable(
                onClick = {},
                onDoubleClick = {
                    val bounds = documentBounds.value ?: return@combinedClickable
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
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "截图失败：${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            )
    ) {
        // ── Header: 绿色顶栏 ──
        CardHeader(state, dateFormat.format(now))

        // ── DescArea: 标题 + 描述 ──
        CardDescArea()

        // ── ColumnBar: 表头 ──
        CardColumnBar()

        // ── DataArea: 数据行 + 合计 ──
        CardDataArea(state, result, enabledFees)

        // ── BottomSection: 联系信息 + 二维码 ──
        CardBottomSection()
    }
}

@Composable
private fun CardHeader(state: QuoteUiState, dateStr: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardGreen)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // LogoBox
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(CardGreenLight),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.foundation.Image(
                painter = painterResource(id = R.drawable.company_logo),
                contentDescription = "公司Logo",
                modifier = Modifier.size(44.dp),
                contentScale = ContentScale.Fit
            )
        }

        // 公司信息
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text("广州小鱼包装", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("专业包装解决方案", fontSize = 9.sp, color = CardGreenLight)
        }

        // 右侧日期 + 编号
        Column(
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("报价日期：", fontSize = 9.sp, color = Color.White)
                Text(dateStr, fontSize = 9.sp, color = Color.White)
            }
            state.traceCode?.let { code ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("工单编号：", fontSize = 9.sp, color = Color.White)
                    Text(code, fontSize = 9.sp, color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun CardDescArea() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardDescBg)
            .padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("报 价 单", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = CardGreen)
        Text("QUOTATION", fontSize = 11.sp, color = CardTextGray)
        Spacer(Modifier.height(2.dp))
        Text(
            "感谢您对本公司的信任，以下是我们为您提供的报价，请您参考：",
            fontSize = 9.sp,
            color = CardTextDesc
        )
    }
}

@Composable
private fun CardColumnBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(30.dp)
            .background(CardGreen)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CardHeaderText("序号", 36.dp, TextAlign.Center)
        CardDivider()
        CardHeaderText("产品名称", 90.dp, TextAlign.Start)
        CardDivider()
        CardHeaderText("规格", 85.dp, TextAlign.Start)
        CardDivider()
        CardHeaderText("数量", 45.dp, TextAlign.End)
        CardDivider()
        CardHeaderText("单价", 45.dp, TextAlign.End)
        CardDivider()
        CardHeaderText("金额", 54.dp, TextAlign.End)
    }
}

@Composable
private fun RowScope.CardHeaderText(text: String, width: Dp, align: TextAlign) {
    Text(
        text,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        textAlign = align,
        modifier = Modifier.width(width)
    )
}

@Composable
private fun RowScope.CardDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(14.dp)
            .background(Color.White.copy(alpha = 0.3f))
    )
}

@Composable
private fun CardDataArea(
    state: QuoteUiState,
    result: QuoteComputation,
    enabledFees: List<com.paperbox.app.domain.model.SpecialFee>
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
            Text("合计金额：", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CardTextDark)
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp)
            .background(if (isAlt) CardRowAlt else Color.White)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("$index", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CardTextDark,
            textAlign = TextAlign.Center, modifier = Modifier.width(36.dp))
        Text(name, fontSize = 10.sp, color = CardTextDark,
            modifier = Modifier.width(90.dp), maxLines = 1)
        Text(spec, fontSize = 10.sp, color = CardTextDark,
            modifier = Modifier.width(85.dp), maxLines = 1)
        Text(quantity, fontSize = 10.sp, color = CardTextDark,
            textAlign = TextAlign.End, modifier = Modifier.width(45.dp))
        Text(unitPrice, fontSize = 10.sp, color = CardTextDark,
            textAlign = TextAlign.End, modifier = Modifier.width(45.dp))
        Text(amount, fontSize = 10.sp, color = CardGreen,
            textAlign = TextAlign.End, modifier = Modifier.width(54.dp))
    }
}

@Composable
private fun CardBottomSection() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(127.dp)
            .background(CardGreen)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 联系信息
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("联系我们", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
            CardInfoRow("联系人：", "吴小姐")
            CardInfoRow("电话：", "13570315323")
            CardInfoRow("微信：", "xbrody")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("地址：", fontSize = 9.sp, color = CardGreenLight)
                Text("广东省广州市增城区新塘镇富源路35号", fontSize = 9.sp, color = Color.White)
            }
        }

        // 二维码
        Box(
            modifier = Modifier
                .size(110.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.foundation.Image(
                painter = painterResource(id = R.drawable.wechat_qr),
                contentDescription = "微信二维码",
                modifier = Modifier.size(100.dp),
                contentScale = ContentScale.Fit
            )
        }
    }
}

@Composable
private fun CardInfoRow(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, fontSize = 9.sp, color = CardGreenLight)
        Text(value, fontSize = 9.sp, color = Color.White)
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
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)
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
