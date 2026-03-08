package io.cleansky.contactless.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 */
@Composable
fun PaymentSuccessAnimation(
    amount: String,
    symbol: String,
    @Suppress("UNUSED_PARAMETER") onAnimationEnd: () -> Unit = {},
) {
    var animationStarted by remember { mutableStateOf(false) }

    val circleScale by animateFloatAsState(
        targetValue = if (animationStarted) 1f else 0f,
        animationSpec =
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow,
            ),
        label = "circleScale",
    )

    val checkScale by animateFloatAsState(
        targetValue = if (animationStarted) 1f else 0f,
        animationSpec =
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium,
            ),
        label = "checkScale",
    )

    val textAlpha by animateFloatAsState(
        targetValue = if (animationStarted) 1f else 0f,
        animationSpec = tween(500, delayMillis = 300),
        label = "textAlpha",
    )

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(1000),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "pulseScale",
    )

    LaunchedEffect(Unit) {
        animationStarted = true
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .size(160.dp)
                    .scale(circleScale * pulseScale),
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    color = AppColors.Success.copy(alpha = 0.2f),
                    radius = size.minDimension / 2,
                )
                drawCircle(
                    color = AppColors.Success,
                    radius = size.minDimension / 2,
                    style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round),
                )
            }

            // Check
            Box(
                modifier =
                    Modifier
                        .size(100.dp)
                        .scale(checkScale)
                        .background(AppColors.Success, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(60.dp),
                    tint = Color.White,
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Texto "Pago Exitoso"
        Text(
            text = "PAGO EXITOSO",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = AppColors.Success,
            modifier = Modifier.alpha(textAlpha),
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = amount,
            fontSize = 48.sp,
            fontWeight = FontWeight.Bold,
            color = AppColors.Black,
            modifier = Modifier.alpha(textAlpha),
        )

        Text(
            text = symbol,
            fontSize = 24.sp,
            fontWeight = FontWeight.Medium,
            color = AppColors.Gray,
            modifier = Modifier.alpha(textAlpha),
        )
    }
}

/**
 */
@Composable
fun PaymentErrorAnimation(
    message: String,
    @Suppress("UNUSED_PARAMETER") onRetry: () -> Unit = {},
) {
    var animationStarted by remember { mutableStateOf(false) }

    val shakeOffset by animateFloatAsState(
        targetValue = if (animationStarted) 0f else 20f,
        animationSpec =
            spring(
                dampingRatio = Spring.DampingRatioHighBouncy,
                stiffness = Spring.StiffnessHigh,
            ),
        label = "shake",
    )

    val scale by animateFloatAsState(
        targetValue = if (animationStarted) 1f else 0f,
        animationSpec =
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium,
            ),
        label = "scale",
    )

    LaunchedEffect(Unit) {
        animationStarted = true
    }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .offset(x = shakeOffset.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .size(120.dp)
                    .scale(scale)
                    .background(AppColors.Error.copy(alpha = 0.1f), CircleShape),
        ) {
            Text(
                text = "✕",
                fontSize = 60.sp,
                fontWeight = FontWeight.Bold,
                color = AppColors.Error,
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "ERROR",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = AppColors.Error,
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = message,
            fontSize = 16.sp,
            color = AppColors.Gray,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp),
        )
    }
}

/**
 */
@Composable
fun ProcessingAnimation(message: String = "Procesando...") {
    val infiniteTransition = rememberInfiniteTransition(label = "processing")

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(1500, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "rotation",
    )

    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(800),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "pulse",
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(100.dp),
        ) {
            Canvas(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .scale(pulse),
            ) {
                drawArc(
                    color = AppColors.CollectPrimary.copy(alpha = 0.3f),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round),
                )
                drawArc(
                    color = AppColors.CollectPrimary,
                    startAngle = rotation,
                    sweepAngle = 90f,
                    useCenter = false,
                    style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round),
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = message,
            fontSize = 18.sp,
            color = AppColors.Gray,
        )
    }
}
