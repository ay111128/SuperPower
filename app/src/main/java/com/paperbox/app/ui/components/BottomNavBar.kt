package com.paperbox.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// 设计稿颜色定义
private val SelectedGreen = Color(0xFF1B8A3E)           // 激活状态图标和文字颜色
private val SelectedGreenBg = Color(0xFFC3F0A5)          // 激活状态圆形背景
private val UnselectedIconColor = Color(0xFF696969)      // 未激活图标颜色
private val UnselectedTextColor = Color(0xFF636363)      // 未激活文字颜色
private val BarBackground = Color(0xFFFFFFFF)            // 白色背景
private val BarShadow = Color(0x14000000)                // 阴影 rgba(0,0,0,0.08)

data class BottomNavItem(
    val route: String,
    val label: String,
    val iconRes: Int
)

@Composable
fun BottomNavBar(
    items: List<BottomNavItem>,
    selectedRoute: String,
    onItemSelected: (String) -> Unit
) {
    // 整体容器：97dp高度，白色背景，顶部圆角16dp，阴影
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(97.dp)
            .navigationBarsPadding()
            .shadow(
                elevation = 20.dp,
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                ambientColor = BarShadow,
                spotColor = BarShadow
            )
            .background(
                color = BarBackground,
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
            )
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        // 内容行：水平均匀分布
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            items.forEach { item ->
                val selected = item.route == selectedRoute

                // 每个Tab：垂直布局，居中，gap=2dp
                Column(
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onItemSelected(item.route) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    // 图标背景：44dp圆形
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(
                                color = if (selected) SelectedGreenBg else Color.Transparent,
                                shape = RoundedCornerShape(22.dp)
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        // 图标：24dp
                        Icon(
                            imageVector = ImageVector.vectorResource(id = item.iconRes),
                            contentDescription = item.label,
                            tint = if (selected) SelectedGreen else UnselectedIconColor,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    // 文字：11sp，gap=2dp通过padding实现
                    Text(
                        text = item.label,
                        fontSize = 11.sp,
                        lineHeight = 13.sp,
                        modifier = Modifier.padding(top = 2.dp),
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) SelectedGreen else UnselectedTextColor,
                    )
                }
            }
        }
    }
}
