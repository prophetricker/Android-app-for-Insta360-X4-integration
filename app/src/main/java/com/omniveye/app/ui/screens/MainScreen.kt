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
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.omniveye.app.cloud.CellularNetworkState
import com.omniveye.app.speech.SpeechRecognitionState
import com.omniveye.app.ui.components.CameraStatusCard
import com.omniveye.app.ui.components.ResultCard
import com.omniveye.app.ui.components.VoiceInputButton
import com.omniveye.app.ui.components.VoiceOutputDisplay
import com.omniveye.app.ui.theme.Background
import com.omniveye.app.ui.theme.CardBackground
import com.omniveye.app.ui.theme.PrimaryBlue
import com.omniveye.app.ui.theme.PrimaryBlueLight
import com.omniveye.app.ui.theme.Success
import com.omniveye.app.ui.theme.Surface
import com.omniveye.app.ui.theme.Warning
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

            BackendUrlCard(
                baseUrl = uiState.backendBaseUrl,
                isLoading = uiState.isLoading,
                cameraState = uiState.cameraState,
                cellularNetworkState = uiState.cellularNetworkState,
                onSaveClick = { viewModel.updateBackendBaseUrl(it) },
                onCheckClick = { viewModel.checkCloudHealth() }
            )

            PhotoCaptureCard(
                isConnected = uiState.cameraState is CameraConnectionState.Connected,
                isLoading = uiState.isLoading,
                onCaptureClick = { viewModel.capturePhoto() },
                onDemoClick = { viewModel.playRoadshowDemo() }
            )

            VoiceInputSection(
                recognitionState = uiState.speechRecognitionState,
                hasAudioPermission = audioPermissionState.status.isGranted,
                onRequestPermission = { audioPermissionState.launchPermissionRequest() },
                onStartListening = { viewModel.startListening() },
                onStopListening = { viewModel.stopListening() }
            )

            AnimatedVisibility(
                visible = uiState.analyzeResult != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                uiState.analyzeResult?.let { result ->
                    AnalyzeResultCard(result, uiState.demoSceneName)
                }
            }

            VoiceOutputDisplay(
                text = uiState.processedResult.ifBlank { uiState.currentTtsText },
                ttsState = uiState.ttsState,
                onSpeakClick = { viewModel.speakText(uiState.processedResult) },
                onStopClick = { viewModel.stopSpeaking() }
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun BackendUrlCard(
    baseUrl: String,
    isLoading: Boolean,
    cameraState: CameraConnectionState,
    cellularNetworkState: CellularNetworkState,
    onSaveClick: (String) -> Unit,
    onCheckClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var text by remember(baseUrl) { mutableStateOf(baseUrl) }

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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "云端地址",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("HTTPS 隧道地址") }
            )
            CloudRouteStatus(
                cameraState = cameraState,
                cellularNetworkState = cellularNetworkState
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { onSaveClick(text) },
                    enabled = !isLoading,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("保存")
                }
                Button(
                    onClick = onCheckClick,
                    enabled = !isLoading,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("检测")
                }
            }
        }
    }
}

@Composable
fun CloudRouteStatus(
    cameraState: CameraConnectionState,
    cellularNetworkState: CellularNetworkState,
    modifier: Modifier = Modifier
) {
    val x4Text = if (cameraState is CameraConnectionState.Connected) {
        "X4 WiFi: connected"
    } else {
        "X4 WiFi: not connected"
    }
    val cellularText = when (cellularNetworkState) {
        is CellularNetworkState.Available -> "Cellular cloud route: ready"
        is CellularNetworkState.Requesting -> "Cellular cloud route: requesting"
        is CellularNetworkState.Lost -> "Cellular cloud route: lost"
        is CellularNetworkState.Unavailable -> "Cellular cloud route: not ready"
    }
    val cellularColor = when (cellularNetworkState) {
        is CellularNetworkState.Available -> Success
        is CellularNetworkState.Requesting -> Warning
        is CellularNetworkState.Lost -> Warning
        is CellularNetworkState.Unavailable -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = x4Text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = cellularText,
            style = MaterialTheme.typography.bodySmall,
            color = cellularColor,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun checkWifiEnabled(context: Context): Boolean {
    val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    return wifiManager.isWifiEnabled
}

@Composable
fun PhotoCaptureCard(
    isConnected: Boolean,
    isLoading: Boolean,
    onCaptureClick: () -> Unit,
    onDemoClick: () -> Unit,
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(PrimaryBlueLight.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Photo,
                        contentDescription = null,
                        tint = PrimaryBlue,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = "图像处理",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "拍摄或获取相机图片",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.End
            ) {
                Button(
                    onClick = onCaptureClick,
                    enabled = !isLoading,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryBlue,
                        disabledContainerColor = PrimaryBlue.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.height(44.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            if (isConnected) "拍照" else "上传",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
                Button(
                    onClick = onDemoClick,
                    enabled = !isLoading,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Warning),
                    modifier = Modifier.height(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Photo,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("路演演示", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
fun VoiceInputSection(
    recognitionState: SpeechRecognitionState,
    hasAudioPermission: Boolean,
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
fun AnalyzeResultCard(
    result: com.omniveye.app.cloud.AnalyzeResponse,
    demoSceneName: String?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
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
                text = if (demoSceneName == null) "云端避障分析" else "路演演示：$demoSceneName",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }

        ResultCard(
            title = "中文提醒",
            content = result.sceneText
        )

        ResultCard(
            title = "距离与风险",
            content = "前方约 %.2f 米，风险等级 %d，置信度 %.0f%%，云端耗时 %d ms".format(
                result.distanceM,
                result.level,
                result.confidence * 100,
                result.latencyMs
            )
        )
    }
}
