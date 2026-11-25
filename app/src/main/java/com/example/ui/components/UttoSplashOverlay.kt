package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.DawnGold
import com.example.ui.theme.DawnGoldBright
import com.example.ui.theme.HorizonCoral
import com.example.ui.theme.StarlightMist
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val SplashBackdropColor = Color(0xFF131622)

/**
 * Lightweight, fast launch splash overlay that immediately renders the Utto app logo
 * with a smooth fade, spring scale, and warm dawn glow pulse before fading out to Home.
 */
@Composable
fun UttoSplashOverlay(
    visible: Boolean,
    onAnimationFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val logoScale = remember { Animatable(0.76f) }
    val logoAlpha = remember { Animatable(0f) }
    val glowPulse = remember { Animatable(0.6f) }
    val titleAlpha = remember { Animatable(0f) }

    LaunchedEffect(visible) {
        if (!visible) return@LaunchedEffect

        // Entrance: immediate fade + spring scale + radiant glow bloom
        launch {
            logoAlpha.animateTo(1f, animationSpec = tween(260, easing = FastOutSlowInEasing))
        }
        launch {
            logoScale.animateTo(
                targetValue = 1.0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }
        launch {
            glowPulse.animateTo(1.15f, animationSpec = tween(550, easing = FastOutSlowInEasing))
            glowPulse.animateTo(0.95f, animationSpec = tween(350, easing = FastOutSlowInEasing))
        }
        launch {
            delay(160L)
            titleAlpha.animateTo(1f, animationSpec = tween(280, easing = FastOutSlowInEasing))
        }

        // Keep launch snappy (~1.05s total before crossfade transition)
        delay(1050L)
        onAnimationFinished()
    }

    AnimatedVisibility(
        visible = visible,
        exit = fadeOut(animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing)),
        modifier = modifier
            .fillMaxSize()
            .testTag("utto_splash_overlay")
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SplashBackdropColor),
            contentAlignment = Alignment.Center
        ) {
            // Subtle pulsing sunrise aura behind the app logo
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height * 0.46f)
                val radius = size.minDimension * 0.34f * glowPulse.value
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            DawnGold.copy(alpha = 0.28f * logoAlpha.value),
                            HorizonCoral.copy(alpha = 0.10f * logoAlpha.value),
                            Color.Transparent
                        ),
                        center = center,
                        radius = radius
                    ),
                    radius = radius,
                    center = center
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_utto_icon_1795498951850),
                    contentDescription = stringResource(R.string.app_name),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(116.dp)
                        .scale(logoScale.value)
                        .alpha(logoAlpha.value)
                        .clip(RoundedCornerShape(28.dp))
                )

                Spacer(Modifier.height(18.dp))

                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        fontSize = 32.sp
                    ),
                    color = StarlightMist,
                    modifier = Modifier.alpha(titleAlpha.value)
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    text = "SUNRISE ALARM",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.4.sp),
                    color = DawnGoldBright.copy(alpha = 0.85f),
                    modifier = Modifier.alpha(titleAlpha.value)
                )
            }
        }
    }
}
