package com.currand60.wprimebalance.data

data class ConfigData(
    val wPrime: Int,          // User's W' in Joules
    val criticalPower: Int,    // User's Critical Power in Watts
    val calculateCp: Boolean = false, // Should we calculate CP mid ride?
    val useKarooFtp: Boolean = true, // Use the Karoo FTP for calculations
    val matchJoulePercent: Int = 10, // Default to 10% of W' to count as an effort
    val minMatchDuration: Int = 30, // Default to 30 seconds
    val matchPowerPercent: Int = 105, // Default to 105% of CP to count as an effort:
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
            minMatchDuration = 30,
            matchPowerPercent = 105
        )
    }
}