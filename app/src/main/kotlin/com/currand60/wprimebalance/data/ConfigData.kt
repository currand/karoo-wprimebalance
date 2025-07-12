package com.currand60.wprimebalance.data

import kotlinx.serialization.Serializable

@Serializable
data class ConfigData(
    val criticalPower: Int = 0,
    val threshold: Int = 0,
    val wPrime: Double = 0.0
)