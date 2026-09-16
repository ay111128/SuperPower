package com.paperbox.app.ui.quote

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.paperbox.app.domain.model.*
import com.paperbox.app.ui.theme.*

// ── 设计稿颜色 ──
private val BgGray = Color(0xFFF5F5F5)
private val SectionTitle = Color(0xFF1A1A1A)
private val LabelGray = Color(0xFF888888)
private val ValueDark = Color(0xFF1A1A1A)
private val UnitGray = Color(0xFFBBBBBB)
private val InputBorder = Color(0xFFEEEEEE)
private val SelectBorder = Color(0xFFE5E5E5)
private val SelectLabel = Color(0xFF333333)
private val SelectPlaceholder = Color(0xFFBBBBBB)
private val Green = Color(0xFF1B8A3E)
private val GreenShadow = Color(0x331B8A3E)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuoteScreen(viewModel: QuoteViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()

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
                        "报价",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    IconButton(
                        onClick = { viewModel.reset() },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "重置", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(BgGray)
                .verticalScroll(rememberScrollState())
                .padding(20.dp, 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // ── 📦 尺寸信息 ──
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "📦 尺寸信息",
                    color = SectionTitle,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DimensionInput(
                        label = "长",
                        value = if (state.form.length > 0) state.form.length.toString() else "",
                        unit = "cm",
                        onValueChange = { viewModel.updateLength(it) },
                        modifier = Modifier.weight(1f)
                    )
                    DimensionInput(
                        label = "宽",
                        value = if (state.form.width > 0) state.form.width.toString() else "",
                        unit = "cm",
                        onValueChange = { viewModel.updateWidth(it) },
                        modifier = Modifier.weight(1f)
                    )
                    DimensionInput(
                        label = "高",
                        value = if (state.form.height > 0) state.form.height.toString() else "",
                        unit = "cm",
                        onValueChange = { viewModel.updateHeight(it) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // ── 📊 生产信息 ──
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "📊 生产信息",
                    color = SectionTitle,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DimensionInput(
                        label = "数量",
                        value = if (state.form.orderQuantity > 0) state.form.orderQuantity.toString() else "",
                        unit = "个",
                        onValueChange = { viewModel.updateQuantity(it) },
                        keyboardType = KeyboardType.Number,
                        modifier = Modifier.weight(1f)
                    )
                    DimensionInput(
                        label = "利润",
                        value = if (state.form.profitPercentage > 0) state.form.profitPercentage.toString() else "",
                        unit = "%",
                        onValueChange = { viewModel.updateProfitPercentage(it) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // ── 🔧 材质与工艺 ──
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "🔧 材质与工艺",
                    color = SectionTitle,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MaterialDropdown(
                        label = "飞机盒材质",
                        options = state.materialConfigs.map { it.key to it.label },
                        selectedKey = state.form.materialKey,
                        onSelect = { viewModel.updateMaterialKey(it) },
                        modifier = Modifier.weight(1f)
                    )
                    LayoutDropdown(
                        label = "工艺",
                        options = LayoutKey.entries.map { it to it.label },
                        selectedKey = state.form.selectedLayout,
                        onSelect = { viewModel.updateLayout(it) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // ── 生成报价按钮 ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .shadow(12.dp, RoundedCornerShape(14.dp), ambientColor = GreenShadow, spotColor = GreenShadow)
                    .background(Green, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Button(
                    onClick = { viewModel.recalculate() },
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    elevation = null,
                    contentPadding = PaddingValues(0.dp),
                    enabled = state.form.length > 0 && state.form.width > 0 && state.form.height > 0 && state.form.orderQuantity > 0
                ) {
                    Text(
                        "生成报价",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // ── 报价结果 ──
            state.result?.let { result ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("报价结果", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        HorizontalDivider(color = InputBorder)

                        result.chargeLines.forEach { line ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(line.name, fontSize = 14.sp, color = Color(0xFF666666))
                                Text("¥${String.format("%.2f", line.amount)}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        HorizontalDivider(color = InputBorder)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("小计", fontSize = 14.sp, color = Color(0xFF666666))
                            Text("¥${String.format("%.2f", result.subtotal)}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("利润", fontSize = 14.sp, color = Color(0xFF666666))
                            Text("¥${String.format("%.2f", result.profitAmount)}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Green)
                        }

                        if (result.specialFeesSum > 0) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("特殊费用", fontSize = 14.sp, color = Color(0xFF666666))
                                Text("¥${String.format("%.2f", result.specialFeesSum)}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        HorizontalDivider(color = InputBorder)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("总价", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text(
                                "¥${String.format("%.2f", result.finalAmount)}",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Green
                            )
                        }

                        if (state.form.orderQuantity > 0) {
                            Text(
                                "单价 ¥${String.format("%.2f", result.finalAmount / state.form.orderQuantity)} | 总重 ${String.format("%.1f", result.totalWeight)}kg",
                                fontSize = 12.sp,
                                color = UnitGray
                            )
                        }

                        Spacer(Modifier.height(4.dp))

                        // 保存按钮
                        Button(
                            onClick = { viewModel.saveQuoteRecord() },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.isLoading,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            if (state.isLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                            } else {
                                Text("保存记录")
                            }
                        }

                        // 追溯码
                        state.traceCode?.let { code ->
                            Card(
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = BgGray)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("追溯码", fontSize = 12.sp, color = LabelGray)
                                        Text(code, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                    }
                                    IconButton(onClick = { viewModel.clearTraceCode() }) {
                                        Text("✕", fontSize = 18.sp, color = LabelGray)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 错误提示
            state.errorMessage?.let { error ->
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEAEA))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(error, color = Color(0xFFD32F2F), fontSize = 13.sp)
                        TextButton(onClick = { viewModel.clearError() }) {
                            Text("关闭")
                        }
                    }
                }
            }

            Spacer(Modifier.height(80.dp))
        }
    }
}

@Composable
private fun DimensionInput(
    label: String,
    value: String,
    unit: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Decimal,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = LabelGray, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(Color.White, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.foundation.text.BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = ValueDark
                    ),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    decorationBox = { innerTextField ->
                        Box {
                            if (value.isEmpty()) {
                                Text("0", fontSize = 18.sp, fontWeight = FontWeight.Medium, color = ValueDark)
                            }
                            innerTextField()
                        }
                    }
                )
                Text(unit, fontSize = 12.sp, color = UnitGray)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MaterialDropdown(
    label: String,
    options: List<Pair<MaterialKey, String>>,
    selectedKey: MaterialKey,
    onSelect: (MaterialKey) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.find { it.first == selectedKey }?.second ?: "请选择"

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = SelectLabel, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .background(Color.White, RoundedCornerShape(8.dp))
                    .menuAnchor()
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        selectedLabel,
                        fontSize = 14.sp,
                        color = if (selectedKey == options.firstOrNull()?.first) SelectPlaceholder else SelectLabel
                    )
                    Icon(
                        Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Color(0xFF999999)
                    )
                }
            }
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { (key, text) ->
                    DropdownMenuItem(
                        text = { Text(text) },
                        onClick = {
                            onSelect(key)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LayoutDropdown(
    label: String,
    options: List<Pair<LayoutKey, String>>,
    selectedKey: LayoutKey,
    onSelect: (LayoutKey) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.find { it.first == selectedKey }?.second ?: "请选择"

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = SelectLabel, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .background(Color.White, RoundedCornerShape(8.dp))
                    .menuAnchor()
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        selectedLabel,
                        fontSize = 14.sp,
                        color = SelectLabel
                    )
                    Icon(
                        Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Color(0xFF999999)
                    )
                }
            }
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { (key, text) ->
                    DropdownMenuItem(
                        text = { Text(text) },
                        onClick = {
                            onSelect(key)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
