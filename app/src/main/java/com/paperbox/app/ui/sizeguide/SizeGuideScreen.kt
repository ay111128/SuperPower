package com.paperbox.app.ui.sizeguide

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

// ── 设计稿颜色 ──
private val BgGray = Color(0xFFF5F5F5)
private val Green = Color(0xFF1B8A3E)
private val InputBg = Color(0xFFFFFFFF)
private val InputBorder = Color(0xFFE5E5E5)
private val PlaceholderGray = Color(0xFF999999)
private val SectionTitle = Color(0xFF333333)
private val UnitInactive = Color(0xFFF0F0F0)
private val UnitInactiveText = Color(0xFF666666)
private val ResultText = Color(0xFF333333)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SizeGuideScreen(viewModel: SizeGuideViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val categories = listOf("kraft" to "牛皮色", "white" to "白色", "color" to "彩色")

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF007A12))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .height(62.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "规格",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(BgGray)
        ) {
            // ── 分类 tabs ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEach { (key, label) ->
                    val count = state.categoryCounts[key] ?: 0
                    val selected = state.selectedCategory == key
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .background(
                                if (selected) Green else Color(0xFFF0F0F0),
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { viewModel.selectCategory(key) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "$label（$count）",
                            fontSize = 13.sp,
                            color = if (selected) Color.White else Color(0xFF333333),
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }

            // ── 内容区 ──
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ── 输入尺寸 ──
                Text(
                    "输入尺寸",
                    color = SectionTitle,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )

                // 三个输入框
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SizeInput(
                        placeholder = "长",
                        value = state.inputL,
                        onValueChange = { l -> viewModel.updateInput(l, state.inputW, state.inputH) },
                        modifier = Modifier.weight(1f)
                    )
                    SizeInput(
                        placeholder = "宽",
                        value = state.inputW,
                        onValueChange = { w -> viewModel.updateInput(state.inputL, w, state.inputH) },
                        modifier = Modifier.weight(1f)
                    )
                    SizeInput(
                        placeholder = "高",
                        value = state.inputH,
                        onValueChange = { h -> viewModel.updateInput(state.inputL, state.inputW, h) },
                        modifier = Modifier.weight(1f)
                    )
                }

                // ── 匹配结果 or 全部规格 ──
                if (state.matchedResults.isNotEmpty()) {
                    Text(
                        "匹配结果",
                        color = SectionTitle,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(state.matchedResults) { result ->
                            Card(
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = InputBg),
                                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(56.dp)
                                        .padding(horizontal = 14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "${result.product.size} - ${result.product.category}",
                                        fontSize = 13.sp,
                                        color = ResultText
                                    )
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = "选中",
                                        tint = Green,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                } else if (state.filteredProducts.isNotEmpty()) {
                    Text(
                        "全部规格",
                        color = SectionTitle,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(state.filteredProducts) { product ->
                            Card(
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(containerColor = InputBg),
                                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .padding(horizontal = 14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        product.size,
                                        fontSize = 13.sp,
                                        color = ResultText
                                    )
                                    Text(
                                        "¥${String.format("%.2f", product.price)}",
                                        fontSize = 13.sp,
                                        color = Green,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SizeInput(
    placeholder: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(44.dp)
            .background(InputBg, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.text.BasicTextField(
            value = value,
            onValueChange = onValueChange,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            textStyle = androidx.compose.ui.text.TextStyle(
                fontSize = 14.sp,
                color = ResultText,
                textAlign = TextAlign.Center
            ),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.Center) {
                    if (value.isEmpty()) {
                        Text(placeholder, fontSize = 13.sp, color = PlaceholderGray)
                    }
                    innerTextField()
                }
            }
        )
    }
}
