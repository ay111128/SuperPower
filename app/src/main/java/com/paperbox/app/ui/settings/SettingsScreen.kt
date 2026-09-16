package com.paperbox.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

// ── 设计稿颜色 ──
private val BgGray = Color(0xFFF5F5F5)
private val CardBg = Color(0xFFFFFFFF)
private val AvatarBg = Color(0xFFE8E8E8)
private val AvatarIcon = Color(0xFFBBBBBB)
private val NameDark = Color(0xFF1A1A1A)
private val PhoneGray = Color(0xFF999999)
private val MenuIcon = Color(0xFF666666)
private val MenuText = Color(0xFF333333)
private val ArrowGray = Color(0xFFCCCCCC)
private val DividerColor = Color(0xFFF0F0F0)
private val VersionGray = Color(0xFFBBBBBB)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF007A12))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .height(62.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "个人中心",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(BgGray)
        ) {
            // ── 用户头像区 ──
            Card(
                shape = RoundedCornerShape(0.dp),
                colors = CardDefaults.cardColors(containerColor = CardBg),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(88.dp)
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 头像
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .background(AvatarBg, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                                tint = AvatarIcon
                            )
                        }
                        // 用户信息
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                state.username.ifEmpty { "未登录" },
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = NameDark
                            )
                            Text(
                                "已登录",
                                fontSize = 13.sp,
                                color = PhoneGray
                            )
                        }
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = ArrowGray,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── 菜单区 ──
            Card(
                shape = RoundedCornerShape(0.dp),
                colors = CardDefaults.cardColors(containerColor = CardBg),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column {
                    MenuItemRow(
                        icon = Icons.Default.Settings,
                        title = "设置",
                        onClick = { }
                    )
                    MenuDivider()
                    MenuItemRow(
                        icon = Icons.Default.Notifications,
                        title = "消息通知",
                        onClick = { }
                    )
                    MenuDivider()
                    MenuItemRow(
                        icon = Icons.Default.Shield,
                        title = "隐私与安全",
                        onClick = { }
                    )
                    MenuDivider()
                    MenuItemRow(
                        icon = Icons.Default.HelpOutline,
                        title = "帮助与反馈",
                        onClick = { }
                    )
                    MenuDivider()
                    MenuItemRow(
                        icon = Icons.Default.Info,
                        title = "关于我们",
                        onClick = { }
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            // ── 版本号 ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "版本号 v1.0.33",
                    fontSize = 12.sp,
                    color = VersionGray
                )
            }
        }
    }
}

@Composable
private fun MenuItemRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clickable { onClick() }
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = MenuIcon, modifier = Modifier.size(20.dp))
            Text(title, fontSize = 15.sp, color = MenuText)
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = ArrowGray,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun MenuDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .height(0.5.dp)
            .background(DividerColor)
    )
}
