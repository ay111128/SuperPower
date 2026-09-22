package com.paperbox.app.ui.media

import android.app.Activity
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.paperbox.app.BuildConfig
import com.paperbox.app.data.api.models.MaterialItem
import com.paperbox.app.ui.components.TagEditor
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import coil.request.ImageRequest
import kotlinx.coroutines.launch

/**
 * 全屏状态：0=普通(有Scaffold+状态栏)，1=全屏(无状态栏，有返回键)，2=纯净(全隐藏)
 */
private const val VIEW_NORMAL = 0
private const val VIEW_FULLSCREEN = 1
private const val VIEW_PURE = 2

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaViewerScreen(
    materialId: String,
    materialType: String,
    onBack: () -> Unit,
    materialsJson: String? = null,
    currentIndex: Int = 0,
    onDeleted: () -> Unit = onBack,
    onMaterialUpdated: () -> Unit = {},
    viewModel: MediaViewerViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // 解析素材列表（var：编辑保存后就地更新，翻页不回退到旧数据）
    var materials by remember(materialsJson) {
        mutableStateOf(
            if (!materialsJson.isNullOrBlank()) {
                try {
                    val type = Types.newParameterizedType(List::class.java, MaterialItem::class.java)
                    Moshi.Builder().build().adapter<List<MaterialItem>>(type).fromJson(materialsJson) ?: emptyList()
                } catch (_: Exception) { emptyList() }
            } else emptyList()
        )
    }
    val hasMultiple = materials.size > 1

    // Pager 状态
    val initialPage = currentIndex.coerceIn(0, (materials.size - 1).coerceAtLeast(0))
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { materials.size })

    // 当前显示的素材
    var currentMaterial by remember {
        mutableStateOf(
            materials.getOrElse(initialPage) {
                MaterialItem(id = materialId, name = "", type = materialType)
            }
        )
    }

    // 滑动时更新当前素材
    LaunchedEffect(pagerState.currentPage) {
        if (materials.isNotEmpty()) {
            currentMaterial = materials[pagerState.currentPage]
        }
    }

    val fileUrl = "${BuildConfig.API_BASE_URL}/materials-api/materials/${currentMaterial.id}/file"

    var showMenu by remember { mutableStateOf(false) }
    var viewState by remember { mutableIntStateOf(VIEW_FULLSCREEN) }
    // 跟踪图片缩放状态，缩放时禁止 Pager 滑动
    var isImageZoomed by remember { mutableStateOf(false) }

    // ── 编辑弹窗状态 ──
    var showEditDialog by remember { mutableStateOf(false) }
    var nameDraft by remember { mutableStateOf("") }
    var remarkDraft by remember { mutableStateOf("") }
    var tagDraft by remember { mutableStateOf<Set<String>>(emptySet()) }
    var availableTags by remember { mutableStateOf<List<String>>(emptyList()) }
    var isSaving by remember { mutableStateOf(false) }

    fun openEditDialog() {
        nameDraft = currentMaterial.name
        remarkDraft = currentMaterial.remark
        tagDraft = currentMaterial.tags.toSet()
        // 先展示素材已有标签，随后端词表补齐
        availableTags = currentMaterial.tags
        showMenu = false
        showEditDialog = true
        viewModel.fetchTags { tags ->
            availableTags = (tags + currentMaterial.tags).distinct()
        }
    }

    BackHandler {
        when (viewState) {
            VIEW_PURE -> viewState = VIEW_FULLSCREEN
            VIEW_FULLSCREEN -> onBack()
            else -> onBack()
        }
    }

    // 系统栏控制
    DisposableEffect(viewState) {
        val activity = context as? Activity
        if (activity != null) {
            val window = activity.window
            when (viewState) {
                VIEW_NORMAL -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        window.insetsController?.show(
                            android.view.WindowInsets.Type.statusBars() or
                            android.view.WindowInsets.Type.navigationBars()
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
                    }
                    window.statusBarColor = android.graphics.Color.BLACK
                    window.navigationBarColor = android.graphics.Color.BLACK
                }
                VIEW_FULLSCREEN, VIEW_PURE -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        val controller = window.insetsController
                        controller?.hide(
                            android.view.WindowInsets.Type.statusBars() or
                            android.view.WindowInsets.Type.navigationBars() or
                            android.view.WindowInsets.Type.systemBars()
                        )
                        controller?.systemBarsBehavior =
                            android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                        window.navigationBarColor = android.graphics.Color.TRANSPARENT
                    } else {
                        @Suppress("DEPRECATION")
                        window.decorView.systemUiVisibility = (
                            View.SYSTEM_UI_FLAG_FULLSCREEN or
                            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        )
                    }
                    window.statusBarColor = android.graphics.Color.TRANSPARENT
                    window.navigationBarColor = android.graphics.Color.TRANSPARENT
                }
            }
        }
        onDispose {
            val act = context as? Activity
            if (act != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    act.window.insetsController?.show(
                        android.view.WindowInsets.Type.statusBars() or
                        android.view.WindowInsets.Type.navigationBars()
                    )
                } else {
                    @Suppress("DEPRECATION")
                    act.window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
                }
                act.window.statusBarColor = android.graphics.Color.TRANSPARENT
                act.window.navigationBarColor = android.graphics.Color.TRANSPARENT
            }
        }
    }

    // 长按/⋮ 菜单：底部弹窗，编辑 / 下载 / 删除 三项竖排（与列表页「更多操作」统一）
    if (showMenu) {
        ModalBottomSheet(
            onDismissRequest = { showMenu = false },
            shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
        ) {
            Text(
                currentMaterial.name.ifBlank { "素材操作" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                textAlign = TextAlign.Center
            )
            ListItem(
                headlineContent = { Text("编辑素材") },
                supportingContent = { Text("名称、描述、标签") },
                leadingContent = {
                    Surface(
                        shape = RoundedCornerShape(11.dp),
                        color = Color(0xFFFFF3E0),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = null,
                            modifier = Modifier.padding(8.dp),
                            tint = Color(0xFFED8936)
                        )
                    }
                },
                modifier = Modifier.clickable { openEditDialog() }
            )
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
                modifier = Modifier.clickable {
                    showMenu = false
                    val ext = if (currentMaterial.type.contains("png")) ".png"
                              else if (currentMaterial.type.startsWith("video")) ".mp4"
                              else ".jpg"
                    viewModel.downloadFile(
                        materialId = currentMaterial.id,
                        filename = "${currentMaterial.id}$ext",
                        saveAsOriginal = true
                    ) { _, msg ->
                        scope.launch { snackbarHostState.showSnackbar(msg) }
                    }
                }
            )
            ListItem(
                headlineContent = { Text("删除素材", color = MaterialTheme.colorScheme.error) },
                supportingContent = { Text("删除后不可恢复", color = Color(0xFFF8A3A3)) },
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
                modifier = Modifier.clickable {
                    showMenu = false
                    // 成功 → 立刻通知列表页刷新并返回；失败 → 顶层 Snackbar 提示
                    viewModel.deleteMaterial(currentMaterial.id) { success, msg ->
                        if (success) {
                            onDeleted()
                        } else {
                            scope.launch { snackbarHostState.showSnackbar(msg) }
                        }
                    }
                }
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    // ── 编辑弹窗：名称 + 描述 + 标签（后端 PATCH 均已支持） ──
    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { if (!isSaving) showEditDialog = false },
            title = { Text("编辑素材", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = nameDraft,
                        onValueChange = { if (it.length <= 200) nameDraft = it },
                        label = { Text("名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = remarkDraft,
                        onValueChange = { if (it.length <= 500) remarkDraft = it },
                        label = { Text("描述") },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                    TagEditor(
                        selected = tagDraft,
                        availableTags = availableTags,
                        onAdd = { tag ->
                            val t = tag.trim()
                            if (t.isNotEmpty() && t !in tagDraft && tagDraft.size < 10) {
                                tagDraft = tagDraft + t
                            }
                        },
                        onRemove = { tagDraft = tagDraft - it }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = nameDraft.isNotBlank() && !isSaving,
                    onClick = {
                        isSaving = true
                        viewModel.updateMaterial(
                            materialId = currentMaterial.id,
                            name = nameDraft.trim(),
                            remark = remarkDraft.trim(),
                            tags = tagDraft.toList()
                        ) { success, msg ->
                            isSaving = false
                            if (success) {
                                // 就地更新列表与当前页，翻页不回退旧数据
                                val updated = currentMaterial.copy(
                                    name = nameDraft.trim(),
                                    remark = remarkDraft.trim(),
                                    tags = tagDraft.toList()
                                )
                                materials = materials.map { if (it.id == updated.id) updated else it }
                                currentMaterial = updated
                                showEditDialog = false
                                onMaterialUpdated()
                            }
                            scope.launch { snackbarHostState.showSnackbar(msg) }
                        }
                    }
                ) {
                    Text(if (isSaving) "保存中…" else "保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) { Text("取消") }
            }
        )
    }

    // ── 核心内容：根据模式渲染 ──
    @Composable
    fun PagerContent(onTap: () -> Unit) {
        if (hasMultiple) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = !isImageZoomed
            ) { page ->
                val material = materials[page]
                val url = "${BuildConfig.API_BASE_URL}/materials-api/materials/${material.id}/file"
                PagerPage(
                    materialType = material.type,
                    fileUrl = url,
                    okHttpClient = viewModel.okHttpClient,
                    onTap = onTap,
                    onZoomChanged = { zoomed -> isImageZoomed = zoomed }
                )
            }
        } else {
            PagerPage(
                materialType = currentMaterial.type,
                fileUrl = fileUrl,
                okHttpClient = viewModel.okHttpClient,
                onTap = onTap,
                onZoomChanged = { zoomed -> isImageZoomed = zoomed }
            )
        }
    }

    // Snackbar 挂在最外层：普通/全屏/纯净三种模式都能显示（原来只挂在普通模式，全屏删除失败无提示）
    Box(modifier = Modifier.fillMaxSize()) {
    when (viewState) {
        VIEW_NORMAL -> {
            Scaffold(
                containerColor = Color.Black,
                topBar = {
                    TopAppBar(
                        title = {
                            if (hasMultiple) {
                                Text(
                                    "${pagerState.currentPage + 1} / ${materials.size}",
                                    color = Color.White.copy(alpha = 0.8f),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "返回",
                                    tint = Color.White
                                )
                            }
                        },
                        actions = {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(
                                    Icons.Default.MoreVert,
                                    contentDescription = "更多操作",
                                    tint = Color.White
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Black.copy(alpha = 0.6f)
                        )
                    )
                }
            ) { padding ->
                Box(
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize()
                        .background(Color.Black)
                ) {
                    PagerContent(
                        onTap = { viewState = VIEW_FULLSCREEN }
                    )
                }
            }
        }
        VIEW_FULLSCREEN -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                PagerContent(
                    onTap = { viewState = VIEW_PURE }
                )
                // 返回键浮层
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                ) {
                    IconButton(
                        onClick = { viewState = VIEW_NORMAL },
                        modifier = Modifier.align(Alignment.TopStart)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = Color.White.copy(alpha = 0.8f)
                        )
                    }
                    // 常显操作入口（长按是隐藏手势，新用户发现不了）
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.align(Alignment.TopEnd)
                    ) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "更多操作",
                            tint = Color.White.copy(alpha = 0.8f)
                        )
                    }
                    // 页码指示器
                    if (hasMultiple) {
                        Text(
                            "${pagerState.currentPage + 1} / ${materials.size}",
                            color = Color.White.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 16.dp)
                        )
                    }
                }
            }
        }
        VIEW_PURE -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                PagerContent(
                    onTap = { viewState = VIEW_FULLSCREEN }
                )
            }
        }
    }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 40.dp)
        )
    }
}

