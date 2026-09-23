package com.paperbox.app.ui.quote

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

// ══════════════════════════════════════════════════════════════
//  飞机盒 3D 预览 —— 纯 Canvas 线框/平面投影，零 3D 依赖
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

/** 面的语义下标（只枚举带缝隙的面，其余仅用于渲染）。 */
private const val FACE_TOP = 0
private const val FACE_FRONT = 1
private const val FACE_RIGHT = 2
private const val FACE_LEFT = 3

private fun Color.shaded(k: Float) = Color(red * k, green * k, blue * k)

/** 一次可见面收集的中间结果（不能在 Canvas lambda 里声明类）。 */
private data class VisFace(val idx: Int, val depth: Float)

/**
 * 飞机盒尺寸预览底部弹层。
 *
 * 盒子尺寸实时绑定 QuoteUiState（弹层内可直接改长宽高，模型即时跟随）；
 * 单指拖动旋转，双指捏合缩放。
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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "飞机盒尺寸预览",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = QuoteTitle,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 2.dp),
                textAlign = TextAlign.Center
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(QuoteRowBg)
                    .border(1.dp, QuoteCardStroke, RoundedCornerShape(12.dp))
            ) {
                BoxPreviewCanvas(
                    lengthCm = state.form.length,
                    widthCm = state.form.width,
                    heightCm = state.form.height,
                    baseColor = materialColor
                )
            }

            Text(
                text = "${trimNumber(state.form.length)} × ${trimNumber(state.form.width)} × " +
                    "${trimNumber(state.form.height)} cm ｜ $materialLabel",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = QuoteTitle
            )

            // 弹层内可直接改尺寸，模型实时跟随
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuoteField("长", state.lengthText, onLength, Modifier.weight(1f))
                QuoteField("宽", state.widthText, onWidth, Modifier.weight(1f))
                QuoteField("高", state.heightText, onHeight, Modifier.weight(1f))
            }

            Text(
                "单指拖动旋转 · 双指缩放 · 单位 cm",
                fontSize = 11.sp,
                color = QuoteMuted,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * 盒子投影画布：
 * 1. 旋转（yaw/pitch 轨道）→ 2. 弱透视 → 3. 背面剔除 + 按深度排序填色 →
 * 4. 结构缝隙（盖缝/舌口/侧折线，只画在朝向相机的面上）→
 * 5. 屏幕空间画三条尺寸线（始终选朝向相机的那条棱，标签是不随旋转的胶囊）。
 */
