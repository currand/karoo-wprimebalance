package com.currand60.wprimebalance.data

import kotlinx.serialization.Serializable

@Serializable
data class ConfigData(
    val wPrime: Int,          // User's W' in Joules
    val criticalPower: Int,    // User's Critical Power in Watts
    val calculateCp: Boolean = false, // Should we calculate CP mid ride?
    val useKarooFtp: Boolean = true, // Use the Karoo FTP for calculations
    val matchJoulePercent: Long = 10, // Default to 10% of W' to count as an effort
    val minMatchDuration: Long = 30, // Default to 30 seconds
) {
    companion object {
        /**
         * Provides default configuration values.
         * These are used when no settings are found or when resetting to defaults.
         */
        val DEFAULT = ConfigData(
            wPrime = 10000,
            criticalPower = 250,
            calculateCp = false,
            useKarooFtp = true,
            matchJoulePercent = 10,
            minMatchDuration = 30
        )
    }
}