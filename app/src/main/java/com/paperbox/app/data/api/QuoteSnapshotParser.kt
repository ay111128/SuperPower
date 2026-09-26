package com.paperbox.app.data.api

import com.paperbox.app.domain.model.LayoutKey
import com.paperbox.app.domain.model.MaterialKey
import com.paperbox.app.domain.model.PricingMode
import com.paperbox.app.domain.model.ProfitMode
import com.paperbox.app.domain.model.ProcessValues
import com.paperbox.app.domain.model.QuoteFormValues
import com.paperbox.app.domain.model.SidedType
import com.paperbox.app.domain.model.SpecialFee
import com.paperbox.app.domain.model.SpotMatch
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/**
 * 报价表单快照 JSON ⇄ 领域模型（form_snapshot / spot_snapshot）。
 *
 * 键名与 Web 端 src/utils/quote.ts 的 cloneFormValues 完全一致
 * （materialKey 用 apiKey 驼峰、selectedLayout 用 'open1x1'、profitMode 用
 * 'percentage'/'amount'、pricingMode 用 'total'/'unitPrice'），
 * 两端保存的快照可以互相读取。
 *
 * 手写 org.json 而不是 Moshi：缺失键回退构造默认值、未知键忽略、
 * 枚举映射（Web 共享 sidedType ↔ 安卓每工艺独立 sided）全部显式可控。
 */
object QuoteSnapshotParser {

    // ── 读 ──

    /**
     * 快照可能被服务端二次编码：安卓保存时把快照当 JSON 字符串发，
     * 服务端 JSON.stringify 后落库值形如 `"{\"length\":35,…}"`（外面多一层引号）。
     * 直接喂 JSONObject 会抛 "must begin with {"，被当成旧记录——所以先剥引号，最多两层。
     */
    private fun unwrapSnapshot(json: String): String {
        var text = json.trim()
        repeat(2) {
            if (!text.startsWith('"')) return text
            val inner = runCatching { JSONTokener(text).nextValue() }.getOrNull()
            if (inner !is String) return text
            text = inner
        }
        return text
    }

    /** 解析 form_snapshot；JSON 坏/结构不符返回 null（调用方退回顶层字段重建） */
    fun parseForm(json: String?): QuoteFormValues? {
        if (json.isNullOrBlank()) return null
        return try {
            val o = JSONObject(unwrapSnapshot(json))
            val d = QuoteFormValues()

            val materialKey = o.optString("materialKey").takeIf { it.isNotEmpty() }
                ?.let { MaterialKey.fromApiKey(it) } ?: d.materialKey
            val unitPrices = mutableMapOf<MaterialKey, Double>()
            o.optJSONObject("materialUnitPrices")?.let { obj ->
                for (key in obj.keys()) {
                    MaterialKey.fromApiKey(key)?.let { mk -> unitPrices[mk] = obj.optDouble(key, 0.0) }
                }
            }

            QuoteFormValues(
                length = o.optDouble("length", d.length),
                width = o.optDouble("width", d.width),
                height = o.optDouble("height", d.height),
                orderQuantity = o.optInt("orderQuantity", d.orderQuantity),
                extraFeeEnabled = o.optBoolean("extraFeeEnabled", d.extraFeeEnabled),
                extraFee = o.optDouble("extraFee", d.extraFee),
                markupRate = o.optDouble("markupRate", d.markupRate),
                profitMode = when (o.optString("profitMode", "percentage")) {
                    "amount" -> ProfitMode.AMOUNT
                    else -> ProfitMode.PERCENTAGE
                },
                profitPercentage = o.optDouble("profitPercentage", d.profitPercentage),
                profitAmount = o.optDouble("profitAmount", d.profitAmount),
                materialKey = materialKey,
                materialUnitPrices = unitPrices,
                selectedLayout = layoutFromKey(o.optString("selectedLayout", "open1x1")),
                processes = parseProcesses(o.optJSONObject("processes")),
                specialFees = parseFees(o.optJSONArray("specialFees")),
            )
        } catch (_: Exception) {
            null
        }
    }

