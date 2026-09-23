package com.paperbox.app.ui.quote

import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.drawToBitmap
import com.paperbox.app.R
import com.paperbox.app.data.api.models.QuoteHistoryEntry
import com.paperbox.app.domain.model.ChargeLine
import com.paperbox.app.domain.model.LayoutKey
import com.paperbox.app.domain.model.PricingMode
import com.paperbox.app.domain.model.QuoteComputation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 报价单表格里一行可编辑的列数据（纯展示层，不回算价格）。 */
private data class DocRow(
    val name: String,
    val spec: String,
    val quantity: String,
    val unitPrice: String,
    val amount: String,
)

/**
 * 由计算结果生成报价单的初始行数据：主品行 + 已启用附加费行。
 *
 * 主品行金额 = 合计 − 附加费（finalAmount 已含附加费，见 CalculateQuoteUseCase）——
 * 附加费单独成行后，行加总才等于合计金额；否则主品行吞掉附加费，
 * 报价单看起来就是「附加费列了但合计没加上」。
 * 附加费行与 Web 打印视图口径一致：规格进规格列，数量 = 订单数，单价 = 金额/数量。
 */
private fun buildDocRows(state: QuoteUiState, result: QuoteComputation, isEnglish: Boolean): List<DocRow> {
    val qty = state.form.orderQuantity
    val mainAmount = result.finalAmount - result.specialFeesSum
    val main = DocRow(
        name = if (isEnglish) "Mailer Box" else "飞机盒",
        spec = "${trimNumber(state.form.length)}×${trimNumber(state.form.width)}×${trimNumber(state.form.height)}",
        quantity = "$qty",
        unitPrice = moneyPlain(if (qty > 0) mainAmount / qty else 0.0),
        amount = money(mainAmount),
    )
    val fees = state.form.specialFees.filter { it.enabled }.map { fee ->
        val effective = when (fee.pricingMode) {
            PricingMode.TOTAL -> fee.amount
            PricingMode.UNIT_PRICE -> fee.unitPrice * qty
        }
        DocRow(
            name = fee.name,
            spec = fee.spec.ifBlank { "-" },
            quantity = if (qty > 0) "$qty" else "-",
            unitPrice = moneyPlain(if (qty > 0) effective / qty else 0.0),
            amount = money(effective),
        )
    }
    return listOf(main) + fees
}

/**
 * 顶栏按钮：裸图标/裸文字，静止时无任何背景和描边。
 * 按压时浮现一层淡淡的白色圆底作为反馈。
 * [pill] = true 时按内容横向撑开（用于「保存」），否则固定 32dp。
 */
@Composable
private fun TopBarButton(
    onClick: () -> Unit,
    pill: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Box(
        modifier = Modifier
            .then(if (pill) Modifier.height(32.dp) else Modifier.size(32.dp))
            .background(
                color = if (pressed) Color.White.copy(alpha = 0.18f) else Color.Transparent,
                shape = CircleShape
            )
            .then(if (pill) Modifier.padding(horizontal = 10.dp) else Modifier)
            .clickable(interactionSource = interactionSource, indication = null) { onClick() },
        contentAlignment = Alignment.Center,
        content = content
    )
}

/**
 * 报价结果页 —— 由浮动按钮「生成报价」推入的独立整页（不是弹窗）。
 *
 * 无状态：只吃 QuoteComputation 和回调，状态全在共用的 QuoteViewModel 里。
 */
