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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.border
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import android.util.Log
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.paperbox.app.BuildConfig
import com.paperbox.app.data.api.models.MaterialItem
import androidx.navigation.NavController
import java.net.URLEncoder
import kotlinx.coroutines.launch

// ── 设计稿颜色 ──
private val BgGray = Color(0xFFF5F5F5)
private val Green = Color(0xFF1B8A3E)
private val FilterBg = Color(0xFFFFFFFF)
private val FilterBorder = Color(0xFFE5E5E5)
private val ToggleBg = Color(0xFFF0F0F0)
private val CardBg = Color(0xFFFFFFFF)
private val TagBg = Color(0xFFF0F0F0)
private val TagText = Color(0xFF888888)
private val SizeText = Color(0xFF999999)
private val LabelGray = Color(0xFF888888)

// ── 类型渐变色 ──
private object MaterialTypeColors {
    data class TypeStyle(val name: String, val gradient: Brush, val icon: ImageVector)

    fun styleFor(type: String): TypeStyle = when {
        type.startsWith("image") -> TypeStyle(
            "图片",
            Brush.linearGradient(listOf(Color(0xFFC4956A), Color(0xFFB8845A))),
            Icons.Default.Image
        )
        type.startsWith("video") -> TypeStyle(
            "视频",
            Brush.linearGradient(listOf(Color(0xFFB8845A), Color(0xFFA0704A))),
            Icons.Default.VideoFile
        )
        type.contains("pdf") || type.contains("document") || type.contains("msword") -> TypeStyle(
            "PDF",
            Brush.linearGradient(listOf(Color(0xFFA8D8EA), Color(0xFF88B8D0))),
            Icons.Default.Description
        )
        type.contains("zip") || type.contains("compressed") || type.contains("archive") -> TypeStyle(
            "压缩包",
            Brush.linearGradient(listOf(Color(0xFF8AA0C8), Color(0xFF4C5C7E))),
            Icons.Default.Folder
        )
        else -> TypeStyle(
            "其他",
            Brush.linearGradient(listOf(Color(0xFFB0B8C8), Color(0xFF7A849A))),
            Icons.Default.Description
        )
    }
}

