package com.paperbox.app.ui.materials

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.paperbox.app.BuildConfig
import com.paperbox.app.data.repository.AuthRepository
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaterialsScreen(viewModel: MaterialsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    var isLoggedIn by remember { mutableStateOf(false) }
    var showLoginDialog by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(Unit) {
        val repo = com.paperbox.app.data.repository.AuthRepository(
            context = context,
            apiClient = com.paperbox.app.data.api.ApiClient(context)
        )
        isLoggedIn = repo.isLoggedIn()
        if (!isLoggedIn) {
            showLoginDialog = true
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.uploadFiles(uris)
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

            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = { viewModel.updateSearch(it) },
                label = { Text("搜索素材") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

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

            if (state.uploadSuccess > 0) {
                Snackbar(
                    action = {
                        TextButton(onClick = { viewModel.clearMessages() }) {
                            Text("关闭")
                        }
                    }
                ) {
                    Text("成功上传 ${state.uploadSuccess} 个文件")
                }
            }

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

    // 未登录提示
    if (showLoginDialog) {
        var username by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showLoginDialog = false },
            title = { Text("需要登录") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("素材管理需要登录后使用", style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("用户名") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("密码") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = {
                    kotlinx.coroutines.MainScope().launch {
                        val repo = com.paperbox.app.data.repository.AuthRepository(
                            context = context,
                            apiClient = com.paperbox.app.data.api.ApiClient(context)
                        )
                        val result = repo.login(username, password)
                        if (result.isSuccess) {
                            isLoggedIn = true
                            showLoginDialog = false
                            viewModel.loadMaterials()
                        }
                    }
                }) { Text("登录") }
            },
            dismissButton = {
                TextButton(onClick = { showLoginDialog = false }) { Text("取消") }
            }
        )
    }
}
