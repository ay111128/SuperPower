package com.paperbox.app.ui.materials

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import coil.compose.AsyncImage
import com.paperbox.app.BuildConfig
import com.paperbox.app.data.api.ApiService
import com.paperbox.app.data.api.models.ColorItem
import com.paperbox.app.data.api.models.MaterialItem
import dagger.hilt.android.lifecycle.HiltViewModel
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
    private val apiService: ApiService
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

    fun uploadFiles(uris: List<Uri>, context: android.content.Context) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val parts = uris.map { uri ->
                    val inputStream = context.contentResolver.openInputStream(uri) ?: return@map null
                    val fileName = uri.lastPathSegment ?: "unknown"
                    val tempFile = File(context.cacheDir, fileName)
                    tempFile.outputStream().use { inputStream.copyTo(it) }

                    val requestBody = tempFile.asRequestBody("application/octet-stream".toMediaTypeOrNull())
                    MultipartBody.Part.createFormData("files", fileName, requestBody)
                }.filterNotNull()

                val response = apiService.uploadMaterials(
                    files = parts,
                    color = _uiState.value.selectedColor.ifBlank { null }?.let {
                        okhttp3.RequestBody.create("text/plain".toMediaTypeOrNull(), it)
                    }
                )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaterialsScreen(viewModel: MaterialsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.uploadFiles(uris, context)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("素材管理 (${state.total})") },
                actions = {
                    IconButton(onClick = { filePicker.launch("image/*,video/*") }) {
                        Icon(Icons.Default.Add, contentDescription = "上传")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            // 搜索
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = { viewModel.updateSearch(it) },
                label = { Text("搜索素材") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // 颜色筛选
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                FilterChip(
                    selected = state.selectedColor == "",
                    onClick = { viewModel.selectColor("") },
                    label = { Text("全部") }
                )
                state.colors.take(5).forEach { color ->
                    FilterChip(
                        selected = state.selectedColor == color.name,
                        onClick = { viewModel.selectColor(color.name) },
                        label = { Text(color.name) }
                    )
                }
            }

            // 上传成功提示
            if (state.uploadSuccess > 0) {
                Snackbar(
                    action = {
                        TextButton(onClick = { viewModel.clearMessages() }) {
                            Text("关闭")
                        }
                    }
                ) {
                    Text("成功上传 $uploadSuccess 个文件")
                }
            }

            // 素材网格
            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.materials) { material ->
                        Card {
                            Column {
                                AsyncImage(
                                    model = "${BuildConfig.API_BASE_URL}/materials-api/materials/${material.id}/file",
                                    contentDescription = material.name,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1f),
                                    contentScale = ContentScale.Crop
                                )
                                Text(
                                    material.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    modifier = Modifier.padding(4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
