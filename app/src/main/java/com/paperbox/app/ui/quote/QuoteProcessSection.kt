package com.paperbox.app.ui.quote

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import com.paperbox.app.domain.model.LayoutKey
import com.paperbox.app.domain.model.SidedType
import com.paperbox.app.domain.model.SpecialFee

/** 附加费金额输入框允许的中间态（含空串和小数点） */
private val FEE_AMOUNT_INPUT = Regex("""^\d{0,7}(\.\d{0,2})?$""")

// ══════════════════════════════════════════════════════════════
//  工艺汇总行 —— 兼作排版选择入口
// ══════════════════════════════════════════════════════════════

/**
 * 「工艺：基础选项500元+印刷定制300元」
 *
 * 排版选择并进了这一行：点开就是 1×1 / 1×2 / 1×4 / 1×6。
 * 默认的 1×1 不显示，免得破坏设计稿上那句话的样子。
 */
@Composable
internal fun QuoteProcessSummaryRow(
    state: QuoteUiState,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSelectLayout: (LayoutKey) -> Unit
) {
    val result = state.result
    val basic = result?.basicProcessCost ?: 0.0
    val print = result?.printProcessCost ?: 0.0

    val summary = buildString {
        if (basic > 0) append("基础选项${trimNumber(basic)}元")
        if (print > 0) {
            if (isNotEmpty()) append("+")
            append("印刷定制${trimNumber(print)}元")
        }
    }.ifEmpty { "未选择" }

    val layoutSuffix =
        if (state.form.selectedLayout != LayoutKey.OPEN_1X1) " · ${state.form.selectedLayout.label}" else ""

    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White)
                .clickable { onExpandedChange(true) },
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("工艺：", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = QuoteAccent)
            Text(
                text = summary + layoutSuffix,
                fontSize = 12.sp,
                color = QuoteMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Icon(
                Icons.Default.KeyboardArrowDown,
                contentDescription = "选择排版",
                tint = QuoteMuted,
                modifier = Modifier.size(16.dp)
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) }
        ) {
            LayoutKey.entries.forEach { layout ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = if (layout == state.form.selectedLayout) "✓  ${layout.label}" else "     ${layout.label}",
                            fontSize = 14.sp
                        )
                    },
                    onClick = {
                        onSelectLayout(layout)
                        onExpandedChange(false)
                    }
                )
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════
//  基础选项 / 印刷定制
// ══════════════════════════════════════════════════════════════

private data class ProcessRow(
    val label: String,
    val hint: String,
    val enabled: Boolean,
    /** 工厂加价走 extraFee 字段，不在 ProcessValues 里，得单独认出来 */
    val extra: Boolean = false,
    val field: ProcessField? = null,
    /** 满印/覆膜/裱纸的单双面，null 表示不显示单双选择器 */
    val sided: SidedType? = null,
    val onSidedChange: ((SidedType) -> Unit)? = null
)

