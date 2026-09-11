package com.paperbox.app.ui.quote

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.paperbox.app.domain.model.*
import com.paperbox.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuoteScreen(viewModel: QuoteViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("飞机盒报价") },
                actions = {
                    IconButton(onClick = { viewModel.reset() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "重置")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── 尺寸输入 ──
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("尺寸 (cm)", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = if (state.form.length > 0) state.form.length.toString() else "",
                            onValueChange = { viewModel.updateLength(it) },
                            label = { Text("长 L") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = if (state.form.width > 0) state.form.width.toString() else "",
                            onValueChange = { viewModel.updateWidth(it) },
                            label = { Text("宽 W") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = if (state.form.height > 0) state.form.height.toString() else "",
                            onValueChange = { viewModel.updateHeight(it) },
                            label = { Text("高 H") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = if (state.form.orderQuantity > 0) state.form.orderQuantity.toString() else "",
                        onValueChange = { viewModel.updateQuantity(it) },
                        label = { Text("数量") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }

            // ── 材质选择 ──
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("材质", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    state.materialConfigs.forEach { config ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = state.form.materialKey == config.key,
                                onClick = { viewModel.updateMaterialKey(config.key) }
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(config.label, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "${config.unitPrice}元/m²",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }
            }

            // ── 排版选择 ──
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("排版", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LayoutKey.entries.forEach { key ->
                            FilterChip(
                                selected = state.form.selectedLayout == key,
                                onClick = { viewModel.updateLayout(key) },
                                label = { Text(key.label) }
                            )
                        }
                    }
                }
            }

            // ── 利润设置 ──
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("利润", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = state.form.profitMode == ProfitMode.PERCENTAGE,
                            onClick = { viewModel.updateProfitMode(ProfitMode.PERCENTAGE) },
                            label = { Text("百分比 %") }
                        )
                        FilterChip(
                            selected = state.form.profitMode == ProfitMode.AMOUNT,
                            onClick = { viewModel.updateProfitMode(ProfitMode.AMOUNT) },
                            label = { Text("金额 ¥") }
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    when (state.form.profitMode) {
                        ProfitMode.PERCENTAGE -> {
                            OutlinedTextField(
                                value = if (state.form.profitPercentage > 0) state.form.profitPercentage.toString() else "",
                                onValueChange = { viewModel.updateProfitPercentage(it) },
                                label = { Text("利润率 %") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        }
                        ProfitMode.AMOUNT -> {
                            OutlinedTextField(
                                value = if (state.form.profitAmount > 0) state.form.profitAmount.toString() else "",
                                onValueChange = { viewModel.updateProfitAmount(it) },
                                label = { Text("利润金额 ¥") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        }
                    }
                }
            }

            // ── 计算按钮 ──
            Button(
                onClick = { viewModel.recalculate() },
                modifier = Modifier.fillMaxWidth(),
                enabled = state.form.length > 0 && state.form.width > 0 && state.form.height > 0 && state.form.orderQuantity > 0
            ) {
                Icon(Icons.Default.Calculate, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("计算报价")
            }

            // ── 报价结果 ──
            state.result?.let { result ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("报价结果", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))

                        result.chargeLines.forEach { line ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(line.name, style = MaterialTheme.typography.bodyMedium)
                                Text("¥${String.format("%.2f", line.amount)}", style = MaterialTheme.typography.bodyMedium)
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("小计", style = MaterialTheme.typography.bodyMedium)
                            Text("¥${String.format("%.2f", result.subtotal)}", style = MaterialTheme.typography.bodyMedium)
                        }

                        if (state.form.extraFeeEnabled) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("附加费", style = MaterialTheme.typography.bodyMedium)
                                Text("¥${String.format("%.2f", state.form.extraFee)}", style = MaterialTheme.typography.bodyMedium)
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("利润", style = MaterialTheme.typography.bodyMedium)
                            Text("¥${String.format("%.2f", result.profitAmount)}", style = MaterialTheme.typography.bodyMedium)
                        }

                        if (result.specialFeesSum > 0) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("特殊费用", style = MaterialTheme.typography.bodyMedium)
                                Text("¥${String.format("%.2f", result.specialFeesSum)}", style = MaterialTheme.typography.bodyMedium)
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("总价", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(
                                "¥${String.format("%.2f", result.finalAmount)}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        if (state.form.orderQuantity > 0) {
                            Text(
                                "单价 ¥${String.format("%.2f", result.finalAmount / state.form.orderQuantity)} | 总重 ${String.format("%.1f", result.totalWeight)}kg",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }

                        Spacer(Modifier.height(12.dp))

                        // 保存按钮
                        Button(
                            onClick = { viewModel.saveQuoteRecord() },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.isLoading
                        ) {
                            if (state.isLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                            } else {
                                Icon(Icons.Default.Save, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("保存记录")
                            }
                        }

                        // 追溯码
                        state.traceCode?.let { code ->
                            Spacer(Modifier.height(8.dp))
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("追溯码", style = MaterialTheme.typography.labelMedium)
                                        Text(code, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                    }
                                    IconButton(onClick = { viewModel.clearTraceCode() }) {
                                        Icon(Icons.Default.Close, contentDescription = "关闭")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 错误提示
            state.errorMessage?.let { error ->
                Snackbar(
                    action = {
                        TextButton(onClick = { viewModel.clearError() }) {
                            Text("关闭")
                        }
                    }
                ) {
                    Text(error)
                }
            }

            Spacer(Modifier.height(80.dp)) // 底部留白给导航栏
        }
    }
}