/**
 * 单页内容：图片或视频（长按菜单已移除，操作入口统一走 ⋮）
 */
@Composable
private fun PagerPage(
    materialType: String,
    fileUrl: String,
    okHttpClient: okhttp3.OkHttpClient,
    onTap: () -> Unit,
    onZoomChanged: (Boolean) -> Unit
) {
    when {
        materialType.startsWith("image") -> {
            ZoomableImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(fileUrl).crossfade(true).build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                onTap = onTap,
                onZoomChanged = onZoomChanged
            )
        }
        materialType.startsWith("video") -> {
            VideoPlayer(
                url = fileUrl,
                okHttpClient = okHttpClient,
                modifier = Modifier.fillMaxSize(),
                onTap = onTap
            )
        }
        else -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "此文件类型不支持预览",
                    color = Color.White.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

/**
 * 极简视频播放器：播放/暂停按钮，长按手势
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun VideoPlayer(
    url: String,
    okHttpClient: okhttp3.OkHttpClient,
    modifier: Modifier = Modifier,
    onLongPress: (() -> Unit)? = null,
    onTap: (() -> Unit)? = null
) {
    AndroidView<FrameLayout>(
        factory = { ctx ->
            val container = FrameLayout(ctx).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setBackgroundColor(android.graphics.Color.BLACK)
            }

            val playerView = PlayerView(ctx).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                useController = true
                setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                controllerShowTimeoutMs = 0
                controllerAutoShow = false
            }

            val dataSourceFactory = androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(okHttpClient)
            val exoPlayer = ExoPlayer.Builder(ctx)
                .setMediaSourceFactory(
                    androidx.media3.exoplayer.source.DefaultMediaSourceFactory(dataSourceFactory)
                )
                .build()

            val mediaItem = MediaItem.fromUri(android.net.Uri.parse(url))
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true

            playerView.player = exoPlayer

            container.setOnLongClickListener {
                onLongPress?.invoke()
                true
            }
            container.setOnClickListener {
                onTap?.invoke()
            }

            container.addView(playerView)
            container.tag = exoPlayer

            container
        },
        modifier = modifier,
        onRelease = { view: FrameLayout ->
            val player = view.tag as? ExoPlayer
            player?.release()
        }
    )
}
