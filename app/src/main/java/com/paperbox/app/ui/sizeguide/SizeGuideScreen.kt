package com.paperbox.app.ui.sizeguide

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SizeGuideScreen(viewModel: SizeGuideViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("尺寸规格") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("输入尺寸匹配", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = state.inputL,
                            onValueChange = { l -> viewModel.updateInput(l, state.inputW, state.inputH) },
                            label = { Text("长") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = state.inputW,
                            onValueChange = { w -> viewModel.updateInput(state.inputL, w, state.inputH) },
                            label = { Text("宽") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = state.inputH,
                            onValueChange = { h -> viewModel.updateInput(state.inputL, state.inputW, h) },
                            label = { Text("高") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                }
            }

            if (state.matchedResults.isNotEmpty()) {
                Text("匹配结果 (${state.matchedResults.size})", fontWeight = FontWeight.Bold)
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.matchedResults) { result ->
                        Card {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(result.product.size, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "¥${result.product.price}",
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    "${result.product.category} | ${result.product.weight}g | 匹配度 ${result.score}%",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = state.selectedCategory == "all", onClick = { viewModel.selectCategory("all") }, label = { Text("全部") })
                FilterChip(selected = state.selectedCategory == "kraft", onClick = { viewModel.selectCategory("kraft") }, label = { Text("🟫 牛皮") })
                FilterChip(selected = state.selectedCategory == "white", onClick = { viewModel.selectCategory("white") }, label = { Text("⬜ 白色") })
                FilterChip(selected = state.selectedCategory == "color", onClick = { viewModel.selectCategory("color") }, label = { Text("🟨 彩色") })
            }

            Text("全部现货规格 (${state.filteredProducts.size})", fontWeight = FontWeight.Bold)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(state.filteredProducts) { product ->
                    Card {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(product.size, fontWeight = FontWeight.Medium)
                                Text("${product.category} | ${product.weight}g", style = MaterialTheme.typography.bodySmall)
                            }
                            Text("¥${product.price}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}
