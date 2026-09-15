package com.paperbox.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.getValue
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
import com.paperbox.app.R

// 颜色定义 - 匹配设计稿
private val SelectedGreen = Color(0xFF1B8A3E)           // 激活状态绿色
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
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .shadow(20.dp, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp), spotColor = BarShadow)
            .background(BarBackground, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .padding(top = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            items.forEach { item ->
                val selected = item.route == selectedRoute

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onItemSelected(item.route) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    gap = 2.dp,
                ) {
                    // 图标容器 - 44dp圆形背景
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(
                                color = if (selected) SelectedGreenBg else Color.Transparent,
                                shape = RoundedCornerShape(22.dp)
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = ImageVector.vectorResource(id = item.iconRes),
                            contentDescription = item.label,
                            tint = if (selected) SelectedGreen else UnselectedIconColor,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    // 标签文字
                    Text(
                        text = item.label,
                        fontSize = 11.sp,
                        lineHeight = 13.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) SelectedGreen else UnselectedTextColor,
                    )
                }
            }
        }
    }
}
