package com.paperbox.app.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import androidx.hilt.navigation.compose.hiltViewModel
import com.paperbox.app.data.api.PrefsKeys
import com.paperbox.app.data.api.dataStore
import com.paperbox.app.ui.theme.Primary
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var isRegisterMode by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var rememberPassword by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 加载保存的用户名和密码
    LaunchedEffect(Unit) {
        context.dataStore.data.map { prefs ->
            Triple(
                prefs[PrefsKeys.SAVED_USERNAME] ?: "",
                prefs[PrefsKeys.SAVED_PASSWORD] ?: "",
                prefs[PrefsKeys.REMEMBER_PASSWORD] ?: false
            )
        }.collect { (savedUser, savedPass, remember) ->
            if (remember) {
                username = savedUser
                password = savedPass
                rememberPassword = true
            }
        }
    }

    // 登录成功跳转
    LaunchedEffect(uiState.isSuccess) {
        if (uiState.isSuccess) {
            // 保存登录信息
            if (rememberPassword) {
                scope.launch {
                    context.dataStore.edit { prefs ->
                        prefs[PrefsKeys.SAVED_USERNAME] = username
                        prefs[PrefsKeys.SAVED_PASSWORD] = password
                        prefs[PrefsKeys.REMEMBER_PASSWORD] = true
                    }
                }
            } else {
                scope.launch {
                    context.dataStore.edit { prefs ->
                        prefs[PrefsKeys.SAVED_USERNAME] = ""
                        prefs[PrefsKeys.SAVED_PASSWORD] = ""
                        prefs[PrefsKeys.REMEMBER_PASSWORD] = false
                    }
                }
            }
            onLoginSuccess()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("飞机盒报价工具") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1B8A3E),
                    titleContentColor = Color.White
                ),
                windowInsets = WindowInsets(0, 0, 0, 0)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Logo / Title
            Text(
                "📦",
                style = MaterialTheme.typography.displayLarge
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "纸盒报价",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Primary
            )
            Spacer(Modifier.height(32.dp))

            // Tab 切换
            TabRow(selectedTabIndex = if (isRegisterMode) 1 else 0) {
                Tab(
                    selected = !isRegisterMode,
                    onClick = { isRegisterMode = false; viewModel.clearError() },
                    text = { Text("登录") }
                )
                Tab(
                    selected = isRegisterMode,
                    onClick = { isRegisterMode = true; viewModel.clearError() },
                    text = { Text("注册") }
                )
            }
            Spacer(Modifier.height(24.dp))

            // 用户名
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("用户名") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))

            // 密码
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("密码") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )

            // 注册模式：确认密码
            if (isRegisterMode) {
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { Text("确认密码") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // 记住密码（仅登录模式）
            if (!isRegisterMode) {
                Spacer(Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = rememberPassword,
                        onCheckedChange = { rememberPassword = it }
                    )
                    Text("记住密码", style = MaterialTheme.typography.bodyMedium)
                }
            }

            Spacer(Modifier.height(24.dp))

            // 错误信息
            if (uiState.errorMessage != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        uiState.errorMessage!!,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
                Spacer(Modifier.height(12.dp))
            }

            // 登录/注册按钮
            Button(
                onClick = {
                    if (isRegisterMode) {
                        if (password != confirmPassword) {
                            viewModel.setError("两次密码不一致")
                        } else {
                            viewModel.register(username, password)
                        }
                    } else {
                        viewModel.login(username, password)
                    }
                },
                enabled = !uiState.isLoading && username.isNotBlank() && password.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(if (isRegisterMode) "注册" else "登录")
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                "默认管理员: admin / paperbox2024",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
