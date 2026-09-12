package com.paperbox.app.ui.media

import android.annotation.SuppressLint
import android.net.http.SslError
import android.view.ViewGroup
import android.webkit.SslErrorHandler
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import com.paperbox.app.BuildConfig
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaViewerScreen(
    materialId: String,
    materialType: String,
    materialName: String,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val fileUrl = "${BuildConfig.API_BASE_URL}/materials-api/materials/$materialId/file"

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        materialName,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "关闭",
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
            when {
                materialType.startsWith("image") -> {
                    // 图片：支持缩放
                    ZoomableImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(fileUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = materialName,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                materialType.startsWith("video") -> {
                    // 视频：用 WebView 播放
                    VideoPlayer(
                        url = fileUrl,
                        mimeType = getVideoMimeType(materialType),
                        modifier = Modifier.fillMaxSize()
                    )
                }
                else -> {
                    // 其他类型：提示不支持预览
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

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun VideoPlayer(
    url: String,
    mimeType: String,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                settings.apply {
                    javaScriptEnabled = true
                    mediaPlaybackRequiresUserGesture = false
                    domStorageEnabled = true
                    allowFileAccess = true
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                }
                webViewClient = object : WebViewClient() {
                    override fun onReceivedSslError(
                        view: WebView?,
                        handler: SslErrorHandler?,
                        error: SslError?
                    ) {
                        handler?.proceed() // 开发环境接受自签名证书
                    }
                }
                loadDataWithBaseURL(
                    null,
                    buildVideoHtml(url, mimeType),
                    "text/html",
                    "UTF-8",
                    null
                )
            }
        },
        modifier = modifier,
        onRelease = { it.destroy() }
    )
}

private fun buildVideoHtml(url: String, mimeType: String): String = """
<!DOCTYPE html>
<html>
<head>
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
<style>
* { margin: 0; padding: 0; box-sizing: border-box; }
body {
    background: #000;
    display: flex;
    align-items: center;
    justify-content: center;
    height: 100vh;
    overflow: hidden;
}
video {
    width: 100%;
    height: 100%;
    object-fit: contain;
    background: #000;
}
</style>
</head>
<body>
<video controls autoplay playsinline preload="metadata"
       onplaying="document.getElementById('ld').style.display='none'">
    <source src="$url" type="$mimeType">
</video>
<div id="ld" style="position:fixed;top:0;left:0;width:100%;height:100%;background:#000;display:flex;align-items:center;justify-content:center;z-index:99;">
    <div style="color:#fff;font-size:14px;">加载中…</div>
</div>
</body>
</html>
""".trimIndent()

private fun getVideoMimeType(type: String): String {
    // type 可能是 "video/mp4" 或其他 MIME 类型，直接返回
    if (type.contains("/")) return type
    return when (type.lowercase()) {
        "mp4", "m4v" -> "video/mp4"
        "webm" -> "video/webm"
        "ogg", "ogv" -> "video/ogg"
        "3gp" -> "video/3gpp"
        else -> "video/mp4"
    }
}
