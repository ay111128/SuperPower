package com.paperbox.app.ui.media

import android.app.Activity
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.paperbox.app.BuildConfig
import com.paperbox.app.data.api.models.MaterialItem
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
    viewModel: MediaViewerViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // 解析素材列表
    val materials = remember(materialsJson) {
        if (!materialsJson.isNullOrBlank()) {
            try {
                val type = Types.newParameterizedType(List::class.java, MaterialItem::class.java)
                Moshi.Builder().build().adapter<List<MaterialItem>>(type).fromJson(materialsJson) ?: emptyList()
            } catch (_: Exception) { emptyList() }
        } else emptyList()
    }
    val hasMultiple = materials.size > 1

    // Pager 状态
    val initialPage = currentIndex.coerceIn(0, (materials.size - 1).coerceAtLeast(0))
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = materials.size)

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

    // 长按菜单弹窗
    if (showMenu) {
        AlertDialog(
            onDismissRequest = { showMenu = false },
            title = { Text("素材操作", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) },
            text = { Text("选择要执行的操作") },
            confirmButton = {
                TextButton(onClick = {
                    showMenu = false
                    scope.launch {
                        val ext = if (currentMaterial.type.contains("png")) ".png"
                                  else if (currentMaterial.type.startsWith("video")) ".mp4"
                                  else ".jpg"
                        viewModel.downloadFile(
                            materialId = currentMaterial.id,
                            filename = "${currentMaterial.id}$ext",
                            saveAsOriginal = true
                        ) { success, msg ->
                            scope.launch { snackbarHostState.showSnackbar(msg) }
                        }
                    }
                }) {
                    Text("下载", color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showMenu = false
                    scope.launch {
                        viewModel.deleteMaterial(currentMaterial.id) { success, msg ->
                            scope.launch {
                                snackbarHostState.showSnackbar(msg)
                                if (success) onBack()
                            }
                        }
                    }
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            }
        )
    }

    // ── 核心内容：根据模式渲染 ──
    @Composable
    fun PagerContent(onTap: () -> Unit, onLongPress: () -> Unit) {
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
                    onLongPress = onLongPress,
                    onZoomChanged = { zoomed -> isImageZoomed = zoomed }
                )
            }
        } else {
            PagerPage(
                materialType = currentMaterial.type,
                fileUrl = fileUrl,
                okHttpClient = viewModel.okHttpClient,
                onTap = onTap,
                onLongPress = onLongPress,
                onZoomChanged = { zoomed -> isImageZoomed = zoomed }
            )
        }
    }

    when (viewState) {
        VIEW_NORMAL -> {
            Scaffold(
                containerColor = Color.Black,
                snackbarHost = { SnackbarHost(snackbarHostState) },
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
                        onTap = { viewState = VIEW_FULLSCREEN },
                        onLongPress = { showMenu = true }
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
                    onTap = { viewState = VIEW_PURE },
                    onLongPress = { showMenu = true }
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
                    onTap = { viewState = VIEW_FULLSCREEN },
                    onLongPress = { showMenu = true }
                )
            }
        }
    }
}

/**
 * 单页内容：图片或视频
 */
@Composable
private fun PagerPage(
    materialType: String,
    fileUrl: String,
    okHttpClient: okhttp3.OkHttpClient,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
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
                onLongPress = onLongPress,
                onZoomChanged = onZoomChanged
            )
        }
        materialType.startsWith("video") -> {
            VideoPlayer(
                url = fileUrl,
                okHttpClient = okHttpClient,
                modifier = Modifier.fillMaxSize(),
                onLongPress = onLongPress,
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
