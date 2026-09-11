package com.vrarengine.core.gesture

import android.graphics.PointF

data class PinchState(
    val isPinching: Boolean,
    val pinchScreenPos: PointF,
    val confidence: Float
)
