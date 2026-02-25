package com.runanywhere.kotlin_starter_example.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.runanywhere.kotlin_starter_example.ui.theme.AppTheme
import kotlinx.coroutines.delay
import kotlin.math.hypot

@Composable
fun SplashScreen(
    onSplashComplete: () -> Unit
) {
    val colors = AppTheme.colors

    // Circular reveal animation progress (0 -> 1)
    val revealProgress = remember { Animatable(0f) }
    // Content fade-in alpha
    val contentAlpha = remember { Animatable(0f) }
    // Content fade-out alpha
    val fadeOutAlpha = remember { Animatable(1f) }

    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    // Max radius = diagonal from center to corner
    val maxRadius = hypot(screenWidthPx / 2f, screenHeightPx / 2f)

    LaunchedEffect(Unit) {
        // Start circular reveal from edges to center
        revealProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing)
        )
        // Fade in the text content
        contentAlpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing)
        )
        // Hold for a moment
        delay(800)
        // Fade out everything
        fadeOutAlpha.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing)
        )
        onSplashComplete()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .alpha(fadeOutAlpha.value),
        contentAlignment = Alignment.Center
    ) {
        // Circular reveal canvas - circle shrinks from edges to center (inverted reveal)
        // At progress=0, the entire screen is covered with accent color
        // At progress=1, the circle has shrunk to nothing, revealing the background
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerX = size.width / 2f
            val centerY = size.height / 2f

            // Draw the background first
            drawRect(color = colors.background)

            // Draw accent-colored circle that shrinks from max radius to 0
            val currentRadius = maxRadius * (1f - revealProgress.value)
            if (currentRadius > 0f) {
                drawCircle(
                    color = colors.accent,
                    radius = currentRadius,
                    center = Offset(centerX, centerY)
                )
            }
        }

        // Center text content
        Column(
            modifier = Modifier.alpha(contentAlpha.value),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "RunAnywhere",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                ),
                color = colors.textPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "On-Device AI",
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textSecondary
            )
        }
    }
}
