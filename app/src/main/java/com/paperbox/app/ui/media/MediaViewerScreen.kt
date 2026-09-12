package com.paperbox.app.ui.media

import android.app.Activity
import android.content.Context
import android.os.Build
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.paperbox.app.BuildConfig
import coil.request.ImageRequest
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaViewerScreen(
    materialId: String,
    materialType: String,
    onBack: () -> Unit,
    viewModel: MediaViewerViewModel = hiltViewModel()
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val fileUrl = "${BuildConfig.API_BASE_URL}/materials-api/materials/$materialId/file"

    var showMenu by remember { mutableStateOf(false) }
    var isFullscreen by remember { mutableStateOf(false) }

    // 全屏控制 - 使用 enableEdgeToEdge 思路
    DisposableEffect(isFullscreen) {
        val activity = context as? Activity
        if (activity != null) {
            val window = activity.window
            if (isFullscreen) {
                // 全屏：隐藏所有系统栏，内容延伸到状态栏后面
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val controller = window.insetsController
                    controller?.hide(
                        android.view.WindowInsets.Type.statusBars() or
                        android.view.WindowInsets.Type.navigationBars()
                    )
                    controller?.systemBarsBehavior =
                        android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
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
            } else {
                // 恢复系统栏
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
        }
        onDispose {
            // 确保退出时恢复
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
                act.window.statusBarColor = android.graphics.Color.BLACK
                act.window.navigationBarColor = android.graphics.Color.BLACK
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
                        val ext = if (materialType.contains("png")) ".png" else if (materialType.contains("video")) ".mp4" else ".jpg"
                        viewModel.downloadFile(
                            materialId = materialId,
                            filename = "${materialId}$ext",
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
                        viewModel.deleteMaterial(materialId) { success, msg ->
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

    if (isFullscreen) {
        // 全屏模式：纯素材，无系统栏，内容延伸到状态栏后面
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable { isFullscreen = false }
        ) {
            when {
                materialType.startsWith("image") -> {
                    ZoomableImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(fileUrl).crossfade(true).build(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        onLongPress = { showMenu = true }
                    )
                }
                materialType.startsWith("video") -> {
                    VideoPlayer(
                        url = fileUrl,
                        okHttpClient = viewModel.okHttpClient,
                        modifier = Modifier.fillMaxSize(),
                        onLongPress = { showMenu = true }
                    )
                }
            }
        }
    } else {
        // 普通模式：有返回栏，状态栏区域填充 padding
        Scaffold(
            containerColor = Color.Black,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {},
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
                    .clickable { isFullscreen = true }
            ) {
                when {
                    materialType.startsWith("image") -> {
                        ZoomableImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(fileUrl).crossfade(true).build(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            onLongPress = { showMenu = true }
                        )
                    }
                    materialType.startsWith("video") -> {
                        VideoPlayer(
                            url = fileUrl,
                            okHttpClient = viewModel.okHttpClient,
                            modifier = Modifier.fillMaxSize(),
                            onLongPress = { showMenu = true }
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
        }
    }
}

/**
 * 极简视频播放器：只有播放/暂停按钮，支持长按手势
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun VideoPlayer(
    url: String,
    okHttpClient: okhttp3.OkHttpClient,
    modifier: Modifier = Modifier,
    onLongPress: (() -> Unit)? = null
) {
    val context = LocalContext.current

    AndroidView(
        factory = { ctx ->
            // 自定义 FrameLayout 包含 PlayerView + 播放按钮覆盖层
            val container = FrameLayout(ctx).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setBackgroundColor(android.graphics.Color.BLACK)
            }

            // PlayerView - 隐藏默认控制器
            val playerView = PlayerView(ctx).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                useController = false // 不使用默认控制器
                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
            }

            // 播放/暂停按钮
            val playButton = ImageView(ctx).apply {
                layoutParams = FrameLayout.LayoutParams(72.dpToPx(ctx), 72.dpToPx(ctx)).apply {
                    gravity = android.view.Gravity.CENTER
                }
                setImageResource(android.R.drawable.ic_media_play)
                setPadding(16.dpToPx(ctx), 16.dpToPx(ctx), 16.dpToPx(ctx), 16.dpToPx(ctx))
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                alpha = 0.8f
            }

            // 创建 ExoPlayer
            val dataSourceFactory = androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(okHttpClient)
            val exoPlayer = ExoPlayer.Builder(ctx)
                .setMediaSourceFactory(
                    androidx.media3.exoplayer.source.DefaultMediaSourceFactory(dataSourceFactory)
                )
                .build()

            val mediaItem = MediaItem.fromUri(android.net.Uri.parse(url))
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()

            // 播放/暂停切换
            val togglePlayPause = {
                if (exoPlayer.isPlaying) {
                    exoPlayer.pause()
                    playButton.setImageResource(android.R.drawable.ic_media_play)
                } else {
                    exoPlayer.playWhenReady = true
                    exoPlayer.play()
                    playButton.setImageResource(android.R.drawable.ic_media_pause)
                }
            }

            playButton.setOnClickListener { togglePlayPause() }

            // 监听播放状态，自动隐藏/显示按钮
            exoPlayer.addListener(object : androidx.media3.common.Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    playButton.setImageResource(
                        if (isPlaying) android.R.drawable.ic_media_pause
                        else android.R.drawable.ic_media_play
                    )
                    // 播放时淡出按钮
                    playButton.animate()
                        .alpha(if (isPlaying) 0f else 0.8f)
                        .setDuration(300)
                        .start()
                }
            })

            // 点击屏幕切换播放/暂停 + 显示按钮
            playerView.setOnClickListener {
                togglePlayPause()
                playButton.animate().alpha(0.8f).setDuration(100).start()
            }

            // 长按手势检测
            val longPressListener = object : GestureDetector.SimpleOnGestureListener() {
                override fun onLongPress(e: MotionEvent) {
                    onLongPress?.invoke()
                }
            }
            val gestureDetector = GestureDetector(ctx, longPressListener)

            playerView.setOnTouchListener { _, event ->
                gestureDetector.onTouchEvent(event)
                true // 消费所有触摸事件以检测长按
            }

            // 组装视图层级
            container.addView(playerView)
            container.addView(playButton)

            // 保存引用以便释放
            container.tag = exoPlayer

            container
        },
        modifier = modifier,
        onRelease = { view ->
            val player = view.tag as? ExoPlayer
            player?.release()
        }
    )
}

/** dp 转 px 扩展函数 */
private fun Int.dpToPx(context: Context): Int =
    (this * context.resources.displayMetrics.density).toInt()
