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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.toAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
//  报价单文档区域
// ══════════════════════════════════════════════════════════════

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QuoteDocumentSection(state: QuoteUiState, result: QuoteComputation) {
    val now = Date()
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
    val timeFormat = SimpleDateFormat("HH:mm", Locale.CHINA)
    val enabledFees = state.form.specialFees.filter { it.enabled }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val graphicsLayer = rememberGraphicsLayer()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                drawLayer(graphicsLayer)
            }
            .border(1.dp, QuoteTitle.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = {},
                onDoubleClick = {
                    scope.launch {
                        val imageBitmap = graphicsLayer.toImageBitmap()
                        val bitmap = imageBitmap.toAndroidBitmap()
                            .copy(Bitmap.Config.ARGB_8888, true)
                        saveBitmapToGallery(context, bitmap)
                    }
                }
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ── 上：标题 + 日期时间 + 编号 ──
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                "小鱼包装报价单",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = QuoteTitle
            )
            Text(
                "报价日期：${dateFormat.format(now)}",
                fontSize = 12.sp,
                color = QuoteGray66
            )
            Text(
                "报价时间：${timeFormat.format(now)}",
                fontSize = 12.sp,
                color = QuoteGray66
            )
            state.traceCode?.let { code ->
                Text(
                    "报价编号：$code",
                    fontSize = 12.sp,
                    color = QuoteGray66
                )
            }
        }

        HorizontalDivider(color = QuoteFieldStroke)

        // ── 中：表格 ──
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // 表头
            DocumentTableRow(
                col1 = "品名", col2 = "规格", col3 = "数量",
                col4 = "单价", col5 = "金额",
                isHeader = true
            )
            HorizontalDivider(color = QuoteFieldStroke)

            // 主品行
            val unitPrice = if (state.form.orderQuantity > 0)
                result.finalAmount / state.form.orderQuantity else 0.0
            val spec = "${trimNumber(state.form.length)}×${trimNumber(state.form.width)}×${trimNumber(state.form.height)}cm"
            DocumentTableRow(
                col1 = result.materialLabel,
                col2 = spec,
                col3 = "${state.form.orderQuantity}",
                col4 = moneyPlain(unitPrice),
                col5 = money(result.finalAmount)
            )

            // 附加费行
            enabledFees.forEach { fee ->
                HorizontalDivider(color = QuoteFieldStroke)
                DocumentTableRow(
                    col1 = fee.name,
                    col2 = "-",
                    col3 = "-",
                    col4 = money(fee.amount),
                    col5 = money(fee.amount)
                )
            }
        }

        HorizontalDivider(color = QuoteFieldStroke)

        // ── 下：公司信息 + 二维码 ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier.weight(1f)
            ) {
                InfoRow("公司", "小鱼包装有限公司")
                InfoRow("联系人", "张经理")
                InfoRow("电话", "138-0000-0000")
                InfoRow("地址", "广东省东莞市xxx路xxx号")
            }

            Spacer(Modifier.width(12.dp))

            // 二维码占位
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .border(1.dp, QuoteFieldStroke, RoundedCornerShape(6.dp))
                    .background(QuoteRowBg, RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("微信\n二维码", fontSize = 10.sp, color = QuoteMuted, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun DocumentTableRow(
    col1: String, col2: String, col3: String,
    col4: String, col5: String,
    isHeader: Boolean = false
) {
    val fontWeight = if (isHeader) FontWeight.SemiBold else FontWeight.Normal
    val textColor = if (isHeader) QuoteGray66 else QuoteTitle
    val bgColor = if (isHeader) QuoteRowBg else Color.Transparent
    val fontSize = if (isHeader) 11.sp else 12.sp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(col1, fontSize = fontSize, fontWeight = fontWeight, color = textColor,
            modifier = Modifier.weight(2.5f), maxLines = 1)
        Text(col2, fontSize = fontSize, fontWeight = fontWeight, color = textColor,
            modifier = Modifier.weight(2f), maxLines = 1)
        Text(col3, fontSize = fontSize, fontWeight = fontWeight, color = textColor,
            modifier = Modifier.weight(1f), textAlign = TextAlign.End, maxLines = 1)
        Text(col4, fontSize = fontSize, fontWeight = fontWeight, color = textColor,
            modifier = Modifier.weight(1.5f), textAlign = TextAlign.End, maxLines = 1)
        Text(col5, fontSize = fontSize, fontWeight = fontWeight, color = textColor,
            modifier = Modifier.weight(1.5f), textAlign = TextAlign.End, maxLines = 1)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row {
        Text("$label：", fontSize = 11.sp, color = QuoteGray66)
        Text(value, fontSize = 11.sp, color = QuoteTitle)
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