// ── 格式化文件大小 ──
private fun formatSize(bytes: Long): String = when {
    bytes >= 1_048_576 -> "%.1fMB".format(bytes / 1_048_576.0)
    bytes >= 1024 -> "%.0fKB".format(bytes / 1024.0)
    else -> "${bytes}B"
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MaterialsScreen(navController: NavController, viewModel: MaterialsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.uploadFiles(uris)
        }
    }

    LaunchedEffect(state.toastMessage) {
        state.toastMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            // ── 顶部导航栏 ──
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF007A12))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .height(62.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "素材",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (state.isSearchActive) {
                        Spacer(Modifier.width(12.dp))
                        BasicTextField(
                            value = state.searchFieldText,
                            onValueChange = { viewModel.updateSearchField(it) },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .height(24.dp)
                                .background(Color.White, RoundedCornerShape(10.dp))
                                .border(1.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(10.dp)),
                            textStyle = androidx.compose.ui.text.TextStyle(
                                fontSize = 14.sp,
                                color = Color(0xFF333333)
                            ),
                            decorationBox = { innerTextField ->
                                Box(
                                    contentAlignment = Alignment.CenterStart,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 1.dp)
                                ) {
                                    if (state.searchFieldText.isEmpty()) {
                                        Text("搜索素材…", color = Color(0xFF999999), fontSize = 14.sp)
                                    }
                                    innerTextField()
                                }
                            }
                        )
                        IconButton(
                            onClick = { viewModel.toggleSearch() },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "关闭搜索",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    } else {
                        Spacer(Modifier.weight(1f))
                        IconButton(
                            onClick = { viewModel.toggleSearch() },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = "搜索",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.showUploadSheet() },
                containerColor = Green,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier
                    .size(56.dp)
                    .shadow(8.dp, CircleShape, ambientColor = Color(0x401B8A3E), spotColor = Color(0x401B8A3E))
            ) {
                Icon(Icons.Default.Add, contentDescription = "上传", modifier = Modifier.size(28.dp))
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(BgGray)
        ) {
            // ── 筛选栏（可左右滑动） ──
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(FilterBg)
                    .padding(vertical = 8.dp)
            ) {
                // 使用服务端筛选计数
                val fc = state.filterCounts
                val totalCount = fc.total
                val colorCounts = fc.colorCounts
                val tagCounts = fc.tagCounts
                val typeCounts = fc.typeCounts

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom
                ) {
                    // 可滚动的下拉框
                    LazyRow(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(start = 12.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        // 分类下拉
                        item {
                            FilterDropdown(
                                label = "分类",
                                options = listOf("全部分类($totalCount)") + state.colors.map { "${it.name}(${colorCounts[it.name] ?: 0})" },
                                selectedIndex = if (state.selectedColor.isBlank()) 0
                                    else state.colors.indexOfFirst { it.name == state.selectedColor } + 1,
                                onSelect = { idx ->
                                    viewModel.selectColor(if (idx == 0) "" else state.colors[idx - 1].name)
                                },
                                modifier = Modifier.width(120.dp)
                            )
                        }
                        // 标签下拉
                        item {
                            FilterDropdown(
                                label = "标签",
                                options = listOf("全部标签($totalCount)") + state.tags.map { "$it(${tagCounts[it] ?: 0})" },
                                selectedIndex = if (state.selectedTags.isEmpty()) 0
                                    else state.tags.indexOfFirst { it in state.selectedTags } + 1,
                                onSelect = { idx ->
                                    if (idx == 0) {
                                        viewModel.clearTags()
                                    } else {
                                        viewModel.toggleTag(state.tags[idx - 1])
                                    }
                                },
                                modifier = Modifier.width(120.dp)
                            )
                        }
                        // 类型下拉
                        item {
                            FilterDropdown(
                                label = "类型",
                                options = listOf("全部类型($totalCount)", "图片(${typeCounts["image"] ?: 0})", "视频(${typeCounts["video"] ?: 0})", "文档(${typeCounts["doc"] ?: 0})"),
                                selectedIndex = when (state.selectedCategory) {
                                    "image" -> 1
                                    "video" -> 2
                                    "doc" -> 3
                                    else -> 0
                                },
                                onSelect = { idx ->
                                    val category = when (idx) {
                                        1 -> "image"
                                        2 -> "video"
                                        3 -> "doc"
                                        else -> "all"
                                    }
                                    viewModel.selectCategory(category)
                                },
                                modifier = Modifier.width(120.dp)
                            )
                        }
                    }

                    // 固定按钮：排序 + 视图切换
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(start = 8.dp, end = 12.dp)
                    ) {
                        // 排序切换
                        Box(
                            modifier = Modifier
                                .width(72.dp)
                                .height(42.dp)
                                .background(FilterBg, RoundedCornerShape(10.dp))
                                .border(1.dp, FilterBorder, RoundedCornerShape(10.dp))
                                .clickable { viewModel.toggleSort() }
                                .padding(horizontal = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    Icons.Default.SwapVert,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = Green
                                )
                                Text(
                                    if (state.sortOrder == "desc") "最新" else "最早",
                                    fontSize = 13.sp,
                                    color = Color(0xFF333333)
                                )
                            }
                        }
                        // 视图切换
                        Box(
                            modifier = Modifier
                                .width(42.dp)
                                .height(42.dp)
                                .background(FilterBg, RoundedCornerShape(10.dp))
                                .border(1.dp, FilterBorder, RoundedCornerShape(10.dp))
                                .clickable { viewModel.toggleLayout() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (state.layoutMode == "grid") Icons.Default.GridView else Icons.Default.ViewList,
                                contentDescription = if (state.layoutMode == "grid") "切换为列表" else "切换为网格",
                                modifier = Modifier.size(18.dp),
                                tint = Green
                            )
                        }
                    }
                }
            }

            // ── 素材区 ──
            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Green)
                }
            } else if (state.materials.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "暂无匹配的素材",
                            fontSize = 15.sp,
                            color = Color(0xFF333333)
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "试试清除筛选条件，\n或点击右下角「+」上传新素材",
                            fontSize = 13.sp,
                            color = SizeText,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                // 滚动状态记忆
                val gridState = rememberLazyGridState(
                    initialFirstVisibleItemIndex = state.scrollIndex,
                    initialFirstVisibleItemScrollOffset = state.scrollOffset
                )
                val listState = rememberLazyListState(
                    initialFirstVisibleItemIndex = state.scrollIndex,
                    initialFirstVisibleItemScrollOffset = state.scrollOffset
                )

                // 保存滚动位置 - Grid模式
                val coroutineScope = rememberCoroutineScope()
                DisposableEffect(gridState) {
                    val job = coroutineScope.launch {
                        snapshotFlow {
                            gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset
                        }.collect { (index, offset) ->
                            viewModel.saveScrollPosition(index, offset)
                        }
                    }
                    onDispose { job.cancel() }
                }

                // 保存滚动位置 - List模式
                DisposableEffect(listState) {
                    val job = coroutineScope.launch {
                        snapshotFlow {
                            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
                        }.collect { (index, offset) ->
                            viewModel.saveScrollPosition(index, offset)
                        }
                    }
                    onDispose { job.cancel() }
                }

                when (state.layoutMode) {
                    "grid" -> {
                        // 无限滚动：滑到底部自动加载更多
                        LaunchedEffect(gridState, state.hasMore, state.isLoadingMore) {
                            snapshotFlow { gridState.layoutInfo }
                                .collect { layoutInfo ->
                                    val totalItems = layoutInfo.totalItemsCount
                                    val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                                    if (totalItems > 0 && lastVisible >= totalItems - 3 && state.hasMore && !state.isLoadingMore) {
                                        viewModel.loadMore()
                                    }
                                }
                        }
                        LazyVerticalGrid(
                            state = gridState,
                            columns = GridCells.Fixed(2),
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(state.materials, key = { it.id }) { material ->
                                MaterialGridCard(
                                    material = material,
                                    videoThumbnail = state.videoThumbnails[material.id],
                                    onClick = {
                                        val idx = state.materials.indexOf(material)
                                        val json = com.squareup.moshi.Moshi.Builder().build()
                                            .adapter(List::class.java)
                                            .toJson(state.materials)
                                        navController.currentBackStackEntry?.savedStateHandle?.apply {
                                            set("materials_json", json)
                                            set("current_index", idx)
                                        }
                                        val encodedType = URLEncoder.encode(material.type, "UTF-8")
                                        navController.navigate("media_viewer/${material.id}/$encodedType")
                                    },
                                    onMore = { viewModel.showMaterialOptions(material) }
                                )
                            }
                        }
                    }
                    "list" -> {
                        // 无限滚动：滑到底部自动加载更多
                        LaunchedEffect(listState, state.hasMore, state.isLoadingMore) {
                            snapshotFlow { listState.layoutInfo }
                                .collect { layoutInfo ->
                                    val totalItems = layoutInfo.totalItemsCount
                                    val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                                    if (totalItems > 0 && lastVisible >= totalItems - 3 && state.hasMore && !state.isLoadingMore) {
                                        viewModel.loadMore()
                                    }
                                }
                        }
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(state.materials, key = { it.id }) { material ->
                                MaterialListCard(
                                    material = material,
                                    videoThumbnail = state.videoThumbnails[material.id],
                                    onClick = {
                                        val idx = state.materials.indexOf(material)
                                        val json = com.squareup.moshi.Moshi.Builder().build()
                                            .adapter(List::class.java)
                                            .toJson(state.materials)
                                        navController.currentBackStackEntry?.savedStateHandle?.apply {
                                            set("materials_json", json)
                                            set("current_index", idx)
                                        }
                                        val encodedType = URLEncoder.encode(material.type, "UTF-8")
                                        navController.navigate("media_viewer/${material.id}/$encodedType")
                                    },
                                    onMore = { viewModel.showMaterialOptions(material) }
                                )
                            }
                        }
                    }
                }
            }
        }
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
            ListItem(
                headlineContent = { Text("选择本地文件") },
                supportingContent = { Text("支持图片 / 视频，可多选") },
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
            ListItem(
                headlineContent = { Text("下载素材") },
                supportingContent = { Text("保存到本地") },
                leadingContent = {
                    Surface(shape = RoundedCornerShape(11.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.padding(8.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                },
                modifier = Modifier.clickable { viewModel.downloadMaterial() }
            )
            ListItem(
                headlineContent = { Text("编辑标签") },
                supportingContent = { Text("修改素材所属标签") },
                leadingContent = {
                    Surface(shape = RoundedCornerShape(11.dp), color = Color(0xFFFFF3E0), modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.Label, contentDescription = null, modifier = Modifier.padding(8.dp), tint = Color(0xFFED8936))
                    }
                },
                modifier = Modifier.clickable { viewModel.showTagDialogForMaterial() }
            )
            ListItem(
                headlineContent = { Text("删除素材", color = MaterialTheme.colorScheme.error) },
                supportingContent = { Text("删除后不可恢复", color = Color(0xFFF8A3A3)) },
                leadingContent = {
                    Surface(shape = RoundedCornerShape(11.dp), color = Color(0xFFFEE2E2), modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.padding(8.dp), tint = MaterialTheme.colorScheme.error)
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
                TextButton(onClick = { viewModel.saveMaterialTags() }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissTagDialog() }) { Text("取消") }
            }
        )
    }

    // ── 删除确认弹窗 ──
    if (state.showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissDeleteDialog() },
            title = { Text("确认删除？", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) },
            text = { Text("「${state.selectedMaterial?.name}」删除后将无法恢复") },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmDelete() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDeleteDialog() }) { Text("取消") }
            }
        )
    }
}