@Composable
internal fun QuoteProcessGroups(state: QuoteUiState, vm: QuoteViewModel) {
    val p = state.form.processes
    val qty = state.form.orderQuantity

    // 顺序按设计稿：工厂加价夹在刀模费和杂费中间
    val basicRows = listOf(
        ProcessRow("模切费", tieredHint(qty, p.dieCutMinQuantity, p.dieCutMinFee, p.dieCutUnitPrice, "张"), p.dieCutEnabled, field = ProcessField.DIE_CUT),
        ProcessRow("刀模费", "${trimNumber(p.toolingFee)} 元", p.toolingEnabled, field = ProcessField.TOOLING),
        ProcessRow("工厂加价", "${trimNumber(state.form.extraFee)} 元", state.form.extraFeeEnabled, extra = true),
        ProcessRow("杂费", "${trimNumber(p.miscPerUnit)} 元/个", p.miscEnabled, field = ProcessField.MISC)
    )

    val printRows = listOf(
        ProcessRow("满印油墨", "${trimNumber(p.fullPrintUnitPrice)} 元/方", p.fullPrintEnabled, field = ProcessField.FULL_PRINT,
            sided = p.fullPrintSided, onSidedChange = { vm.setProcessSided(ProcessField.FULL_PRINT, it) }),
        ProcessRow("覆膜", "${trimNumber(p.laminationUnitPrice)} 元/方", p.laminationEnabled, field = ProcessField.LAMINATION,
            sided = p.laminationSided, onSidedChange = { vm.setProcessSided(ProcessField.LAMINATION, it) }),
        ProcessRow("裱纸", "${trimNumber(p.mountingUnitPrice)} 元/方", p.mountingEnabled, field = ProcessField.MOUNTING,
            sided = p.mountingSided, onSidedChange = { vm.setProcessSided(ProcessField.MOUNTING, it) }),
        ProcessRow("印刷费", tieredHint(qty, p.printingMinQuantity, p.printingMinFee, p.printingUnitPrice, "张"), p.printingEnabled, field = ProcessField.PRINTING),
        ProcessRow("丝印费", tieredHint(qty, p.screenPrintMinQuantity, p.screenPrintMinFee, p.screenPrintUnitPrice, "个"), p.screenPrintEnabled, field = ProcessField.SCREEN_PRINT)
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ProcessGroup(
            title = "基础选项",
            treePrefix = "├─",
            rows = basicRows,
            defaultExpanded = false,
            masterChecked = basicRows.any { it.enabled },
            onToggleMaster = { vm.setBasicGroupEnabled(it) },
            onToggleRow = { row, on ->
                if (row.extra) vm.setExtraFeeEnabled(on)
                else row.field?.let { vm.setProcessEnabled(it, on) }
            }
        )

        ProcessGroup(
            title = "印刷定制",
            treePrefix = "└─",
            rows = printRows,
            defaultExpanded = true,
            masterChecked = printRows.any { it.enabled },
            onToggleMaster = { vm.setPrintGroupEnabled(it) },
            onToggleRow = { row, on -> row.field?.let { vm.setProcessEnabled(it, on) } }
        )
    }
}

/**
 * 一组工艺：标题行（树形前缀 + 标题 + 展开箭头 + 总开关）+ 折叠起来的子项。
 * 子项标签带树形前缀，最后一项用 └─，其余用 ├─。
 */
@Composable
private fun ProcessGroup(
    title: String,
    treePrefix: String = "",
    rows: List<ProcessRow>,
    defaultExpanded: Boolean = true,
    masterChecked: Boolean,
    onToggleMaster: (Boolean) -> Unit,
    onToggleRow: (ProcessRow, Boolean) -> Unit
) {
    var expanded by remember { mutableStateOf(defaultExpanded) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 32.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { expanded = !expanded }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (treePrefix.isNotEmpty()) {
                    Text(treePrefix, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = QuoteGray55)
                }
                Text(title, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = QuoteGray55)
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "收起" else "展开",
                    tint = QuoteMuted,
                    modifier = Modifier.size(16.dp)
                )
            }
            QuoteToggle(checked = masterChecked, onCheckedChange = onToggleMaster)
        }

        if (expanded) {
            rows.forEachIndexed { index, row ->
                val branch = if (index == rows.lastIndex) "└─" else "├─"
                ProcessOptionRow(
                    label = "$branch ${row.label}（${row.hint}）",
                    checked = row.enabled,
                    onCheckedChange = { onToggleRow(row, it) },
                    sided = row.sided,
                    onSidedChange = row.onSidedChange
                )
            }
        }
    }
}

@Composable
private fun ProcessOptionRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    sided: SidedType? = null,
    onSidedChange: ((SidedType) -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(QuoteRowBg)
            .padding(horizontal = 36.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = QuoteGray85,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        // 单双面选择器（满印/覆膜/裱纸）
        if (sided != null && onSidedChange != null) {
            SidedToggle(
                sided = sided,
                onSidedChange = onSidedChange,
                modifier = Modifier.padding(start = 6.dp)
            )
        }
        Box(modifier = Modifier.padding(start = 8.dp)) {
            QuoteToggle(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

/**
 * 紧凑的单/双面选择器：「单」和「双」两个字并排，点击切换。
 */
@Composable
private fun SidedToggle(
    sided: SidedType,
    onSidedChange: (SidedType) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(width = 44.dp, height = 22.dp)
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, QuoteFieldStroke, RoundedCornerShape(6.dp))
            .clickable { onSidedChange(if (sided == SidedType.SINGLE) SidedType.DOUBLE else SidedType.SINGLE) }
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(width = 22.dp, height = 22.dp)
                    .clip(RoundedCornerShape(topStart = 5.dp, bottomStart = 5.dp))
                    .background(if (sided == SidedType.SINGLE) QuoteGreen else Color.Transparent),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "单",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (sided == SidedType.SINGLE) Color.White else QuoteGray66
                )
            }
            Box(
                modifier = Modifier
                    .size(width = 22.dp, height = 22.dp)
                    .clip(RoundedCornerShape(topEnd = 5.dp, bottomEnd = 5.dp))
                    .background(if (sided == SidedType.DOUBLE) QuoteGreen else Color.Transparent),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "双",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (sided == SidedType.DOUBLE) Color.White else QuoteGray66
                )
            }
        }
    }
}

