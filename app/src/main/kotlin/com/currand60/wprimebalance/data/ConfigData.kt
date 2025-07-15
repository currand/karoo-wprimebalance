package com.currand60.wprimebalance.data

import kotlinx.serialization.Serializable

@Serializable
data class ConfigData(
    val wPrime: Int,          // Example: User's W' in kilojoules
    val criticalPower: Int,      // Example: User's Critical Power in Watts
    val threshold: Int           // Example: User's FTP or another threshold in Watts
) {
    companion object {
        /**
         * Provides default configuration values.
         * These are used when no settings are found or when resetting to defaults.
         */
        val DEFAULT = ConfigData(
            wPrime = 10000,          // Default W' of 20.0 kJ
            criticalPower = 250,    // Default Critical Power of 250 W
            threshold = 240         // Default Threshold of 240 W
        )
    }
}