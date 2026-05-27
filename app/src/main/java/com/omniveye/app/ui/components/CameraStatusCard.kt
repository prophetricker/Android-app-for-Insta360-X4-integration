package com.omniveye.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.omniveye.app.camera.CameraConnectionState
import com.omniveye.app.cloud.CloudState
import com.omniveye.app.ui.theme.CameraConnected
import com.omniveye.app.ui.theme.CameraDisconnected
import com.omniveye.app.ui.theme.CardBackground
import com.omniveye.app.ui.theme.Info
import com.omniveye.app.ui.theme.PrimaryBlue
import com.omniveye.app.ui.theme.PrimaryBlueLight
import com.omniveye.app.ui.theme.Success
import com.omniveye.app.ui.theme.Warning

@Composable
fun CameraStatusCard(
    cameraState: CameraConnectionState,
    cloudState: CloudState,
    onConnectClick: () -> Unit,
    onDisconnectClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isConnected = cameraState is CameraConnectionState.Connected
    val statusColor by animateColorAsState(
        targetValue = if (isConnected) CameraConnected else CameraDisconnected,
        animationSpec = tween(300),
        label = "statusColor"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(
                                if (isConnected) Success.copy(alpha = 0.1f)
                                else CameraDisconnected.copy(alpha = 0.1f)
                            )
                            .border(
                                width = 2.dp,
                                color = statusColor.copy(alpha = 0.3f),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isConnected) Icons.Default.CameraAlt else Icons.Default.WifiOff,
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "Insta360 X4",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StatusIndicator(
                                color = statusColor,
                                isPulsing = cameraState is CameraConnectionState.Connecting
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = when (cameraState) {
                                    is CameraConnectionState.Connected -> "已连接"
                                    is CameraConnectionState.Connecting -> "连接中..."
                                    is CameraConnectionState.Disconnected -> "未连接"
                                    is CameraConnectionState.Error -> "错误"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = statusColor,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (cameraState is CameraConnectionState.Error) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = CameraDisconnected.copy(alpha = 0.1f)
                    )
                ) {
                    Text(
                        text = cameraState.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = CameraDisconnected,
                        modifier = Modifier.padding(12.dp)
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CloudStatusBadge(cloudState = cloudState)

                TextButton(
                    onClick = if (isConnected) onDisconnectClick else onConnectClick,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (isConnected) CameraDisconnected.copy(alpha = 0.1f)
                            else PrimaryBlue.copy(alpha = 0.1f)
                        )
                ) {
                    Text(
                        text = if (isConnected) "断开连接" else "连接相机",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isConnected) CameraDisconnected else PrimaryBlue,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun CloudStatusBadge(
    cloudState: CloudState,
    modifier: Modifier = Modifier
) {
    val (icon, text, color, bgColor) = when (cloudState) {
        is CloudState.Connected -> Quadruple(Icons.Default.CloudQueue, "后端在线", Success, Success.copy(alpha = 0.1f))
        is CloudState.Connecting -> Quadruple(Icons.Default.CloudQueue, "连接中...", Warning, Warning.copy(alpha = 0.1f))
        is CloudState.Processing -> Quadruple(Icons.Default.CloudQueue, "处理中...", Info, Info.copy(alpha = 0.1f))
        is CloudState.Uploading -> Quadruple(Icons.Default.CloudQueue, "上传中...", Info, Info.copy(alpha = 0.1f))
        is CloudState.Success<*> -> Quadruple(Icons.Default.CloudQueue, "成功", Success, Success.copy(alpha = 0.1f))
        is CloudState.Error -> Quadruple(Icons.Default.CloudOff, "错误", CameraDisconnected, CameraDisconnected.copy(alpha = 0.1f))
        else -> Quadruple(Icons.Default.CloudOff, "未连接", Color(0xFF9E9E9E), Color(0xFFF5F5F5))
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            fontWeight = FontWeight.Medium
        )
    }
}

data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@Composable
fun StatusIndicator(
    color: Color,
    isPulsing: Boolean = false,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isPulsing) 0.3f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Box(
        modifier = modifier
            .size(10.dp)
            .scale(if (isPulsing) 1.2f else 1f)
            .clip(CircleShape)
            .background(color.copy(alpha = alpha))
    )
}
