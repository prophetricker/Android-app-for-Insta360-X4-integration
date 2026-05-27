package com.omniveye.app.demo

import com.omniveye.app.cloud.AnalyzeResponse

data class RoadshowDemoScene(
    val name: String,
    val response: AnalyzeResponse
)

object RoadshowDemo {
    val scenes: List<RoadshowDemoScene> = listOf(
        RoadshowDemoScene(
            name = "通畅",
            response = AnalyzeResponse(
                distanceM = 3.6,
                level = 0,
                confidence = 0.72,
                sceneText = "前方通畅，可以继续前进。",
                latencyMs = 180
            )
        ),
        RoadshowDemoScene(
            name = "注意",
            response = AnalyzeResponse(
                distanceM = 1.45,
                level = 2,
                confidence = 0.86,
                sceneText = "前方约一米半有障碍物，请减速。",
                latencyMs = 210
            )
        ),
        RoadshowDemoScene(
            name = "危险",
            response = AnalyzeResponse(
                distanceM = 0.72,
                level = 3,
                confidence = 0.91,
                sceneText = "前方障碍物较近，请立即避让。",
                latencyMs = 195
            )
        ),
        RoadshowDemoScene(
            name = "紧急",
            response = AnalyzeResponse(
                distanceM = 0.34,
                level = 4,
                confidence = 0.94,
                sceneText = "前方三十厘米有障碍物，请停止前进。",
                latencyMs = 205
            )
        )
    )

    fun sceneAt(index: Int): RoadshowDemoScene {
        val safeIndex = index.floorMod(scenes.size)
        return scenes[safeIndex]
    }
}

private fun Int.floorMod(divisor: Int): Int {
    val remainder = this % divisor
    return if (remainder >= 0) remainder else remainder + divisor
}
