package com.omniveye.app.speech

import android.speech.SpeechRecognizer

fun formatSpeechError(error: Int): String {
    val diagnostic = when (error) {
        SpeechRecognizer.ERROR_AUDIO ->
            "录音设备错误。请检查麦克风权限、是否有其它 App 正在录音，以及系统是否允许本应用使用麦克风。"
        SpeechRecognizer.ERROR_CLIENT ->
            "客户端错误。当前系统后台语音识别服务可能不可用，请尝试系统语音识别入口。"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
            "麦克风权限不足。请在系统设置中允许 OmniEye 使用麦克风。"
        SpeechRecognizer.ERROR_NETWORK ->
            "语音识别网络错误。请确认手机当前网络可访问语音识别服务。"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
            "语音识别网络超时。请确认手机当前网络稳定。"
        SpeechRecognizer.ERROR_NO_MATCH ->
            "没有识别到有效语音。请靠近手机麦克风重新说一遍。"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
            "识别服务正忙。请等一秒后重试。"
        SpeechRecognizer.ERROR_SERVER ->
            "语音识别服务端错误。请尝试系统语音识别入口。"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
            "没有检测到语音输入。请开始录音后马上说话。"
        else ->
            "未知语音识别错误。请记录错误码。"
    }
    return "语音识别失败：错误码 $error，$diagnostic"
}
