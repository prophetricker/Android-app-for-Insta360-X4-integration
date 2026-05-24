package com.omniveye.app.feedback

fun obstacleLevel(level: Int): Int = level.coerceIn(0, 4)

fun shouldVibrateForLevel(level: Int): Boolean = obstacleLevel(level) >= 2

fun vibrationDurationMs(level: Int): Long = when (obstacleLevel(level)) {
    0, 1 -> 0L
    2 -> 120L
    3 -> 240L
    else -> 420L
}

fun roadshowVibrationDurationMs(level: Int, confidence: Double): Long {
    val riskDuration = vibrationDurationMs(level)
    if (riskDuration > 0L) return riskDuration
    return if (level == 1 && confidence > 0.0) 70L else 0L
}
