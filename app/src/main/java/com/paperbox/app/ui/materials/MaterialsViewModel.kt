package com.paperbox.app.ui.materials

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paperbox.app.BuildConfig
import com.paperbox.app.data.api.ApiClient
import com.paperbox.app.data.api.ApiService
import com.paperbox.app.data.api.models.ColorItem
import com.paperbox.app.data.api.models.MaterialItem
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    // 筛选
    val selectedColor: String = "",
    val selectedCategory: String = "all",
    val selectedTags: Set<String> = emptySet(),
    val searchQuery: String = "",
    // 布局
    val layoutMode: String = "grid", // "grid" or "list"
    // 弹窗状态
    val showSearchDialog: Boolean = false,
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
    }

    fun loadMaterials(offset: Int = 0) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val state = _uiState.value
                val tagsParam = state.selectedTags.joinToString(",").ifBlank { null }
                val response = apiService.getMaterials(
                    color = state.selectedColor.ifBlank { null },
                    tags = tagsParam,
                    query = state.searchQuery.ifBlank { null },
                    limit = 50,
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
                    _uiState.value = _uiState.value.copy(
                        materials = filtered,
                        total = body.total,
                        isLoading = false
                    )
                    // 日志：打印所有素材类型
                    body.items.forEach { m ->
                        diagLog("Material: id=${m.id} type='${m.type}' name='${m.name}'")
                    }
                    diagnoseImageLoading()
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "加载失败：${e.message}",
                    isLoading = false
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

    fun toggleLayout() {
        val newMode = if (_uiState.value.layoutMode == "grid") "list" else "grid"
        _uiState.value = _uiState.value.copy(layoutMode = newMode)
    }

    fun updateSearch(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        loadMaterials()
    }

    // ── 弹窗控制 ──

    fun showSearchDialog() {
        _uiState.value = _uiState.value.copy(showSearchDialog = true)
    }

    fun dismissSearchDialog() {
        _uiState.value = _uiState.value.copy(showSearchDialog = false)
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
        _uiState.value = _uiState.value.copy(
            showMoreSheet = false,
            toastMessage = "开始下载「${material.name}」"
        )
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
