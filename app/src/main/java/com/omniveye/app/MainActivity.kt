package com.omniveye.app

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.omniveye.app.speech.HardwareVoiceAction
import com.omniveye.app.speech.HardwareVoiceControl
import com.omniveye.app.ui.screens.MainScreen
import com.omniveye.app.ui.theme.OmniEyeTheme

class MainActivity : ComponentActivity() {
    private val viewModel: com.omniveye.app.viewmodel.MainViewModel by viewModels()
    private val hardwareVoiceControl = HardwareVoiceControl()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            OmniEyeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(viewModel = viewModel)
                }
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                when (hardwareVoiceControl.onVolumeDownPressed()) {
                    HardwareVoiceAction.ShowRecognizingCommand -> viewModel.showRoadshowCommandRecognition()
                    HardwareVoiceAction.PlayRoadshowSurroundingsResult -> viewModel.playRoadshowSurroundingsResult()
                    HardwareVoiceAction.Ignore -> Unit
                }
            } else if (event.action == KeyEvent.ACTION_UP) {
                hardwareVoiceControl.onVolumeDownReleased()
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}
