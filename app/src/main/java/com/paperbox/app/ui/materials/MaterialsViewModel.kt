package com.paperbox.app.ui.materials

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paperbox.app.data.api.ApiService
import com.paperbox.app.data.api.models.ColorItem
import com.paperbox.app.data.api.models.MaterialItem
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import javax.inject.Inject

data class MaterialsUiState(
    val materials: List<MaterialItem> = emptyList(),
    val total: Int = 0,
    val colors: List<ColorItem> = emptyList(),
    val selectedColor: String = "",
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val uploadSuccess: Int = 0,
    val errorMessage: String? = null
)

@HiltViewModel
class MaterialsViewModel @Inject constructor(
    private val apiService: ApiService,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(MaterialsUiState())
    val uiState: StateFlow<MaterialsUiState> = _uiState.asStateFlow()

    init {
        loadMaterials()
        loadColors()
    }

    fun loadMaterials(offset: Int = 0) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val response = apiService.getMaterials(
                    color = _uiState.value.selectedColor.ifBlank { null },
                    query = _uiState.value.searchQuery.ifBlank { null },
                    limit = 50,
                    offset = offset
                )
                if (response.isSuccessful) {
                    val body = response.body()!!
                    _uiState.value = _uiState.value.copy(
                        materials = body.items,
                        total = body.total,
                        isLoading = false
                    )
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

    fun selectColor(color: String) {
        _uiState.value = _uiState.value.copy(selectedColor = color)
        loadMaterials()
    }

    fun updateSearch(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        loadMaterials()
    }

    fun deleteMaterial(id: String) {
        viewModelScope.launch {
            try {
                val response = apiService.deleteMaterial(id)
                if (response.isSuccessful) {
                    loadMaterials()
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = "删除失败：${e.message}")
            }
        }
    }

    fun uploadFiles(uris: List<Uri>) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
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
                        isLoading = false
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

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(errorMessage = null, uploadSuccess = 0)
    }
}
