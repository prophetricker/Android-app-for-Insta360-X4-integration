package com.omniveye.app

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.omniveye.app.ui.screens.MainScreen
import com.omniveye.app.ui.theme.OmniEyeTheme

class MainActivity : ComponentActivity() {

    private var volumeDownStartTime: Long = 0
    private val longPressThreshold = 300L

    private val viewModel: MainViewModel by lazy {
        ViewModelProvider(this)[MainViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            OmniEyeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen()
                }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (volumeDownStartTime == 0L) {
                volumeDownStartTime = System.currentTimeMillis()
                viewModel.startRecording()
            }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (volumeDownStartTime != 0L) {
                val pressDuration = System.currentTimeMillis() - volumeDownStartTime
                if (pressDuration >= longPressThreshold) {
                    viewModel.stopRecording()
                }
                volumeDownStartTime = 0L
            }
            return true
        }
        return super.onKeyUp(keyCode, event)
    }
}