// ── 筛选下拉框 ──
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterDropdown(
    label: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .background(FilterBg, RoundedCornerShape(10.dp))
                    .menuAnchor()
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        options.getOrElse(selectedIndex) { label },
                        fontSize = 13.sp,
                        color = Color(0xFF333333)
                    )
                    Icon(
                        Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = Color(0xFF999999)
                    )
                }
            }
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEachIndexed { idx, text ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text,
                                fontWeight = if (idx == selectedIndex) FontWeight.SemiBold else FontWeight.Normal
                            )
                        },
                        onClick = {
                            onSelect(idx)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

// ── 网格卡片 ──
@Composable
private fun MaterialGridCard(
    material: MaterialItem,
    videoThumbnail: android.graphics.Bitmap? = null,
    onClick: () -> Unit,
    onMore: () -> Unit
) {
    val typeStyle = MaterialTypeColors.styleFor(material.type)

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column {
            // 缩略图区域
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .background(typeStyle.gradient)
            ) {
                if (material.type.startsWith("image")) {
                    val imageUrl = "${BuildConfig.API_BASE_URL}/materials-api/materials/${material.id}/file"
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                            .data(imageUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = material.name,
                        modifier = Modifier.fillMaxSize().background(Color.Black),
                        contentScale = ContentScale.Crop,
                        loading = {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                            }
                        },
                        error = {
                            Box(
                                modifier = Modifier.fillMaxSize().background(typeStyle.gradient),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(typeStyle.icon, contentDescription = null, modifier = Modifier.size(24.dp), tint = Color.White.copy(alpha = 0.7f))
                            }
                        }
                    )
                } else if (material.type.startsWith("video") && videoThumbnail != null) {
                    androidx.compose.foundation.Image(
                        bitmap = videoThumbnail.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        typeStyle.icon,
                        contentDescription = null,
                        modifier = Modifier.align(Alignment.Center).size(32.dp),
                        tint = Color.White.copy(alpha = 0.9f)
                    )
                }
                // 类型 badge（左下角）
                Surface(
                    shape = RoundedCornerShape(3.dp),
                    color = Color.Black.copy(alpha = 0.4f),
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp).align(Alignment.BottomStart)
                ) {
                    Text(
                        text = typeStyle.name,
                        color = Color.White,
                        fontSize = 9.sp,
                        lineHeight = 9.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                // 文件大小 badge（右下角）
                Surface(
                    shape = RoundedCornerShape(3.dp),
                    color = Color.Black.copy(alpha = 0.4f),
                    modifier = Modifier.padding(end = 4.dp, bottom = 4.dp).align(Alignment.BottomEnd)
                ) {
                    Text(
                        text = formatSize(material.size),
                        color = Color.White,
                        fontSize = 9.sp,
                        lineHeight = 9.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // 信息区
            Column(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // 标签行
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (material.tags.isNotEmpty()) {
                        material.tags.take(2).forEach { tag ->
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = TagBg
                            ) {
                                Text(
                                    text = tag,
                                    fontSize = 10.sp,
                                    lineHeight = 10.sp,
                                    color = TagText
                                )
                            }
                        }
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
    videoThumbnail: android.graphics.Bitmap? = null,
    onClick: () -> Unit,
    onMore: () -> Unit
) {
    val typeStyle = MaterialTypeColors.styleFor(material.type)

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(typeStyle.gradient),
                contentAlignment = Alignment.Center
            ) {
                if (material.type.startsWith("image")) {
                    val imageUrl = "${BuildConfig.API_BASE_URL}/materials-api/materials/${material.id}/file"
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                            .data(imageUrl).crossfade(true).build(),
                        contentDescription = material.name,
                        modifier = Modifier.fillMaxSize().background(Color.Black),
                        contentScale = ContentScale.Crop,
                        error = {
                            Icon(typeStyle.icon, contentDescription = null, modifier = Modifier.size(22.dp), tint = Color.White.copy(alpha = 0.9f))
                        }
                    )
                } else if (material.type.startsWith("video") && videoThumbnail != null) {
                    androidx.compose.foundation.Image(
                        bitmap = videoThumbnail.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().background(Color.Black),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(typeStyle.icon, contentDescription = null, modifier = Modifier.size(22.dp), tint = Color.White.copy(alpha = 0.9f))
                }
                // 文件大小 badge（右下角）
                Surface(
                    shape = RoundedCornerShape(2.dp),
                    color = Color.Black.copy(alpha = 0.45f),
                    modifier = Modifier.align(Alignment.BottomEnd).padding(1.dp)
                ) {
                    Text(
                        text = formatSize(material.size),
                        color = Color.White,
                        fontSize = 6.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (material.tags.isNotEmpty()) {
                        material.tags.take(2).forEach { tag ->
                            Surface(shape = RoundedCornerShape(4.dp), color = TagBg) {
                                Text(
                                    text = tag,
                                    fontSize = 10.sp,
                                    lineHeight = 10.sp,
                                    color = TagText
                                )
                            }
                        }
                    }
                }
            }
            Surface(
                shape = RoundedCornerShape(7.dp),
                color = ToggleBg,
                modifier = Modifier
                    .size(26.dp)
                    .clickable { onMore() }
            ) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = "更多",
                    modifier = Modifier.padding(5.dp),
                    tint = Color(0xFF999999)
                )
            }
        }
    }
}
