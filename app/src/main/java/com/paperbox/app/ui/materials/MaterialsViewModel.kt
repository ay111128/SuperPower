package com.paperbox.app.ui.materials

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paperbox.app.BuildConfig
import com.paperbox.app.data.api.ApiClient
import com.paperbox.app.data.api.ApiService
import com.paperbox.app.data.api.PrefsKeys
import com.paperbox.app.data.api.dataStore
import com.paperbox.app.data.api.models.ColorItem
import com.paperbox.app.data.api.models.FilterCountsResponse
import com.paperbox.app.data.api.models.MaterialItem
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import javax.inject.Inject

data class MaterialsUiState(
    val materials: List<MaterialItem> = emptyList(),
    val total: Int = 0,
    val colors: List<ColorItem> = emptyList(),
    val tags: List<String> = emptyList(),
    val videoThumbnails: Map<String, android.graphics.Bitmap> = emptyMap(),
    // 筛选
    val selectedColor: String = "",
    val selectedCategory: String = "all",
    val selectedTags: Set<String> = emptySet(),
    val searchQuery: String = "",
    // 内联搜索
    val isSearchActive: Boolean = false,
    val searchFieldText: String = "",
    val allMaterials: List<MaterialItem> = emptyList(),
    // 分页
    val hasMore: Boolean = false,
    val isLoadingMore: Boolean = false,
    // 服务端筛选计数
    val filterCounts: FilterCountsResponse = FilterCountsResponse(),
    // 滚动位置记忆
    val scrollIndex: Int = 0,
    val scrollOffset: Int = 0,
    // 布局
    val layoutMode: String = "grid", // "grid" or "list"
    // 弹窗状态
    val showUploadSheet: Boolean = false,
    val showMoreSheet: Boolean = false,
    val showTagDialog: Boolean = false,
    val showDeleteDialog: Boolean = false,
    val selectedMaterial: MaterialItem? = null,
    val tagDraft: Set<String> = emptySet(),
    // 消息
    val isLoading: Boolean = false,
    val uploadSuccess: Int = 0,
    val toastMessage: String? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class MaterialsViewModel @Inject constructor(
    private val apiService: ApiService,
    private val apiClient: ApiClient,
    private val okHttpClient: OkHttpClient,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(MaterialsUiState())
    val uiState: StateFlow<MaterialsUiState> = _uiState.asStateFlow()

    init {
        loadMaterials()
        loadColors()
        loadTags()
        loadFilterCounts()
    }

    companion object {
        private const val PAGE_SIZE = 50
    }

    fun loadMaterials(offset: Int = 0) {
        viewModelScope.launch {
            if (offset == 0) {
                _uiState.value = _uiState.value.copy(isLoading = true)
            }
            try {
                val state = _uiState.value
                val tagsParam = state.selectedTags.joinToString(",").ifBlank { null }
                val response = apiService.getMaterials(
                    color = state.selectedColor.ifBlank { null },
                    tags = tagsParam,
                    query = state.searchQuery.ifBlank { null },
                    limit = PAGE_SIZE,
                    offset = offset
                )
                if (response.isSuccessful) {
                    val body = response.body()!!
                    // 客户端分类筛选（API 不支持 type 参数）
                    val filtered = if (state.selectedCategory == "all") body.items
                    else body.items.filter { m ->
                        when (state.selectedCategory) {
                            "image" -> m.type.startsWith("image")
                            "video" -> m.type.startsWith("video")
                            "doc" -> m.type.contains("pdf") || m.type.contains("document") || m.type.contains("msword")
                            "zip" -> m.type.contains("zip") || m.type.contains("compressed")
                            else -> true
                        }
                    }
                    val newMaterials = if (offset == 0) filtered
                    else state.materials + filtered
                    _uiState.value = _uiState.value.copy(
                        materials = newMaterials,
                        allMaterials = if (_uiState.value.isSearchActive) body.items else _uiState.value.allMaterials,
                        total = body.total,
                        hasMore = offset + body.items.size < body.total,
                        isLoading = false,
                        isLoadingMore = false
                    )
                    // 异步加载视频缩略图
                    loadVideoThumbnails(filtered)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "加载失败：${e.message}",
                    isLoading = false,
                    isLoadingMore = false
                )
            }
        }
    }

    fun loadMore() {
        if (_uiState.value.isLoadingMore || !_uiState.value.hasMore) return
        _uiState.value = _uiState.value.copy(isLoadingMore = true)
        loadMaterials(offset = _uiState.value.materials.size)
    }

    /** 从服务端加载筛选计数（颜色/标签/类型） */
    private fun loadFilterCounts() {
        viewModelScope.launch {
            try {
                val response = apiService.getFilterCounts()
                if (response.isSuccessful) {
                    val counts = response.body()!!
                    _uiState.value = _uiState.value.copy(filterCounts = counts)
                }
            } catch (_: Exception) { }
        }
    }

    /** 异步提取视频缩略图（带磁盘缓存） */
    private fun loadVideoThumbnails(materials: List<MaterialItem>) {
        val videos = materials.filter { it.type.startsWith("video") }
        if (videos.isEmpty()) return

        viewModelScope.launch {
            val thumbnails = mutableMapOf<String, android.graphics.Bitmap>()
            withContext(Dispatchers.IO) {
                // 获取认证 token
                val token = try {
                    context.dataStore.data.map { it[PrefsKeys.TOKEN] ?: "" }.first()
                } catch (_: Exception) { "" }
                val headers = HashMap<String, String>()
                if (token.isNotEmpty()) {
                    headers["Authorization"] = "Bearer $token"
                }

                // 缓存目录
                val cacheDir = File(context.cacheDir, "video_thumbnails")
                cacheDir.mkdirs()

                for (video in videos) {
                    try {
                        // 1. 先检查磁盘缓存
                        val cacheFile = File(cacheDir, "${video.id}.jpg")
                        if (cacheFile.exists()) {
                            val cachedBitmap = android.graphics.BitmapFactory.decodeFile(cacheFile.absolutePath)
                            if (cachedBitmap != null) {
                                thumbnails[video.id] = cachedBitmap
                                Log.d("MaterialsVM", "Thumbnail loaded from cache: ${video.id}")
                                continue
                            }
                        }

                        // 2. 缓存未命中，从网络提取
                        val retriever = android.media.MediaMetadataRetriever()
                        val url = "${BuildConfig.API_BASE_URL}/materials-api/materials/${video.id}/file"
                        retriever.setDataSource(url, headers)
                        val bitmap = retriever.frameAtTime
                        if (bitmap != null) {
                            thumbnails[video.id] = bitmap
                            // 3. 保存到磁盘缓存
                            try {
                                cacheFile.outputStream().use { out ->
                                    bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, out)
                                }
                                Log.d("MaterialsVM", "Thumbnail saved to cache: ${video.id}")
                            } catch (e: Exception) {
                                Log.w("MaterialsVM", "Failed to cache thumbnail: ${e.message}")
                            }
                        }
                        retriever.release()
                    } catch (e: Exception) {
                        Log.w("MaterialsVM", "Thumbnail failed for ${video.id}: ${e.message}")
                    }
                }
            }
            if (thumbnails.isNotEmpty()) {
                _uiState.value = _uiState.value.copy(
                    videoThumbnails = _uiState.value.videoThumbnails + thumbnails
                )
            }
        }
    }

    private fun loadColors() {
        viewModelScope.launch {
            try {
                val response = apiService.getColors()
                if (response.isSuccessful) {
                    _uiState.value = _uiState.value.copy(colors = response.body()!!.colors)
                }
            } catch (_: Exception) {}
        }
    }

    private fun loadTags() {
        viewModelScope.launch {
            try {
                val response = apiService.getTags()
                if (response.isSuccessful) {
                    _uiState.value = _uiState.value.copy(tags = response.body()!!.tags)
                }
            } catch (_: Exception) {}
        }
    }

    fun selectColor(color: String) {
        _uiState.value = _uiState.value.copy(selectedColor = color)
        loadMaterials()
    }

    fun selectCategory(category: String) {
        _uiState.value = _uiState.value.copy(selectedCategory = category)
        loadMaterials()
    }

    fun toggleTag(tag: String) {
        val current = _uiState.value.selectedTags.toMutableSet()
        if (current.contains(tag)) current.remove(tag) else current.add(tag)
        _uiState.value = _uiState.value.copy(selectedTags = current)
        loadMaterials()
    }

    fun clearTags() {
        _uiState.value = _uiState.value.copy(selectedTags = emptySet())
        loadMaterials()
    }

    fun toggleLayout() {
        val newMode = if (_uiState.value.layoutMode == "grid") "list" else "grid"
        _uiState.value = _uiState.value.copy(layoutMode = newMode, scrollIndex = 0, scrollOffset = 0)
    }

    fun saveScrollPosition(index: Int, offset: Int) {
        _uiState.value = _uiState.value.copy(scrollIndex = index, scrollOffset = offset)
    }

    fun updateSearch(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        loadMaterials()
    }

    // ── 内联搜索 ──

    private var searchDebounceJob: kotlinx.coroutines.Job? = null

    fun toggleSearch() {
        val current = _uiState.value
        if (current.isSearchActive) {
            // 关闭搜索：提交搜索词并重新加载
            dismissSearch()
        } else {
            // 打开搜索：备份当前数据以便实时过滤
            _uiState.value = current.copy(
                isSearchActive = true,
                searchFieldText = current.searchQuery,
                allMaterials = current.materials
            )
        }
    }

    fun updateSearchField(text: String) {
        _uiState.value = _uiState.value.copy(searchFieldText = text)
        // 实时客户端过滤（从备份数据过滤，避免级联过滤）
        val baseMaterials = _uiState.value.allMaterials
        if (text.isBlank()) {
            _uiState.value = _uiState.value.copy(materials = baseMaterials)
        } else {
            val query = text.lowercase()
            _uiState.value = _uiState.value.copy(
                materials = baseMaterials.filter { m ->
                    m.name.lowercase().contains(query) ||
                    m.tags.any { it.lowercase().contains(query) }
                }
            )
        }
        // 防抖：500ms 后向服务器请求更多结果
        searchDebounceJob?.cancel()
        searchDebounceJob = viewModelScope.launch {
            kotlinx.coroutines.delay(500)
            _uiState.value = _uiState.value.copy(searchQuery = text)
            loadMaterials()
        }
    }

    fun dismissSearch() {
        searchDebounceJob?.cancel()
        _uiState.value = _uiState.value.copy(
            isSearchActive = false,
            searchFieldText = "",
            searchQuery = ""
        )
        loadMaterials()
    }

    fun showUploadSheet() {
        _uiState.value = _uiState.value.copy(showUploadSheet = true)
    }

    fun dismissUploadSheet() {
        _uiState.value = _uiState.value.copy(showUploadSheet = false)
    }

    fun showMaterialOptions(material: MaterialItem) {
        _uiState.value = _uiState.value.copy(
            showMoreSheet = true,
            selectedMaterial = material
        )
    }

    fun dismissMoreSheet() {
        _uiState.value = _uiState.value.copy(showMoreSheet = false)
    }

    fun showTagDialogForMaterial() {
        val material = _uiState.value.selectedMaterial ?: return
        _uiState.value = _uiState.value.copy(
            showMoreSheet = false,
            showTagDialog = true,
            tagDraft = material.tags.toSet()
        )
    }

    fun toggleTagDraft(tag: String) {
        val current = _uiState.value.tagDraft.toMutableSet()
        if (current.contains(tag)) current.remove(tag) else current.add(tag)
        _uiState.value = _uiState.value.copy(tagDraft = current)
    }

    fun dismissTagDialog() {
        _uiState.value = _uiState.value.copy(showTagDialog = false)
    }

    fun saveMaterialTags() {
        val material = _uiState.value.selectedMaterial ?: return
        val tags = _uiState.value.tagDraft.toList()
        viewModelScope.launch {
            try {
                val response = apiService.updateMaterial(
                    material.id,
                    mapOf("tags" to tags)
                )
                if (response.isSuccessful) {
                    _uiState.value = _uiState.value.copy(
                        showTagDialog = false,
                        toastMessage = "标签已更新"
                    )
                    loadMaterials()
                    loadFilterCounts()
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    toastMessage = "更新失败：${e.message}"
                )
            }
        }
    }

    fun showDeleteDialog() {
        _uiState.value = _uiState.value.copy(
            showMoreSheet = false,
            showDeleteDialog = true
        )
    }

    fun dismissDeleteDialog() {
        _uiState.value = _uiState.value.copy(showDeleteDialog = false)
    }

    fun confirmDelete() {
        val material = _uiState.value.selectedMaterial ?: return
        viewModelScope.launch {
            try {
                val response = apiService.deleteMaterial(material.id)
                if (response.isSuccessful) {
                    _uiState.value = _uiState.value.copy(
                        showDeleteDialog = false,
                        toastMessage = "已删除"
                    )
                    loadMaterials()
                    loadFilterCounts()
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    toastMessage = "删除失败：${e.message}"
                )
            }
        }
    }

    // ── 操作 ──

    fun downloadMaterial() {
        val material = _uiState.value.selectedMaterial ?: return
        _uiState.value = _uiState.value.copy(showMoreSheet = false)

        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val url = "${BuildConfig.API_BASE_URL}/materials-api/materials/${material.id}/file"
                    val request = Request.Builder().url(url).build()
                    val response = apiClient.okHttpClient.newCall(request).execute()

                    if (!response.isSuccessful) {
                        throw Exception("HTTP ${response.code}")
                    }

                    val filename = material.name.ifBlank { "${material.id}${material.ext}" }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        // Android 10+ 使用 MediaStore
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
                _uiState.value = _uiState.value.copy(toastMessage = "已下载「${material.name}」")
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(toastMessage = "下载失败：${e.message}")
            }
        }
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(errorMessage = null, uploadSuccess = 0, toastMessage = null)
    }

    fun showToast(message: String) {
        _uiState.value = _uiState.value.copy(toastMessage = message)
    }

    // ── 诊断 ──

    private fun diagLog(message: String) {
        Log.d("MaterialsVM", message)
        try { apiClient.uploadDiagLog(message) } catch (_: Exception) {}
    }

    private fun diagnoseImageLoading() {
        viewModelScope.launch {
            val materials = _uiState.value.materials
            if (materials.isEmpty()) {
                diagLog("No materials to diagnose")
                return@launch
            }

            val first = materials.first()
            val url = "${BuildConfig.API_BASE_URL}/materials-api/materials/${first.id}/file"
            diagLog("=== Diagnosis === URL: $url | ID: ${first.id} | type: '${first.type}' | Name: ${first.name}")

            val result = withContext(Dispatchers.IO) {
                try {
                    val request = Request.Builder().url(url).build()
                    val response = okHttpClient.newCall(request).execute()
                    val code = response.code
                    val contentType = response.header("Content-Type")
                    val contentLength = response.body?.contentLength() ?: 0
                    response.close()
                    "HTTP $code | CT: $contentType | Size: $contentLength"
                } catch (e: Exception) {
                    "ERROR: ${e.javaClass.simpleName}: ${e.message}"
                }
            }
            diagLog("Diagnosis result: $result")
        }
    }

    fun uploadFiles(uris: List<Uri>) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, showUploadSheet = false)
            try {
                val parts = uris.mapNotNull { uri ->
                    val inputStream = context.contentResolver.openInputStream(uri) ?: return@mapNotNull null
                    val fileName = uri.lastPathSegment ?: "unknown"
                    val tempFile = File(context.cacheDir, fileName)
                    tempFile.outputStream().use { inputStream.copyTo(it) }

                    val requestBody = tempFile.asRequestBody("application/octet-stream".toMediaTypeOrNull())
                    MultipartBody.Part.createFormData("files", fileName, requestBody)
                }

                val colorBody = _uiState.value.selectedColor.ifBlank { null }?.let {
                    okhttp3.RequestBody.create("text/plain".toMediaTypeOrNull(), it)
                }
                val response = apiService.uploadMaterials(files = parts, color = colorBody)
                if (response.isSuccessful) {
                    val body = response.body()!!
                    _uiState.value = _uiState.value.copy(
                        uploadSuccess = body.created.size,
                        isLoading = false,
                        toastMessage = "成功上传 ${body.created.size} 个文件"
                    )
                    loadMaterials()
                    loadFilterCounts()
                } else {
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "上传失败：${response.code()}",
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "上传错误：${e.message}",
                    isLoading = false
                )
            }
        }
    }
}
