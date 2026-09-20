package com.paperbox.app.ui.quote

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paperbox.app.data.api.ApiService
import com.paperbox.app.data.api.models.QuoteRecordRequest
import com.paperbox.app.data.api.models.SpotProduct
import com.paperbox.app.domain.model.*
import com.paperbox.app.domain.usecase.CalculateQuoteUseCase
import com.paperbox.app.domain.usecase.MatchSpotProductsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * 输入框里的原始文本单独存一份 —— 不能只存解析后的 Double，
 * 否则用户想输入「1.」的时候会被立刻规范化成「1」，小数点永远打不进去。
 */
private val DIMENSION_INPUT = Regex("""^\d{0,6}(\.\d{0,3})?$""")
private val QUANTITY_INPUT = Regex("""^\d{0,7}$""")
private val PERCENT_INPUT = Regex("""^\d{0,3}(\.\d{0,2})?$""")

@Immutable
data class QuoteUiState(
    val form: QuoteFormValues = QuoteFormValues(),
    // 输入框原始文本
    val lengthText: String = "",
    val widthText: String = "",
    val heightText: String = "",
    val quantityText: String = "",
    val profitText: String = "",
    val result: QuoteComputation? = null,
    val materialConfigs: List<MaterialConfig> = CalculateQuoteUseCase.DEFAULT_MATERIALS,
    val spotProducts: List<SpotProduct> = emptyList(),
    val spotMatchEnabled: Boolean = false,
    val spotTolerance: Double = DEFAULT_TOLERANCE,
    val spotCategory: String = "kraft",
    val spotCounts: Map<String, Int> = emptyMap(),
    val spotMatches: List<SpotMatch> = emptyList(),
    val selectedSpotProduct: SpotMatch? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val traceCode: String? = null,
    val lastSavedForm: QuoteFormValues? = null,
    val isEnglish: Boolean = false,
    val isSearchActive: Boolean = false,
    val searchFieldText: String = "",
    val searchReady: Boolean = false
) {
    companion object {
        /** 匹配容差的默认值，同时是滑块的起始位置 */
        const val DEFAULT_TOLERANCE = 5.0
        /** 滑块量程（cm）——最大 10cm，步进 1cm */
        const val MIN_TOLERANCE = 0.0
        const val MAX_TOLERANCE = 10.0
    }
}

