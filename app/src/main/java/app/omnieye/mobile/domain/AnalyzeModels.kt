package app.omnieye.mobile.domain

data class AnalyzeResponse(
    val distanceM: Double,
    val level: Int,
    val confidence: Double,
    val sceneText: String,
    val latencyMs: Int,
)

fun clampHapticLevel(level: Int): Int = level.coerceIn(0, 4)

fun shouldVibrate(level: Int): Boolean = clampHapticLevel(level) >= 2

fun vibrationDurationMs(level: Int): Long = when (clampHapticLevel(level)) {
    0, 1 -> 0L
    2 -> 120L
    3 -> 240L
    else -> 420L
}
