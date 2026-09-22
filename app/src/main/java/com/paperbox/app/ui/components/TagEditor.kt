package com.paperbox.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

/**
 * 素材标签编辑器。
 *
 * - 已选标签渲染为可删除的 chip；
 * - 输入框可直接输入新标签，回车或点「+」添加；
 * - 输入时对已有标签（词表来自 GET /materials/tags）做过滤建议，
 *   点建议即选中，标签变多后不必肉眼扫全表。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagEditor(
    selected: Set<String>,
    availableTags: List<String>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
    maxTags: Int = 10, // 后端 MAX_TAGS = 10
) {
    var input by remember { mutableStateOf("") }
    val trimmed = input.trim()
    val atLimit = selected.size >= maxTags

    // 输入为空 → 展示全部未选中的已有标签；输入非空 → 包含匹配（忽略大小写）
    val matches = availableTags.filter { tag ->
        tag !in selected && (trimmed.isEmpty() || tag.contains(trimmed, ignoreCase = true))
    }
    val exactMatch = matches.any { it.equals(trimmed, ignoreCase = true) }
    val canCreate = !atLimit && trimmed.isNotEmpty() && trimmed !in selected && !exactMatch

    fun commit() {
        val tag = input.trim()
        if (tag.isEmpty() || atLimit) return
        onAdd(tag)
        input = ""
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 已选标签：点 × 移除
        if (selected.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                selected.forEach { tag ->
                    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = tag,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(start = 14.dp, top = 4.dp, bottom = 4.dp)
                            )
                            IconButton(
                                onClick = { onRemove(tag) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "删除标签 $tag",
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }

        // 输入 + 添加
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text("加标签（${selected.size}/$maxTags）") },
                singleLine = true,
                enabled = !atLimit,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { commit() }),
                supportingText = if (atLimit) {
                    { Text("最多 $maxTags 个，先删除一个再添加") }
                } else null,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { commit() }, enabled = !atLimit && trimmed.isNotEmpty()) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "添加标签")
            }
        }

        // 已有标签建议：输入即过滤，点一下选中；无匹配时给出「新建」入口
        if (!atLimit && (matches.isNotEmpty() || canCreate)) {
            Text(
                text = if (trimmed.isEmpty()) "已有标签（点击添加）" else "匹配「$trimmed」",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.typography.onSurfaceVariant
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                matches.forEach { tag ->
                    SuggestionChip(tag = tag, onClick = {
                        onAdd(tag)
                        input = ""
                    })
                }
                if (canCreate) {
                    SuggestionChip(
                        tag = "新建「$trimmed」",
                        onClick = { commit() },
                        emphasized = true
                    )
                }
            }
        }
    }
}

@Composable
private fun SuggestionChip(
    tag: String,
    onClick: () -> Unit,
    emphasized: Boolean = false,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (emphasized) MaterialTheme.colorScheme.tertiaryContainer
               else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = tag,
            color = if (emphasized) MaterialTheme.colorScheme.onTertiaryContainer
                   else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}
