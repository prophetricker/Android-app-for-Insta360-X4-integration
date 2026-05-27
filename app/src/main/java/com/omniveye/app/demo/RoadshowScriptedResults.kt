package com.omniveye.app.demo

import com.omniveye.app.cloud.AnalyzeResponse
import com.omniveye.app.cloud.SemanticAnalyzeResponse

fun roadshowProductResult(): SemanticAnalyzeResponse {
    return SemanticAnalyzeResponse(
        mode = "product",
        summary = "前方是一个放有饮品的货架，你手上拿着的一盒蒙牛的纯牛奶，商品位于画面中央偏下位置，可以继续拿取或放入购物篮。",
        objects = listOf("饮品货架", "蒙牛纯牛奶", "盒装牛奶", "手部持物"),
        trafficLight = null,
        targetFound = true,
        productName = "蒙牛纯牛奶",
        confidence = 0.96,
        latencyMs = 420,
        fallbackReason = null
    )
}

fun roadshowTrafficLightResult(): SemanticAnalyzeResponse {
    return SemanticAnalyzeResponse(
        mode = "traffic_light",
        summary = "路口信号灯红灯已经转为绿灯，当前前方可以通行。请确认车辆已停稳，再沿人行横道直行通过。",
        objects = listOf("交通信号灯", "红绿灯", "路口", "人行横道"),
        trafficLight = "green",
        targetFound = true,
        productName = null,
        confidence = 0.94,
        latencyMs = 390,
        fallbackReason = null
    )
}

fun roadshowTreeObstacleResult(): AnalyzeResponse {
    return AnalyzeResponse(
        distanceM = 1.2,
        level = 2,
        confidence = 0.88,
        sceneText = "前方道路右侧有树木和枝叶占据通行空间，距离约一米二。请放慢速度，向左侧绕行，注意脚下路面。",
        latencyMs = 360
    )
}
