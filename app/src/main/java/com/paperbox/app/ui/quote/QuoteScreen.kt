package com.paperbox.app.ui.quote

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.hilt.navigation.compose.hiltViewModel
import com.paperbox.app.domain.model.ChargeLine
import com.paperbox.app.domain.model.LayoutKey
import com.paperbox.app.domain.model.MaterialKey
import com.paperbox.app.domain.model.PricingMode
import com.paperbox.app.domain.model.ProfitMode
import com.paperbox.app.domain.model.QuoteFormValues
import com.paperbox.app.domain.model.SidedType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuoteScreen(
    onGenerateQuote: () -> Unit,
    onOpenSizeGuide: () -> Unit,
    viewModel: QuoteViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current

    // 搜索失败时弹 Toast
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.clearError()
        }
    }

    // 轻提示（如：载入工单配置后）弹 Toast
    LaunchedEffect(state.infoMessage) {
        state.infoMessage?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.clearInfoMessage()
        }
    }

    // 「工艺」那一行的排版下拉。放在这里而不是 UiState ——
    // 一个纯粹的临时 UI 标志，塞进 state 会让整页跟着重组
    var layoutMenuExpanded by remember { mutableStateOf(false) }

    // 3D 尺寸预览弹层 —— 低频功能，只在点「3D」时打开
    var showBoxPreview by remember { mutableStateOf(false) }

    LaunchedEffect(scrollState.isScrollInProgress) {
        // 滚动时菜单会脱锚，直接收起来
        if (scrollState.isScrollInProgress) layoutMenuExpanded = false
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(QuoteHeaderGreen)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 2.dp)
                        .heightIn(min = 62.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (state.isSearchActive) {
                        Spacer(Modifier.width(4.dp))
                        val searchFocusRequester = remember { FocusRequester() }
                        var searchIsFocused by remember { mutableStateOf(false) }
                        var searchFieldValue by remember(state.searchFieldText) {
                            mutableStateOf(TextFieldValue(text = state.searchFieldText, selection = TextRange(state.searchFieldText.length)))
                        }
                        LaunchedEffect(searchIsFocused) {
                            if (searchIsFocused && searchFieldValue.text.isNotEmpty()) {
                                searchFieldValue = searchFieldValue.copy(selection = TextRange(0, searchFieldValue.text.length))
                            }
                        }
                        BasicTextField(
                            value = searchFieldValue,
                            onValueChange = {
                                searchFieldValue = it
                                viewModel.updateSearchField(it.text)
                            },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .height(32.dp)
                                .background(Color.White, RoundedCornerShape(10.dp))
                                .border(1.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                                .focusRequester(searchFocusRequester)
                                .onFocusChanged { searchIsFocused = it.isFocused },
                            textStyle = androidx.compose.ui.text.TextStyle(
                                fontSize = 14.sp,
                                color = Color(0xFF333333)
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = {
                                focusManager.clearFocus()
                                viewModel.searchByTraceCode(searchFieldValue.text)
                            }),
                            decorationBox = { innerTextField ->
                                Box(
                                    contentAlignment = Alignment.CenterStart,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 1.dp)
                                ) {
                                    if (searchFieldValue.text.isEmpty()) {
                                        Text("输入工单编号…", color = Color(0xFF999999), fontSize = 14.sp)
                                    }
                                    innerTextField()
                                }
                            }
                        )
                        // 搜索按钮：点击即查询
                        IconButton(
                            onClick = {
                                focusManager.clearFocus()
                                viewModel.searchByTraceCode(searchFieldValue.text)
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = "搜索",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(
                            onClick = { viewModel.toggleSearch() },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "关闭搜索",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    } else {
                        Text(
                            "报价",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        // ── 表单实时摘要：标题右边，三行显示 ──
                        val hasAnyInput = state.lengthText.isNotEmpty() || state.widthText.isNotEmpty() ||
                                state.heightText.isNotEmpty() || state.quantityText.isNotEmpty()
                        if (hasAnyInput) {
                            Spacer(Modifier.width(14.dp))
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    modifier = Modifier.wrapContentWidth(),
                                    verticalArrangement = Arrangement.spacedBy(1.dp)
                                ) {
                                    // 第一行：规格
                                    val parts = mutableListOf<String>()
                                    if (state.lengthText.isNotEmpty()) parts.add(state.lengthText)
                                    if (state.widthText.isNotEmpty()) parts.add(state.widthText)
                                    if (state.heightText.isNotEmpty()) parts.add(state.heightText)
                                    if (parts.isNotEmpty()) {
                                        Text(
                                            text = "规格：${parts.joinToString(" × ")}",
                                            color = Color.White.copy(alpha = 0.85f),
                                            fontSize = 11.sp,
                                            lineHeight = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    // 第二行：数量
                                    if (state.quantityText.isNotEmpty()) {
                                        Text(
                                            text = "数量：${state.quantityText}",
                                            color = Color.White.copy(alpha = 0.85f),
                                            fontSize = 11.sp,
                                            lineHeight = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    // 第三行：单价
                                    val computation = state.result
                                    if (computation != null && state.form.orderQuantity > 0) {
                                        val unitPrice = computation.finalAmount / state.form.orderQuantity
                                        Text(
                                            text = "单价：¥${String.format("%.2f", unitPrice)}/个",
                                            color = Color.White.copy(alpha = 0.7f),
                                            fontSize = 11.sp,
                                            lineHeight = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        // 第四行：总计
                                        Text(
                                            text = "总计：¥${String.format("%.2f", computation.finalAmount)}",
                                            color = Color.White.copy(alpha = 0.7f),
                                            fontSize = 11.sp,
                                            lineHeight = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                        // ── 现货匹配摘要（右侧） ──
                        val hasDimensions = state.lengthText.isNotEmpty() || state.widthText.isNotEmpty() || state.heightText.isNotEmpty()
                        if (hasDimensions && state.spotCounts.isNotEmpty()) {
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    modifier = Modifier.wrapContentWidth(),
                                    verticalArrangement = Arrangement.spacedBy(1.dp)
                                ) {
                                    Text(
                                        text = "现货匹配：",
                                        color = Color.White.copy(alpha = 0.85f),
                                        fontSize = 11.sp,
                                        lineHeight = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    // 按分类显示匹配数量
                                    val spotLabels = listOf("kraft" to "牛皮色", "white" to "白色", "color" to "彩色")
                                    val matchedLines = spotLabels.mapNotNull { (key, label) ->
                                        val count = state.spotCounts[key] ?: 0
                                        if (count > 0) "${label}（${count}）" else null
                                    }
                                    matchedLines.take(3).forEach { line ->
                                        Text(
                                            text = line,
                                            color = Color.White.copy(alpha = 0.85f),
                                            fontSize = 11.sp,
                                            lineHeight = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                        IconButton(
                            onClick = { viewModel.toggleSearch() },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = "查询工单",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        // 表单和 FAB 放在同一个 Box 里，表单 fillMaxSize 白色背景覆盖整片区域
        // FAB 浮在右下角，不需要自己的背景
        val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(bottom = (imeBottom - 97.dp).coerceAtLeast(0.dp))
        ) {
            // 表单区：填满整个 Box，白色背景
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 14.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                QuoteDimensionSection(
                    state = state,
                    onLength = viewModel::updateLength,
                    onWidth = viewModel::updateWidth,
                    onHeight = viewModel::updateHeight,
                    onOpen3D = {
                        if (state.form.length > 0 && state.form.width > 0 && state.form.height > 0) {
                            focusManager.clearFocus()
                            showBoxPreview = true
                        } else {
                            android.widget.Toast.makeText(
                                context, "请先输入长宽高尺寸", android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                )

                QuoteProductionSection(
                    state = state,
                    onQuantity = viewModel::updateQuantity,
                    onProfit = viewModel::updateProfitPercentage
                )

                // ── 材质 / 工艺 / 附加费 ──
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    QuoteMaterialSection(state = state, onSelect = viewModel::updateMaterialKey)

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(QuoteTabIdleBg)
                            .padding(horizontal = 2.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        QuoteProcessSummaryRow(
                            state = state,
                            expanded = layoutMenuExpanded,
                            onExpandedChange = { layoutMenuExpanded = it },
                            onSelectLayout = { layout: LayoutKey -> viewModel.updateLayout(layout) }
                        )
                        QuoteProcessGroups(state = state, vm = viewModel)
                    }

                    QuoteSpecialFeeSection(state = state, vm = viewModel)
                }

                SpotMatchCard(
                    state = state,
                    onToggleEnabled = viewModel::setSpotMatchEnabled,
                    onToleranceChange = viewModel::setSpotTolerance,
                    onToleranceCommit = viewModel::commitSpotTolerance,
                    onSelectCategory = viewModel::selectSpotCategory,
                    onOpenSizeGuide = onOpenSizeGuide,
                    onSelectSpot = viewModel::selectSpotProduct
                )
            }

            // FAB 浮在表单右下角，表单的白色背景自然透过来
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(end = 16.dp, bottom = 16.dp),
                contentAlignment = Alignment.BottomEnd
            ) {
                QuoteGenerateFab(
                    enabled = state.result != null,
                    onClick = onGenerateQuote
                )
            }

            // ── 历史搜索记录（搜索态且输入为空时展示） ──
            if (state.isSearchActive && state.searchFieldText.isBlank() && state.searchHistory.isNotEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Surface(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White,
                        shadowElevation = 6.dp
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("最近搜索", fontSize = 12.sp, color = Color(0xFF999999))
                                Spacer(Modifier.weight(1f))
                                Text(
                                    "清空",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = QuoteGreen,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable { viewModel.clearSearchHistory() }
                                        .padding(horizontal = 6.dp, vertical = 4.dp)
                                )
                            }
                            state.searchHistory.forEach { query ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { viewModel.searchByTraceCode(query) }
                                        .padding(vertical = 8.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Search,
                                        contentDescription = null,
                                        tint = Color(0xFFBBBBBB),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        query,
                                        fontSize = 13.sp,
                                        color = Color(0xFF333333),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── 搜索结果列表（多条时弹出） ──
            if (state.searchResults.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f))
                        .clickable { viewModel.clearSearchResults() },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .background(Color.White, RoundedCornerShape(16.dp))
                            .clickable { /* 阻止穿透 */ }
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "找到 ${state.searchResults.size} 条记录",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = QuoteTitle
                        )
                        state.searchResults.forEach { record ->
                            val mk = record.materialKey?.let { MaterialKey.fromApiKey(it)?.label } ?: ""
                            val tc = record.traceCode
                            val price = if (record.finalAmount != null) "¥${String.format("%.2f", record.finalAmount)}" else ""
                            val dim = "${trimNumber(record.length)}×${trimNumber(record.width)}×${trimNumber(record.height)}cm"
                            val qty = "${record.quantity}个"
                            val date = formatLocalDateTime(record.createdAt).take(10)
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(QuoteRowBg)
                                    .clickable { viewModel.selectSearchResult(record) }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        tc,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = QuoteGreen
                                    )
                                    Text(
                                        price,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = QuotePrice
                                    )
                                }
                                Text(
                                    "$dim  $qty  $mk  $date",
                                    fontSize = 11.sp,
                                    color = QuoteGray66
                                )
                            }
                        }
                    }
                }
            }

            // ── 工单计费详情弹窗（查询/搜索命中后打开） ──
            state.searchDetail?.let { detail ->
                SearchDetailDialog(
                    detail = detail,
                    onClose = viewModel::closeSearchDetail,
                    onLoad = viewModel::confirmSearchDetailLoad,
                )
            }

            // ── 3D 尺寸预览弹层 ──
            // 注：Compose 不允许 try/catch 包组合调用；组合阶段崩溃走全局 handler
            // 落盘 → 下次冷启动 CrashDiagnostics 自动上传
            if (showBoxPreview) {
                BoxPreviewSheet(
                    state = state,
                    onLength = viewModel::updateLength,
                    onWidth = viewModel::updateWidth,
                    onHeight = viewModel::updateHeight,
                    onDismiss = { showBoxPreview = false }
                )
            }
        }
    }
}

/**
 * 浮动「生成报价」按钮。
 * 不用 M3 的 Button —— 它自带 defaultMinSize，会把 42dp 撑高，
 * 禁用态还会变灰而不是变淡绿。
 */
@Composable
private fun QuoteGenerateFab(enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(width = 74.dp, height = 42.dp)
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(24.dp),
                ambientColor = QuoteFabShadow,
                spotColor = QuoteFabShadow
            )
            .background(
                color = if (enabled) QuoteGreen else QuoteGreen.copy(alpha = 0.38f),
                shape = RoundedCornerShape(24.dp)
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "生成报价",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

// ─────────────────────────────────────────────────────────────
// 工单计费详情弹窗（与 Web 端 QuoteRecordSearchDialog 对齐）
// ─────────────────────────────────────────────────────────────

private fun fmtMoney(value: Double): String = String.format("%.2f", value)

/**
 * 弹窗里的金额强调色（工艺分组求和/单项金额、旧记录费用构成）。
 * 「记录合计」条上那支琥珀 #FBBF24 是配黑底设计的，压到白底会发虚看不清，
 * 白底改用同色系深一档的橘黄 #D97706（与 Web 端金额用的 amber-600 一致）。
 */
private val AmountAmber = Color(0xFFD97706)

/**
 * ISO(UTC) 时间串 → 本地时区 'yyyy-MM-dd HH:mm:ss'。
 * created_at 是服务端 toISOString() 存的 UTC，直接截串显示会差 8 小时（跨天还会错日期）。
 */
private fun formatLocalDateTime(iso: String): String = try {
    java.time.LocalDateTime.ofInstant(
        java.time.Instant.parse(iso),
        java.time.ZoneId.systemDefault()
    ).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
} catch (_: Exception) {
    iso
}

/** 按分取整：金额显示与求和表达式共用，保证「分项相加 = 显示合计」不会差 0.01 */
private fun round2(value: Double): Double = Math.round(value * 100.0) / 100.0

/** 金额 → 展示串：按分取整并去尾零（200 → "200"，752.72 → "752.72"），与 Web 端 formatMoney 口径一致 */
private fun fmtAmount(value: Double): String = trimNumber(round2(value))

/**
 * 按报价页的两个分组竖排生成树形文本；无任何勾选返回 null。
 * 分组标题后追加求和表达式（200+200+300+45=745元），每个选项后追加实际计费金额（200元）——
 * 金额取自快照重算的明细行 [lines]（工厂加价不在明细行里，直接用表单值），与 Web 端 QuoteRecordSearchDialog 对齐。
 */
private fun buildProcessTree(form: QuoteFormValues, lines: List<ChargeLine>): AnnotatedString? {
    val p = form.processes
    fun mark(s: SidedType) = if (s == SidedType.DOUBLE) " ×2" else ""
    // 明细行里的名字：模切费 / 刀模费 / 杂费 / 满印油墨 / 覆膜 / 裱纸 / 印刷费 / 丝印费（已含单双面倍数）
    fun amt(name: String) = lines.firstOrNull { it.name == name }?.amount ?: 0.0

    val basic = buildList {
        if (p.dieCutEnabled) add("模切费" to amt("模切费"))
        if (p.toolingEnabled) add("刀模费" to amt("刀模费"))
        if (form.extraFeeEnabled) add("工厂加价" to form.extraFee)
        if (p.miscEnabled) add("杂费/个" to amt("杂费"))
    }
    val print = buildList {
        if (p.fullPrintEnabled) add(("满印油墨" + mark(p.fullPrintSided)) to amt("满印油墨"))
        if (p.laminationEnabled) add(("覆膜" + mark(p.laminationSided)) to amt("覆膜"))
        if (p.mountingEnabled) add(("裱纸" + mark(p.mountingSided)) to amt("裱纸"))
        if (p.printingEnabled) add("印刷费" to amt("印刷费"))
        if (p.screenPrintEnabled) add("丝印费" to amt("丝印费"))
    }
    val groups = listOf("基础选项" to basic, "印刷定制" to print).filter { it.second.isNotEmpty() }
    if (groups.isEmpty()) return null
    return buildAnnotatedString {
        groups.forEachIndexed { gi, (title, items) ->
            if (gi > 0) append("\n")
            append(if (gi == groups.size - 1) "└─ " else "├─ ")
            append(title)
            // 分组标题后的求和表达式：各项按分取整后相加，合计 = 取整后的分项之和
            val total = items.sumOf { round2(it.second) }
            pushStyle(SpanStyle(color = AmountAmber, fontWeight = FontWeight.SemiBold))
            append(" ${items.joinToString("+") { (_, amount) -> fmtAmount(amount) }}=${fmtAmount(total)}元")
            pop()
            items.forEachIndexed { ii, (label, amount) ->
                append("\n  ")
                append(if (ii == items.size - 1) "└─ " else "├─ ")
                append(label)
                pushStyle(SpanStyle(color = AmountAmber, fontWeight = FontWeight.SemiBold))
                append(" ${fmtAmount(amount)}元")
                pop()
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, valueColor: Color = Color(0xFF333333)) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, fontSize = 13.sp, color = Color(0xFF999999))
        Text(
            value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = valueColor,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f).padding(start = 16.dp)
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = Color(0xFF666666),
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
    )
}

@Composable
private fun DetailLineRow(line: ChargeLine) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(line.name, fontSize = 13.sp, color = Color(0xFF333333))
            if (line.detail.isNotBlank()) {
                Text(line.detail, fontSize = 10.sp, color = Color(0xFF999999))
            }
        }
        Text(
            "¥${fmtMoney(line.amount)}",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = AmountAmber
        )
    }
}

@Composable
private fun SearchDetailDialog(
    detail: SearchDetail,
    onClose: () -> Unit,
    onLoad: () -> Unit,
) {
    val record = detail.record
    val form = detail.form
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = Color.White,
        title = {
            // 去掉「工单计费详情」标题：工单号排最左，时间提到同一行右对齐
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 工单号黑底与底部「记录合计」条同色（QuoteTitle），文字用它的琥珀色，首尾呼应
                Text(
                    record.traceCode,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFBBF24),
                    modifier = Modifier
                        .background(QuoteTitle, RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 3.dp)
                )
                Spacer(Modifier.weight(1f))
                Text(
                    formatLocalDateTime(record.createdAt),
                    fontSize = 11.sp,
                    color = Color(0xFF999999)
                )
            }
        },
        text = {
            // 顺序关键：heightIn 必须在 verticalScroll 外层（限制视口），
            // 反过来会把内容硬裁到 460dp 且滚动距离为 0，超出部分滚不出来
            Column(
                modifier = Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // 时间已上移到标题行（与工单号同行）
                Spacer(Modifier.height(4.dp))

                // 尺寸 / 数量各一个浅底圆角标签，代替原来的字段名
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${trimNumber(record.length)}×${trimNumber(record.width)}×${trimNumber(record.height)} CM",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = QuoteGray66,
                        modifier = Modifier
                            .background(QuoteTabIdleBg, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                    Text(
                        "${record.quantity}个",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = QuoteGray66,
                        modifier = Modifier
                            .background(QuoteTabIdleBg, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                // 利润排在尺寸/数量下面，金额在前、口径放括号里（记录落库值优先）
                val profitText = when {
                    form == null -> "利润 ${fmtMoney(record.profitAmount ?: 0.0)} 元(口径未存)"
                    form.profitMode == ProfitMode.AMOUNT ->
                        "利润 ${fmtMoney(record.profitAmount ?: form.profitAmount)} 元(金额)"
                    else ->
                        "利润 ${fmtMoney(record.profitAmount ?: 0.0)} 元(${trimNumber(form.profitPercentage)}%)"
                }
                Text(
                    profitText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = QuoteGreen,
                    modifier = Modifier.padding(top = 2.dp)
                )

                // 纸材 / 现货 / 工艺 / 附加费：同属配置摘要，包进一个浅底圆角块（与 Web 端一致）
                Spacer(Modifier.height(6.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(QuoteRowBg, RoundedCornerShape(12.dp))
                        .border(1.dp, QuoteCardStroke, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    val paperText = if (form != null) {
                        val unitPrice = form.materialUnitPrices[form.materialKey]
                            ?: (record.materialUnitPrice ?: 0.0)
                        val base = "${form.materialKey.label} · ${fmtMoney(unitPrice)} 元/方"
                        // 单价后追加「总平方 · 材料费」；现货单的材料行叫「现货价格」不计材料费，故不追加（与 Web 端一致）
                        val materialLine = detail.lines.firstOrNull { it.name == "材料费" }
                        val areaM2 = detail.areaM2
                        if (materialLine != null && areaM2 != null) {
                            val totalArea = trimNumber(round2(areaM2 * form.orderQuantity))
                            "$base · $totalArea 平方 · ${fmtMoney(materialLine.amount)} 元"
                        } else {
                            base
                        }
                    } else {
                        record.materialLabel ?: "—"
                    }
                    DetailRow("纸材", paperText)

                    detail.spot?.let { spot ->
                        DetailRow("现货", "${spot.category} ${spot.size} · ¥${fmtMoney(spot.price)}/个")
                    }

                    SectionLabel("工艺")
                    val tree = form?.let { buildProcessTree(it, detail.lines) }
                    if (tree != null) {
                        Text(
                            tree,
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            color = Color(0xFF333333)
                        )
                    } else {
                        // 区分「旧记录没快照」和「有快照但一个工艺都没勾」
                        Text(
                            if (form == null) "旧记录未存工艺配置" else "未勾选工艺",
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            color = Color(0xFF999999)
                        )
                    }

                    SectionLabel("附加费")
                    when {
                        form != null -> {
                            val active = form.specialFees.filter { it.enabled }
                            if (active.isEmpty()) {
                                Text("无", fontSize = 13.sp, color = Color(0xFF999999))
                            } else {
                                active.forEach { fee ->
                                    val amount = if (fee.pricingMode == PricingMode.UNIT_PRICE) {
                                        fee.unitPrice * form.orderQuantity
                                    } else {
                                        fee.amount
                                    }
                                    val spec = if (fee.spec.isNotEmpty()) "（${fee.spec}）" else ""
                                    Text(
                                        "${fee.name}$spec ¥${fmtMoney(amount)} 元",
                                        fontSize = 13.sp,
                                        color = Color(0xFF333333),
                                        modifier = Modifier.padding(vertical = 1.dp)
                                    )
                                }
                            }
                        }
                        (record.specialFeesCost ?: 0.0) > 0 -> Text(
                            "合计 ¥${fmtMoney(record.specialFeesCost ?: 0.0)} 元（明细未存）",
                            fontSize = 13.sp,
                            color = Color(0xFF333333)
                        )
                        else -> Text("无", fontSize = 13.sp, color = Color(0xFF999999))
                    }
                }

                // 逐项「计费明细」表已去掉：配置摘要每项已带金额，底部有记录合计兜底。
                // 旧记录没有快照，只保留聚合的费用构成作兜底。
                if (form == null) {
                    SectionLabel("费用构成")
                    DetailLineRow(
                        ChargeLine(
                            if (detail.spot != null) "现货成本" else "材料成本",
                            record.materialCost ?: 0.0
                        )
                    )
                    DetailLineRow(ChargeLine("其他费用", record.processCost ?: 0.0))
                    Text(
                        "旧记录未存明细快照，仅展示落库聚合值。",
                        fontSize = 10.sp,
                        color = Color(0xFF999999),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                if (detail.drift) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "按当前费率重算与记录合计不一致（价目有变动），以记录为准。",
                        fontSize = 11.sp,
                        color = Color(0xFFB45309),
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFFFF7ED), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }

                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(QuoteTitle, RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("记录合计", fontSize = 11.sp, color = Color(0xFFB0B8C4))
                        Text(
                            "¥${fmtMoney(record.finalAmount ?: 0.0)}",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFBBF24)
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        if (record.unitPrice != null) {
                            Text(
                                "单价 ¥${fmtMoney(record.unitPrice)}/个",
                                fontSize = 11.sp,
                                color = Color(0xFFB0B8C4)
                            )
                        }
                        if (record.totalWeight != null && record.totalWeight > 0) {
                            Text(
                                "总重 ${fmtMoney(record.totalWeight)} kg",
                                fontSize = 11.sp,
                                color = Color(0xFFB0B8C4)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onLoad) {
                Text("载入编辑", fontWeight = FontWeight.Bold, color = QuoteGreen)
            }
        },
        dismissButton = {
            TextButton(onClick = onClose) {
                Text("关闭", color = Color(0xFF999999))
            }
        }
    )
}
