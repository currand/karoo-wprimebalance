package com.currand60.wprimebalance.data

import com.currand60.wprimebalance.managers.ConfigurationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.math.exp
import kotlin.math.max

/**
 Reproduced from [RT-Critical-Power](https://github.com/Berg0162/RT-Critical-Power).
 Extremely heavy use of AI to convert to Kotlin from C++ and a bunch of vibe coding
 Your mileage may vary, not available in all 50 states, prices higher in HI and AK.
 */


class WPrimeCalculator(
    private val configurationManager: ConfigurationManager,
) {
    private val calculatorScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    var cP60: Int = 0 // Your (estimate of) Critical Power, more or less the same as FTP
        private set
    var wPrimeUsr: Int = 0 // Your (estimate of) W-prime or a base value
        private set
    // These represent the 'algorithmic' or 'modified' values that change mid-ride
    var eCP: Int = 0 // Algorithmic estimate of Critical Power during intense workout
        private set
    var ewPrimeMod: Int = 0 // First order estimate of W-prime modified during intense workout
        private set
    var ewPrimeTest: Int = 0 // 20-min-test algorithmic estimate (20 minute @ 5% above eCP) of W-prime for a given eCP!
        private set
    var wPrimeBalance: Long = 0L // Can be negative !!! (initialized to 0 as in C++ global scope)
        private set

    // This property controls if the algorithm can update CP and W' mid-ride
    private var useEstimatedCp: Boolean = false

    // --- Static variables from C++ functions, translated as private class properties to preserve state ---
    // From `CalculateAveragePowerBelowCP` function
    private var countPowerBelowCP: Long = 0L
    private var sumPowerBelowCP: Long = 0L

    // From `CalculateAveragePowerAboveCP` function (and implicitly used in `w_prime_balance_waterworth`)
    private var sumPowerAboveCP: Long = 0L
    private var countPowerAboveCP: Long = 0L
    private var avPower: Int = 0

    // From `w_prime_balance_waterworth` function
    private var iTLim: Double = 0.0
    private var timeSpent: Double = 0.0 // Changed to Long for consistency with SampleTime, as was done in C++ with unsigned long
    private var runningSum: Double = 0.0
    private val nextLevelStep: Long = 1000L
    private var nextUpdateLevel: Long = 0L
    private var prevReadingTime: Long = 0L // Initialized to 0L, will be set on first calculate call

    init {
        Timber.d("WPrimeCalculator created. Starting config observer.")
        calculatorScope.launch {
            configurationManager.getConfigFlow()
                .distinctUntilChanged() // Only react when the config truly changes
                .onEach { config ->
                    Timber.d("Configuration change detected: $config. Applying to WPrimeCalculator.")
                    // When config changes, apply the new settings.
                    // This does NOT reset the ride-specific state (e.g., wPrimeBalance).
                    applyConfig(config)
                }
                .collect {  }
        }
    }

    private fun applyConfig(config: ConfigData) {
        cP60 = config.criticalPower
        wPrimeUsr = config.wPrime
        useEstimatedCp = config.calculateCp

        // Ensure values are rational if we are calculating them
        if (useEstimatedCp) {
            constrainWPrimeValue()
        }
        // Initialize/re-initialize algorithmic estimates based on (potentially constrained) user values
        eCP = cP60
        ewPrimeMod = wPrimeUsr
        ewPrimeTest = wPrimeUsr

        Timber.d("WPrimeCalculator _applyConfig completed: CP60=$cP60, wPrimeUsr=$wPrimeUsr, useEstimatedCp=$useEstimatedCp")
    }

    suspend fun resetRideState(initialTimestampMillis: Long) {
        Timber.d("Resetting WPrimeCalculator ride state.")

        val latestConfig = configurationManager.getConfigFlow().first() // Get current value
        applyConfig(latestConfig) // Apply it to update CP60, wPrimeUsr etc.

        wPrimeBalance = wPrimeUsr.toLong() // W' balance starts at full capacity with the current W'
        countPowerBelowCP = 0L
        sumPowerBelowCP = 0L
        sumPowerAboveCP = 0L
        countPowerAboveCP = 0L
        avPower = 0
        iTLim = 0.0
        timeSpent = 0.0
        // nextUpdateLevel is already set by _applyConfig
        prevReadingTime = initialTimestampMillis // Set initial timestamp for ride calculations
        nextUpdateLevel = 0L


        Timber.d("WPrimeCalculator ride state reset. W' Balance set to $wPrimeBalance J, initial timestamp: $initialTimestampMillis")
    }


    // ------------------------ W'Balance Functions -----------------------------------

    private fun calculateAveragePowerBelowCP(iPower: Int, iCP: Int): Int {

        if (iPower < iCP) {
            sumPowerBelowCP += iPower.toLong()
            countPowerBelowCP++
        }

        return if (countPowerBelowCP > 0) {
            (sumPowerBelowCP / countPowerBelowCP).toInt() // Calculate and return average power below CP
        } else {
            0 // Return 0 if no power readings below CP have been recorded yet
        }
    }


    private fun calculateAveragePowerAboveCP(iPower: Int) {
        sumPowerAboveCP += iPower.toLong()
        countPowerAboveCP++
        // Handle division by zero for the average calculation.
        avPower = if (countPowerAboveCP > 0) {
            (sumPowerAboveCP / countPowerAboveCP).toInt() // Calculate average power above CP
        } else {
            0 // Return 0 if no power readings above CP have been recorded yet
        }
    }

    private fun tauWPrimeBalance(iPower: Int, iCP: Int): Double {
        val avgPowerBelowCp = calculateAveragePowerBelowCP(iPower, iCP)
        val deltaCp = (iCP - avgPowerBelowCp).toDouble()
        return (546.00 * exp(-0.01 * deltaCp) + 316.00)
    }

    // The Waterworth method of calculating W' Balance.
    private fun wPrimeBalanceWaterworth(iPower: Int, iCP: Int, iwPrime: Int, currentTimestampMillis: Long) {
        // Determine the individual sample time in seconds, it may/will vary during the workout.
        // Using the provided `currentTimestampMillis` for calculation.
        val sampleTime: Double = (currentTimestampMillis - prevReadingTime) / 1000.0
        prevReadingTime = currentTimestampMillis

        val tau = tauWPrimeBalance(iPower, iCP)
        timeSpent += sampleTime // The summed value of all sample time values during the workout

        val powerAboveCp = (iPower - iCP)

//        Timber.d("Time:${timeSpent.toDouble()} ST: ${sampleTime.toDouble()} tau: $tau")

        // w_prime is energy and measured in Joules = Watt*second.
        // Determine the expended energy above CP since the previous measurement (i.e., during SampleTime).
        val wPrimeExpended = max(0, powerAboveCp).toDouble() * sampleTime // Calculates (Watts_above_CP) * (its duration in seconds)

        // Calculate the exponential terms used in the W' balance equation.
        val expTerm1 = exp(timeSpent / tau) // Exponential term 1
        val expTerm2 = exp(-timeSpent / tau) // Exponential term 2

        runningSum = runningSum + (wPrimeExpended * expTerm1) // Determine the running sum

//        Timber.d("Running Sum: $runningSum")

        wPrimeBalance = (iwPrime.toDouble() - (runningSum * expTerm2)).toLong()

        Timber.d("W\' Balance: $wPrimeBalance J W\' expended: ${wPrimeExpended.toInt()} CP: $iCP W': $iwPrime")


        //--------------- extra --------------------------------------------------------------------------------------
        // This section implements logic to dynamically update estimated CP and W' based on depletion levels.
        if (powerAboveCp > 0) {
            calculateAveragePowerAboveCP(iPower)
            iTLim += sampleTime // Time to exhaustion: accurate sum of every second spent above CP
        }

//        Timber.d(" [$CountPowerAboveCP]")

        // Check if W' balance is further depleted to a new "level" to trigger an update of eCP and ew_prime.
        if ((wPrimeBalance < nextUpdateLevel) && (wPrimeExpended > 0)) {
            nextUpdateLevel -= nextLevelStep // Move to the next lower depletion level
            eCP = getCpFromTwoParameterAlgorithm(
                avPower,
                iTLim,
                iwPrime
            ) // Estimate a new `eCP` value
            ewPrimeMod =
                wPrimeUsr - nextUpdateLevel.toInt() // Adjust `ew_prime_modified` to the new depletion level
            ewPrimeTest = getWPrimeFromTwoParameterAlgorithm(
                (eCP * 1.045).toInt(),
                1200.0,
                eCP
            ) // 20-Min-test estimate for W-Prime
        }
            Timber.d("Update of eCP - ew_prime $ewPrimeMod - avPower: $avPower - T-lim:$iTLim --> eCP: $eCP --> Test estimate of W-Prime: $ewPrimeTest")
    }

    private fun constrainWPrimeValue() {
        if (cP60 < 100) {
            cP60 = 100 // Update `CP60` to the lowest allowed level
        }
        // First, determine the "minimal" value for W_Prime according to a 20-min-test estimate, given the `CP60` value.
        val wPrimeEstimate = getWPrimeFromTwoParameterAlgorithm((cP60 * 1.045).toInt(), 1200.0, cP60)

        if (wPrimeUsr < wPrimeEstimate) {
            wPrimeUsr = wPrimeEstimate // Update `w_prime_usr` to a realistic level if it's too low
        }
    }

    private fun getCpFromTwoParameterAlgorithm(iavPower: Int, iTLim: Double, iwPrime: Int): Int {
        val wPrimeDivTLim = (iwPrime.toDouble() / iTLim).toInt() // Type cast for correct calculations

        return if (iavPower > wPrimeDivTLim) { // Check for valid scope
            (iavPower - wPrimeDivTLim) // Solve 2-parameter algorithm to estimate CP
        } else {
            eCP // Return the class's current `eCP` property.
        }
    }

    @Suppress("SameParameterValue")
    private fun getWPrimeFromTwoParameterAlgorithm(iAvPower: Int, iTLim: Double, iCP: Int): Int {
        return if (iAvPower > iCP) { // Check for valid scope
            (iAvPower - iCP) * iTLim.toInt() // Solve 2-parameter algorithm to estimate new W-Prime
        } else {
            wPrimeUsr // Return the class's current `w_prime_usr` property.
        }
    }

    /**
     * Calculates and updates the W' Prime Balance based on the instantaneous power and provided timestamp.
     * This is the primary external method to interact with the calculator.
     * It orchestrates the internal calculations by calling the faithfully reproduced
     * `w_prime_balance_waterworth` function.
     *
     * @param instantaneousPower The current instantaneous power reading in Watts.
     * @param currentTimeMillis The current timestamp of this power reading in milliseconds.
     * @return The updated W' Prime Balance in Joules. Note: as per the original C++ code, this value can be negative.
     */
    fun calculateWPrimeBalance(instantaneousPower: Int, currentTimeMillis: Long): Long {
        // Call the core C++ logic faithfully, passing along the necessary class properties
        // and the current timestamp for time delta calculation.
        if (useEstimatedCp) {
            // Allow the algorithm to update CP and W' values mid-ride
            cP60 = eCP
            wPrimeUsr = ewPrimeMod
        }

        wPrimeBalanceWaterworth(instantaneousPower, cP60, wPrimeUsr, currentTimeMillis)

        // Return the updated W' Prime Balance, which is a class property modified by the above function.
        return wPrimeBalance
    }

    fun getPreviousReadingTime(): Long {
        return prevReadingTime
    }

    fun getCurrentCP(): Int {
        return cP60
    }

    fun getCurrentWPrimeJoules(): Int { // Added to expose the 'initial' W' for percentage calculation
        return wPrimeUsr
    }

    fun calculateTimeToExhaust(avgPower30sec: Int): Int {
        // Return the time to exhaust W' in seconds based on current 30s
        // average power. This is a LINEAR rate and does not use exponential
        // decay as above.

        val powerAboveCp = avgPower30sec - cP60

        if (wPrimeBalance < 1000 || powerAboveCp <= 0) {
            return 0
        }


        return (wPrimeBalance / powerAboveCp).toInt()

    }
}
