package com.paperbox.app.ui.media

import android.app.Activity
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

    /** 下载素材文件到 Downloads（公开目录，使用 MediaStore） */
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

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        // Android 10+ 使用 MediaStore（与 MaterialsViewModel 一致）
                        val mimeType = response.body?.contentType()?.toString() ?: "application/octet-stream"
                        val contentValues = ContentValues().apply {
                            put(MediaStore.Downloads.DISPLAY_NAME, filename)
                            put(MediaStore.Downloads.MIME_TYPE, mimeType)
                            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Paperbox")
                            put(MediaStore.Downloads.IS_PENDING, 1)
                        }
                        val uri = context.contentResolver.insert(
                            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                            contentValues
                        )
                        if (uri != null) {
                            context.contentResolver.openOutputStream(uri)?.use { os ->
                                response.body?.byteStream()?.use { input -> input.copyTo(os) }
                            }
                            contentValues.clear()
                            contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
                            context.contentResolver.update(uri, contentValues, null, null)
                        }
                    } else {
                        // Android 9 及以下直接写文件
                        @Suppress("DEPRECATION")
                        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                        val paperboxDir = File(dir, "Paperbox")
                        paperboxDir.mkdirs()
                        val file = File(paperboxDir, filename)
                        response.body?.byteStream()?.use { input ->
                            file.outputStream().use { output -> input.copyTo(output) }
                        }
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

    /** 拉取全部标签（编辑弹窗的可选标签词表） */
    fun fetchTags(onResult: (List<String>) -> Unit) {
        viewModelScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    apiClient.apiService.getTags()
                }
                onResult(if (response.isSuccessful) response.body()?.tags ?: emptyList() else emptyList())
            } catch (_: Exception) {
                onResult(emptyList())
            }
        }
    }

    /** 编辑素材：名称 / 描述 / 标签 */
    fun updateMaterial(
        materialId: String,
        name: String,
        remark: String,
        tags: List<String>,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val response = apiClient.apiService.updateMaterial(
                        materialId,
                        mapOf("name" to name, "remark" to remark, "tags" to tags)
                    )
                    if (!response.isSuccessful) {
                        @Suppress("DEPRECATION")
                        val code = response.code()
                        throw Exception("HTTP $code")
                    }
                }
                onResult(true, "已保存")
            } catch (e: Exception) {
                onResult(false, "保存失败：${e.message}")
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
