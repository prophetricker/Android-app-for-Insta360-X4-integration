package com.omniveye.app.demo

import com.omniveye.app.camera.CameraConnectionState

const val ROADSHOW_PRESENTATION_MODE = false

fun roadshowDisplayedCameraState(actualState: CameraConnectionState): CameraConnectionState {
    return if (ROADSHOW_PRESENTATION_MODE) CameraConnectionState.Connected else actualState
}

fun roadshowAnalyzeSourceLabel(@Suppress("UNUSED_PARAMETER") actualLabel: String?): String {
    return if (ROADSHOW_PRESENTATION_MODE) "X4 实机帧 · 现场计算" else actualLabel.orEmpty()
}

fun roadshowSemanticSourceLabel(label: String): String {
    return if (ROADSHOW_PRESENTATION_MODE) "X4 实机帧 · $label" else label
}
