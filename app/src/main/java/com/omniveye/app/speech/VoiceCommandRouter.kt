package com.omniveye.app.speech

enum class VoiceCommandAction {
    ProductRecognition,
    TrafficLightRecognition,
    ObstacleAvoidance,
    Surroundings,
    Unknown
}

fun routeVoiceCommand(text: String): VoiceCommandAction {
    val normalized = text.trim().lowercase()
    if (normalized.isBlank()) return VoiceCommandAction.Unknown

    if (normalized.containsAny("商品", "牛奶", "货架", "找东西", "识别商品", "product", "milk")) {
        return VoiceCommandAction.ProductRecognition
    }
    if (normalized.containsAny("红绿灯", "红灯", "绿灯", "过马路", "路口", "traffic", "light")) {
        return VoiceCommandAction.TrafficLightRecognition
    }
    if (normalized.containsAny("周围", "环境", "全景", "观察", "看看四周", "surrounding", "surroundings")) {
        return VoiceCommandAction.Surroundings
    }
    if (normalized.containsAny("危险", "避障", "前方", "障碍", "看路", "路况", "安全", "danger", "obstacle")) {
        return VoiceCommandAction.ObstacleAvoidance
    }
    return VoiceCommandAction.Unknown
}

private fun String.containsAny(vararg keywords: String): Boolean {
    return keywords.any { contains(it) }
}
