package com.paperbox.app.ui.media

import android.app.Activity
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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

    // 全屏控制
    DisposableEffect(isFullscreen) {
        val activity = context as? Activity
        if (activity != null) {
            if (isFullscreen) {
                // 全屏：隐藏所有系统栏
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val controller = activity.window.insetsController
                    controller?.hide(
                        android.view.WindowInsets.Type.statusBars() or
                        android.view.WindowInsets.Type.navigationBars()
                    )
                    controller?.systemBarsBehavior =
                        android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                } else {
                    @Suppress("DEPRECATION")
                    activity.window.decorView.systemUiVisibility = (
                        View.SYSTEM_UI_FLAG_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    )
                }
                activity.window.statusBarColor = android.graphics.Color.TRANSPARENT
                activity.window.navigationBarColor = android.graphics.Color.TRANSPARENT
            } else {
                // 恢复系统栏
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    activity.window.insetsController?.show(
                        android.view.WindowInsets.Type.statusBars() or
                        android.view.WindowInsets.Type.navigationBars()
                    )
                } else {
                    @Suppress("DEPRECATION")
                    activity.window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
                }
                activity.window.statusBarColor = android.graphics.Color.BLACK
                activity.window.navigationBarColor = android.graphics.Color.BLACK
            }
        }
        onDispose {}
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
                        val ext = if (materialType.contains("png")) ".png" else ".jpg"
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
        // 全屏模式：纯素材，点击退出
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
        // 普通模式：有返回栏
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

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun VideoPlayer(
    url: String,
    okHttpClient: okhttp3.OkHttpClient,
    modifier: Modifier = Modifier,
    onLongPress: (() -> Unit)? = null
) {
    AndroidView(
        factory = { ctx ->
            val playerView = PlayerView(ctx).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                useController = true
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
            playerView.tag = exoPlayer
            playerView
        },
        modifier = modifier,
        onRelease = { view ->
            val player = view.tag as? ExoPlayer
            player?.release()
        }
    )
}
