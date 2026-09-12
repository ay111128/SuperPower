package com.paperbox.app.ui.materials

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.paperbox.app.data.api.models.MaterialItem

// ── 类型渐变色 ──
private object MaterialTypeColors {
    data class TypeStyle(val name: String, val gradient: Brush, val icon: ImageVector)

    fun styleFor(type: String): TypeStyle = when (type) {
        "image" -> TypeStyle(
            "图片",
            Brush.linearGradient(listOf(Color(0xFF6AA6FF), Color(0xFF3B5BFF))),
            Icons.Default.Image
        )
        "doc" -> TypeStyle(
            "文档",
            Brush.linearGradient(listOf(Color(0xFFFFC27A), Color(0xFFFF8A3D))),
            Icons.Default.Description
        )
        "video" -> TypeStyle(
            "视频",
            Brush.linearGradient(listOf(Color(0xFFC77CFF), Color(0xFF8A3DFF))),
            Icons.Default.VideoFile
        )
        "zip" -> TypeStyle(
            "压缩包",
            Brush.linearGradient(listOf(Color(0xFF8AA0C8), Color(0xFF4C5C7E))),
            Icons.Default.FolderZip
        )
        else -> TypeStyle(
            "其他",
            Brush.linearGradient(listOf(Color(0xFFB0B8C8), Color(0xFF7A849A))),
            Icons.Default.InsertDriveFile
        )
    }
}

// ── 分类数据 ──
private val categories = listOf(
    "all" to "全部素材",
    "image" to "图片",
    "doc" to "文档",
    "video" to "视频",
    "zip" to "压缩包"
)

