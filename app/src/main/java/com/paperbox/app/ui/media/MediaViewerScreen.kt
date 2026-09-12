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
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.paperbox.app.BuildConfig
import com.paperbox.app.data.api.PrefsKeys
import com.paperbox.app.data.api.dataStore
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaViewerScreen(
    materialId: String,
    materialType: String,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val fileUrl = "${BuildConfig.API_BASE_URL}/materials-api/materials/$materialId/file"
    val title = when {
        materialType.startsWith("image") -> "图片查看"
        materialType.startsWith("video") -> "视频播放"
        else -> "文件预览"
    }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        title,
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
                        contentDescription = "图片",
                        modifier = Modifier.fillMaxSize()
                    )
                }
                materialType.startsWith("video") -> {
                    // 视频：先下载到缓存再播放
                    val context = LocalContext.current
                    var cachedFile by remember { mutableStateOf<File?>(null) }
                    var downloadError by remember { mutableStateOf<String?>(null) }
                    var isDownloading by remember { mutableStateOf(true) }

                    LaunchedEffect(materialId) {
                        withContext(Dispatchers.IO) {
                            try {
                                // 读取 auth token
                                val token = context.dataStore.data
                                    .map { it[PrefsKeys.TOKEN] ?: "" }
                                    .first()

                                val cacheDir = File(context.cacheDir, "video_cache")
                                cacheDir.mkdirs()
                                val ext = when {
                                    materialType.contains("mp4") -> ".mp4"
                                    materialType.contains("webm") -> ".webm"
                                    materialType.contains("ogg") -> ".ogg"
                                    else -> ".mp4"
                                }
                                val cacheFile = File(cacheDir, "${materialId}$ext")

                                if (!cacheFile.exists()) {
                                    // 下载视频到缓存
                                    val client = createVideoClient()
                                    val request = Request.Builder()
                                        .url(fileUrl)
                                        .addHeader("Authorization", "Bearer $token")
                                        .build()
                                    val response = client.newCall(request).execute()
                                    if (response.isSuccessful) {
                                        response.body?.byteStream()?.use { input ->
                                            cacheFile.outputStream().use { output ->
                                                input.copyTo(output)
                                            }
                                        }
                                    } else {
                                        throw Exception("HTTP ${response.code}")
                                    }
                                }
                                cachedFile = cacheFile
                            } catch (e: Exception) {
                                downloadError = e.message
                            } finally {
                                isDownloading = false
                            }
                        }
                    }

                    when {
                        isDownloading -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                        }
                        downloadError != null -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "视频加载失败：$downloadError",
                                    color = Color.White.copy(alpha = 0.6f),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                        cachedFile != null -> {
                            VideoPlayer(
                                localFile = cachedFile!!,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
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

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun VideoPlayer(
    localFile: java.io.File,
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
                }
                webViewClient = WebViewClient()
                loadDataWithBaseURL(
                    null,
                    buildLocalVideoHtml(localFile),
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

private fun buildLocalVideoHtml(file: java.io.File): String {
    // 使用 file:// 协议加载本地视频，无需 SSL 认证
    val fileUrl = "file://${file.absolutePath}"
    return """
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
<video controls autoplay playsinline preload="metadata">
    <source src="$fileUrl">
</video>
</body>
</html>
""".trimIndent()
}

/** 创建信任自签名证书的 OkHttpClient（仅用于视频下载到缓存） */
private fun createVideoClient(): OkHttpClient {
    val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    })
    val sslContext = SSLContext.getInstance("TLS").apply {
        init(null, trustAllCerts, SecureRandom())
    }
    return OkHttpClient.Builder()
        .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
        .hostnameVerifier { _, _ -> true }
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
}
