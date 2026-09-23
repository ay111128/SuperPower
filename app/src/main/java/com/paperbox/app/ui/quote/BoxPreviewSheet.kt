package com.paperbox.app.ui.quote

import android.content.Context
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.draw
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paperbox.app.CrashDiagnostics
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

// ══════════════════════════════════════════════════════════════
//  飞机盒 3D 预览 —— 纯 Canvas 平面投影，零 3D 依赖
//  模型坐标系：x = 长，y = 高（上为正），z = 宽（+z 为前脸）
// ══════════════════════════════════════════════════════════════

/** 模型空间点（单位 cm）。 */
private data class Vec3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    fun dot(o: Vec3) = x * o.x + y * o.y + z * o.z
    fun cross(o: Vec3) = Vec3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
    fun normalized(): Vec3 {
        val len = sqrt(x * x + y * y + z * z).takeIf { it > 1e-6f } ?: 1f
        return Vec3(x / len, y / len, z / len)
    }
}

/** 标注方向：按边的方向归类（x 向棱 = 长，z 向 = 宽，y 向 = 高）。 */
private enum class Dim { LEN, WID, HT }

private fun Color.shaded(k: Float) = Color(red * k, green * k, blue * k)

/** 一次可见面收集的中间结果。 */
private data class VisFace(val idx: Int, val depth: Float)

/** 6 个面的顶点绕序（外看逆时针）：0 底 1 顶 2 前 3 后 4 右 5 左。 */
private val FACE_CORNERS = listOf(
    intArrayOf(0, 1, 5, 4),   // 底 -y
    intArrayOf(3, 7, 6, 2),   // 顶 +y
    intArrayOf(4, 5, 6, 7),   // 前 +z
    intArrayOf(1, 0, 3, 2),   // 后 -z
    intArrayOf(5, 1, 2, 6),   // 右 +x
    intArrayOf(0, 4, 7, 3)    // 左 -x
)

/** 每条棱（无向，角标对）相邻的面 —— 用于判断棱是否可见、响应点选。 */
private val EDGE_FACES: Map<Pair<Int, Int>, List<Int>> = run {
    val m = LinkedHashMap<Pair<Int, Int>, MutableList<Int>>()
    FACE_CORNERS.forEachIndexed { f, idx ->
        for (i in idx.indices) {
            val a = idx[i]
            val b = idx[(i + 1) % idx.size]
            val key = if (a < b) a to b else b to a
            m.getOrPut(key) { mutableListOf() }.add(f)
        }
    }
    m
}
private val ALL_EDGES: List<Pair<Int, Int>> = EDGE_FACES.keys.toList()

/** 点到线段距离（点选边线用）。 */
private fun distToSegment(p: Offset, a: Offset, b: Offset): Float {
    val ab = b - a
    val len2 = ab.x * ab.x + ab.y * ab.y
    if (len2 < 1e-3f) return (p - a).getDistance()
    val ap = p - a
    val t = ((ap.x * ab.x + ap.y * ab.y) / len2).coerceIn(0f, 1f)
    return (p - (a + ab * t)).getDistance()
}

/**
 * 一次投影快照：旋转 + 弱透视 + 屏幕映射。
 * 绘制、点选、导出三者共用同一份，保证「点到的」「看到的」「存下的」是同一个盒子。
 */
