package com.paperbox.app.ui.quote

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paperbox.app.data.api.ApiService
import com.paperbox.app.data.api.models.QuoteRecordRequest
import com.paperbox.app.domain.model.*
import com.paperbox.app.domain.usecase.CalculateQuoteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class QuoteUiState(
    val form: QuoteFormValues = QuoteFormValues(),
    val result: QuoteComputation? = null,
    val materialConfigs: List<MaterialConfig> = CalculateQuoteUseCase.DEFAULT_MATERIALS,
    val spotProducts: List<com.paperbox.app.data.api.models.SpotProduct> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val traceCode: String? = null
)

@HiltViewModel
class QuoteViewModel @Inject constructor(
    private val calculateQuote: CalculateQuoteUseCase,
    private val apiService: ApiService
) : ViewModel() {

    private val _uiState = MutableStateFlow(QuoteUiState())
    val uiState: StateFlow<QuoteUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            try {
                // 加载材质配置
                val configs = apiService.getMaterialConfigs()
                if (configs.isSuccessful) {
                    val materials = configs.body()!!.map { c ->
                        val key = when (c.key) {
                            "kraftSmall" -> MaterialKey.KRAFT_SMALL
                            "kraftLarge" -> MaterialKey.KRAFT_LARGE
                            "whiteCard" -> MaterialKey.WHITE_CARD
                            "whiteKraft" -> MaterialKey.WHITE_KRAFT
                            else -> MaterialKey.KRAFT_SMALL
                        }
                        MaterialConfig(key, c.label, c.unitPrice, c.weightFactor, c.weightOffset)
                    }
                    _uiState.value = _uiState.value.copy(materialConfigs = materials)
                }

                // 加载现货产品
                val spots = apiService.getSpotProducts()
                if (spots.isSuccessful) {
                    _uiState.value = _uiState.value.copy(spotProducts = spots.body()!!)
                }
            } catch (_: Exception) {
                // 使用默认值
            }
            // 首次计算
            recalculate()
        }
    }

    fun updateLength(value: String) {
        val v = value.toDoubleOrNull() ?: 0.0
        _uiState.value = _uiState.value.copy(form = _uiState.value.form.copy(length = v))
        recalculate()
    }

    fun updateWidth(value: String) {
        val v = value.toDoubleOrNull() ?: 0.0
        _uiState.value = _uiState.value.copy(form = _uiState.value.form.copy(width = v))
        recalculate()
    }

    fun updateHeight(value: String) {
        val v = value.toDoubleOrNull() ?: 0.0
        _uiState.value = _uiState.value.copy(form = _uiState.value.form.copy(height = v))
        recalculate()
    }

    fun updateQuantity(value: String) {
        val v = value.toIntOrNull() ?: 0
        _uiState.value = _uiState.value.copy(form = _uiState.value.form.copy(orderQuantity = v))
        recalculate()
    }

    fun updateMaterialKey(key: MaterialKey) {
        _uiState.value = _uiState.value.copy(form = _uiState.value.form.copy(materialKey = key))
        recalculate()
    }

    fun updateLayout(key: LayoutKey) {
        _uiState.value = _uiState.value.copy(form = _uiState.value.form.copy(selectedLayout = key))
        recalculate()
    }

    fun updateProfitMode(mode: ProfitMode) {
        _uiState.value = _uiState.value.copy(form = _uiState.value.form.copy(profitMode = mode))
        recalculate()
    }

    fun updateProfitPercentage(value: String) {
        val v = value.toDoubleOrNull() ?: 0.0
        _uiState.value = _uiState.value.copy(form = _uiState.value.form.copy(profitPercentage = v))
        recalculate()
    }

    fun updateProfitAmount(value: String) {
        val v = value.toDoubleOrNull() ?: 0.0
        _uiState.value = _uiState.value.copy(form = _uiState.value.form.copy(profitAmount = v))
        recalculate()
    }

    private fun recalculate() {
        val state = _uiState.value
        val result = calculateQuote.calculate(state.form, state.materialConfigs)
        _uiState.value = state.copy(result = result)
    }

    fun saveQuoteRecord() {
        val state = _uiState.value
        val result = state.result ?: return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val request = QuoteRecordRequest(
                    length = state.form.length,
                    width = state.form.width,
                    height = state.form.height,
                    quantity = state.form.orderQuantity,
                    materialKey = state.form.materialKey.name,
                    materialLabel = result.materialLabel,
                    materialCost = result.chargeLines.firstOrNull { it.name == "材料费" }?.amount,
                    processCost = result.subtotal - (result.chargeLines.firstOrNull { it.name == "材料费" }?.amount ?: 0.0),
                    profitAmount = result.profitAmount,
                    finalAmount = result.finalAmount,
                    unitPrice = if (state.form.orderQuantity > 0) result.finalAmount / state.form.orderQuantity else 0.0,
                    totalWeight = result.totalWeight
                )
                val response = apiService.saveQuoteRecord(request)
                if (response.isSuccessful) {
                    _uiState.value = _uiState.value.copy(
                        traceCode = response.body()!!.traceCode,
                        isLoading = false
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "保存失败：${response.code()}",
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "网络错误：${e.message}",
                    isLoading = false
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun clearTraceCode() {
        _uiState.value = _uiState.value.copy(traceCode = null)
    }

    fun reset() {
        _uiState.value = _uiState.value.copy(
            form = QuoteFormValues(),
            result = null,
            traceCode = null
        )
        recalculate()
    }
}
