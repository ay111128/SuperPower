package com.paperbox.app.ui.media

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.paperbox.app.BuildConfig
import com.paperbox.app.data.api.ApiClient
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import javax.inject.Inject

@HiltViewModel
class MediaViewerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apiClient: ApiClient
) : ViewModel() {

    val okHttpClient get() = apiClient.okHttpClient

    /** 保存图片到相册 */
    fun saveImageToGallery(
        imageUrl: String,
        filename: String,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    val loader = ImageLoader(context)
                    val request = ImageRequest.Builder(context)
                        .data(imageUrl)
                        .allowHardware(false)
                        .build()
                    val result = loader.execute(request)
                    if (result is SuccessResult) {
                        (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                    } else null
                }

                if (bitmap == null) {
                    onResult(false, "图片加载失败")
                    return@launch
                }

                val saved = withContext(Dispatchers.IO) {
                    saveBitmapToGallery(bitmap, filename)
                }
                onResult(saved, if (saved) "已保存到相册" else "保存失败")
            } catch (e: Exception) {
                onResult(false, "保存失败：${e.message}")
            }
        }
    }

    private fun saveBitmapToGallery(bitmap: Bitmap, filename: String): Boolean {
        val mimeType = if (filename.endsWith(".png", true)) "image/png" else "image/jpeg"
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Paperbox")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val uri = context.contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        ) ?: return false

        return try {
            context.contentResolver.openOutputStream(uri)?.use { os ->
                val format = if (filename.endsWith(".png", true)) Bitmap.CompressFormat.PNG
                             else Bitmap.CompressFormat.JPEG
                bitmap.compress(format, 95, os)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                context.contentResolver.update(uri, contentValues, null, null)
            }
            true
        } catch (e: Exception) {
            Log.e("MediaViewer", "Save failed", e)
            context.contentResolver.delete(uri, null, null)
            false
        }
    }

    /** 下载素材文件到 Downloads */
    fun downloadFile(
        materialId: String,
        filename: String,
        saveAsOriginal: Boolean = false,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val url = "${BuildConfig.API_BASE_URL}/materials-api/materials/$materialId/file"
                    val request = Request.Builder().url(url).build()
                    val response = apiClient.okHttpClient.newCall(request).execute()

                    if (!response.isSuccessful) {
                        throw Exception("HTTP ${response.code}")
                    }

                    val downloadDir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir
                    } else {
                        @Suppress("DEPRECATION")
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    }
                    val paperboxDir = File(downloadDir, "Paperbox")
                    paperboxDir.mkdirs()
                    val file = File(paperboxDir, filename)

                    // 直接保存原始字节流，不进行任何压缩或格式转换
                    response.body?.byteStream()?.use { input ->
                        file.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }

                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                        android.media.MediaScannerConnection.scanFile(
                            context, arrayOf(file.absolutePath), null, null
                        )
                    }
                }
                onResult(true, "已下载到 Downloads/Paperbox/")
            } catch (e: Exception) {
                onResult(false, "下载失败：${e.message}")
            }
        }
    }

    /** 删除素材 */
    fun deleteMaterial(
        materialId: String,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val response = apiClient.apiService.deleteMaterial(materialId)
                    if (!response.isSuccessful) {
                        @Suppress("DEPRECATION")
                        val code = response.code()
                        throw Exception("HTTP $code")
                    }
                }
                onResult(true, "已删除")
            } catch (e: Exception) {
                onResult(false, "删除失败：${e.message}")
            }
        }
    }
}
