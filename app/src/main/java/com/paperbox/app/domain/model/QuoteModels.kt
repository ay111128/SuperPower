package com.paperbox.app.domain.model

import androidx.compose.runtime.Immutable

// ── 材质 ──

/**
 * [apiKey] 是后端 /pricing-api 用的键（驼峰），存报价记录时必须发这个，
 * 不能发枚举名 —— QuoteViewModel.loadData 的反向映射即证据。
 */
enum class MaterialKey(val label: String, val apiKey: String) {
    KRAFT_SMALL("牛皮纸（小）", "kraftSmall"),
    KRAFT_LARGE("牛皮纸（大）", "kraftLarge"),
    WHITE_CARD("白卡", "whiteCard"),
    WHITE_KRAFT("白牛皮", "whiteKraft");

    companion object {
        fun fromApiKey(key: String): MaterialKey? = entries.find { it.apiKey == key }
    }
}

@Immutable
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

/**
 * 费率默认值全部对齐 Web 端 src/utils/quote.ts:234-277 的 DEFAULT_FORM_VALUES ——
 * 之前这里全是 0.0，导致工艺开关打开也算出 0 元。
 */
@Immutable
data class ProcessValues(
    // 满印油墨
    val fullPrintEnabled: Boolean = false,
    val fullPrintUnitPrice: Double = 0.5,
    // 印刷费
    val printingEnabled: Boolean = false,
    val printingMinFee: Double = 350.0,
    val printingMinQuantity: Int = 1000,
    val printingUnitPrice: Double = 0.4,
    // 丝印费
    val screenPrintEnabled: Boolean = false,
    val screenPrintMinFee: Double = 300.0,
    val screenPrintMinQuantity: Int = 1000,
    val screenPrintUnitPrice: Double = 0.3,
    // 覆膜
    val laminationEnabled: Boolean = false,
    val laminationUnitPrice: Double = 0.4,
    // 裱纸
    val mountingEnabled: Boolean = false,
    val mountingUnitPrice: Double = 0.25,
    // 模切费
    val dieCutEnabled: Boolean = false,
    val dieCutMinFee: Double = 200.0,
    val dieCutMinQuantity: Int = 2000,
    val dieCutUnitPrice: Double = 0.1,
    // 刀模费
    val toolingEnabled: Boolean = false,
    val toolingFee: Double = 200.0,
    // 杂费 —— 计费数量取订单数量（Web 端 buildUnitChargeLine 传的就是 orderQuantity），
    // 这个字段只在 Web 的类型里留着，实际不参与计算
    val miscEnabled: Boolean = false,
    val miscPerUnit: Double = 0.045,
    val miscQuantity: Int = 100,
    // 单双面
    val sidedType: SidedType = SidedType.SINGLE,
    // 物流
    val logisticsEnabled: Boolean = false,
    val logisticsFee: Double = 0.0
)

enum class SidedType { SINGLE, DOUBLE }

// ── 特殊费用 ──

@Immutable
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

@Immutable
data class QuoteFormValues(
    val length: Double = 0.0,
    val width: Double = 0.0,
    val height: Double = 0.0,
    val orderQuantity: Int = 0,
    val extraFeeEnabled: Boolean = false,
    val extraFee: Double = 300.0,
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

@Immutable
data class ChargeLine(
    val name: String,
    val amount: Double,
    val detail: String = ""
)

// ── 报价结果 ──

/**
 * [materialCost] / [basicProcessCost] / [printProcessCost] 是给界面上
 * 「材质：」「工艺：」两行做分组汇总用的（基础选项 = 模切+刀模+杂费+工厂加价，
 * 印刷定制 = 满印油墨+印刷费+丝印费+覆膜+裱纸），避免 UI 层去 chargeLines 里按名字捞。
 */
@Immutable
data class QuoteComputation(
    val layouts: Map<LayoutKey, LayoutResult>,
    val areaM2: Double,
    val materialLabel: String,
    val materialKey: MaterialKey,
    val materialUnitPrice: Double,
    val chargeLines: List<ChargeLine>,
    val subtotal: Double,
    val afterExtraFee: Double,
    val profitAmount: Double,
    val finalAmount: Double,
    val specialFeesSum: Double,
    val markupTotal: Double,
    val totalWeight: Double,
    val materialCost: Double,
    val basicProcessCost: Double,
    val printProcessCost: Double
)

// ── 现货匹配 ──

@Immutable
data class SpotMatch(
    val category: String,
    val size: String,
    val price: Double,
    val weight: Double,
    val score: Int,
    val exact: Boolean,
    val totalDiff: Double
)
