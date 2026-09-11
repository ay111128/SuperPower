package com.paperbox.app.ui.sizeguide

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.paperbox.app.data.api.ApiService
import com.paperbox.app.data.api.models.SpotProduct
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SizeGuideUiState(
    val products: List<SpotProduct> = emptyList(),
    val filteredProducts: List<SpotProduct> = emptyList(),
    val selectedCategory: String = "all",
    val searchQuery: String = "",
    val inputL: String = "",
    val inputW: String = "",
    val inputH: String = "",
    val matchedResults: List<MatchResult> = emptyList(),
    val isLoading: Boolean = false
)

data class MatchResult(
    val product: SpotProduct,
    val score: Int,
    val totalDiff: Double,
    val exact: Boolean
)

@HiltViewModel
class SizeGuideViewModel @Inject constructor(
    private val apiService: ApiService
) : ViewModel() {

    private val _uiState = MutableStateFlow(SizeGuideUiState())
    val uiState: StateFlow<SizeGuideUiState> = _uiState.asStateFlow()

    init {
        loadProducts()
    }

    private fun loadProducts() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val response = apiService.getSpotProducts()
                if (response.isSuccessful) {
                    val products = response.body()!!
                    _uiState.value = _uiState.value.copy(
                        products = products,
                        filteredProducts = products,
                        isLoading = false
                    )
                }
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    fun selectCategory(category: String) {
        _uiState.value = _uiState.value.copy(selectedCategory = category)
        filterProducts()
    }

    fun updateSearch(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        filterProducts()
    }

    fun updateInput(l: String, w: String, h: String) {
        _uiState.value = _uiState.value.copy(inputL = l, inputW = w, inputH = h)
        matchSizes()
    }

    private fun filterProducts() {
        val state = _uiState.value
        var filtered = state.products
        if (state.selectedCategory != "all") {
            filtered = filtered.filter { it.category == state.selectedCategory }
        }
        if (state.searchQuery.isNotBlank()) {
            val q = state.searchQuery.lowercase()
            filtered = filtered.filter { it.size.lowercase().contains(q) }
        }
        _uiState.value = state.copy(filteredProducts = filtered)
    }

    private fun matchSizes() {
        val state = _uiState.value
        val l = state.inputL.toDoubleOrNull() ?: return
        val w = state.inputW.toDoubleOrNull() ?: return
        val h = state.inputH.toDoubleOrNull() ?: return

        val tolerance = 5.0
        val results = state.products.mapNotNull { product ->
            val parsed = parseSize(product.size) ?: return@mapNotNull null
            val diffL = kotlin.math.abs(parsed.first - l)
            val diffW = kotlin.math.abs(parsed.second - w)
            val diffH = kotlin.math.abs(parsed.third - h)
            val totalDiff = diffL + diffW + diffH

            if (totalDiff <= tolerance * 3) {
                val score = ((1.0 - totalDiff / (tolerance * 3)) * 100).toInt()
                MatchResult(product, score, totalDiff, totalDiff == 0.0)
            } else null
        }.sortedBy { it.totalDiff }

        _uiState.value = state.copy(matchedResults = results)
    }

    private fun parseSize(size: String): Triple<Double, Double, Double>? {
        val regex = Regex("""(\d+)x(\d+)x(\d+)""")
        val match = regex.find(size) ?: return null
        return Triple(match.groupValues[1].toDouble(), match.groupValues[2].toDouble(), match.groupValues[3].toDouble())
    }
}

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
            // 尺寸输入
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

            // 匹配结果
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

            // 分类筛选
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.selectedCategory == "all",
                    onClick = { viewModel.selectCategory("all") },
                    label = { Text("全部") }
                )
                FilterChip(
                    selected = state.selectedCategory == "kraft",
                    onClick = { viewModel.selectCategory("kraft") },
                    label = { Text("🟫 牛皮") }
                )
                FilterChip(
                    selected = state.selectedCategory == "white",
                    onClick = { viewModel.selectCategory("white") },
                    label = { Text("⬜ 白色") }
                )
                FilterChip(
                    selected = state.selectedCategory == "color",
                    onClick = { viewModel.selectCategory("color") },
                    label = { Text("🟨 彩色") }
                )
            }

            // 搜索
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = { viewModel.updateSearch(it) },
                label = { Text("搜索尺寸") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // 全部规格列表
            Text("全部现货规格 (${state.filteredProducts.size})", fontWeight = FontWeight.Bold)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(state.filteredProducts) { product ->
                    Card {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(product.size, fontWeight = FontWeight.Medium)
                                Text(
                                    "${product.category} | ${product.weight}g",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Text(
                                "¥${product.price}",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}
