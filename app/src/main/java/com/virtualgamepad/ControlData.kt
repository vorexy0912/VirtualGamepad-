package com.virtualgamepad

data class ControlData(
    var leftX: Float = 0f,
    var leftY: Float = 0f,
    var rightX: Float = 0f,
    var rightY: Float = 0f,
    var buttons: Int = 0,
    var azimuth: Float = 0f,
    var timestamp: Long = System.currentTimeMillis()
)

