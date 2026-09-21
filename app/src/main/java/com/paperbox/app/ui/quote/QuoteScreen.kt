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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.hilt.navigation.compose.hiltViewModel
import com.paperbox.app.domain.model.LayoutKey
import com.paperbox.app.domain.model.MaterialKey

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuoteScreen(
    onGenerateQuote: () -> Unit,
    onSearchSuccess: () -> Unit,
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

    // 搜索成功后跳转结果页
    LaunchedEffect(state.searchReady) {
        if (state.searchReady) {
            viewModel.clearSearchReady()
            onSearchSuccess()
        }
    }

    // 「工艺」那一行的排版下拉。放在这里而不是 UiState ——
    // 一个纯粹的临时 UI 标志，塞进 state 会让整页跟着重组
    var layoutMenuExpanded by remember { mutableStateOf(false) }

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
                    onHeight = viewModel::updateHeight
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
                            val date = record.createdAt.take(10)
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