private class BoxProj(
    l: Float, w: Float, h: Float,
    yawDeg: Float, pitchDeg: Float, zoomLevel: Float,
    val viewW: Float, val viewH: Float,
    density: Density
) {
    val hx = l / 2f
    val hy = h / 2f
    val hz = w / 2f
    val corners = arrayOf(
        Vec3(-hx, -hy, -hz), Vec3(hx, -hy, -hz), Vec3(hx, hy, -hz), Vec3(-hx, hy, -hz),
        Vec3(-hx, -hy, hz), Vec3(hx, -hy, hz), Vec3(hx, hy, hz), Vec3(-hx, hy, hz)
    )
    val cx = viewW / 2f
    val cy = viewH / 2f
    val valid: Boolean
    val scale: Float

    private val camDist = 3f * max(l, max(w, h)) // 相机距离（cm），透视温和
    private val cosY: Float
    private val sinY: Float
    private val cosP: Float
    private val sinP: Float

    init {
        val radY = Math.toRadians(yawDeg.toDouble())
        val radP = Math.toRadians(pitchDeg.toDouble())
        cosY = cos(radY).toFloat()
        sinY = sin(radY).toFloat()
        cosP = cos(radP).toFloat()
        sinP = sin(radP).toFloat()

        // 对角线在任何旋转角下都是投影上界 → 固定基准缩放，旋转时大小稳定不“呼吸”
        val diag = sqrt(l * l + w * w + h * h)
        with(density) {
            val padX = 56.dp.toPx()
            val padY = 52.dp.toPx()
            val avail = min(viewW - padX * 2f, viewH - padY * 2f)
            valid = diag > 0f && avail > 0f
            // 0.9 给弱透视的近面放大留余量
            scale = if (valid) (avail / diag) * 0.9f * zoomLevel else 0f
        }
    }

    /** 绕 Y 偏航再绕 X 俯仰（未透视）。 */
    fun rot(p: Vec3): Vec3 {
        val x1 = p.x * cosY + p.z * sinY
        val z1 = -p.x * sinY + p.z * cosY
        val y2 = p.y * cosP - z1 * sinP
        val z2 = p.y * sinP + z1 * cosP
        return Vec3(x1, y2, z2)
    }

    /** 屏幕坐标（弱透视 + y 翻转）。 */
    fun screen(p: Vec3): Offset {
        val r = rot(p)
        val persp = camDist / (camDist - r.z) // |r.z| ≤ diag/2 < camDist，安全
        return Offset(cx + r.x * persp * scale, cy - r.y * persp * scale)
    }

    fun depth(p: Vec3): Float = rot(p).z

    fun visibleFaceSet(): Set<Int> {
        val out = mutableSetOf<Int>()
        FACE_CORNERS.forEachIndexed { f, idx ->
            val a = rot(corners[idx[0]])
            val b = rot(corners[idx[1]])
            val c = rot(corners[idx[2]])
            if ((b - a).cross(c - a).normalized().z > 0.01f) out.add(f)
        }
        return out
    }

    /** 棱的方向归类（端点只有一个坐标不同 → 该坐标轴就是边方向）。 */
    fun classifyEdge(key: Pair<Int, Int>): Dim {
        val p = corners[key.first]
        val q = corners[key.second]
        return when {
            p.x != q.x -> Dim.LEN
            p.z != q.z -> Dim.WID
            else -> Dim.HT
        }
    }

    /** 自动模式：从候选棱对里挑朝向相机（视空间 z 更大）的那条。 */
    fun nearerIdx(cands: List<Pair<Int, Int>>): Pair<Int, Int> =
        cands.maxBy { (depth(corners[it.first]) + depth(corners[it.second])) / 2f }
}

/**
 * 把整个预览场景（背景 / 阴影 / 盒子 / 尺寸标注）画进当前 DrawScope。
 * 屏幕预览与导出位图**共用这一份** —— 导出图与所见完全一致，
 * 且不依赖任何窗口坐标换算（此前「截屏再裁剪」在 Dialog 窗口下坐标系错位，已废弃）。
 */
