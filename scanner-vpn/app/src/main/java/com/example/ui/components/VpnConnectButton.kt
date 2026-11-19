package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.ripple
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.TunnelStatus
import com.example.ui.theme.ConnectedGreen
import com.example.ui.theme.ConnectingAmber
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurfaceBorder
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.DisconnectedGray
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonGreenGlow
import com.example.ui.theme.TextPrimary

@Composable
fun VpnConnectButton(
    status: TunnelStatus,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (status == TunnelStatus.CONNECTED || status == TunnelStatus.CONNECTING) 1.15f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = if (status == TunnelStatus.CONNECTED || status == TunnelStatus.CONNECTING) 0.1f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val mainColor = when (status) {
        TunnelStatus.CONNECTED -> ConnectedGreen
        TunnelStatus.CONNECTING, TunnelStatus.DISCONNECTING -> ConnectingAmber
        TunnelStatus.ERROR -> ErrorRed
        TunnelStatus.DISCONNECTED -> ElectricBlue
    }

    val glowColor = when (status) {
        TunnelStatus.CONNECTED -> NeonGreenGlow
        TunnelStatus.CONNECTING, TunnelStatus.DISCONNECTING -> Color(0x66FFB020)
        TunnelStatus.ERROR -> Color(0x66FF453A)
        TunnelStatus.DISCONNECTED -> Color(0x2200D2FF)
    }

    Box(
        modifier = modifier.size(200.dp),
        contentAlignment = Alignment.Center
    ) {
        // Outer pulsing ring for active / connecting status
        if (status == TunnelStatus.CONNECTED || status == TunnelStatus.CONNECTING) {
            Box(
                modifier = Modifier
                    .size(190.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(glowColor.copy(alpha = pulseAlpha))
            )
        }

        // Secondary outer border ring
        Box(
            modifier = Modifier
                .size(172.dp)
                .clip(CircleShape)
                .border(
                    width = 2.dp,
                    brush = Brush.sweepGradient(
                        listOf(
                            mainColor.copy(alpha = 0.8f),
                            ElectricBlue.copy(alpha = 0.5f),
                            mainColor.copy(alpha = 0.8f)
                        )
                    ),
                    shape = CircleShape
                )
        )

        // Main clickable central button
        Box(
            modifier = Modifier
                .size(150.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            DarkSurfaceElevated,
                            DarkBackground
                        )
                    )
                )
                .border(
                    width = 3.dp,
                    color = mainColor,
                    shape = CircleShape
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = true, color = mainColor),
                    onClick = onClick
                )
                .testTag("vpn_connect_button"),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val icon = when (status) {
                    TunnelStatus.CONNECTED -> Icons.Default.Lock
                    TunnelStatus.CONNECTING, TunnelStatus.DISCONNECTING -> Icons.Default.Sync
                    TunnelStatus.ERROR -> Icons.Default.Warning
                    TunnelStatus.DISCONNECTED -> Icons.Default.LockOpen
                }

                Icon(
                    imageVector = icon,
                    contentDescription = "VPN Connection Toggle",
                    tint = mainColor,
                    modifier = Modifier.size(44.dp)
                )

                Spacer(modifier = Modifier.height(6.dp))

                val buttonLabel = when (status) {
                    TunnelStatus.CONNECTED -> "CONNECTED"
                    TunnelStatus.CONNECTING -> "CONNECTING"
                    TunnelStatus.DISCONNECTING -> "STOPPING"
                    TunnelStatus.ERROR -> "RETRY"
                    TunnelStatus.DISCONNECTED -> "CONNECT"
                }

                Text(
                    text = buttonLabel,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}
