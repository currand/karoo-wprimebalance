package com.currand60.wprimebalance.data

import timber.log.Timber
import kotlin.math.exp
import kotlin.math.max

/**
 Reproduced from [RT-Critical-Power](https://github.com/Berg0162/RT-Critical-Power).
 Extremely heavy use of AI to convert to Kotlin from C++ and a bunch of vibe coding
 Your mileage may vary, not available in all 50 states, prices higher in HI and AK.
 */

class WPrimeCalculator() {
    // Note: These class properties are no longer `val` as they need to be re-initialized during configuration.
    // They represent the 'user' or 'initial' values for the session.
    var CP60: Int = 0 // Your (estimate of) Critical Power, more or less the same as FTP
    var wPrimeUsr: Int = 0 // Your (estimate of) W-prime or a base value

    // These represent the 'algorithmic' or 'modified' values that change mid-ride
    var eCP: Int = 0 // Algorithmic estimate of Critical Power during intense workout
    var ewPrimeMod: Int = 0 // First order estimate of W-prime modified during intense workout
    var ewPrimeTest: Int = 0 // 20-min-test algorithmic estimate (20 minute @ 5% above eCP) of W-prime for a given eCP!
    var wPrimeBalance: Long = 0L // Can be negative !!! (initialized to 0 as in C++ global scope)

    // This property controls if the algorithm can update CP and W' mid-ride
    private var useEstimatedCp: Boolean = false

    // --- Static variables from C++ functions, translated as private class properties to preserve state ---
    // From `CalculateAveragePowerBelowCP` function
    private var CountPowerBelowCP: Long = 0L
    private var SumPowerBelowCP: Long = 0L

    // From `CalculateAveragePowerAboveCP` function (and implicitly used in `w_prime_balance_waterworth`)
    private var SumPowerAboveCP: Long = 0L
    private var CountPowerAboveCP: Long = 0L
    private var avPower: Int = 0

    // From `w_prime_balance_waterworth` function
    private var iTLim: Double = 0.0
    private var timeSpent: Long = 0L // Changed to Long for consistency with SampleTime, as was done in C++ with unsigned long
    private var runningSum: Double = 0.0
    private val nextLevelStep: Long = 1000L
    private var nextUpdateLevel: Long = 0L
    private var prevReadingTime: Long = 0L // Initialized to 0L, will be set on first calculate call

    fun configure(config: ConfigData, initialTimestampMillis: Long) {
        // Apply initial configuration
        CP60 = config.criticalPower
        wPrimeUsr = config.wPrime

        // Reset internal state variables
        wPrimeBalance = wPrimeUsr.toLong() // W' balance starts at full capacity
        useEstimatedCp = config.calculateCp
        CountPowerBelowCP = 0L
        SumPowerBelowCP = 0L
        SumPowerAboveCP = 0L
        CountPowerAboveCP = 0L
        avPower = 0
        iTLim = 0.0
        timeSpent = 0L
        runningSum = 0.0
        nextUpdateLevel = 0L // Reset next update level
        prevReadingTime = initialTimestampMillis // Set initial timestamp

        // Apply constraints and initialize dependent algorithmic estimates
        constrainWPrimeValue() // Ensure we have a realistic value for `wPrimeUsr` and `CP60`

        // After `constrainWPrimeValue` might have modified `CP60` or `wPrimeUsr`,
        // re-initialize other dependent class properties to reflect any changes.
        eCP = CP60
        ewPrimeMod = wPrimeUsr
        ewPrimeTest = wPrimeUsr

        Timber.d("WPrimeCalculator configured: CP60=$CP60, wPrimeUsr=$wPrimeUsr, useEstimatedCp=$useEstimatedCp, initialTimestamp=$initialTimestampMillis")
    }

    // ------------------------ W'Balance Functions -----------------------------------

    private fun CalculateAveragePowerBelowCP(iPower: Int, iCP: Int): Int {

        if (iPower < iCP) {
            SumPowerBelowCP += iPower.toLong()
            CountPowerBelowCP++
        }

        return if (CountPowerBelowCP > 0) {
            (SumPowerBelowCP / CountPowerBelowCP).toInt() // Calculate and return average power below CP
        } else {
            0 // Return 0 if no power readings below CP have been recorded yet
        }
    }


