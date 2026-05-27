package com.omniveye.app.ui.screens

import android.Manifest
import android.content.Context
import android.net.wifi.WifiManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.omniveye.app.camera.CameraConnectionState
import com.omniveye.app.cloud.GitHubUploadResult
import com.omniveye.app.cloud.UploadProgress
import com.omniveye.app.speech.SpeechRecognitionState
import com.omniveye.app.ui.components.CameraStatusCard
import com.omniveye.app.ui.components.VoiceInputButton
import com.omniveye.app.ui.components.VoiceOutputDisplay
import com.omniveye.app.ui.theme.Background
import com.omniveye.app.ui.theme.CardBackground
import com.omniveye.app.ui.theme.PrimaryBlue
import com.omniveye.app.ui.theme.PrimaryBlueLight
import com.omniveye.app.ui.theme.Surface
import com.omniveye.app.viewmodel.MainUiState
import com.omniveye.app.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val audioPermissionState = rememberPermissionState(Manifest.permission.RECORD_AUDIO)

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    LaunchedEffect(uiState.speechRecognitionState) {
        if (uiState.speechRecognitionState is SpeechRecognitionState.Result) {
            val recognized = (uiState.speechRecognitionState as SpeechRecognitionState.Result).text
            if (recognized.isNotBlank()) {
                viewModel.processVoiceInput(recognized)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(PrimaryBlue),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "OmniEye",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            CameraStatusCard(
                cameraState = uiState.cameraState,
                cloudState = uiState.cloudState,
                onConnectClick = {
                    if (checkWifiEnabled(context)) {
                        viewModel.connectToCamera()
                    } else {
                        viewModel.clearError()
                    }
                },
                onDisconnectClick = { viewModel.disconnectCamera() }
            )

            // GitHub上传状态
            if (uiState.githubUploadProgress !is UploadProgress.Idle || uiState.lastUploadResult != null) {
                GitHubUploadStatusCard(
                    uploadProgress = uiState.githubUploadProgress,
                    lastResult = uiState.lastUploadResult,
                    onDismiss = { viewModel.clearUploadProgress() }
                )
            }

            VoiceInputSection(
                recognitionState = uiState.speechRecognitionState,
                hasAudioPermission = audioPermissionState.status.isGranted,
                modelReady = uiState.voskModelReady,
                onRequestPermission = { audioPermissionState.launchPermissionRequest() },
                onStartListening = { viewModel.startListening() },
                onStopListening = { viewModel.stopListening() }
            )

            VoiceOutputDisplay(
                text = uiState.processedResult.ifBlank { uiState.currentTtsText },
                ttsState = uiState.ttsState,
                onSpeakClick = { viewModel.speakText(uiState.processedResult) },
                onStopClick = { viewModel.stopSpeaking() }
            )

            // 操作按钮区域
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 拍照并上传到GitHub
                Button(
                    onClick = { viewModel.captureAndUploadToGitHub() },
                    enabled = uiState.cameraState is CameraConnectionState.Connected &&
                              !uiState.isLoading &&
                              uiState.isGithubConfigured,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF24292E) // GitHub dark color
                    )
                ) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("上传GitHub")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

private fun checkWifiEnabled(context: Context): Boolean {
    val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    return wifiManager.isWifiEnabled
}

@Composable
fun VoiceInputSection(
    recognitionState: SpeechRecognitionState,
    hasAudioPermission: Boolean,
    modelReady: Boolean,
    onRequestPermission: () -> Unit,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(PrimaryBlueLight.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = null,
                        tint = PrimaryBlue,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "语音输入",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (!hasAudioPermission) {
                Button(
                    onClick = onRequestPermission,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) {
                    Text("授予麦克风权限", style = MaterialTheme.typography.labelLarge)
                }
            } else if (!modelReady) {
                // Vosk model is still loading
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        color = PrimaryBlue
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "正在下载语音模型...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                VoiceInputButton(
                    state = recognitionState,
                    enabled = true,
                    onStartListening = onStartListening,
                    onStopListening = onStopListening
                )
            }

            if (recognitionState is SpeechRecognitionState.Result) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = PrimaryBlueLight.copy(alpha = 0.1f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "识别结果:",
                            style = MaterialTheme.typography.labelMedium,
                            color = PrimaryBlue
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = recognitionState.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GitHubUploadStatusCard(
    uploadProgress: UploadProgress,
    lastResult: GitHubUploadResult?,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (uploadProgress) {
                is UploadProgress.Success -> Color(0xFF4CAF50).copy(alpha = 0.1f)
                is UploadProgress.Error -> Color(0xFFF44336).copy(alpha = 0.1f)
                is UploadProgress.Uploading -> Color(0xFF2196F3).copy(alpha = 0.1f)
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = when (uploadProgress) {
                            is UploadProgress.Success -> Icons.Default.CloudDone
                            is UploadProgress.Error -> Icons.Default.CloudOff
                            is UploadProgress.Uploading -> Icons.Default.CloudUpload
                            is UploadProgress.Preparing -> Icons.Default.CloudSync
                            else -> Icons.Default.Cloud
                        },
                        contentDescription = null,
                        tint = when (uploadProgress) {
                            is UploadProgress.Success -> Color(0xFF4CAF50)
                            is UploadProgress.Error -> Color(0xFFF44336)
                            else -> MaterialTheme.colorScheme.primary
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "GitHub 上传",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                if (uploadProgress is UploadProgress.Success || uploadProgress is UploadProgress.Error) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "关闭")
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            when (uploadProgress) {
                is UploadProgress.Idle -> {
                    Text(
                        text = "等待上传...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                is UploadProgress.Preparing -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = uploadProgress.message,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                is UploadProgress.Uploading -> {
                    Column {
                        Text(
                            text = "上传中: ${uploadProgress.fileName}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { uploadProgress.progress / 100f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            text = "${uploadProgress.progress}%",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.align(Alignment.End)
                        )
                    }
                }
                is UploadProgress.Success -> {
                    Column {
                        Text(
                            text = "上传成功!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF4CAF50)
                        )
                        uploadProgress.result.downloadUrl?.let { url ->
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "文件名: ${uploadProgress.result.fileName}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "路径: ${uploadProgress.result.filePath}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
                is UploadProgress.Error -> {
                    Text(
                        text = "错误: ${uploadProgress.message}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFF44336)
                    )
                }
            }
        }
    }
}