private fun DrawScope.renderBoxScene(
    lengthCm: Double,
    widthCm: Double,
    heightCm: Double,
    baseColor: Color,
    isEnglish: Boolean,
    yawDeg: Float,
    pitchDeg: Float,
    zoomLevel: Float,
    pins: Map<Dim, Pair<Int, Int>>,
    measurer: TextMeasurer,
    context: Context,
) {
    // 背景：导出位图不透明；屏幕端与外层 Box 同色，重复填充无视觉差异
    drawRect(color = QuoteRowBg, size = size)
    if (lengthCm <= 0 || widthCm <= 0 || heightCm <= 0) return

    // 诊断期包裹：绘制异常时落盘上报并把错误画出来，进程不退（同一条异常只记一次）
    try {
        val proj = BoxProj(
            lengthCm.toFloat(), widthCm.toFloat(), heightCm.toFloat(),
            yawDeg, pitchDeg, zoomLevel,
            size.width, size.height, this
        )
        if (!proj.valid) return

        val hx = proj.hx
        val hy = proj.hy
        val corners = proj.corners
        fun scr(p: Vec3): Offset = proj.screen(p)

        // ── 阴影：底面中心下方一枚椭圆 ──
        run {
            val base = scr(Vec3(0f, -hy, 0f))
            val rx = max(hx * proj.scale * 1.1f, 24.dp.toPx())
            val ry = rx * 0.3f
            drawOval(
                color = Color(0x1F000000),
                topLeft = Offset(base.x - rx, base.y - ry * 0.4f),
                size = Size(rx * 2f, ry * 2f)
            )
        }

        // ── 6 个面：背面剔除后按深度远→近填色 ──
        val light = Vec3(0.35f, 0.8f, 0.45f).normalized() // 光源（视空间）：左上前方
        val visible = ArrayList<VisFace>(6)
        val rotCache = arrayOfNulls<Vec3>(8)
        corners.forEachIndexed { i, c -> rotCache[i] = proj.rot(c) }

        FACE_CORNERS.forEachIndexed { fi, idx ->
            val a = rotCache[idx[0]]!!
            val b = rotCache[idx[1]]!!
            val c = rotCache[idx[2]]!!
            val n = (b - a).cross(c - a).normalized()
            if (n.z <= 0.01f) return@forEachIndexed // 背面剔除
            val depth = (rotCache[idx[0]]!!.z + rotCache[idx[1]]!!.z +
                rotCache[idx[2]]!!.z + rotCache[idx[3]]!!.z) / 4f
            visible.add(VisFace(fi, depth))
        }
        visible.sortBy { it.depth } // z 小 = 远，先画

        val outlineColor = baseColor.shaded(0.45f)
        for (vf in visible) {
            val idx = FACE_CORNERS[vf.idx]
            val path = Path()
            idx.forEachIndexed { k, ci ->
                val t = scr(corners[ci])
                if (k == 0) path.moveTo(t.x, t.y) else path.lineTo(t.x, t.y)
            }
            path.close()

            // 法线明暗：朝光亮，背光暗（0.5~1.0）
            val a = rotCache[idx[0]]!!
            val b = rotCache[idx[1]]!!
            val c = rotCache[idx[2]]!!
            val n = (b - a).cross(c - a).normalized()
            val intensity = 0.5f + 0.5f * max(0f, n.dot(light))
            drawPath(path, color = baseColor.shaded(intensity))
            drawPath(path, color = outlineColor, style = Stroke(width = 1.4.dp.toPx()))
        }

        // ── 尺寸标注（屏幕空间，标签不随旋转）──
        // 钉住的棱不画额外高亮线：标注跳位即反馈，多余的线在图里没有语义
        val dimColor = Color(0xFF888888)
        val dimLineW = 1.dp.toPx()
        val dimOffset = 18.dp.toPx()
        val tickHalf = 4.dp.toPx()
        val labelStyle = TextStyle(
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = QuoteTitle
        )

        fun drawDim(edge: Pair<Int, Int>, label: String) {
            val sa = scr(corners[edge.first])
            val sb = scr(corners[edge.second])
            val mid = Offset((sa.x + sb.x) / 2f, (sa.y + sb.y) / 2f)

            // 偏移方向 = 棱的屏幕单位法线，取背离盒心的一侧。
            // 法线方向只随棱自身的屏幕倾角变化：垂直棱 → 严格水平偏移，刻度与两端点恒等高。
            val e = sb - sa
            val eLen = e.getDistance()
            val degenerate = eLen <= 1e-2f
            var nrm = if (degenerate) Offset(0f, 1f) else Offset(-e.y / eLen, e.x / eLen)
            val away = mid - Offset(proj.cx, proj.cy)
            if (nrm.x * away.x + nrm.y * away.y < 0f) nrm = -nrm

            val q0 = sa + nrm * dimOffset
            val q1 = sb + nrm * dimOffset
            drawLine(dimColor, q0, q1, strokeWidth = dimLineW)

            // 两端刻度：沿法线方向跨在标注线上（= 垂直于标注线）
            if (!degenerate) {
                val tick = nrm * tickHalf
                drawLine(dimColor, q0 - tick, q0 + tick, strokeWidth = dimLineW)
                drawLine(dimColor, q1 - tick, q1 + tick, strokeWidth = dimLineW)
            }

            // 标签胶囊：白底描边，压在线中间
            val layout = measurer.measure(label, labelStyle)
            val pillW = layout.size.width + 14.dp.toPx()
            val pillH = layout.size.height + 7.dp.toPx()
            val pillC = mid + nrm * (dimOffset + 9.dp.toPx())
            val pillTopLeft = Offset(pillC.x - pillW / 2f, pillC.y - pillH / 2f)
            drawRoundRect(
                color = Color.White.copy(alpha = 0.92f),
                topLeft = pillTopLeft,
                size = Size(pillW, pillH),
                cornerRadius = CornerRadius(pillH / 2f)
            )
            drawRoundRect(
                color = Color(0xFFDDDDDD),
                topLeft = pillTopLeft,
                size = Size(pillW, pillH),
                cornerRadius = CornerRadius(pillH / 2f),
                style = Stroke(1.dp.toPx())
            )
            drawText(
                layout,
                topLeft = Offset(
                    pillC.x - layout.size.width / 2f,
                    pillC.y - layout.size.height / 2f
                )
            )
        }

        // 长：钉住优先，否则自动选前/后底边里朝向相机的
        val lenEdge = pins[Dim.LEN]
            ?: proj.nearerIdx(listOf(4 to 5, 0 to 1))
        // 宽：右底边 vs 左底边
        val widEdge = pins[Dim.WID]
            ?: proj.nearerIdx(listOf(5 to 1, 0 to 4))
        // 高：钉住优先，否则底面离相机最近的角的竖棱
        val htEdge = pins[Dim.HT] ?: run {
            val bottom = listOf(0, 1, 4, 5).maxBy { proj.depth(corners[it]) }
            // 底角 → 顶角 = 翻 y 位（XOR 3）。曾误写成 +3：底角5会算出8导致越界(length=8; index=8)
            bottom to (bottom xor 3)
        }
        val (lenWord, widWord, htWord) =
            if (isEnglish) Triple("Length", "Width", "Height")
            else Triple("长", "宽", "高")
        drawDim(lenEdge, "$lenWord ${trimNumber(lengthCm)}cm")
        drawDim(widEdge, "$widWord ${trimNumber(widthCm)}cm")
        drawDim(htEdge, "$htWord ${trimNumber(heightCm)}cm")
    } catch (e: Throwable) {
        // 绘制崩溃兜底：自动落盘并上传（同一条异常只记一次），错误也画出来，不中断
        CrashDiagnostics.record(e, context)
        try {
            val errLayout = measurer.measure(
                "绘制崩溃(已拦截，已自动上报)\n$e",
                TextStyle(fontSize = 12.sp, color = Color(0xFFD32F2F))
            )
            drawText(errLayout, topLeft = Offset(16.dp.toPx(), 16.dp.toPx()))
        } catch (_: Throwable) {
            // 兜底绘制也失败就放弃本帧，日志已经在路上了
        }
    }
}

