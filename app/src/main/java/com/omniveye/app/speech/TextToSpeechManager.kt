package com.omniveye.app.speech

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.Locale
import java.util.UUID

sealed class TtsState {
    data object Idle : TtsState()
    data object Initializing : TtsState()
    data object Ready : TtsState()
    data object Speaking : TtsState()
    data class Error(val message: String) : TtsState()
}

class TextToSpeechManager(private val context: Context) {

    companion object {
        private const val TAG = "TextToSpeechManager"
    }

    private var tts: TextToSpeech? = null
    private var mediaPlayer: MediaPlayer? = null

    private val _ttsState = MutableStateFlow<TtsState>(TtsState.Idle)
    val ttsState: StateFlow<TtsState> = _ttsState.asStateFlow()

    private val _currentText = MutableStateFlow("")
    val currentText: StateFlow<String> = _currentText.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private var isInitialized = false
    private var pendingText: String? = null

    fun initialize() {
        if (isInitialized) {
            Log.d(TAG, "TTS already initialized")
            return
        }

        _ttsState.value = TtsState.Initializing
        Log.d(TAG, "Initializing TextToSpeech...")

        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale.CHINESE)
                if (result == TextToSpeech.LANG_MISSING_DATA ||
                    result == TextToSpeech.LANG_NOT_SUPPORTED
                ) {
                    Log.w(TAG, "Chinese language not supported, falling back to default")
                    tts?.setLanguage(Locale.getDefault())
                }

                tts?.setOnUtteranceProgressListener(createUtteranceListener())

                isInitialized = true
                _ttsState.value = TtsState.Ready
                Log.d(TAG, "TextToSpeech initialized successfully")

                pendingText?.let {
                    speak(it)
                    pendingText = null
                }
            } else {
                Log.e(TAG, "Failed to initialize TTS, status: $status")
                _ttsState.value = TtsState.Error("Failed to initialize TTS")
            }
        }
    }

    fun speak(text: String, flush: Boolean = true) {
        if (text.isBlank()) {
            Log.w(TAG, "Empty text, skipping speech")
            return
        }

        _currentText.value = text

        if (!isInitialized) {
            Log.d(TAG, "TTS not initialized, queuing text")
            pendingText = text
            initialize()
            return
        }

        val queueMode = if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        val utteranceId = UUID.randomUUID().toString()

        _isSpeaking.value = true
        _ttsState.value = TtsState.Speaking

        val speakResult = tts?.speak(text, queueMode, null, utteranceId)
        if (speakResult == TextToSpeech.ERROR) {
            Log.e(TAG, "Failed to speak text")
            _isSpeaking.value = false
            _ttsState.value = TtsState.Error("Failed to speak")
        } else {
            Log.d(TAG, "Speaking: $text")
        }
    }

    fun stop() {
        tts?.stop()
        _isSpeaking.value = false
        _ttsState.value = TtsState.Ready
        Log.d(TAG, "Stopped speaking")
    }

    fun pause() {
        tts?.stop()
        _isSpeaking.value = false
        _ttsState.value = TtsState.Ready
    }

    fun playFromUrl(url: String) {
        try {
            releaseMediaPlayer()
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(context, Uri.parse(url))
                prepareAsync()
                setOnPreparedListener {
                    start()
                    _isSpeaking.value = true
                    _ttsState.value = TtsState.Speaking
                }
                setOnCompletionListener {
                    _isSpeaking.value = false
                    _ttsState.value = TtsState.Ready
                }
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
                    _isSpeaking.value = false
                    _ttsState.value = TtsState.Error("Playback error")
                    true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play from URL", e)
            _ttsState.value = TtsState.Error("Failed to play audio")
        }
    }

    fun playFromFile(file: File) {
        try {
            releaseMediaPlayer()
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(file.absolutePath)
                prepare()
                start()
                _isSpeaking.value = true
                _ttsState.value = TtsState.Speaking
                setOnCompletionListener {
                    _isSpeaking.value = false
                    _ttsState.value = TtsState.Ready
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play file", e)
            _ttsState.value = TtsState.Error("Failed to play audio file")
        }
    }

    fun setSpeechRate(rate: Float) {
        tts?.setSpeechRate(rate.coerceIn(0.5f, 2.0f))
    }

    fun setPitch(pitch: Float) {
        tts?.setPitch(pitch.coerceIn(0.5f, 2.0f))
    }

    fun getAvailableLanguages(): List<Locale> {
        return tts?.availableLanguages?.toList() ?: emptyList()
    }

    fun setLanguage(locale: Locale): Boolean {
        val result = tts?.setLanguage(locale)
        return result != TextToSpeech.LANG_MISSING_DATA &&
                result != TextToSpeech.LANG_NOT_SUPPORTED
    }

    private fun createUtteranceListener(): UtteranceProgressListener {
        return object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                Log.d(TAG, "Started utterance: $utteranceId")
                _isSpeaking.value = true
                _ttsState.value = TtsState.Speaking
            }

            override fun onDone(utteranceId: String?) {
                Log.d(TAG, "Completed utterance: $utteranceId")
                _isSpeaking.value = false
                _ttsState.value = TtsState.Ready
            }

            override fun onError(utteranceId: String?) {
                Log.e(TAG, "Error in utterance: $utteranceId")
                _isSpeaking.value = false
                _ttsState.value = TtsState.Error("Speech synthesis error")
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?, errorCode: Int) {
                Log.e(TAG, "Error in utterance: $utteranceId, code: $errorCode")
                _isSpeaking.value = false
                _ttsState.value = TtsState.Error("Speech synthesis error: $errorCode")
            }
        }
    }

    private fun releaseMediaPlayer() {
        mediaPlayer?.release()
        mediaPlayer = null
    }

    fun shutdown() {
        stop()
        releaseMediaPlayer()
        tts?.shutdown()
        tts = null
        isInitialized = false
        _ttsState.value = TtsState.Idle
        Log.d(TAG, "TextToSpeech shutdown")
    }
}