// ── 格式化文件大小 ──
private fun formatSize(bytes: Long): String = when {
    bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1024 -> "%.0f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MaterialsScreen(viewModel: MaterialsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.uploadFiles(uris)
        }
    }

    // Toast
    LaunchedEffect(state.toastMessage) {
        state.toastMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            // 顶部导航
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "素材管理",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 搜索按钮
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 1.dp,
                        modifier = Modifier
                            .size(40.dp)
                            .clickable { viewModel.showSearchDialog() }
                    ) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "搜索",
                            modifier = Modifier
                                .padding(8.dp)
                                .size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    // 上传按钮
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(44.dp)
                            .clickable { viewModel.showUploadSheet() }
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "上传",
                            modifier = Modifier
                                .padding(10.dp)
                                .size(24.dp),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // ── 分类 Tab ──
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                items(categories) { (key, label) ->
                    val isSelected = state.selectedCategory == key
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.surface,
                        border = if (!isSelected) ButtonDefaults.outlinedButtonBorder(enabled = true) else null,
                        modifier = Modifier.clickable { viewModel.selectCategory(key) }
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                   else MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp)
                        )
                    }
                }
            }

            // ── 标签筛选 ──
            if (state.tags.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    item {
                        Text(
                            "标签",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    items(state.tags) { tag ->
                        val isSelected = tag in state.selectedTags
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) Color(0xFFFFF7E8)
                                   else MaterialTheme.colorScheme.surface,
                            border = ButtonDefaults.outlinedButtonBorder(enabled = !isSelected),
                            modifier = Modifier.clickable { viewModel.toggleTag(tag) }
                        ) {
                            Text(
                                text = tag,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) Color(0xFFB45309)
                                       else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            // ── 统计条 ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "共 ${state.total} 个素材",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // 网格布局按钮
                    IconButton(
                        onClick = { if (state.layoutMode != "grid") viewModel.toggleLayout() },
                        modifier = Modifier
                            .size(30.dp)
                            .background(
                                if (state.layoutMode == "grid") MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surface,
                                RoundedCornerShape(8.dp)
                            )
                    ) {
                        Icon(
                            Icons.Default.GridView,
                            contentDescription = "网格",
                            modifier = Modifier.size(16.dp),
                            tint = if (state.layoutMode == "grid") MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    // 列表布局按钮
                    IconButton(
                        onClick = { if (state.layoutMode != "list") viewModel.toggleLayout() },
                        modifier = Modifier
                            .size(30.dp)
                            .background(
                                if (state.layoutMode == "list") MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surface,
                                RoundedCornerShape(8.dp)
                            )
                    ) {
                        Icon(
                            Icons.Default.ViewList,
                            contentDescription = "列表",
                            modifier = Modifier.size(16.dp),
                            tint = if (state.layoutMode == "list") MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // ── 素材区 ──
            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (state.materials.isEmpty()) {
                // 空状态
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            shape = RoundedCornerShape(28.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = ButtonDefaults.outlinedButtonBorder(enabled = true),
                            modifier = Modifier.size(96.dp)
                        ) {
                            Icon(
                                Icons.Outlined.CloudUpload,
                                contentDescription = null,
                                modifier = Modifier.padding(24.dp),
                                tint = Color(0xFFAEB7D0)
                            )
                        }
                        Spacer(Modifier.height(18.dp))
                        Text(
                            "暂无匹配的素材",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "试试清除筛选条件，\n或点击右上角「+」上传新素材",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                when (state.layoutMode) {
                    "grid" -> LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(state.materials, key = { it.id }) { material ->
                            MaterialGridCard(
                                material = material,
                                onClick = { viewModel.showToast("打开素材：${material.name}") },
                                onMore = { viewModel.showMaterialOptions(material) }
                            )
                        }
                    }
                    "list" -> LazyColumn(
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(state.materials, key = { it.id }) { material ->
                            MaterialListCard(
                                material = material,
                                onClick = { viewModel.showToast("打开素材：${material.name}") },
                                onMore = { viewModel.showMaterialOptions(material) }
                            )
                        }
                    }
                }
            }
        }
    }

    // ── 搜索弹窗 ──
    if (state.showSearchDialog) {
        var searchInput by remember { mutableStateOf(state.searchQuery) }
        AlertDialog(
            onDismissRequest = { viewModel.dismissSearchDialog() },
            title = { Text("搜索素材", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = searchInput,
                    onValueChange = { searchInput = it },
                    placeholder = { Text("输入素材名称或标签…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.updateSearch(searchInput)
                    viewModel.dismissSearchDialog()
                }) {
                    Text("搜索")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissSearchDialog() }) {
                    Text("取消")
                }
            }
        )
    }

    // ── 上传底部弹窗 ──
    if (state.showUploadSheet) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.dismissUploadSheet() },
            shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
        ) {
            Text(
                "上传素材",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                textAlign = TextAlign.Center
            )

            // 选择本地文件
            ListItem(
                headlineContent = { Text("选择本地文件") },
                supportingContent = { Text("支持图片 / 文档 / 视频 / 压缩包，可多选") },
                leadingContent = {
                    Surface(
                        shape = RoundedCornerShape(11.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Default.FileUpload,
                            contentDescription = null,
                            modifier = Modifier.padding(8.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                modifier = Modifier.clickable {
                    viewModel.dismissUploadSheet()
                    filePicker.launch("image/*,video/*")
                }
            )

            // 从相册导入
            ListItem(
                headlineContent = { Text("从相册导入") },
                supportingContent = { Text("直接选择手机相册中的图片") },
                leadingContent = {
                    Surface(
                        shape = RoundedCornerShape(11.dp),
                        color = Color(0xFFFFF3E0),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Default.PhotoLibrary,
                            contentDescription = null,
                            modifier = Modifier.padding(8.dp),
                            tint = Color(0xFFED8936)
                        )
                    }
                },
                modifier = Modifier.clickable {
                    viewModel.dismissUploadSheet()
                    filePicker.launch("image/*")
                }
            )

            Spacer(Modifier.height(24.dp))
        }
    }

    // ── 更多操作底部弹窗 ──
    if (state.showMoreSheet) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.dismissMoreSheet() },
            shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
        ) {
            Text(
                state.selectedMaterial?.name ?: "素材操作",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                textAlign = TextAlign.Center
            )

            // 下载
            ListItem(
                headlineContent = { Text("下载素材") },
                supportingContent = { Text("保存到本地") },
                leadingContent = {
                    Surface(
                        shape = RoundedCornerShape(11.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Default.FileDownload,
                            contentDescription = null,
                            modifier = Modifier.padding(8.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                modifier = Modifier.clickable { viewModel.downloadMaterial() }
            )

            // 编辑标签
            ListItem(
                headlineContent = { Text("编辑标签") },
                supportingContent = { Text("修改素材所属标签") },
                leadingContent = {
                    Surface(
                        shape = RoundedCornerShape(11.dp),
                        color = Color(0xFFFFF3E0),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Default.Label,
                            contentDescription = null,
                            modifier = Modifier.padding(8.dp),
                            tint = Color(0xFFED8936)
                        )
                    }
                },
                modifier = Modifier.clickable { viewModel.showTagDialogForMaterial() }
            )

            // 删除
            ListItem(
                headlineContent = { Text("删除素材", color = MaterialTheme.colorScheme.error) },
                supportingContent = {
                    Text("删除后不可恢复，请谨慎操作", color = Color(0xFFF8A3A3))
                },
                leadingContent = {
                    Surface(
                        shape = RoundedCornerShape(11.dp),
                        color = Color(0xFFFEE2E2),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.padding(8.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                },
                modifier = Modifier.clickable { viewModel.showDeleteDialog() }
            )

            Spacer(Modifier.height(24.dp))
        }
    }

    // ── 编辑标签弹窗 ──
    if (state.showTagDialog) {
        val availableTags = remember(state.tags, state.selectedMaterial) {
            state.tags.ifEmpty { listOf("设计稿", "产品图", "封面", "图标", "合同") }
        }
        AlertDialog(
            onDismissRequest = { viewModel.dismissTagDialog() },
            title = { Text("编辑标签", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        "编辑「${state.selectedMaterial?.name}」的标签",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        availableTags.forEach { tag ->
                            val isSelected = tag in state.tagDraft
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                       else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.clickable { viewModel.toggleTagDraft(tag) }
                            ) {
                                Text(
                                    text = tag,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary
                                           else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.saveMaterialTags() }) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissTagDialog() }) {
                    Text("取消")
                }
            }
        )
    }

    // ── 删除确认弹窗 ──
    if (state.showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissDeleteDialog() },
            title = { Text("确认删除？", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) },
            text = {
                Text("「${state.selectedMaterial?.name}」删除后将无法恢复")
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmDelete() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDeleteDialog() }) {
                    Text("取消")
                }
            }
        )
    }
}

// ── 网格卡片 ──
@Composable
private fun MaterialGridCard(
    material: MaterialItem,
    onClick: () -> Unit,
    onMore: () -> Unit
) {
    val typeStyle = MaterialTypeColors.styleFor(material.type)

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column {
            // 缩略图区域
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(132.dp)
                    .background(typeStyle.gradient)
            ) {
                // 类型图标
                Icon(
                    typeStyle.icon,
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(40.dp),
                    tint = Color.White.copy(alpha = 0.9f)
                )
                // 类型 badge
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color.White.copy(alpha = 0.22f),
                    modifier = Modifier.padding(8.dp)
                ) {
                    Text(
                        text = typeStyle.name,
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            // 信息区
            Column(modifier = Modifier.padding(horizontal = 11.dp, vertical = 10.dp)) {
                Text(
                    text = material.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(7.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (material.tags.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFFFFF7E8)
                            ) {
                                Text(
                                    text = "# ${material.tags.first()}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFB45309),
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = formatSize(material.size),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(7.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .size(26.dp)
                            .clickable { onMore() }
                    ) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "更多",
                            modifier = Modifier.padding(5.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

// ── 列表卡片 ──
@Composable
private fun MaterialListCard(
    material: MaterialItem,
    onClick: () -> Unit,
    onMore: () -> Unit
) {
    val typeStyle = MaterialTypeColors.styleFor(material.type)

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 小缩略图
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(typeStyle.gradient),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    typeStyle.icon,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = Color.White.copy(alpha = 0.9f)
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = material.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (material.tags.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFFFF7E8)
                        ) {
                            Text(
                                text = "# ${material.tags.first()}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFB45309),
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = formatSize(material.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 更多按钮
            Surface(
                shape = RoundedCornerShape(7.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .size(26.dp)
                    .clickable { onMore() }
            ) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = "更多",
                    modifier = Modifier.padding(5.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