@Composable
private fun BoxPreviewCanvas(
    lengthCm: Double,
    widthCm: Double,
    heightCm: Double,
    baseColor: Color
) {
    var yawDeg by remember { mutableFloatStateOf(-38f) }
    var pitchDeg by remember { mutableFloatStateOf(26f) }
    var zoomLevel by remember { mutableFloatStateOf(1f) }
    val measurer = rememberTextMeasurer()

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    yawDeg += pan.x * 0.45f
                    pitchDeg = (pitchDeg - pan.y * 0.4f).coerceIn(-20f, 78f)
                    zoomLevel = (zoomLevel * zoom).coerceIn(0.6f, 2.2f)
                }
            }
    ) {
        if (lengthCm <= 0 || widthCm <= 0 || heightCm <= 0) return@Canvas

        val l = lengthCm.toFloat()
        val w = widthCm.toFloat()
        val h = heightCm.toFloat()
        val hx = l / 2f
        val hy = h / 2f
        val hz = w / 2f

        val cx = size.width / 2f
        val cy = size.height / 2f

        // 对角线在任何旋转角下都是投影上界 → 固定基准缩放，旋转时大小稳定不“呼吸”
        val diag = sqrt(l * l + w * w + h * h)
        val padX = 56.dp.toPx()
        val padY = 52.dp.toPx()
        val avail = min(size.width - padX * 2f, size.height - padY * 2f)
        if (avail <= 0f) return@Canvas
        // 0.9 给弱透视的近面放大留余量
        val scale = (avail / diag) * 0.9f * zoomLevel

        // 相机距离（cm）：取最大边 3 倍，透视温和不夸张
        val camDist = 3f * max(l, max(w, h))

        val radY = Math.toRadians(yawDeg.toDouble())
        val radP = Math.toRadians(pitchDeg.toDouble())
        val cosY = cos(radY).toFloat()
        val sinY = sin(radY).toFloat()
        val cosP = cos(radP).toFloat()
        val sinP = sin(radP).toFloat()

        /** 旋转（未透视）：先绕 Y 偏航，再绕 X 俯仰。 */
        fun rot(p: Vec3): Vec3 {
            val x1 = p.x * cosY + p.z * sinY
            val z1 = -p.x * sinY + p.z * cosY
            val y2 = p.y * cosP - z1 * sinP
            val z2 = p.y * sinP + z1 * cosP
            return Vec3(x1, y2, z2)
        }

        /** 旋转 + 弱透视 + 屏幕映射。返回 (屏幕x, 屏幕y, 视空间z)。 */
        fun project(p: Vec3): Triple<Float, Float, Float> {
            val r = rot(p)
            val persp = camDist / (camDist - r.z) // |r.z| ≤ diag/2 < camDist，安全
            return Triple(cx + r.x * persp * scale, cy - r.y * persp * scale, r.z)
        }

        // ── 8 顶点 ──
        val corners = arrayOf(
            Vec3(-hx, -hy, -hz), Vec3(hx, -hy, -hz), Vec3(hx, hy, -hz), Vec3(-hx, hy, -hz),
            Vec3(-hx, -hy, hz), Vec3(hx, -hy, hz), Vec3(hx, hy, hz), Vec3(-hx, hy, hz)
        )
        val screenPts = arrayOfNulls<Triple<Float, Float, Float>>(8)
        corners.forEachIndexed { i, c -> screenPts[i] = project(c) }

        fun scr(p: Vec3): Offset {
            val t = project(p)
            return Offset(t.first, t.second)
        }

        // ── 阴影：底面中心下方一枚椭圆 ──
        run {
            val base = scr(Vec3(0f, -hy, 0f))
            val halfW = hx * scale * 1.1f
            val rx = max(halfW, 24.dp.toPx())
            val ry = rx * 0.3f
            drawOval(
                color = Color(0x1F000000),
                topLeft = Offset(base.x - rx, base.y - ry * 0.4f),
                size = Size(rx * 2f, ry * 2f)
            )
        }

        // ── 6 个面：外法线一致的绕序，背面剔除后按深度远→近填色 ──
        // 下标：0 底 1 顶 2 前 3 后 4 右 5 左（绕序从外看逆时针）
        val faceIdx = listOf(
            intArrayOf(0, 1, 5, 4),   // 底 -y
            intArrayOf(3, 7, 6, 2),   // 顶 +y
            intArrayOf(4, 5, 6, 7),   // 前 +z
            intArrayOf(1, 0, 3, 2),   // 后 -z
            intArrayOf(5, 1, 2, 6),   // 右 +x
            intArrayOf(0, 4, 7, 3)    // 左 -x
        )
        // 语义面 → faceIdx 下标（带缝隙的面）
        val seamFaceVisible = BooleanArray(4)
        // 光源（视空间）：左上前方
        val light = Vec3(0.35f, 0.8f, 0.45f).normalized()

        val visible = ArrayList<VisFace>(6)
        val rotCache = arrayOfNulls<Vec3>(8)
        corners.forEachIndexed { i, c -> rotCache[i] = rot(c) }

        faceIdx.forEachIndexed { fi, idx ->
            val a = rotCache[idx[0]]!!
            val b = rotCache[idx[1]]!!
            val cc = rotCache[idx[2]]!!
            val n = (b - a).cross(cc - a).normalized()
            if (n.z <= 0.01f) return@forEachIndexed // 背面剔除
            val depth = (rotCache[idx[0]]!!.z + rotCache[idx[1]]!!.z +
                rotCache[idx[2]]!!.z + rotCache[idx[3]]!!.z) / 4f
            visible.add(VisFace(fi, depth))
        }
        visible.sortBy { it.depth } // z 小 = 远，先画

        val outlineColor = baseColor.shaded(0.45f)
        for (vf in visible) {
            val idx = faceIdx[vf.idx]
            val path = Path()
            idx.forEachIndexed { k, ci ->
                val t = screenPts[ci]!!
                if (k == 0) path.moveTo(t.first, t.second) else path.lineTo(t.first, t.second)
            }
            path.close()

            // 法线明暗：朝光亮，背光暗（0.5~1.0）
            val a = rotCache[idx[0]]!!
            val b = rotCache[idx[1]]!!
            val cc = rotCache[idx[2]]!!
            val n = (b - a).cross(cc - a).normalized()
            val intensity = 0.5f + 0.5f * max(0f, n.dot(light))
            drawPath(path, color = baseColor.shaded(intensity))
            drawPath(path, color = outlineColor, style = Stroke(width = 1.4.dp.toPx()))

            // 语义面可见性 → 决定缝隙是否绘制
            when (vf.idx) {
                1 -> seamFaceVisible[FACE_TOP] = true
                2 -> seamFaceVisible[FACE_FRONT] = true
                4 -> seamFaceVisible[FACE_RIGHT] = true
                5 -> seamFaceVisible[FACE_LEFT] = true
            }
        }

        // ── 结构缝隙：模型空间线段，只画在朝向相机的面上 ──
        val seamColor = baseColor.shaded(0.58f)
        val seamWidth = 1.1.dp.toPx()
        val insetX = hx * 0.94f
        val seams = listOf(
            // 顶面：盖缝台阶（靠前两条平行线）
            FACE_TOP to Pair(Vec3(-insetX, hy, hz * 0.88f), Vec3(insetX, hy, hz * 0.88f)),
            FACE_TOP to Pair(Vec3(-insetX, hy, hz * 0.76f), Vec3(insetX, hy, hz * 0.76f)),
            // 前脸：横缝 + 中段舌口（开口朝下的 ∩）
            FACE_FRONT to Pair(Vec3(-insetX, hy * 0.15f, hz), Vec3(insetX, hy * 0.15f, hz)),
            FACE_FRONT to Pair(Vec3(-hx * 0.25f, hy * 0.15f, hz), Vec3(-hx * 0.25f, hy * 0.75f, hz)),
            FACE_FRONT to Pair(Vec3(hx * 0.25f, hy * 0.15f, hz), Vec3(hx * 0.25f, hy * 0.75f, hz)),
            FACE_FRONT to Pair(Vec3(-hx * 0.25f, hy * 0.75f, hz), Vec3(hx * 0.25f, hy * 0.75f, hz)),
            // 侧面：靠前 1/3 竖向折线
            FACE_RIGHT to Pair(Vec3(hx, -hy * 0.94f, hz * 0.35f), Vec3(hx, hy * 0.94f, hz * 0.35f)),
            FACE_LEFT to Pair(Vec3(-hx, -hy * 0.94f, hz * 0.35f), Vec3(-hx, hy * 0.94f, hz * 0.35f))
        )
        for ((face, seg) in seams) {
            if (!seamFaceVisible[face]) continue
            drawLine(seamColor, scr(seg.first), scr(seg.second), strokeWidth = seamWidth)
        }

        // ── 尺寸标注（屏幕空间，标签不随旋转）──
        val dimColor = Color(0xFF888888)
        val dimLineW = 1.dp.toPx()
        val dimOffset = 18.dp.toPx()
        val tickHalf = 4.dp.toPx()
        val labelStyle = TextStyle(
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = QuoteTitle
        )

        fun avgZ(a: Vec3, b: Vec3): Float = (rot(a).z + rot(b).z) / 2f

        /** 从候选棱对里挑朝向相机（视空间 z 更大）的那条。 */
        fun nearer(cands: List<Pair<Vec3, Vec3>>): Pair<Vec3, Vec3> =
            cands.maxBy { avgZ(it.first, it.second) }

        fun drawDim(edge: Pair<Vec3, Vec3>, label: String) {
            val sa = scr(edge.first)
            val sb = scr(edge.second)
            val mid = Offset((sa.x + sb.x) / 2f, (sa.y + sb.y) / 2f)

            // 外法方向：从画布中心指向棱中点（贴边时不为零向量兜底朝下）
            var out = mid - Offset(cx, cy)
            val dist = out.getDistance()
            out = if (dist < 1f) Offset(0f, 1f) else out * (1f / dist)

            val q0 = sa + out * dimOffset
            val q1 = sb + out * dimOffset
            drawLine(dimColor, q0, q1, strokeWidth = dimLineW)

            // 两端垂直刻度
            val dir = (q1 - q0)
            val dirLen = dir.getDistance()
            if (dirLen > 1f) {
                val perp = Offset(-dir.y, dir.x) * (tickHalf / dirLen)
                drawLine(dimColor, q0 - perp, q0 + perp, strokeWidth = dimLineW)
                drawLine(dimColor, q1 - perp, q1 + perp, strokeWidth = dimLineW)
            }

            // 标签胶囊：白底描边，压在线中间
            val layout = measurer.measure(label, labelStyle)
            val pillW = layout.size.width + 14.dp.toPx()
            val pillH = layout.size.height + 7.dp.toPx()
            val pillC = mid + out * (dimOffset + 9.dp.toPx())
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

        // 长：前底边 vs 后底边
        drawDim(
            nearer(
                listOf(
                    Pair(Vec3(-hx, -hy, hz), Vec3(hx, -hy, hz)),
                    Pair(Vec3(-hx, -hy, -hz), Vec3(hx, -hy, -hz))
                )
            ),
            "长 ${trimNumber(lengthCm)}"
        )
        // 宽：右底边 vs 左底边
        drawDim(
            nearer(
                listOf(
                    Pair(Vec3(hx, -hy, hz), Vec3(hx, -hy, -hz)),
                    Pair(Vec3(-hx, -hy, hz), Vec3(-hx, -hy, -hz))
                )
            ),
            "宽 ${trimNumber(widthCm)}"
        )
        // 高：四条竖棱里取底面离相机最近的那条角
        val nearestBottom = corners.filter { it.y == -hy }
            .maxBy { rot(it).z }
        drawDim(
            Pair(
                Vec3(nearestBottom.x, -hy, nearestBottom.z),
                Vec3(nearestBottom.x, hy, nearestBottom.z)
            ),
            "高 ${trimNumber(heightCm)}"
        )
    }
}
