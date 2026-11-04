package com.sabifox.vpn.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import org.jetbrains.compose.resources.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sabifox.vpn.resources.*
import com.sabifox.vpn.resources.Res
import com.sabifox.vpn.data.model.ConnectionState

/** Soft aurora wash used behind the main screens. */
@Composable
fun sabiAuroraBrush(): Brush {
    val scheme = MaterialTheme.colorScheme
    val base = scheme.background
    return Brush.verticalGradient(
        0f to lerp(base, scheme.tertiary, 0.16f),
        0.38f to lerp(base, scheme.primary, 0.07f),
        1f to base,
    )
}

@Composable
fun SabiMark(modifier: Modifier = Modifier, size: Dp = 40.dp) {
    Image(
        painter = painterResource(Res.drawable.ic_sabifox_mark),
        contentDescription = null,
        modifier = modifier.size(size),
    )
}

@Composable
fun SabiWordmark(modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        SabiMark(size = 30.dp)
        Spacer(Modifier.width(10.dp))
        Text(
            text = "SABI",
            fontWeight = FontWeight.Black,
            fontSize = 20.sp,
            letterSpacing = 3.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "\u2009FOX",
            fontWeight = FontWeight.Light,
            fontSize = 20.sp,
            letterSpacing = 3.sp,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** Animated connect control: rotating aurora ring, pulse halo and tick dial. */
@Composable
fun SabiConnectOrb(
    state: ConnectionState,
    ringColor: Color,
    scale: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "sabi-orb")
    val rotation by
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec =
                infiniteRepeatable(
                    animation =
                        tween(
                            durationMillis = if (state == ConnectionState.CONNECTING) 1400 else 9000,
                            easing = LinearEasing,
                        )
                ),
            label = "sabi-orb-rotation",
        )
    val pulse by
        transition.animateFloat(
            initialValue = 0.8f,
            targetValue = 1f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
            label = "sabi-orb-pulse",
        )
    val mint = MaterialTheme.colorScheme.primary
    val violet = MaterialTheme.colorScheme.tertiary
    val core = MaterialTheme.colorScheme.surfaceContainerHigh
    val active = state != ConnectionState.DISCONNECTED

    Box(
        modifier =
            modifier.size(212.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2f
            val haloAlpha = if (active) 0.30f * pulse else 0.10f
            drawCircle(
                brush =
                    Brush.radialGradient(
                        colors = listOf(ringColor.copy(alpha = haloAlpha), Color.Transparent),
                        center = center,
                        radius = radius,
                    ),
                radius = radius,
            )
            val ticks = 60
            for (i in 0 until ticks) {
                rotate(degrees = i * 360f / ticks) {
                    drawLine(
                        color = ringColor.copy(alpha = if (i % 5 == 0) 0.6f else 0.18f),
                        start = Offset(center.x, center.y - radius * 0.95f),
                        end = Offset(center.x, center.y - radius * (if (i % 5 == 0) 0.88f else 0.91f)),
                        strokeWidth = 1.5.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
            }
            rotate(degrees = rotation) {
                drawCircle(
                    brush = Brush.sweepGradient(listOf(mint, violet, ringColor, mint), center = center),
                    radius = radius * 0.78f * scale,
                    style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round),
                    alpha = if (active) 1f else 0.4f,
                )
            }
            val coreRadius = radius * 0.68f * scale
            drawCircle(color = core, radius = coreRadius)
            drawCircle(
                brush =
                    Brush.radialGradient(
                        colors = listOf(ringColor.copy(alpha = 0.24f), Color.Transparent),
                        center = center,
                        radius = coreRadius,
                    ),
                radius = coreRadius,
            )
        }
        Icon(
            imageVector = Icons.Filled.PowerSettingsNew,
            contentDescription = if (state == ConnectionState.DISCONNECTED) "Connect" else "Disconnect",
            tint = ringColor,
            modifier = Modifier.size(58.dp),
        )
    }
}
