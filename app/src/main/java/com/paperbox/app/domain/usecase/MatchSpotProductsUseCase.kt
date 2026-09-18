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
        const val DEFAULT_TOLERANCE = 5.0

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
        if (l <= 0 || w <= 0 || h <= 0 || tolerance < 0) return emptyList()

        // 容差为 0 时只有完全一致的尺寸能命中，score 会走 exact 分支，这里避免除零
        val maxDiff = (tolerance * 3).takeIf { it > 0 } ?: 1.0

        return products
            .asSequence()
            .filter { category == null || category == "all" || it.category == category }
            .mapNotNull { product ->
                val parsed = parseSize(product.size) ?: return@mapNotNull null
                val diffL = abs(parsed.first - l)
                val diffW = abs(parsed.second - w)
                val diffH = abs(parsed.third - h)

                // 每个维度都得在容差内
                if (diffL > tolerance || diffW > tolerance || diffH > tolerance) return@mapNotNull null

                val totalDiff = diffL + diffW + diffH
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
