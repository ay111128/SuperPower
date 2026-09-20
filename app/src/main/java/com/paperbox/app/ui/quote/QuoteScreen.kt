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
import androidx.hilt.navigation.compose.hiltViewModel
import com.paperbox.app.domain.model.LayoutKey

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
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .height(62.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (state.isSearchActive) {
                        Spacer(Modifier.width(4.dp))
                        BasicTextField(
                            value = state.searchFieldText,
                            onValueChange = { viewModel.updateSearchField(it) },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .height(32.dp)
                                .background(Color.White, RoundedCornerShape(10.dp))
                                .border(1.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(10.dp)),
                            textStyle = androidx.compose.ui.text.TextStyle(
                                fontSize = 14.sp,
                                color = Color(0xFF333333)
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = {
                                focusManager.clearFocus()
                                viewModel.searchByTraceCode(state.searchFieldText)
                            }),
                            decorationBox = { innerTextField ->
                                Box(
                                    contentAlignment = Alignment.CenterStart,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 1.dp)
                                ) {
                                    if (state.searchFieldText.isEmpty()) {
                                        Text("输入工单编号…", color = Color(0xFF999999), fontSize = 14.sp)
                                    }
                                    innerTextField()
                                }
                            }
                        )
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
                        Spacer(Modifier.weight(1f))
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
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
        ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .verticalScroll(scrollState)
                // 设计稿的 Form Area 是 padding=[20,16]，在 pen 里是「上下 20、左右 16」
                .padding(horizontal = 16.dp, vertical = 20.dp),
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

                QuoteProcessSummaryRow(
                    state = state,
                    expanded = layoutMenuExpanded,
                    onExpandedChange = { layoutMenuExpanded = it },
                    onSelectLayout = { layout: LayoutKey -> viewModel.updateLayout(layout) }
                )

                QuoteProcessGroups(state = state, vm = viewModel)

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

            // 给浮动按钮留出位置
            Spacer(Modifier.height(96.dp))
        }

            // 悬浮按钮放在 Box 里，imePadding 让它跟着键盘上移
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 24.dp)
            ) {
                QuoteGenerateFab(
                    enabled = state.result != null,
                    onClick = onGenerateQuote
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
