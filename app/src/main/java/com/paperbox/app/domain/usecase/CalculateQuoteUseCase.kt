package com.paperbox.app.domain.usecase

import com.paperbox.app.domain.model.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * 报价计算核心逻辑 —— 移植自 Web 端 src/utils/quote.ts
 */
@Singleton
class CalculateQuoteUseCase @Inject constructor() {

    companion object {
        // 默认材质预设（fallback，优先用 API 加载的配置）
        val DEFAULT_MATERIALS = listOf(
            MaterialConfig(MaterialKey.KRAFT_SMALL, "牛皮纸150+90+125双面优牛（小）", 1.72, 0.3350, -0.009),
            MaterialConfig(MaterialKey.KRAFT_LARGE, "牛皮纸150+120+125双面优牛（大）", 1.82, 0.3350, -0.009),
            MaterialConfig(MaterialKey.WHITE_CARD, "白卡250+110+140", 2.60, 0.4400, -0.009),
            MaterialConfig(MaterialKey.WHITE_KRAFT, "白牛皮170+130+170", 2.35, 0.4250, -0.009)
        )
    }

    fun calculate(
        form: QuoteFormValues,
        materialConfigs: List<MaterialConfig> = DEFAULT_MATERIALS
    ): QuoteComputation? {
        val l = form.length
        val w = form.width
        val h = form.height
        val qty = form.orderQuantity

        if (l <= 0 || w <= 0 || h <= 0 || qty <= 0) return null

        val material = materialConfigs.find { it.key == form.materialKey }
            ?: DEFAULT_MATERIALS.first { it.key == form.materialKey }

        // 获取用户覆盖的单价，否则用预设
        val unitPrice = form.materialUnitPrices[material.key] ?: material.unitPrice

        // 1. 计算所有排版方案
        val layouts = calculateLayouts(l, w, h)

        // 2. 用选中的排版计算面积
        val selectedLayout = layouts[form.selectedLayout] ?: layouts[LayoutKey.OPEN_1X1]!!
        val layoutCount = when (form.selectedLayout) {
            LayoutKey.OPEN_1X1 -> 1
            LayoutKey.OPEN_1X2 -> 2
            LayoutKey.OPEN_1X4 -> 4
            LayoutKey.OPEN_1X6 -> 6
        }
        val areaM2 = (selectedLayout.width * selectedLayout.height) / 10000.0 / layoutCount

        // 3. 克重
        val unitWeight = areaM2 * material.weightFactor + material.weightOffset
        val totalWeight = unitWeight * qty

        // 4. 费用明细
        val chargeLines = mutableListOf<ChargeLine>()
        val sidedMultiplier = if (form.processes.sidedType == SidedType.DOUBLE) 2.0 else 1.0

        // 材料成本
        val materialCost = unitPrice * areaM2 * qty
        chargeLines.add(ChargeLine("材料费", materialCost, "${unitPrice}元/m² × ${String.format("%.4f", areaM2)}m² × $qty"))

        // 满印油墨
        if (form.processes.fullPrintEnabled && form.processes.fullPrintUnitPrice > 0) {
            val cost = form.processes.fullPrintUnitPrice * areaM2 * sidedMultiplier * qty
            chargeLines.add(ChargeLine("满印油墨", cost))
        }

        // 印刷费
        if (form.processes.printingEnabled && form.processes.printingUnitPrice > 0) {
            val perUnit = form.processes.printingUnitPrice
            val cost = if (qty < form.processes.printingMinQuantity && form.processes.printingMinFee > 0) {
                form.processes.printingMinFee
            } else {
                perUnit * qty
            }
            chargeLines.add(ChargeLine("印刷费", cost))
        }

        // 丝印费
        if (form.processes.screenPrintEnabled && form.processes.screenPrintUnitPrice > 0) {
            val perUnit = form.processes.screenPrintUnitPrice
            val cost = if (qty < form.processes.screenPrintMinQuantity && form.processes.screenPrintMinFee > 0) {
                form.processes.screenPrintMinFee
            } else {
                perUnit * qty
            }
            chargeLines.add(ChargeLine("丝印费", cost))
        }

        // 覆膜
        if (form.processes.laminationEnabled && form.processes.laminationUnitPrice > 0) {
            val cost = form.processes.laminationUnitPrice * areaM2 * sidedMultiplier * qty
            chargeLines.add(ChargeLine("覆膜", cost))
        }

        // 裱纸
        if (form.processes.mountingEnabled && form.processes.mountingUnitPrice > 0) {
            val cost = form.processes.mountingUnitPrice * areaM2 * sidedMultiplier * qty
            chargeLines.add(ChargeLine("裱纸", cost))
        }

        // 模切费
        if (form.processes.dieCutEnabled && form.processes.dieCutUnitPrice > 0) {
            val cost = if (qty < form.processes.dieCutMinQuantity && form.processes.dieCutMinFee > 0) {
                form.processes.dieCutMinFee
            } else {
                form.processes.dieCutUnitPrice * qty
            }
            chargeLines.add(ChargeLine("模切费", cost))
        }

        // 刀模费
        if (form.processes.toolingEnabled && form.processes.toolingFee > 0) {
            chargeLines.add(ChargeLine("刀模费", form.processes.toolingFee))
        }

        // 杂费
        if (form.processes.miscEnabled && form.processes.miscPerUnit > 0) {
            val cost = form.processes.miscPerUnit * form.processes.miscQuantity
            chargeLines.add(ChargeLine("杂费", cost))
        }

        val subtotal = chargeLines.sumOf { it.amount }

        // 5. 加价（工厂附加费）
        val afterExtraFee = if (form.extraFeeEnabled) subtotal + form.extraFee else subtotal

        // 6. 利润
        val profit = when (form.profitMode) {
            ProfitMode.PERCENTAGE -> afterExtraFee * form.profitPercentage / 100.0
            ProfitMode.AMOUNT -> form.profitAmount
        }

        // 7. 特殊费用
        val specialFeesSum = form.specialFees
            .filter { it.enabled }
            .sumOf { fee ->
                when (fee.pricingMode) {
                    PricingMode.TOTAL -> fee.amount
                    PricingMode.UNIT_PRICE -> fee.unitPrice * qty
                }
            }

        // 8. 物流费
        val logisticsCost = if (form.processes.logisticsEnabled) form.processes.logisticsFee else 0.0
        if (logisticsCost > 0) {
            chargeLines.add(ChargeLine("物流费", logisticsCost))
        }

        val finalAmount = afterExtraFee + profit + specialFeesSum
        val markupTotal = subtotal * form.markupRate

        return QuoteComputation(
            layouts = layouts,
            areaM2 = areaM2,
            materialLabel = material.label,
            chargeLines = chargeLines,
            subtotal = subtotal,
            afterExtraFee = afterExtraFee,
            profitAmount = profit,
            finalAmount = finalAmount,
            specialFeesSum = specialFeesSum,
            markupTotal = markupTotal,
            totalWeight = totalWeight
        )
    }

    private fun calculateLayouts(l: Double, w: Double, h: Double): Map<LayoutKey, LayoutResult> {
        // 移植自 Web 端 calculateLayouts 函数
        // 各排版方案的板材尺寸计算公式
        val tabExtra = 2.0 // 折叠余量

        return mapOf(
            LayoutKey.OPEN_1X1 to LayoutResult(
                "1×1",
                width = 2 * (l + h) + tabExtra,
                height = w + 2 * h + tabExtra
            ),
            LayoutKey.OPEN_1X2 to LayoutResult(
                "1×2",
                width = 2 * (l + h) + tabExtra,
                height = 2 * (w + 2 * h) + tabExtra
            ),
            LayoutKey.OPEN_1X4 to LayoutResult(
                "1×4",
                width = 2 * (2 * (l + h)) + tabExtra,
                height = 2 * (w + 2 * h) + tabExtra
            ),
            LayoutKey.OPEN_1X6 to LayoutResult(
                "1×6",
                width = 2 * (3 * (l + h)) + tabExtra,
                height = 2 * (w + 2 * h) + tabExtra
            )
        )
    }
}
