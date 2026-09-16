package com.paperbox.app.ui.analysis

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ── 设计稿颜色 ──
private val BgGray = Color(0xFFF5F5F5)
private val Green = Color(0xFF1B8A3E)
private val CardBg = Color(0xFFFFFFFF)
private val CardBorder = Color(0xFFE5E5E5)
private val TitleDark = Color(0xFF333333)
private val SubGray = Color(0xFF999999)
private val LabelGray = Color(0xFF666666)
private val ValueDark = Color(0xFF1A1A1A)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalysisScreen() {
    var supplierFileName by remember { mutableStateOf<String?>(null) }
    var alibabaFileName by remember { mutableStateOf<String?>(null) }
    var showResult by remember { mutableStateOf(false) }

    val supplierPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { supplierFileName = it.lastPathSegment ?: "已选择" }
    }

    val alibabaPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { alibabaFileName = it.lastPathSegment ?: "已选择" }
    }

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
                        "对账",
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── 上传区域 ──
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // 供应商 Excel
                UploadCard(
                    title = "供应商 Excel",
                    subtitle = "上传供应商报价表",
                    fileName = supplierFileName,
                    onUpload = { supplierPicker.launch("*/*") }
                )
                // 1688 后台 Excel
                UploadCard(
                    title = "1688 后台 Excel",
                    subtitle = "上传1688后台数据",
                    fileName = alibabaFileName,
                    onUpload = { alibabaPicker.launch("*/*") }
                )
            }

            // ── 计算利润按钮 ──
            Button(
                onClick = { showResult = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Green),
                enabled = supplierFileName != null && alibabaFileName != null
            ) {
                Text(
                    "计算利润",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }

            // ── 利润结果 ──
            if (showResult) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "利润结果",
                        color = TitleDark,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = CardBg),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            ProfitRow(label = "总成本", value = "¥12,580.00", valueColor = ValueDark)
                            ProfitRow(label = "总收入", value = "¥18,920.00", valueColor = ValueDark)
                            ProfitRow(label = "净利润", value = "¥6,340.00", valueColor = Green)
                            ProfitRow(label = "利润率", value = "33.5%", valueColor = Green)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UploadCard(
    title: String,
    subtitle: String,
    fileName: String?,
    onUpload: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    if (fileName != null) "$title ✓" else title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = TitleDark
                )
                Text(
                    if (fileName != null) fileName else subtitle,
                    fontSize = 12.sp,
                    color = if (fileName != null) Green else SubGray
                )
            }
            Button(
                onClick = onUpload,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Green),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                modifier = Modifier.height(36.dp)
            ) {
                Text("上传", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color.White)
            }
        }
    }
}

@Composable
private fun ProfitRow(label: String, value: String, valueColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 14.sp, color = LabelGray)
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = valueColor)
    }
}
