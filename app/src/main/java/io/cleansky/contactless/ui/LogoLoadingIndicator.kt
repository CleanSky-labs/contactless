package io.cleansky.contactless.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.cleansky.contactless.R

/**
 * CleanSky logo with pulsing animation for loading states.
 * Replaces CircularProgressIndicator with branded loading experience.
 */
@Composable
fun LogoLoadingIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 64.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "loading")

    // Pulse scale animation
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    // Subtle alpha animation
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Image(
        painter = painterResource(id = R.drawable.ic_cleansky),
        contentDescription = "Loading",
        modifier = modifier
            .size(size)
            .scale(scale)
            .alpha(alpha)
    )
}

/**
 * Smaller version for inline loading (buttons, etc)
 */
@Composable
fun LogoLoadingIndicatorSmall(
    modifier: Modifier = Modifier
) {
    LogoLoadingIndicator(
        modifier = modifier,
        size = 24.dp
    )
}
