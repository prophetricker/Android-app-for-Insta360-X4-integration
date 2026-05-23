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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.omniveye.app.speech.SpeechRecognitionState
import com.omniveye.app.ui.theme.CameraDisconnected
import com.omniveye.app.ui.theme.Listening
import com.omniveye.app.ui.theme.PrimaryBlue
import com.omniveye.app.ui.theme.PrimaryBlueLight
import com.omniveye.app.ui.theme.ShadowLight
import com.omniveye.app.ui.theme.Speaking
import com.omniveye.app.ui.theme.Success

@Composable
fun VoiceInputButton(
    state: SpeechRecognitionState,
    enabled: Boolean = true,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isListening = state is SpeechRecognitionState.Listening
    val isProcessing = state is SpeechRecognitionState.Processing
    val hasError = state is SpeechRecognitionState.Error

    val backgroundColor by animateColorAsState(
        targetValue = when {
            hasError -> CameraDisconnected
            isListening -> Listening
            isProcessing -> Speaking
            enabled -> PrimaryBlue
            else -> Color.Gray
        },
        animationSpec = tween(300),
        label = "bgColor"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isListening) 1.12f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val ringAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(700),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ringPulse"
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .scale(if (isListening) scale else 1f)
                .clip(CircleShape)
                .background(backgroundColor)
                .then(
                    if (isListening) {
                        Modifier.border(4.dp, Listening.copy(alpha = ringAlpha), CircleShape)
                    } else {
                        Modifier.border(1.dp, ShadowLight, CircleShape)
                    }
                )
                .clickable(enabled = enabled) {
                    if (isListening) {
                        onStopListening()
                    } else {
                        onStartListening()
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = when {
                    !enabled -> Icons.Default.MicOff
                    isListening -> Icons.Default.Stop
                    else -> Icons.Default.Mic
                },
                contentDescription = if (isListening) "停止录音" else "开始录音",
                tint = Color.White,
                modifier = Modifier.size(42.dp)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = when {
                hasError -> "录音失败"
                isListening -> "点击停止"
                isProcessing -> "识别中..."
                enabled -> "按住说话"
                else -> "不可用"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )

        if (state is SpeechRecognitionState.Error) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = state.message,
                style = MaterialTheme.typography.bodySmall,
                color = CameraDisconnected,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}
