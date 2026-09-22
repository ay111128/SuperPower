package com.paperbox.app.data.api.models

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// ── 认证 ──

@JsonClass(generateAdapter = true)
data class LoginRequest(
    val username: String,
    val password: String
)

@JsonClass(generateAdapter = true)
data class LoginResponse(
    val token: String,
    val username: String,
    val role: String = "user"
)

@JsonClass(generateAdapter = true)
data class RegisterRequest(
    val username: String,
    val password: String
)

// ── 素材 ──

@JsonClass(generateAdapter = true)
data class MaterialItem(
    val id: String,
    val name: String,
    val type: String,
    val ext: String = "",
    val size: Long = 0,
    @Json(name = "createdAt") val createdAt: String = "",
    val color: String = "",
    val remark: String = "",
    val hash: String = "",
    val tags: List<String> = emptyList(),
    val online: Boolean = false
)

@JsonClass(generateAdapter = true)
data class MaterialListResponse(
    val items: List<MaterialItem>,
    val total: Int
)

@JsonClass(generateAdapter = true)
data class UploadResponse(
    val created: List<MaterialItem>,
    val duplicates: List<DuplicateItem>
)

@JsonClass(generateAdapter = true)
data class DuplicateItem(
    val name: String,
    @Json(name = "existingId") val existingId: String,
    @Json(name = "existingName") val existingName: String
)

// ── 颜色 ──

@JsonClass(generateAdapter = true)
data class ColorItem(
    val name: String,
    val hex: String
)

@JsonClass(generateAdapter = true)
data class ColorsResponse(
    val colors: List<ColorItem>
)

@JsonClass(generateAdapter = true)
data class ColorCountsResponse(
    val counts: Map<String, Int>,
    val total: Int
)

// ── 标签 ──

@JsonClass(generateAdapter = true)
data class TagsResponse(
    val tags: List<String>
)

// ── 筛选计数 ──

@JsonClass(generateAdapter = true)
data class FilterCountsResponse(
    val total: Int = 0,
    val colorCounts: Map<String, Int> = emptyMap(),
    val typeCounts: Map<String, Int> = emptyMap(),
    val tagCounts: Map<String, Int> = emptyMap()
)

// ── 现货产品 ──

@JsonClass(generateAdapter = true)
data class SpotProduct(
    val id: Int = 0,
    val category: String,
    val size: String,
    val price: Double,
    val weight: Double
)

// ── 材质配置 ──

@JsonClass(generateAdapter = true)
data class MaterialConfig(
    val key: String,
    val label: String,
    @Json(name = "unit_price") val unitPrice: Double,
    @Json(name = "weight_factor") val weightFactor: Double,
    @Json(name = "weight_offset") val weightOffset: Double
)

// ── 工艺配置 ──

@JsonClass(generateAdapter = true)
data class ProcessConfig(
    val code: String,
    val name: String,
    @Json(name = "pricing_type") val pricingType: String,
    @Json(name = "unit_price") val unitPrice: Double = 0.0,
    @Json(name = "min_quantity") val minQuantity: Int = 0,
    @Json(name = "min_fee") val minFee: Double = 0.0
)

// ── 报价记录 ──

@JsonClass(generateAdapter = true)
data class QuoteRecordRequest(
    @Json(name = "product_name") val productName: String = "飞机盒",
    val length: Double,
    val width: Double,
    val height: Double,
    val quantity: Int,
    @Json(name = "material_key") val materialKey: String? = null,
    @Json(name = "material_label") val materialLabel: String? = null,
    @Json(name = "material_unit_price") val materialUnitPrice: Double? = null,
    @Json(name = "material_cost") val materialCost: Double? = null,
    @Json(name = "process_cost") val processCost: Double? = null,
    @Json(name = "logistics_cost") val logisticsCost: Double? = null,
    @Json(name = "special_fees_cost") val specialFeesCost: Double? = null,
    @Json(name = "profit_amount") val profitAmount: Double? = null,
    @Json(name = "extra_fee") val extraFee: Double? = null,
    @Json(name = "final_amount") val finalAmount: Double? = null,
    @Json(name = "unit_price") val unitPrice: Double? = null,
    @Json(name = "total_weight") val totalWeight: Double? = null
)

@JsonClass(generateAdapter = true)
data class QuoteRecordResponse(
    @Json(name = "trace_code") val traceCode: String,
    @Json(name = "full_uuid") val fullUuid: String,
    @Json(name = "created_at") val createdAt: String
)

/**
 * 本机报价历史条目 —— 只存展示所需字段，DataStore 持久化（JSON 数组），上限 20 条。
 * createdAt 为本地保存时刻（epoch millis）。
 */
@JsonClass(generateAdapter = true)
data class QuoteHistoryEntry(
    val length: Double,
    val width: Double,
    val height: Double,
    val quantity: Int,
    /** 折合单价（元/个） */
    val unitPrice: Double,
    val finalAmount: Double,
    val createdAt: Long = System.currentTimeMillis()
)

@JsonClass(generateAdapter = true)
data class QuoteRecordDetail(
    val uuid: String = "",
    @Json(name = "trace_code") val traceCode: String = "",
    @Json(name = "created_at") val createdAt: String = "",
    val length: Double = 0.0,
    val width: Double = 0.0,
    val height: Double = 0.0,
    val quantity: Int = 0,
    @Json(name = "material_key") val materialKey: String? = null,
    @Json(name = "material_label") val materialLabel: String? = null,
    @Json(name = "material_unit_price") val materialUnitPrice: Double? = null,
    @Json(name = "material_cost") val materialCost: Double? = null,
    @Json(name = "process_cost") val processCost: Double? = null,
    @Json(name = "logistics_cost") val logisticsCost: Double? = null,
    @Json(name = "special_fees_cost") val specialFeesCost: Double? = null,
    @Json(name = "profit_amount") val profitAmount: Double? = null,
    @Json(name = "extra_fee") val extraFee: Double? = null,
    @Json(name = "final_amount") val finalAmount: Double? = null,
    @Json(name = "unit_price") val unitPrice: Double? = null,
    @Json(name = "total_weight") val totalWeight: Double? = null
)