    private fun calculateAveragePowerAboveCP(iPower: Int) {
        SumPowerAboveCP += iPower.toLong()
        CountPowerAboveCP++
        // Handle division by zero for the average calculation.
        avPower = if (CountPowerAboveCP > 0) {
            (SumPowerAboveCP / CountPowerAboveCP).toInt() // Calculate average power above CP
        } else {
            0 // Return 0 if no power readings above CP have been recorded yet
        }
    }

    private fun tauWPrimeBalance(iPower: Int, iCP: Int): Double {
        val avgPowerBelowCp = CalculateAveragePowerBelowCP(iPower, iCP)
        val deltaCp = (iCP - avgPowerBelowCp).toDouble()

        return (546.00 * exp(-0.01 * deltaCp) + 316.00)
    }

    // The Waterworth method of calculating W' Balance.
    private fun wPrimeBalanceWaterworth(iPower: Int, iCP: Int, iwPrime: Int, currentTimestampMillis: Long) {
        // Determine the individual sample time in seconds, it may/will vary during the workout.
        // Using the provided `currentTimestampMillis` for calculation.
        val sampleTime: Long = (currentTimestampMillis - prevReadingTime) / 1000
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
            eCP = getCpFromTwoParameterAlgorithm(avPower, iTLim.toLong(), iwPrime) // Estimate a new `eCP` value
            ewPrimeMod = wPrimeUsr - nextUpdateLevel.toInt() // Adjust `ew_prime_modified` to the new depletion level
            ewPrimeTest = getWPrimeFromTwoParameterAlgorithm((eCP * 1.045).toInt(), 1200.0, eCP) // 20-Min-test estimate for W-Prime

            Timber.d("Update of eCP - ew_prime $ewPrimeMod - avPower: $avPower - T-lim:$iTLim --> eCP: $eCP --> Test estimate of W-Prime: $ewPrimeTest")
        }
    }

    private fun constrainWPrimeValue() {
        if (CP60 < 100) {
            CP60 = 100 // Update `CP60` to the lowest allowed level
        }
        // First, determine the "minimal" value for W_Prime according to a 20-min-test estimate, given the `CP60` value.
        val w_prime_estimate = getWPrimeFromTwoParameterAlgorithm((CP60 * 1.045).toInt(), 1200.0, CP60)

        if (wPrimeUsr < w_prime_estimate) {
            wPrimeUsr = w_prime_estimate // Update `w_prime_usr` to a realistic level if it's too low
        }
    }

    private fun getCpFromTwoParameterAlgorithm(iavPower: Int, iTLim: Long, iwPrime: Int): Int {
        val wPrimeDivTLim = (iwPrime.toDouble() / iTLim.toDouble()).toInt() // Type cast for correct calculations

        if (iavPower > wPrimeDivTLim) { // Check for valid scope
            return (iavPower - wPrimeDivTLim) // Solve 2-parameter algorithm to estimate CP
        } else {
            return eCP // Return the class's current `eCP` property.
        }
    }

    @Suppress("SameParameterValue")
    private fun getWPrimeFromTwoParameterAlgorithm(iav_Power: Int, iT_lim: Double, iCP: Int): Int {
        if (iav_Power > iCP) { // Check for valid scope
            return (iav_Power - iCP) * iT_lim.toInt() // Solve 2-parameter algorithm to estimate new W-Prime
        } else {
            return wPrimeUsr // Return the class's current `w_prime_usr` property.
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
            CP60 = eCP
            wPrimeUsr = ewPrimeMod
        }

        wPrimeBalanceWaterworth(instantaneousPower, CP60, wPrimeUsr, currentTimeMillis)

        // Return the updated W' Prime Balance, which is a class property modified by the above function.
        return wPrimeBalance
    }

    fun getPreviousReadingTime(): Long {
        return prevReadingTime
    }

    fun getCurrentCP(): Int {
        return if (useEstimatedCp) {
            eCP
        } else {
            CP60
        }
    }

    fun getCurrentWPrimeJoules(): Int { // Added to expose the 'initial' W' for percentage calculation
        return if (useEstimatedCp) {
            wPrimeUsr
        } else {
            ewPrimeMod
        }
    }
}
