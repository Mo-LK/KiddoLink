package com.educatorapp.utils

import kotlin.math.pow

object DistanceEstimator {

    fun estimateDistance(rssi: Int, txPower: Int = -59): Double {
        return 10.0.pow((txPower - rssi) / 20.0)
    }
}