@Composable
internal fun QuoteResultPage(
    state: QuoteUiState,
    onBack: () -> Unit,
    onClearError: () -> Unit,
    onToggleLanguage: () -> Unit
) {
    val result = state.result ?: return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val documentBounds = remember { mutableStateOf<Rect?>(null) }

    // ── 报价单编辑模式：只改表格列数据（logo/日期/工单号/列名不可编辑）──
    var editing by remember { mutableStateOf(false) }
    // 原始行随计算结果/语言变化；一旦编辑过就以 editedRows 为准（语言切换不再覆盖手工修改）
    val originalRows = remember(state, result, state.isEnglish) {
        buildDocRows(state, result, state.isEnglish)
    }
    val originalTotal = remember(result) { money(result.finalAmount) }
    var editedRows by remember { mutableStateOf<List<DocRow>?>(null) }
    var editedTotal by remember { mutableStateOf<String?>(null) }
    val shownRows = editedRows ?: originalRows
    val shownTotal = editedTotal ?: originalTotal

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
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
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
            // 右侧按钮区：正常态 = 中英文切换 / 编辑 / 下载；编辑态 = 保存
            if (editing) {
                TopBarButton(onClick = { editing = false }, pill = true) {
                    Text(
                        "保存",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                // 中英文切换
                TopBarButton(onClick = onToggleLanguage) {
                    Text(
                        if (state.isEnglish) "中" else "EN",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                // 编辑
                TopBarButton(onClick = { editing = true }) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "编辑报价单",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
                // 下载截图
                TopBarButton(onClick = { captureDocument() }) {
                    Icon(
                        Icons.Default.FileDownload,
                        contentDescription = "下载截图",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
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
            QuoteDocumentSection(
                state = state,
                result = result,
                isEnglish = state.isEnglish,
                rows = shownRows,
                totalText = shownTotal,
                editing = editing,
                onEditRow = { i, transform ->
                    editedRows = shownRows.mapIndexed { idx, r -> if (idx == i) transform(r) else r }
                },
                onEditTotal = { editedTotal = it }
            ) { rect ->
                documentBounds.value = rect
            }
            QuoteSummaryCard(state, result)
            QuoteBreakdownCard(state, result)

            // 报价历史记录 —— 刻意做小做灰，放在底部不抢戏（原来的追溯码展示，已移除）
            QuoteHistoryCard(state.history)

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
                    // 名称（规格）—— 和 Web 明细行 label 口径一致
                    state.form.specialFees.filter { it.enabled }.joinToString("、") { fee ->
                        if (fee.spec.isNotBlank()) "${fee.name}（${fee.spec}）" else fee.name
                    }
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

/**
 * 报价历史记录 —— 页面底部的低调小卡：
 * 尺寸/数量/单价/金额 一行灰字，日期时间（yyyy-MM-dd HH:mm:ss）更小一号；
 * 整体小字浅灰，不抢上方报价单的戏。无记录时整卡隐藏。
 */
@Composable
private fun QuoteHistoryCard(history: List<QuoteHistoryEntry>) {
    if (history.isEmpty()) return
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFF5F5F5))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("报价历史记录", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = QuoteMuted)

        history.take(10).forEach { entry ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "${trimNumber(entry.length)}×${trimNumber(entry.width)}×${trimNumber(entry.height)} cm" +
                        " · ${entry.quantity}个" +
                        " · ${money(entry.unitPrice)}/个" +
                        " · 合计${money(entry.finalAmount)}",
                    fontSize = 11.sp,
                    color = QuoteGray66
                )
                Text(
                    text = dateFormat.format(Date(entry.createdAt)),
                    fontSize = 9.sp,
                    color = QuoteMuted
                )
            }
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
private val CardSpecBg = Color(0xFFF8F8F8)
private val CardTextGray = Color(0xFF999999)
private val CardTextDesc = Color(0xFF666666)
private val CardTextDark = Color(0xFF333333)
private val CellDivider = Color(0xFFCCCCCC)

// 表格列权重（按设计稿 36/90/85/45/45/54 dp 的比例）：仅用于内容放不下/有富余时的分配比例
private val CardColWeights = listOf(36f, 90f, 85f, 45f, 45f, 54f)

/** 表头文案（列名不可编辑，测量下限也用它）。 */
private fun tableHeaders(isEnglish: Boolean): List<String> =
    if (isEnglish) listOf("No.", "Product", "Spec (cm)", "Qty", "Price", "Amount")
    else listOf("序号", "产品名称", "规格（cm）", "数量", "单价", "金额")

/**
 * 按实际列宽在整行高度上画竖向分隔线——画在行级（而不是单个单元格 Text 上），行多高线就多高，永不断线。
 * [afterCols]：0-based 列下标集合，表示在该列右缘画线；默认画全部内部边界。
 */
private fun DrawScope.drawTableColumns(color: Color, colWidths: List<Dp>, afterCols: Set<Int> = setOf(0, 1, 2, 3, 4)) {
    val stroke = 1.dp.toPx()
    var acc = 0f
    colWidths.forEachIndexed { i, w ->
        acc += w.toPx()
        if (i in afterCols && i < colWidths.lastIndex) {
            drawLine(color, Offset(acc, 0f), Offset(acc, size.height), strokeWidth = stroke)
        }
    }
}

/**
 * 内容决定列宽的分配（全部 px）：
 * 1. [floor]（硬需求，含表头下限与数字列单行内容）必须先满足；
 * 2. 有富余时先喂给 [desire] 还没到位的软列（名称/规格，让它们尽量不换行）；
 * 3. 仍有富余按 [weights] 比例分给各列；
 * 4. floor 总和超过可用宽时（极端情况）等比压缩，由省略号兜底。
 */
private fun distributeCols(
    availablePx: Float,
    floor: FloatArray,
    desire: FloatArray,
    weights: List<Float>,
): List<Float> {
    val out = floor.copyOf()
    if (availablePx - out.sum() > 1f) {
        // 软列先按缺口比例吃到不换行的宽度
        val needy = out.indices.filter { desire[it] > out[it] + 1f }
        if (needy.isNotEmpty()) {
            val totalNeed = needy.sumOf { (desire[it] - out[it]).toDouble() }.toFloat()
            val give = minOf(availablePx - out.sum(), totalNeed)
            needy.forEach { i -> out[i] += give * ((desire[i] - out[i]) / totalNeed) }
        }
        val rest = availablePx - out.sum()
        if (rest > 1f) {
            val wSum = weights.sum()
            for (i in out.indices) out[i] += rest * weights[i] / wSum
        }
    } else if (availablePx - out.sum() < -1f) {
        val scale = availablePx / out.sum()
        for (i in out.indices) out[i] *= scale
    }
    return out.toList()
}

private data class TablePlan(
    val widths: List<Dp>,
    val cellFont: TextUnit,
    val totalFont: TextUnit,
)

/**
 * 内容决定列宽（两遍测量）：
 * - floor = max(表头, 数字列内容) + 内边距 —— 表头永远是下限，名称/规格可换行所以内容不进 floor；
 * - 数字列（数量/单价/金额）必须单行放得下，放不下就字号 10→9→8 逐级缩；
 * - 富余先让名称/规格拿到不换行的宽度，再按设计权重分。
 * 表头、数据行、合计行共用这一套宽度 → 永远对齐，且任何一列内容变长都有同一套逻辑兜着。
 */
@Composable
private fun rememberTablePlan(
    tableWidth: Dp,
    rows: List<DocRow>,
    totalText: String,
    isEnglish: Boolean,
): TablePlan {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(tableWidth, rows, totalText, isEnglish, measurer, density) {
        with(density) {
            val avail = tableWidth.toPx()
            val pad = 12.dp.toPx() // 单元格左右各 6dp
            val headers = tableHeaders(isEnglish)
            val headerStyle = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold)

            fun textW(s: String, style: TextStyle): Float =
                if (s.isEmpty()) 0f else measurer.measure(s, style).size.width.toFloat()

            val headerW = FloatArray(6) { textW(headers[it], headerStyle) }

            fun floorsDesires(cellFont: TextUnit, totalFont: TextUnit): Pair<FloatArray, FloatArray> {
                val cellStyle = TextStyle(fontSize = cellFont)
                val boldCell = cellStyle.copy(fontWeight = FontWeight.Bold)
                val floor = FloatArray(6)
                val desire = FloatArray(6)
                // 0 序号（行号，硬）
                val idxW = rows.indices.maxOfOrNull { textW("${it + 1}", boldCell) } ?: 0f
                floor[0] = maxOf(headerW[0], idxW) + pad
                desire[0] = floor[0]
                // 1 产品名称（软：放不下可换 2 行）
                val nameW = rows.maxOfOrNull { textW(it.name, cellStyle) } ?: 0f
                floor[1] = headerW[1] + pad
                desire[1] = nameW + pad
                // 2 规格（软）
                val specW = rows.maxOfOrNull { textW(it.spec, cellStyle) } ?: 0f
                floor[2] = headerW[2] + pad
                desire[2] = specW + pad
                // 3 数量（硬，必须单行）
                val qtyW = rows.maxOfOrNull { textW(it.quantity, cellStyle) } ?: 0f
                floor[3] = maxOf(headerW[3], qtyW) + pad
                desire[3] = floor[3]
                // 4 单价（硬）
                val priceW = rows.maxOfOrNull { textW(it.unitPrice, cellStyle) } ?: 0f
                floor[4] = maxOf(headerW[4], priceW) + pad
                desire[4] = floor[4]
                // 5 金额（硬）：数据行金额与合计金额（粗体大一号）取最大 —— 这就是这次撑宽的列
                val amtW = rows.maxOfOrNull { textW(it.amount, cellStyle) } ?: 0f
                val totalW = textW(totalText, TextStyle(fontSize = totalFont, fontWeight = FontWeight.Bold))
                floor[5] = maxOf(headerW[5], amtW, totalW) + pad
                desire[5] = floor[5]
                return floor to desire
            }

            var chosen: TablePlan? = null
            for ((cf, tf) in listOf(10.sp to 13.sp, 9.sp to 12.sp, 8.sp to 11.sp)) {
                val (floor, desire) = floorsDesires(cf, tf)
                // 每轮都会算一份（放不下时靠 distribute 的等比压缩兜底），放得下就停
                chosen = TablePlan(
                    widths = distributeCols(avail, floor, desire, CardColWeights).map { it.toDp() },
                    cellFont = cf,
                    totalFont = tf
                )
                if (floor.sum() <= avail) break
            }
            chosen!!
        }
    }
}

@Composable
private fun QuoteDocumentSection(
    state: QuoteUiState,
    result: QuoteComputation,
    isEnglish: Boolean = false,
    rows: List<DocRow>,
    totalText: String,
    editing: Boolean,
    onEditRow: (Int, (DocRow) -> DocRow) -> Unit,
    onEditTotal: (String) -> Unit,
    onDocumentBounds: (Rect?) -> Unit = {}
) {
    val now = Date()
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)

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

        // ── 表格主体：表头 + 数据行 + 合计 ──
        // 列宽由内容测量决定（rememberTablePlan），表头/数据/合计共用同一套宽；
        // 外框用 drawWithContent 画在最上层（children 的背景会盖住 border，所以不能用 Modifier.border）
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .drawWithContent {
                    drawContent()
                    val stroke = 1.dp.toPx()
                    val half = stroke / 2
                    drawRect(
                        color = CellDivider,
                        topLeft = Offset(half, half),
                        size = Size(size.width - stroke, size.height - stroke),
                        style = Stroke(width = stroke)
                    )
                }
        ) {
            val plan = rememberTablePlan(maxWidth, rows, totalText, isEnglish)
            Column(Modifier.fillMaxWidth()) {
                CardColumnBar(plan.widths, isEnglish)
                CardDataArea(
                    rows = rows,
                    totalText = totalText,
                    specHint = buildSpecHint(state, result, isEnglish),
                    plan = plan,
                    isEnglish = isEnglish,
                    editing = editing,
                    onEditRow = onEditRow,
                    onEditTotal = onEditTotal
                )
            }
        }

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
private fun CardColumnBar(colWidths: List<Dp>, isEnglish: Boolean = false) {
    val dividerColor = Color.White.copy(alpha = 0.4f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(30.dp)
            .background(CardGreen)
            // 竖线画在整行高度上，用同一套实测列宽 → 表头/数据永远对齐
            .drawBehind { drawTableColumns(dividerColor, colWidths) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        val headers = tableHeaders(isEnglish)
        headers.forEachIndexed { i, h -> CardHeaderText(h, colWidths[i]) }
    }
}

@Composable
private fun RowScope.CardHeaderText(text: String, width: Dp) {
    Text(
        text,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .width(width)
            .padding(horizontal = 2.dp)
    )
}

/**
 * 箱规提示行的文案：平铺展开尺寸 + 单重 + 总重。
 *
 * 尺寸用「平铺」口径——快递打包时飞机盒是拆开平铺叠放的，
 * 不能用成型后的长×宽×高；单张展开尺寸直接取计算结果里的
 * 1×1 刀模排版（result.layouts，源头是 CalculateQuoteUseCase.calculateLayouts，
 * 与 Web 端一致），不在此处另抄公式。
 * 单重 = 总重 / 数量（g/个），和 Web 端打印视图口径一致。
 */
private fun buildSpecHint(state: QuoteUiState, result: QuoteComputation, isEnglish: Boolean): String {
    val open1x1 = result.layouts[LayoutKey.OPEN_1X1]
    val dims = if (open1x1 != null) {
        "${trimNumber(open1x1.width)}×${trimNumber(open1x1.height)} cm"
    } else {
        "${trimNumber(state.form.length)}×${trimNumber(state.form.width)}×${trimNumber(state.form.height)} cm"
    }
    val totalKg = num(result.totalWeight, 2)
    if (state.form.orderQuantity <= 0) {
        return if (isEnglish) "Carton: $dims ｜ Total Wt: $totalKg kg"
                else "箱规：$dims ｜ 总重 ${totalKg}kg"
    }
    val unitG = num(result.totalWeight / state.form.orderQuantity * 1000, 0)
    return if (isEnglish) "Carton: $dims ｜ Wt/pc: $unitG g ｜ Total Wt: $totalKg kg"
            else "箱规：$dims ｜ 单重 ${unitG}g/个 ｜ 总重 ${totalKg}kg"
}

@Composable
private fun CardDataArea(
    rows: List<DocRow>,
    totalText: String,
    specHint: String,
    plan: TablePlan,
    isEnglish: Boolean = false,
    editing: Boolean = false,
    onEditRow: (Int, (DocRow) -> DocRow) -> Unit,
    onEditTotal: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
    ) {
        rows.forEachIndexed { i, row ->
            CardDataRow(
                index = i + 1,
                row = row,
                isAlt = (i + 1) % 2 == 1, // 第1、3…行浅绿，与原斑马纹一致
                colWidths = plan.widths,
                cellFont = plan.cellFont,
                editing = editing,
                onEdit = { transform -> onEditRow(i, transform) }
            )
        }

        // 合计行：标签合并前 5 列（不画内部竖线），金额锁定在金额列宽度内 —— 和 Excel 合并单元格一致
        val labelWidth = plan.widths.take(5).reduce { a, b -> a + b }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .background(CardTotalBg)
                .drawBehind { drawTableColumns(CellDivider, plan.widths, afterCols = setOf(4)) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(labelWidth)
                    .padding(end = 10.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Text(if (isEnglish) "Total: " else "合计金额：", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CardTextDark)
            }
            // 关键：金额必须锁死在金额列宽度内，否则会按内容撑宽、把栅格顶歪
            CellContent(
                editing = editing,
                value = totalText,
                onValueChange = onEditTotal,
                color = CardGreen,
                fontSize = plan.totalFont,
                fontWeight = FontWeight.Bold,
                singleLine = true,
                modifier = Modifier
                    .width(plan.widths[5])
                    .padding(horizontal = 6.dp)
            )
        }

        // 箱规提示行：紧跟合计金额下方，横跨整表的浅色小字（提示信息，不进列栅格、不可编辑）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CardSpecBg)
                .drawBehind {
                    val stroke = 0.5.dp.toPx()
                    drawLine(
                        color = Color(0xFFE0E0E0),
                        start = Offset(0f, stroke / 2),
                        end = Offset(size.width, stroke / 2),
                        strokeWidth = stroke
                    )
                }
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                specHint,
                fontSize = 9.sp,
                color = CardTextGray,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CardDataRow(
    index: Int,
    row: DocRow,
    isAlt: Boolean,
    colWidths: List<Dp>,
    cellFont: TextUnit,
    editing: Boolean = false,
    onEdit: (transform: (DocRow) -> DocRow) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isAlt) CardRowAlt else Color.White)
            // 行高由最高的单元格撑开（长材质名换行也不会断线），竖线/底线都画在整行上
            .drawBehind {
                drawTableColumns(CellDivider, colWidths)
                val stroke = 0.5.dp.toPx()
                drawLine(
                    color = Color(0xFFE0E0E0),
                    start = Offset(0f, size.height - stroke / 2),
                    end = Offset(size.width, size.height - stroke / 2),
                    strokeWidth = stroke
                )
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 序号是行号，不是数据，不可编辑
        CardDataCell(colWidths[0]) {
            Text("$index", fontSize = cellFont, fontWeight = FontWeight.Bold, color = CardTextDark, maxLines = 1)
        }
        CardDataCell(colWidths[1]) {
            CellContent(editing, row.name, { v -> onEdit { it.copy(name = v) } }, fontSize = cellFont, maxLines = 2)
        }
        CardDataCell(colWidths[2]) {
            CellContent(editing, row.spec, { v -> onEdit { it.copy(spec = v) } }, fontSize = cellFont, maxLines = 2)
        }
        CardDataCell(colWidths[3]) {
            CellContent(editing, row.quantity, { v -> onEdit { it.copy(quantity = v) } }, fontSize = cellFont, singleLine = true)
        }
        CardDataCell(colWidths[4]) {
            CellContent(editing, row.unitPrice, { v -> onEdit { it.copy(unitPrice = v) } }, fontSize = cellFont, singleLine = true)
        }
        CardDataCell(colWidths[5]) {
            CellContent(editing, row.amount, { v -> onEdit { it.copy(amount = v) } }, color = CardGreen, fontSize = cellFont, singleLine = true)
        }
    }
}

/** 数据行单元格：按实测列宽占位，内边距撑出统一行高，多行文字自动把整行撑高。 */
@Composable
private fun RowScope.CardDataCell(
    width: Dp,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .width(width)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

/**
 * 单元格内容：非编辑态是普通 Text；编辑态是浅黄底的 BasicTextField（Excel 选中单元格的感觉）。
 * 列名称/序号不走这里 → 天然不可编辑。
 */
@Composable
private fun CellContent(
    editing: Boolean,
    value: String,
    onValueChange: (String) -> Unit,
    color: Color = CardTextDark,
    fontSize: TextUnit = 10.sp,
    fontWeight: FontWeight = FontWeight.Normal,
    maxLines: Int = 2,
    singleLine: Boolean = false,
    modifier: Modifier = Modifier,
) {
    if (editing) {
        BasicTextField(
            value = value,
            onValueChange = { raw ->
                val v = if (singleLine) raw.replace("\n", "") else raw
                onValueChange(v.take(100))
            },
            textStyle = TextStyle(
                fontSize = fontSize,
                color = color,
                fontWeight = fontWeight,
                textAlign = TextAlign.Center
            ),
            cursorBrush = SolidColor(CardGreen),
            maxLines = if (singleLine) 1 else maxLines,
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0xFFFFF8E1))
                .padding(horizontal = 3.dp, vertical = 2.dp)
        )
    } else {
        Text(
            value,
            fontSize = fontSize,
            color = color,
            fontWeight = fontWeight,
            textAlign = TextAlign.Center,
            maxLines = if (singleLine) 1 else maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier
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