@HiltViewModel
class QuoteViewModel @Inject constructor(
    private val calculateQuote: CalculateQuoteUseCase,
    private val matchSpotProducts: MatchSpotProductsUseCase,
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
                        MaterialConfig(
                            key = MaterialKey.fromApiKey(c.key) ?: MaterialKey.KRAFT_SMALL,
                            label = c.label,
                            unitPrice = c.unitPrice,
                            weightFactor = c.weightFactor,
                            weightOffset = c.weightOffset
                        )
                    }
                    if (materials.isNotEmpty()) {
                        _uiState.value = _uiState.value.copy(materialConfigs = materials)
                    }
                }
            } catch (_: Exception) {
                // 用 CalculateQuoteUseCase.DEFAULT_MATERIALS 兜底
            }

            try {
                // 加载现货产品
                val spots = apiService.getSpotProducts()
                if (spots.isSuccessful) {
                    val products = spots.body()!!
                    _uiState.value = _uiState.value.copy(spotProducts = products)
                }
            } catch (_: Exception) {
                // 现货可用性不影响报价
            }

            try {
                // 工艺费率以服务端为准，拿不到就沿用本地默认值
                val processConfigs = apiService.getProcessConfigs()
                if (processConfigs.isSuccessful) {
                    applyProcessConfigs(processConfigs.body()!!)
                }
            } catch (_: Exception) {
                // 保留 ProcessValues 的默认费率
            }

            recalculate()
            rematch()
        }
    }

    /**
     * 服务端的 code 命名未知，用「中文名 或 code」双路匹配，都命中不了就保留本地默认值。
     * 这样服务端改了配置能生效，接口挂了也不会把价格算成 0。
     */
    private fun applyProcessConfigs(configs: List<com.paperbox.app.data.api.models.ProcessConfig>) {
        var p = _uiState.value.form.processes
        for (c in configs) {
            fun hit(vararg keys: String) = keys.any {
                it.equals(c.name, ignoreCase = true) || it.equals(c.code, ignoreCase = true)
            }
            p = when {
                hit("满印油墨", "full-print", "fullPrint") ->
                    p.copy(fullPrintUnitPrice = c.unitPrice)

                hit("印刷费", "printing") ->
                    p.copy(printingUnitPrice = c.unitPrice, printingMinFee = c.minFee, printingMinQuantity = c.minQuantity)

                hit("丝印费", "screen-printing", "screenPrinting") ->
                    p.copy(screenPrintUnitPrice = c.unitPrice, screenPrintMinFee = c.minFee, screenPrintMinQuantity = c.minQuantity)

                hit("覆膜", "lamination") ->
                    p.copy(laminationUnitPrice = c.unitPrice)

                hit("裱纸", "mounting") ->
                    p.copy(mountingUnitPrice = c.unitPrice)

                hit("模切费", "die-cut", "dieCut") ->
                    p.copy(dieCutUnitPrice = c.unitPrice, dieCutMinFee = c.minFee, dieCutMinQuantity = c.minQuantity)

                hit("刀模费", "tooling") ->
                    p.copy(toolingFee = c.unitPrice.takeIf { it > 0 } ?: c.minFee)

                hit("杂费", "杂费/个", "misc") ->
                    p.copy(miscPerUnit = c.unitPrice)

                hit("物流", "logistics") ->
                    p.copy(logisticsFee = c.unitPrice.takeIf { it > 0 } ?: c.minFee)

                else -> p
            }
        }
        _uiState.value = _uiState.value.copy(form = _uiState.value.form.copy(processes = p))
    }

    // ── 尺寸 / 生产输入 ──

    // 三个尺寸输入框：先过滤非法字符，再同时写入解析值和原始文本

    fun updateLength(text: String) {
        if (!DIMENSION_INPUT.matches(text)) return
        val s = _uiState.value
        _uiState.value = s.copy(
            form = s.form.copy(length = text.toDoubleOrNull() ?: 0.0),
            lengthText = text,
            selectedSpotProduct = null  // 手动改尺寸，清除现货选中
        )
        recalculate()
        rematch()
    }

    fun updateWidth(text: String) {
        if (!DIMENSION_INPUT.matches(text)) return
        val s = _uiState.value
        _uiState.value = s.copy(
            form = s.form.copy(width = text.toDoubleOrNull() ?: 0.0),
            widthText = text,
            selectedSpotProduct = null
        )
        recalculate()
        rematch()
    }

    fun updateHeight(text: String) {
        if (!DIMENSION_INPUT.matches(text)) return
        val s = _uiState.value
        _uiState.value = s.copy(
            form = s.form.copy(height = text.toDoubleOrNull() ?: 0.0),
            heightText = text,
            selectedSpotProduct = null
        )
        recalculate()
        rematch()
    }

    fun updateQuantity(text: String) {
        if (!QUANTITY_INPUT.matches(text)) return
        val state = _uiState.value
        _uiState.value = state.copy(
            form = state.form.copy(orderQuantity = text.toIntOrNull() ?: 0),
            quantityText = text
        )
        recalculate()
    }

    fun updateProfitPercentage(text: String) {
        if (!PERCENT_INPUT.matches(text)) return
        val state = _uiState.value
        _uiState.value = state.copy(
            form = state.form.copy(profitPercentage = text.toDoubleOrNull() ?: 0.0),
            profitText = text
        )
        recalculate()
    }

    fun updateProfitMode(mode: ProfitMode) {
        _uiState.value = _uiState.value.copy(form = _uiState.value.form.copy(profitMode = mode))
        recalculate()
    }

    // ── 材质 / 排版 ──

    fun updateMaterialKey(key: MaterialKey) {
        _uiState.value = _uiState.value.copy(form = _uiState.value.form.copy(materialKey = key))
        recalculate()
    }

    fun updateLayout(key: LayoutKey) {
        _uiState.value = _uiState.value.copy(form = _uiState.value.form.copy(selectedLayout = key))
        recalculate()
    }

    // ── 工艺 ──

    /**
     * 统一入口：改哪个开关就在 lambda 里改哪个字段。
     * 比原来按字符串 key 分发安全（拼错一个 key 会静默不生效）。
     */
    fun updateProcesses(transform: (ProcessValues) -> ProcessValues) {
        val state = _uiState.value
        _uiState.value = state.copy(form = state.form.copy(processes = transform(state.form.processes)))
        recalculate()
    }

    fun setProcessEnabled(field: ProcessField, enabled: Boolean) {
        updateProcesses { p ->
            when (field) {
                ProcessField.FULL_PRINT -> p.copy(fullPrintEnabled = enabled)
                ProcessField.PRINTING -> p.copy(printingEnabled = enabled)
                ProcessField.SCREEN_PRINT -> p.copy(screenPrintEnabled = enabled)
                ProcessField.LAMINATION -> p.copy(laminationEnabled = enabled)
                ProcessField.MOUNTING -> p.copy(mountingEnabled = enabled)
                ProcessField.DIE_CUT -> p.copy(dieCutEnabled = enabled)
                ProcessField.TOOLING -> p.copy(toolingEnabled = enabled)
                ProcessField.MISC -> p.copy(miscEnabled = enabled)
                ProcessField.LOGISTICS -> p.copy(logisticsEnabled = enabled)
            }
        }
    }

    fun setProcessSided(field: ProcessField, sided: SidedType) {
        updateProcesses { p ->
            when (field) {
                ProcessField.FULL_PRINT -> p.copy(fullPrintSided = sided)
                ProcessField.LAMINATION -> p.copy(laminationSided = sided)
                ProcessField.MOUNTING -> p.copy(mountingSided = sided)
                else -> p // 其他工艺不支持单双面
            }
        }
    }

    /** 基础选项那一组的总开关（工厂加价是 extraFee，不在 ProcessValues 里，得一起改） */
    fun setBasicGroupEnabled(enabled: Boolean) {
        val state = _uiState.value
        _uiState.value = state.copy(
            form = state.form.copy(
                processes = state.form.processes.copy(
                    dieCutEnabled = enabled,
                    toolingEnabled = enabled,
                    miscEnabled = enabled
                ),
                extraFeeEnabled = enabled
            )
        )
        recalculate()
    }

    /** 印刷定制那一组的总开关 */
    fun setPrintGroupEnabled(enabled: Boolean) {
        updateProcesses {
            it.copy(
                fullPrintEnabled = enabled,
                printingEnabled = enabled,
                screenPrintEnabled = enabled,
                laminationEnabled = enabled,
                mountingEnabled = enabled
            )
        }
    }

    fun setExtraFeeEnabled(enabled: Boolean) {
        val state = _uiState.value
        _uiState.value = state.copy(form = state.form.copy(extraFeeEnabled = enabled))
        recalculate()
    }

    // ── 附加费（用户手动增删）──

    fun addSpecialFee(name: String, amount: Double) {
        val state = _uiState.value
        val fee = SpecialFee(
            id = UUID.randomUUID().toString(),
            name = name.ifBlank { "附加费" },
            amount = amount,
            enabled = true
        )
        _uiState.value = state.copy(form = state.form.copy(specialFees = state.form.specialFees + fee))
        recalculate()
    }

    fun updateSpecialFee(id: String, name: String, amount: Double) {
        val state = _uiState.value
        val updated = state.form.specialFees.map {
            if (it.id == id) it.copy(name = name.ifBlank { it.name }, amount = amount) else it
        }
        _uiState.value = state.copy(form = state.form.copy(specialFees = updated))
        recalculate()
    }

    fun toggleSpecialFee(id: String) {
        val state = _uiState.value
        val updated = state.form.specialFees.map {
            if (it.id == id) it.copy(enabled = !it.enabled) else it
        }
        _uiState.value = state.copy(form = state.form.copy(specialFees = updated))
        recalculate()
    }

    fun deleteSpecialFee(id: String) {
        val state = _uiState.value
        _uiState.value = state.copy(form = state.form.copy(specialFees = state.form.specialFees.filterNot { it.id == id }))
        recalculate()
    }

    // ── 现货匹配 ──

    /**
     * 双击匹配结果中的某个尺寸 → 用该现货的价格和尺寸来计价。
     * 自动打开现货计价开关，用现货尺寸填充表单。
     */
    fun selectSpotProduct(match: SpotMatch) {
        // 解析现货尺寸填入表单
        val parsed = MatchSpotProductsUseCase.parseSize(match.size)
        val (l, w, h) = parsed ?: Triple(0.0, 0.0, 0.0)

        _uiState.value = _uiState.value.copy(
            selectedSpotProduct = match,
            spotMatchEnabled = true,
            form = _uiState.value.form.copy(length = l, width = w, height = h),
            lengthText = trimNumber(l),
            widthText = trimNumber(w),
            heightText = trimNumber(h)
        )
        recalculate()
        rematch()
    }

    fun clearSpotSelection() {
        _uiState.value = _uiState.value.copy(selectedSpotProduct = null)
        recalculate()
    }

    fun setSpotMatchEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(
            spotMatchEnabled = enabled,
            selectedSpotProduct = if (!enabled) null else _uiState.value.selectedSpotProduct
        )
        if (!enabled) recalculate()
    }

    /** 拖动过程中实时匹配；步进 0.1cm */
    fun setSpotTolerance(tolerance: Double) {
        val snapped = (tolerance * 10).roundToInt() / 10.0
            .coerceIn(QuoteUiState.MIN_TOLERANCE, QuoteUiState.MAX_TOLERANCE)
        _uiState.value = _uiState.value.copy(spotTolerance = snapped)
        rematch()
    }

    /** 松手时才真正重算匹配结果 */
    fun commitSpotTolerance() = rematch()

    fun selectSpotCategory(category: String) {
        _uiState.value = _uiState.value.copy(spotCategory = category)
        rematch()
    }

    private fun rematch() {
        val state = _uiState.value
        val matches = matchSpotProducts.match(
            products = state.spotProducts,
            l = state.form.length,
            w = state.form.width,
            h = state.form.height,
            tolerance = state.spotTolerance,
            category = null  // 匹配全部分类，用于统计各分类数量
        )
        // tab 计数：从匹配结果按分类统计真实数量
        val counts = matches.groupingBy { it.category }.eachCount()
        // 当前选中分类的匹配结果
        val filtered = if (state.spotCategory == "all") matches
        else matches.filter { it.category == state.spotCategory }
        _uiState.value = state.copy(
            spotCounts = counts,
            spotMatches = filtered
        )
    }

    // ── 计算 / 保存 ──

    fun recalculate() {
        val state = _uiState.value
        _uiState.value = state.copy(
            result = calculateQuote.calculate(
                state.form,
                state.materialConfigs,
                state.selectedSpotProduct
            )
        )
    }

    /**
     * 点击"生成报价"时调用：保存记录并返回工单号。
     * 如果表单没变且已有 traceCode，直接复用不重复保存。
     * 返回 true 表示可以跳转结果页，false 表示无结果可保存。
     */
    fun generateQuote(): Boolean {
        val state = _uiState.value
        val result = state.result ?: return false

        // 表单没变且已有工单号，直接跳结果页，不重复保存
        if (state.form == state.lastSavedForm && state.traceCode != null) {
            return true
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                val request = QuoteRecordRequest(
                    length = state.form.length,
                    width = state.form.width,
                    height = state.form.height,
                    quantity = state.form.orderQuantity,
                    materialKey = state.form.materialKey.apiKey,
                    materialLabel = result.materialLabel,
                    materialUnitPrice = result.materialUnitPrice,
                    materialCost = result.materialCost,
                    processCost = result.subtotal - result.materialCost,
                    logisticsCost = if (state.form.processes.logisticsEnabled) state.form.processes.logisticsFee else 0.0,
                    specialFeesCost = result.specialFeesSum,
                    profitAmount = result.profitAmount,
                    extraFee = if (state.form.extraFeeEnabled) state.form.extraFee else 0.0,
                    finalAmount = result.finalAmount,
                    unitPrice = if (state.form.orderQuantity > 0) result.finalAmount / state.form.orderQuantity else 0.0,
                    totalWeight = result.totalWeight
                )
                val response = apiService.saveQuoteRecord(request)
                if (response.isSuccessful) {
                    _uiState.value = _uiState.value.copy(
                        traceCode = response.body()!!.traceCode,
                        lastSavedForm = state.form,
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
        return true
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun clearSearchReady() {
        _uiState.value = _uiState.value.copy(searchReady = false)
    }

    fun clearTraceCode() {
        _uiState.value = _uiState.value.copy(traceCode = null, lastSavedForm = null)
    }

    fun toggleLanguage() {
        _uiState.value = _uiState.value.copy(isEnglish = !_uiState.value.isEnglish)
    }

    // ── 工单查询 ──

    fun toggleSearch() {
        val active = !_uiState.value.isSearchActive
        _uiState.value = _uiState.value.copy(
            isSearchActive = active,
            searchFieldText = ""
        )
    }

    fun updateSearchField(text: String) {
        _uiState.value = _uiState.value.copy(searchFieldText = text)
    }

    /**
     * 按工单编号查询后端记录，成功后构造 QuoteComputation 写入 state。
     */
    fun searchByTraceCode(code: String) {
        val trimmed = code.trim()
        if (trimmed.isEmpty()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                val response = apiService.getQuoteRecord(trimmed)
                if (response.isSuccessful) {
                    val record = response.body()!!
                    val materialKey = record.materialKey?.let { MaterialKey.fromApiKey(it) } ?: MaterialKey.KRAFT_SMALL
                    val materialCost = record.materialCost ?: 0.0
                    val processCost = record.processCost ?: 0.0
                    val specialFeesCost = record.specialFeesCost ?: 0.0
                    val profitAmount = record.profitAmount ?: 0.0
                    val extraFee = record.extraFee ?: 0.0
                    val finalAmount = record.finalAmount ?: 0.0

                    val computation = QuoteComputation(
                        layouts = emptyMap(),
                        areaM2 = 0.0,
                        materialLabel = record.materialLabel ?: "",
                        materialKey = materialKey,
                        materialUnitPrice = record.materialUnitPrice ?: 0.0,
                        chargeLines = emptyList(),
                        subtotal = materialCost + processCost,
                        afterExtraFee = materialCost + processCost + extraFee,
                        profitAmount = profitAmount,
                        finalAmount = finalAmount,
                        specialFeesSum = specialFeesCost,
                        markupTotal = finalAmount,
                        totalWeight = record.totalWeight ?: 0.0,
                        materialCost = materialCost,
                        basicProcessCost = processCost,
                        printProcessCost = 0.0
                    )

                    _uiState.value = _uiState.value.copy(
                        result = computation,
                        traceCode = record.traceCode,
                        form = QuoteFormValues(
                            length = record.length,
                            width = record.width,
                            height = record.height,
                            orderQuantity = record.quantity,
                            materialKey = materialKey
                        ),
                        lengthText = trimNumber(record.length),
                        widthText = trimNumber(record.width),
                        heightText = trimNumber(record.height),
                        quantityText = record.quantity.toString(),
                        isSearchActive = false,
                        searchFieldText = "",
                        searchReady = true,
                        isLoading = false
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "未找到工单记录",
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

    /**
     * 顶栏的重置按钮。已加载的材质 / 现货数据要留着，
     * 只清空用户输入（原来的实现漏了文本状态和现货匹配状态）。
     */
    fun reset() {
        _uiState.value = _uiState.value.copy(
            form = QuoteFormValues(),
            lengthText = "",
            widthText = "",
            heightText = "",
            quantityText = "",
            profitText = "",
            result = null,
            traceCode = null,
            lastSavedForm = null,
            errorMessage = null,
            isSearchActive = false,
            searchFieldText = "",
            searchReady = false,
            spotMatchEnabled = false,
            spotTolerance = QuoteUiState.DEFAULT_TOLERANCE,
            spotCategory = "kraft",
            spotMatches = emptyList(),
            selectedSpotProduct = null
        )
        recalculate()
        rematch()
    }
}

/** 工艺开关的字段标识，替代原来的字符串 key */
enum class ProcessField {
    FULL_PRINT, PRINTING, SCREEN_PRINT, LAMINATION, MOUNTING, DIE_CUT, TOOLING, MISC, LOGISTICS
}
