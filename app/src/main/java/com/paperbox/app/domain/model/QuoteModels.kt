package com.paperbox.app.domain.model

// ── 材质 ──

enum class MaterialKey(val label: String) {
    KRAFT_SMALL("牛皮纸（小）"),
    KRAFT_LARGE("牛皮纸（大）"),
    WHITE_CARD("白卡"),
    WHITE_KRAFT("白牛皮")
}

data class MaterialConfig(
    val key: MaterialKey,
    val label: String,
    val unitPrice: Double,     // 元/m²
    val weightFactor: Double,  // 克重系数
    val weightOffset: Double   // 克重偏移
)

// ── 排版 ──

enum class LayoutKey(val label: String) {
    OPEN_1X1("1×1"),
    OPEN_1X2("1×2"),
    OPEN_1X4("1×4"),
    OPEN_1X6("1×6")
}

data class LayoutResult(
    val label: String,
    val width: Double,
    val height: Double
)

// ── 工艺 ──

enum class PricingType {
    AREA,      // 按面积
    TIERED,    // 阶梯（最低数量/最低费用）
    FIXED,     // 固定费用
    UNIT       // 按个数
}

data class ProcessValues(
    // 满印油墨
    val fullPrintEnabled: Boolean = false,
    val fullPrintUnitPrice: Double = 0.0,
    // 印刷费
    val printingEnabled: Boolean = false,
    val printingMinFee: Double = 0.0,
    val printingMinQuantity: Int = 0,
    val printingUnitPrice: Double = 0.0,
    // 丝印费
    val screenPrintEnabled: Boolean = false,
    val screenPrintMinFee: Double = 0.0,
    val screenPrintMinQuantity: Int = 0,
    val screenPrintUnitPrice: Double = 0.0,
    // 覆膜
    val laminationEnabled: Boolean = false,
    val laminationUnitPrice: Double = 0.0,
    // 裱纸
    val mountingEnabled: Boolean = false,
    val mountingUnitPrice: Double = 0.0,
    // 模切费
    val dieCutEnabled: Boolean = false,
    val dieCutMinFee: Double = 0.0,
    val dieCutMinQuantity: Int = 0,
    val dieCutUnitPrice: Double = 0.0,
    // 刀模费
    val toolingEnabled: Boolean = false,
    val toolingFee: Double = 0.0,
    // 杂费
    val miscEnabled: Boolean = false,
    val miscPerUnit: Double = 0.0,
    val miscQuantity: Int = 0,
    // 单双面
    val sidedType: SidedType = SidedType.SINGLE,
    // 物流
    val logisticsEnabled: Boolean = false,
    val logisticsFee: Double = 0.0
)

enum class SidedType { SINGLE, DOUBLE }

// ── 特殊费用 ──

data class SpecialFee(
    val id: String,
    val name: String,
    val spec: String = "",
    val amount: Double = 0.0,
    val pricingMode: PricingMode = PricingMode.TOTAL,
    val unitPrice: Double = 0.0,
    val enabled: Boolean = true
)

enum class PricingMode { TOTAL, UNIT_PRICE }

// ── 利润 ──

enum class ProfitMode { PERCENTAGE, AMOUNT }

// ── 报价表单 ──

data class QuoteFormValues(
    val length: Double = 0.0,
    val width: Double = 0.0,
    val height: Double = 0.0,
    val orderQuantity: Int = 0,
    val extraFeeEnabled: Boolean = false,
    val extraFee: Double = 0.0,
    val markupRate: Double = 1.05,
    val profitMode: ProfitMode = ProfitMode.PERCENTAGE,
    val profitPercentage: Double = 0.0,
    val profitAmount: Double = 0.0,
    val materialKey: MaterialKey = MaterialKey.KRAFT_SMALL,
    val materialUnitPrices: Map<MaterialKey, Double> = emptyMap(),
    val selectedLayout: LayoutKey = LayoutKey.OPEN_1X1,
    val processes: ProcessValues = ProcessValues(),
    val specialFees: List<SpecialFee> = emptyList()
)

// ── 费用明细行 ──

data class ChargeLine(
    val name: String,
    val amount: Double,
    val detail: String = ""
)

// ── 报价结果 ──

data class QuoteComputation(
    val layouts: Map<LayoutKey, LayoutResult>,
    val areaM2: Double,
    val materialLabel: String,
    val chargeLines: List<ChargeLine>,
    val subtotal: Double,
    val afterExtraFee: Double,
    val profitAmount: Double,
    val finalAmount: Double,
    val specialFeesSum: Double,
    val markupTotal: Double,
    val totalWeight: Double
)

// ── 现货匹配 ──

data class SpotMatch(
    val category: String,
    val size: String,
    val price: Double,
    val weight: Double,
    val score: Int,
    val exact: Boolean,
    val totalDiff: Double
)