/**
 * 裸按钮：静止无背景无描边，只显示符号，沉浸式嵌在页面里；
 * 按压反馈用 Compose 默认的水波纹（标准 clickable，不加自定义背景）。
 */
@Composable
private fun BareSymbolButton(
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
        content = content
    )
}

/**
 * 飞机盒尺寸预览底部弹层。
 *
 * 盒子尺寸实时绑定 QuoteUiState（弹层内可直接改长宽高，模型即时跟随）；
 * 单指拖动旋转，双指捏合缩放；点某条棱把标注钉到那条棱上。
 * 标题右侧：中/EN 切换 + 下载（离屏重渲当前场景成位图存相册，不截屏）。
 * 画布通栏贴屏幕边缘。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BoxPreviewSheet(
    state: QuoteUiState,
    onLength: (String) -> Unit,
    onWidth: (String) -> Unit,
    onHeight: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val chip = MaterialChipOptions.firstOrNull { it.key == state.form.materialKey }
    val materialLabel = chip?.label ?: state.form.materialKey.label
    val materialColor = chip?.dot ?: Color(0xFFC4956A)

    var isEnglish by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // 画布注册的导出函数：基于当前相机状态离屏重渲一张位图
    var captureProvider by remember { mutableStateOf<(() -> Bitmap?)?>(null) }

    /** 离屏重渲当前场景 → 存相册。 */
    fun downloadPreview() {
        val bmp = captureProvider?.invoke()
        if (bmp == null) {
            Toast.makeText(
                context,
                if (isEnglish) "Render failed" else "生成图片失败",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        scope.launch {
            try {
                saveBitmapToGallery(context, bmp, "飞机盒尺寸_${System.currentTimeMillis()}.jpg")
                Toast.makeText(
                    context,
                    if (isEnglish) "Saved to gallery" else "已保存到相册",
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                Toast.makeText(
                    context,
                    if (isEnglish) "Save failed: ${e.message}" else "保存失败：${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── 标题行：标题居中，右手边 中/EN + 下载 ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(36.dp)
            ) {
                Text(
                    if (isEnglish) "Box Size Preview" else "飞机盒尺寸预览",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = QuoteTitle,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.Center)
                )
                Row(
                    modifier = Modifier.align(Alignment.CenterEnd),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 中/EN：显示当前可切换到的语言（和报价结果页顶栏同约定）
                    BareSymbolButton(onClick = { isEnglish = !isEnglish }) {
                        Text(
                            if (isEnglish) "中" else "EN",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = QuoteGreen
                        )
                    }
                    // 下载：离屏重渲画布内容存相册
                    BareSymbolButton(onClick = { downloadPreview() }) {
                        Icon(
                            Icons.Default.FileDownload,
                            contentDescription = if (isEnglish) "Download screenshot" else "下载截图",
                            tint = QuoteGreen,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // ── 画布：通栏贴屏幕边缘，不给内边距，视野全留给模型 ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp)
                    .background(QuoteRowBg)
            ) {
                BoxPreviewCanvas(
                    lengthCm = state.form.length,
                    widthCm = state.form.width,
                    heightCm = state.form.height,
                    baseColor = materialColor,
                    isEnglish = isEnglish,
                    onRegisterCapture = { captureProvider = it }
                )
            }

            Text(
                text = "${trimNumber(state.form.length)} × ${trimNumber(state.form.width)} × " +
                    "${trimNumber(state.form.height)} cm ｜ $materialLabel",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = QuoteTitle,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            // 弹层内可直接改尺寸，模型实时跟随
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuoteField("长", state.lengthText, onLength, Modifier.weight(1f))
                QuoteField("宽", state.widthText, onWidth, Modifier.weight(1f))
                QuoteField("高", state.heightText, onHeight, Modifier.weight(1f))
            }

            Text(
                if (isEnglish) "Drag to rotate · Pinch to zoom · Tap an edge to set the label"
                else "拖动旋转 · 双指缩放 · 点边线指定标注（点空白恢复自动）",
                fontSize = 11.sp,
                color = QuoteMuted,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * 盒子投影画布：
 * 1. 旋转（yaw/pitch 轨道）→ 2. 弱透视 → 3. 背面剔除 + 按深度远→近填色 + 面轮廓描边 →
 * 4. 尺寸标注：默认自动选朝向相机的棱；点选后钉在指定棱上（无额外高亮线，跳位即反馈）。
 * 同时把「导出位图」的回调注册给弹层：导出 = 用 [renderBoxScene] 离屏重渲，与屏幕所见同一份代码。
 */
@Composable
private fun BoxPreviewCanvas(
    lengthCm: Double,
    widthCm: Double,
    heightCm: Double,
    baseColor: Color,
    isEnglish: Boolean = false,
    onRegisterCapture: ((() -> Bitmap?) -> Unit)? = null
) {
    var yawDeg by remember { mutableFloatStateOf(-38f) }
    var pitchDeg by remember { mutableFloatStateOf(26f) }
    var zoomLevel by remember { mutableFloatStateOf(1f) }
    // 每个方向各钉一条棱；空 = 该方向回自动
    var pins by remember { mutableStateOf(emptyMap<Dim, Pair<Int, Int>>()) }
    var canvasSize by remember { mutableStateOf(Size.Zero) }
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val context = LocalContext.current
    val measurer = rememberTextMeasurer()

    // 注册导出：用屏幕同一份场景代码离屏重渲到 Bitmap（坐标、密度完全一致，不经过截屏）。
    // 键带上所有「按值捕获」的 plain 参数 —— 弹层内改尺寸/材质/语言后必须重新注册，
    // 否则导出的还是旧参数的图（yaw/pitch/zoom/pins 是 state 委托，调用时读最新值，无需作键）。
    LaunchedEffect(lengthCm, widthCm, heightCm, baseColor, isEnglish) {
        onRegisterCapture?.invoke {
            val w = canvasSize.width.roundToInt()
            val h = canvasSize.height.roundToInt()
            if (w < 2 || h < 2) return@invoke null
            try {
                val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val offscreen = androidx.compose.ui.graphics.Canvas(
                    android.graphics.Canvas(bitmap)
                )
                CanvasDrawScope().draw(
                    density, layoutDirection, offscreen, Size(w.toFloat(), h.toFloat())
                ) {
                    renderBoxScene(
                        lengthCm, widthCm, heightCm, baseColor, isEnglish,
                        yawDeg, pitchDeg, zoomLevel, pins, measurer, context
                    )
                }
                bitmap
            } catch (t: Throwable) {
                CrashDiagnostics.record(t, context)
                null
            }
        }
    }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { canvasSize = Size(it.width.toFloat(), it.height.toFloat()) }
            // 点选：离点击最近的可见棱 → 钉住/取消；点空白恢复自动
            .pointerInput(lengthCm, widthCm, heightCm) {
                detectTapGestures { tap ->
                    // 诊断期包裹：点选异常 Toast 提示，不崩
                    try {
                        if (canvasSize.width <= 0f) return@detectTapGestures
                        val proj = BoxProj(
                            lengthCm.toFloat(), widthCm.toFloat(), heightCm.toFloat(),
                            yawDeg, pitchDeg, zoomLevel,
                            canvasSize.width, canvasSize.height, density
                        )
                        if (!proj.valid) return@detectTapGestures

                        val visFaces = proj.visibleFaceSet()
                        val threshold = with(density) { 30.dp.toPx() }
                        var bestKey: Pair<Int, Int>? = null
                        var bestDist = Float.MAX_VALUE
                        for (key in ALL_EDGES) {
                            // 只考虑当前视角下可见的棱（至少一个相邻面朝向相机）
                            if (EDGE_FACES[key]!!.none { it in visFaces }) continue
                            val d = distToSegment(
                                tap,
                                proj.screen(proj.corners[key.first]),
                                proj.screen(proj.corners[key.second])
                            )
                            if (d < bestDist) {
                                bestDist = d
                                bestKey = key
                            }
                        }

                        if (bestKey == null || bestDist > threshold) {
                            pins = emptyMap() // 点空白：全部恢复自动
                            return@detectTapGestures
                        }
                        val dim = proj.classifyEdge(bestKey)
                        // 再点同一条 = 解除钉住；点别的 = 换钉
                        pins = if (pins[dim] == bestKey) pins - dim else pins + (dim to bestKey)
                    } catch (e: Throwable) {
                        CrashDiagnostics.record(e, context)
                        try {
                            Toast.makeText(context, "点选标注出错，已记录并上传", Toast.LENGTH_SHORT).show()
                        } catch (_: Throwable) {
                        }
                    }
                }
            }
            // 拖动/捏合在后：点按（无位移）不会被它消费，拖动时点按手势自行取消
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    yawDeg += pan.x * 0.45f
                    pitchDeg = (pitchDeg - pan.y * 0.4f).coerceIn(-20f, 78f)
                    zoomLevel = (zoomLevel * zoom).coerceIn(0.6f, 2.2f)
                }
            }
    ) {
        renderBoxScene(
            lengthCm, widthCm, heightCm, baseColor, isEnglish,
            yawDeg, pitchDeg, zoomLevel, pins, measurer, context
        )
    }
}
