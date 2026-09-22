package com.paperbox.app.domain.usecase

import com.paperbox.app.data.api.models.SpotProduct
import com.paperbox.app.domain.model.SpotMatch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 现货尺寸匹配 —— 报价页的「现货匹配」卡片和规格页共用同一套规则。
 *
 * 规则移植自 Web 端 src/utils/sizeGuide.ts:154-195：
 * - **每个维度单独判定**，任意一维超出容差就淘汰（不是三个维度差值之和不超过 3 倍容差）
 * - totalDiff 取三维差值的和，用于排序和算分
 * - score = (1 - totalDiff / (tolerance * 3)) * 100，完全命中记 100 分
 */
@Singleton
class MatchSpotProductsUseCase @Inject constructor() {

    companion object {
        const val DEFAULT_TOLERANCE = 0.0

        /** 兼容 "350x250x150cm" / "350×250×150" / "350 * 250 * 150" 这类写法 */
        private val SIZE_REGEX = Regex("""([\d.]+)\s*[x×X*]\s*([\d.]+)\s*[x×X*]\s*([\d.]+)""")

        fun parseSize(raw: String): Triple<Double, Double, Double>? {
            val m = SIZE_REGEX.find(raw) ?: return null
            val l = m.groupValues[1].toDoubleOrNull() ?: return null
            val w = m.groupValues[2].toDoubleOrNull() ?: return null
            val h = m.groupValues[3].toDoubleOrNull() ?: return null
            return Triple(l, w, h)
        }
    }

    /**
     * @param tolerance 单维度的最大容差，单位与 [l]、[w]、[h] 一致
     * @return 按 totalDiff 升序排列（最接近的排最前）
     */
    fun match(
        products: List<SpotProduct>,
        l: Double,
        w: Double,
        h: Double,
        tolerance: Double = DEFAULT_TOLERANCE,
        category: String? = null
    ): List<SpotMatch> {
        if (tolerance < 0) return emptyList()

        // 只匹配已填的维度（> 0 的维度），未填的维度跳过
        val checkL = l > 0
        val checkW = w > 0
        val checkH = h > 0
        val noDimension = !checkL && !checkW && !checkH

        // 容差为 0 时只有完全一致的尺寸能命中，score 会走 exact 分支，这里避免除零
        val filledCount = listOf(checkL, checkW, checkH).count { it }
        val maxDiff = (tolerance * filledCount).takeIf { it > 0 } ?: 1.0

        return products
            .asSequence()
            .filter { category == null || category == "all" || it.category == category }
            .mapNotNull { product ->
                val parsed = parseSize(product.size) ?: return@mapNotNull null

                // 没有任何维度输入时，显示全部现货
                if (noDimension) {
                    return@mapNotNull SpotMatch(
                        category = product.category,
                        size = product.size,
                        price = product.price,
                        weight = product.weight,
                        score = 0,
                        exact = false,
                        totalDiff = 0.0
                    )
                }

                // 只检查已填维度
                if (checkL) { val d = abs(parsed.first - l); if (d > tolerance) return@mapNotNull null }
                if (checkW) { val d = abs(parsed.second - w); if (d > tolerance) return@mapNotNull null }
                if (checkH) { val d = abs(parsed.third - h); if (d > tolerance) return@mapNotNull null }

                // totalDiff 只累加已填维度的差值
                val totalDiff = listOf(
                    if (checkL) abs(parsed.first - l) else 0.0,
                    if (checkW) abs(parsed.second - w) else 0.0,
                    if (checkH) abs(parsed.third - h) else 0.0
                ).sum()
                val exact = totalDiff == 0.0
                val score = if (exact) 100 else ((1 - totalDiff / maxDiff) * 100).roundToInt()

                SpotMatch(
                    category = product.category,
                    size = product.size,
                    price = product.price,
                    weight = product.weight,
                    score = score,
                    exact = exact,
                    totalDiff = totalDiff
                )
            }
            .sortedBy { it.totalDiff }
            .toList()
    }

    /** 各分类的现货数量，用于 tab 上的角标，键是后端返回的分类（kraft / white / color） */
    fun countByCategory(products: List<SpotProduct>): Map<String, Int> =
        products.groupingBy { it.category }.eachCount()
}
