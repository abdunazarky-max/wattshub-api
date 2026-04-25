package com.hyzin.whtsappclone

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.hyzin.whtsappclone.ui.theme.*
import kotlin.math.sin
import kotlin.random.Random
@Composable
fun AnimatedBackground() {
    val infiniteTransition = rememberInfiniteTransition(label = "sky_anim")
    val twinklePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "twinkle"
    )

    // Pre-calculate stars to avoid randomizing on every frame
    val stars = remember {
        List(150) {
            Triple(Random.nextFloat(), Random.nextFloat(), Random.nextFloat())
        }
    }

    Canvas(modifier = Modifier.fillMaxSize().background(SkyNightDeep)) {
        val width = size.width
        val height = size.height

        // 1. Draw Deep Sky Gradient
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(SkyNightDeep, SkyNightLight, SkyNightDeep.copy(alpha = 0.8f))
            )
        )

        // 2. Draw Twinkling Stars
        stars.forEach { (xRatio, yRatio, starSize) ->
            val x = xRatio * width
            val y = yRatio * height
            
            // Individual star twinkle logic
            val starAlpha = (sin(twinklePhase * 2 * Math.PI.toFloat() + x * 0.01f + y * 0.01f) + 1f) / 2f * 0.6f + 0.2f
            val baseSize = starSize * 3f + 1f
            
            // Draw Star Glow
            drawCircle(
                color = StarColor.copy(alpha = starAlpha * 0.3f),
                radius = baseSize * 2f,
                center = Offset(x, y)
            )
            
            // Draw Star Core
            drawCircle(
                color = StarColor.copy(alpha = starAlpha),
                radius = baseSize,
                center = Offset(x, y)
            )
        }

        // 3. Optional: Subtle Cloud/Nebula effect
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(SkyNightLight.copy(alpha = 0.1f), Color.Transparent),
                center = Offset(width * 0.7f, height * 0.3f),
                radius = width * 0.5f
            )
        )
    }
}