/** 阶梯计价的费率提示，规则照搬 Web 端 Home.tsx 的 printingHint / dieCutHint */
private fun tieredHint(qty: Int, minQuantity: Int, minFee: Double, unitPrice: Double, unit: String): String = when {
    qty <= 0 -> "${trimNumber(minFee)} 元起"
    qty <= minQuantity -> "≤$minQuantity$unit，${trimNumber(minFee)} 元"
    else -> "$qty$unit × ${trimNumber(unitPrice)}元 = ${trimNumber(qty * unitPrice)} 元"
}

// ══════════════════════════════════════════════════════════════
//  附加费 —— 用户可以自己增删
// ══════════════════════════════════════════════════════════════

@Composable
internal fun QuoteSpecialFeeSection(state: QuoteUiState, vm: QuoteViewModel) {
    var editing by remember { mutableStateOf<SpecialFee?>(null) }
    var adding by remember { mutableStateOf(false) }

    val fees = state.form.specialFees
    val enabledFees = fees.filter { it.enabled }
    val summary = if (enabledFees.isEmpty()) {
        if (fees.isEmpty()) "未添加" else "已全部关闭"
    } else {
        enabledFees.joinToString(" + ") { "${it.name} ${trimNumber(it.amount)}元" }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("附加费：", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = QuoteAccent)
            Text(
                text = summary,
                fontSize = 12.sp,
                color = QuoteMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
        }

        fees.forEachIndexed { index, fee ->
            val branch = if (index == fees.lastIndex) "└─" else "├─"
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 点标签进编辑弹窗（改名 / 改金额 / 删除），别跟开关抢热区
                Text(
                    text = "$branch ${fee.name}（${trimNumber(fee.amount)} 元）",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = QuoteGray55,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { editing = fee }
                        .padding(vertical = 2.dp)
                )
                Box(modifier = Modifier.padding(start = 8.dp)) {
                    QuoteToggle(
                        checked = fee.enabled,
                        onCheckedChange = { vm.toggleSpecialFee(fee.id) }
                    )
                }
            }
        }

        TextButton(
            onClick = { adding = true },
            modifier = Modifier.heightIn(min = 36.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null, tint = QuoteGreen, modifier = Modifier.size(16.dp))
            Text("  添加附加费", fontSize = 13.sp, color = QuoteGreen, fontWeight = FontWeight.Medium)
        }
    }

    val target = editing
    if (adding || target != null) {
        SpecialFeeDialog(
            initial = target,
            onDismiss = { adding = false; editing = null },
            onConfirm = { name, amount ->
                if (target == null) vm.addSpecialFee(name, amount)
                else vm.updateSpecialFee(target.id, name, amount)
                adding = false
                editing = null
            },
            onDelete = target?.let {
                {
                    vm.deleteSpecialFee(it.id)
                    editing = null
                }
            }
        )
    }
}

@Composable
private fun SpecialFeeDialog(
    initial: SpecialFee?,
    onDismiss: () -> Unit,
    onConfirm: (String, Double) -> Unit,
    onDelete: (() -> Unit)?
) {
    // 用 id 做 key，换一条记录编辑时输入框要重新初始化
    var name by remember(initial?.id) { mutableStateOf(initial?.name.orEmpty()) }
    var amountText by remember(initial?.id) {
        mutableStateOf(initial?.amount?.let { trimNumber(it) } ?: "")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "添加附加费" else "编辑附加费", fontSize = 16.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { if (FEE_AMOUNT_INPUT.matches(it)) amountText = it },
                    label = { Text("金额（元）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name, amountText.toDoubleOrNull() ?: 0.0) }) {
                Text("保存", color = QuoteGreen, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text("删除", color = Color(0xFFD32F2F))
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("取消", color = QuoteGray66, textAlign = TextAlign.End)
                }
            }
        }
    )
}