    /** 解析 spot_snapshot；结构不符返回 null */
    fun parseSpot(json: String?): SpotMatch? {
        if (json.isNullOrBlank()) return null
        return try {
            val o = JSONObject(unwrapSnapshot(json))
            val category = o.optString("category", "")
            val size = o.optString("size", "")
            if (category.isEmpty() && size.isEmpty()) null
            else SpotMatch(
                category = category,
                size = size,
                price = o.optDouble("price", 0.0),
                weight = o.optDouble("weight", 0.0),
                score = 100,
                exact = true,
                totalDiff = 0.0,
                selected = true,
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun parseProcesses(p: JSONObject?): ProcessValues {
        val d = ProcessValues()
        if (p == null) return d
        // Web 是共享 sidedType；安卓是每工艺独立 sided —— 先取共享值，再让独立键覆盖
        val shared = if (p.optString("sidedType", "single") == "double") SidedType.DOUBLE else SidedType.SINGLE
        fun sided(key: String): SidedType =
            if (p.has(key)) (if (p.optString(key) == "double") SidedType.DOUBLE else SidedType.SINGLE) else shared

        return ProcessValues(
            fullPrintEnabled = p.optBoolean("fullPrintEnabled", d.fullPrintEnabled),
            fullPrintSided = sided("fullPrintSided"),
            fullPrintUnitPrice = p.optDouble("fullPrintUnitPrice", d.fullPrintUnitPrice),
            printingEnabled = p.optBoolean("printingEnabled", d.printingEnabled),
            printingMinFee = p.optDouble("printingMinFee", d.printingMinFee),
            printingMinQuantity = p.optInt("printingMinQuantity", d.printingMinQuantity),
            printingUnitPrice = p.optDouble("printingUnitPrice", d.printingUnitPrice),
            screenPrintEnabled = p.optBoolean("screenPrintEnabled", d.screenPrintEnabled),
            screenPrintMinFee = p.optDouble("screenPrintMinFee", d.screenPrintMinFee),
            screenPrintMinQuantity = p.optInt("screenPrintMinQuantity", d.screenPrintMinQuantity),
            screenPrintUnitPrice = p.optDouble("screenPrintUnitPrice", d.screenPrintUnitPrice),
            laminationEnabled = p.optBoolean("laminationEnabled", d.laminationEnabled),
            laminationSided = sided("laminationSided"),
            laminationUnitPrice = p.optDouble("laminationUnitPrice", d.laminationUnitPrice),
            mountingEnabled = p.optBoolean("mountingEnabled", d.mountingEnabled),
            mountingSided = sided("mountingSided"),
            mountingUnitPrice = p.optDouble("mountingUnitPrice", d.mountingUnitPrice),
            dieCutEnabled = p.optBoolean("dieCutEnabled", d.dieCutEnabled),
            dieCutMinFee = p.optDouble("dieCutMinFee", d.dieCutMinFee),
            dieCutMinQuantity = p.optInt("dieCutMinQuantity", d.dieCutMinQuantity),
            dieCutUnitPrice = p.optDouble("dieCutUnitPrice", d.dieCutUnitPrice),
            toolingEnabled = p.optBoolean("toolingEnabled", d.toolingEnabled),
            toolingFee = p.optDouble("toolingFee", d.toolingFee),
            miscEnabled = p.optBoolean("miscEnabled", d.miscEnabled),
            miscPerUnit = p.optDouble("miscPerUnit", d.miscPerUnit),
            miscQuantity = p.optInt("miscQuantity", d.miscQuantity),
            logisticsEnabled = p.optBoolean("logisticsEnabled", d.logisticsEnabled),
            logisticsFee = p.optDouble("logisticsFee", d.logisticsFee),
        )
    }

    private fun parseFees(arr: JSONArray?): List<SpecialFee> {
        if (arr == null) return emptyList()
        val list = mutableListOf<SpecialFee>()
        for (i in 0 until arr.length()) {
            val f = arr.optJSONObject(i) ?: continue
            val id = f.optString("id", "").ifEmpty { "fee-$i" }
            list.add(
                SpecialFee(
                    id = id,
                    name = f.optString("name", ""),
                    spec = f.optString("spec", ""),
                    amount = f.optDouble("amount", 0.0),
                    pricingMode = if (f.optString("pricingMode", "total") == "unitPrice") {
                        PricingMode.UNIT_PRICE
                    } else {
                        PricingMode.TOTAL
                    },
                    unitPrice = f.optDouble("unitPrice", 0.0),
                    enabled = f.optBoolean("enabled", true),
                )
            )
        }
        return list
    }

    // ── 写（保存记录时发给服务端，Web 端可直接读） ──

    fun toJson(form: QuoteFormValues): String = JSONObject().apply {
        put("length", form.length)
        put("width", form.width)
        put("height", form.height)
        put("orderQuantity", form.orderQuantity)
        put("extraFeeEnabled", form.extraFeeEnabled)
        put("extraFee", form.extraFee)
        put("markupRate", form.markupRate)
        put("profitMode", if (form.profitMode == ProfitMode.AMOUNT) "amount" else "percentage")
        put("profitPercentage", form.profitPercentage)
        put("profitAmount", form.profitAmount)
        put("materialKey", form.materialKey.apiKey)
        put("selectedLayout", layoutToKey(form.selectedLayout))
        put(
            "materialUnitPrices",
            JSONObject().apply {
                // 只发 apiKey 已知的键，避免枚举名泄进快照
                form.materialUnitPrices.forEach { (key, price) -> put(key.apiKey, price) }
            },
        )
        put("processes", processesToJson(form.processes))
        put(
            "specialFees",
            JSONArray().apply {
                form.specialFees.forEach { fee ->
                    put(
                        JSONObject().apply {
                            put("id", fee.id)
                            put("name", fee.name)
                            put("spec", fee.spec)
                            put("amount", fee.amount)
                            put("pricingMode", if (fee.pricingMode == PricingMode.UNIT_PRICE) "unitPrice" else "total")
                            put("unitPrice", fee.unitPrice)
                            put("enabled", fee.enabled)
                        },
                    )
                }
            },
        )
        // logisticsRows 安卓没有对应结构，省略 —— Web 解析缺失键时回退默认值
    }.toString()

    fun spotToJson(spot: SpotMatch): String = JSONObject().apply {
        put("category", spot.category)
        put("size", spot.size)
        put("price", spot.price)
        put("weight", spot.weight)
    }.toString()

    private fun processesToJson(p: ProcessValues): JSONObject = JSONObject().apply {
        put("fullPrintEnabled", p.fullPrintEnabled)
        put("fullPrintSided", sidedKey(p.fullPrintSided))
        put("fullPrintUnitPrice", p.fullPrintUnitPrice)
        put("printingEnabled", p.printingEnabled)
        put("printingMinFee", p.printingMinFee)
        put("printingMinQuantity", p.printingMinQuantity)
        put("printingUnitPrice", p.printingUnitPrice)
        put("screenPrintEnabled", p.screenPrintEnabled)
        put("screenPrintMinFee", p.screenPrintMinFee)
        put("screenPrintMinQuantity", p.screenPrintMinQuantity)
        put("screenPrintUnitPrice", p.screenPrintUnitPrice)
        put("laminationEnabled", p.laminationEnabled)
        put("laminationSided", sidedKey(p.laminationSided))
        put("laminationUnitPrice", p.laminationUnitPrice)
        put("mountingEnabled", p.mountingEnabled)
        put("mountingSided", sidedKey(p.mountingSided))
        put("mountingUnitPrice", p.mountingUnitPrice)
        put("dieCutEnabled", p.dieCutEnabled)
        put("dieCutMinFee", p.dieCutMinFee)
        put("dieCutMinQuantity", p.dieCutMinQuantity)
        put("dieCutUnitPrice", p.dieCutUnitPrice)
        put("toolingEnabled", p.toolingEnabled)
        put("toolingFee", p.toolingFee)
        put("miscEnabled", p.miscEnabled)
        put("miscPerUnit", p.miscPerUnit)
        put("miscQuantity", p.miscQuantity)
        put("logisticsEnabled", p.logisticsEnabled)
        put("logisticsFee", p.logisticsFee)
        // Web 只认共享 sidedType：以满印为准（Web 的 ×2 后缀也只对三个联动项生效）
        put("sidedType", sidedKey(p.fullPrintSided))
    }

    private fun sidedKey(type: SidedType): String = if (type == SidedType.DOUBLE) "double" else "single"

    private fun layoutFromKey(key: String): LayoutKey = when (key) {
        "open1x2" -> LayoutKey.OPEN_1X2
        "open1x4" -> LayoutKey.OPEN_1X4
        "open1x6" -> LayoutKey.OPEN_1X6
        else -> LayoutKey.OPEN_1X1
    }

    private fun layoutToKey(layout: LayoutKey): String = when (layout) {
        LayoutKey.OPEN_1X1 -> "open1x1"
        LayoutKey.OPEN_1X2 -> "open1x2"
        LayoutKey.OPEN_1X4 -> "open1x4"
        LayoutKey.OPEN_1X6 -> "open1x6"
    }
}
