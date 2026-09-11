package com.aurora.player.visual

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

/**
 * องค์ประกอบภาพหลัก: แผ่นเสียงหมุน + วงแหวนความคืบหน้า + เงาเรืองแสง
 *
 * หลัก Motion Design ที่ใช้:
 *  - Rotation: linear easing + infiniteRepeatable (การหมุนสม่ำเสมอคือ physical truth)
 *  - Scale   : spring(dampingRatio = MediumBouncy) — สื่ออารมณ์ "มีชีวิต" เมื่อเริ่มเล่น
 *  - ทุกค่าถูกอ่านภายใน graphicsLayer{} lambda → deferred read (Draw Phase เท่านั้น)
 */
@Composable
fun VinylArtwork(
    artworkModel: Any?,
    isPlaying: Boolean,
    progress: Float,
    palette: AuroraPalette,
    modifier: Modifier = Modifier,
    size: Dp = 300.dp,
    rotationPeriodSeconds: Int = 24,
) {
    val infinite = rememberInfiniteTransition(label = "vinyl")
    val spin by infinite.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(rotationPeriodSeconds * 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = "spin"
    )
    // หยุด/เริ่มหมุนอย่างนุ่มนวลด้วยการถ่วงน้ำหนักมุม
    val spinFactor by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = tween(1400, easing = FastOutSlowInEasing), label = "spinFactor"
    )
    val scale by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0.9f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ), label = "scale"
    )
    val glow by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0.35f,
        animationSpec = tween(800), label = "glow"
    )
    val animProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(220, easing = LinearEasing), label = "progress"
    )

    val density = LocalDensity.current
    val ringStroke = with(density) { 5.dp.toPx() }

    Box(modifier.size(size), contentAlignment = Alignment.Center) {

        // Glow ด้านหลัง
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = glow * 0.9f; scaleX = 1.08f; scaleY = 1.08f }
                .drawWithCache {
                    val brush = Brush.radialGradient(
                        listOf(palette.vibrant.copy(alpha = 0.55f), Color.Transparent)
                    )
                    onDrawBehind { drawCircle(brush) }
                }
        )

        // ปกอัลบั้มหมุน
        AsyncImage(
            model = artworkModel,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize(0.88f)
                .graphicsLayer {
                    rotationZ = spin * spinFactor
                    scaleX = scale; scaleY = scale
                    shadowElevation = 28f
                    shape = CircleShape
                    clip = true
                }
        )

        // รูตรงกลางแผ่นเสียง
        Box(
            Modifier
                .fillMaxSize(0.16f)
                .graphicsLayer { shape = CircleShape; clip = true }
                .drawWithCache {
                    onDrawBehind {
                        drawCircle(palette.dominant)
                        drawCircle(palette.accent.copy(alpha = 0.6f),
                            radius = size.minDimension / 2f, style = Stroke(2f))
                    }
                }
        )

        // วงแหวนความคืบหน้า
        Box(
            Modifier
                .fillMaxSize()
                .drawWithCache {
                    val sweepBrush = Brush.sweepGradient(
                        listOf(palette.accent, palette.vibrant, palette.accent)
                    )
                    onDrawBehind {
                        val inset = ringStroke / 2f
                        drawArc(
                            color = Color.White.copy(alpha = 0.12f),
                            startAngle = -90f, sweepAngle = 360f, useCenter = false,
                            topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                            size = androidx.compose.ui.geometry.Size(
                                size.width - ringStroke, size.height - ringStroke),
                            style = Stroke(ringStroke, cap = StrokeCap.Round)
                        )
                        drawArc(
                            brush = sweepBrush,
                            startAngle = -90f,
                            sweepAngle = 360f * animProgress.coerceIn(0f, 1f),
                            useCenter = false,
                            topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                            size = androidx.compose.ui.geometry.Size(
                                size.width - ringStroke, size.height - ringStroke),
                            style = Stroke(ringStroke, cap = StrokeCap.Round)
                        )
                    }
                }
        )
    }
}
