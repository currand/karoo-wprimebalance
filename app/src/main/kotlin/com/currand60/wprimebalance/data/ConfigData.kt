package com.currand60.wprimebalance.data

import kotlinx.serialization.Serializable

@Serializable
data class ConfigData(
    val wPrime: Int,          // User's W' in Joules
    val criticalPower: Int,    // User's Critical Power in Watts
    val calculateCp: Boolean = false, // Should we calculate CP mid ride?
    val useKarooFtp: Boolean = true // Use the Karoo FTP for calculations

) {
    companion object {
        /**
         * Provides default configuration values.
         * These are used when no settings are found or when resetting to defaults.
         */
        val DEFAULT = ConfigData(
            wPrime = 10000,          // Default W' of 10000 J
            criticalPower = 250,    // Default Critical Power of 250 W
            calculateCp = false, // Default to not calculating CP mid ride
            useKarooFtp = true
        )
    }
}