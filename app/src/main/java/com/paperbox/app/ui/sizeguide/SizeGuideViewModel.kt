package com.paperbox.app.ui.sizeguide

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paperbox.app.data.api.ApiService
import com.paperbox.app.data.api.models.SpotProduct
import com.paperbox.app.domain.model.SpotMatch
import com.paperbox.app.domain.usecase.MatchSpotProductsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SizeGuideUiState(
    val products: List<SpotProduct> = emptyList(),
    val filteredProducts: List<SpotProduct> = emptyList(),
    val selectedCategory: String = "kraft",
    val categoryCounts: Map<String, Int> = emptyMap(),
    val searchQuery: String = "",
    val inputL: String = "",
    val inputW: String = "",
    val inputH: String = "",
    val matchedResults: List<SpotMatch> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class SizeGuideViewModel @Inject constructor(
    private val apiService: ApiService,
    private val matchSpotProducts: MatchSpotProductsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(SizeGuideUiState())
    val uiState: StateFlow<SizeGuideUiState> = _uiState.asStateFlow()

    init {
        loadProducts()
    }

    fun loadProducts() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                val response = apiService.getSpotProducts()
                if (response.isSuccessful) {
                    val products = response.body()!!
                    val counts = products.groupBy { it.category }.mapValues { it.value.size }
                    _uiState.value = _uiState.value.copy(
                        products = products,
                        filteredProducts = products.filter { it.category == "kraft" },
                        categoryCounts = counts,
                        isLoading = false
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "加载失败 (${response.code()})，请检查网络后重试"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "网络连接失败，请检查网络后重试"
                )
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

        _uiState.value = state.copy(
            matchedResults = matchSpotProducts.match(state.products, l, w, h)
        )
    }
}
