package com.paperbox.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.paperbox.app.ui.navigation.AppNavGraph
import com.paperbox.app.ui.theme.PaperboxTheme
import com.paperbox.app.ui.theme.Primary
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 状态栏透明，图标用浅色（白色），背景由 Scaffold 顶部绘制品牌棕色
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(scrim = 0x00000000)
        )
        setContent {
            PaperboxTheme {
                AppNavGraph()
            }
        }
    }
}
