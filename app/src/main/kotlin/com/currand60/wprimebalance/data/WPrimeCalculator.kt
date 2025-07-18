package com.currand60.wprimebalance.data

import timber.log.Timber
import kotlin.math.exp
import kotlin.math.max

/**
 Reproduced from [RT-Critical-Power](https://github.com/Berg0162/RT-Critical-Power).
 Extremely heavy use of AI to convert to Kotlin from C++ and a bunch of vibe coding
 Your mileage may vary, not available in all 50 states, prices higher in HI and AK.
 */

class WPrimeCalculator(
    initialEstimatedCP: Int,
    initialEstimatedWPrimeJoules: Int,
    currentTimeMillis: Long,
    private var useEstimatedCp: Boolean = false
) {
    var CP60: Int = initialEstimatedCP // Your (estimate of) Critical Power, more or less the same as FTP
    var eCP: Int // Algorithmic estimate of Critical Power during intense workout
    var wPrimeUsr: Int = initialEstimatedWPrimeJoules // Your (estimate of) W-prime or a base value
    var ewPrimeMod: Int // First order estimate of W-prime modified during intense workout
    var ewPrimeTest: Int // 20-min-test algorithmic estimate (20 minute @ 5% above eCP) of W-prime for a given eCP!
    var wPrimeBalance: Long = 0L // Can be negative !!! (initialized to 0 as in C++ global scope)

    // --- Static variables from C++ functions, translated as private class properties to preserve state ---
    // From `CalculateAveragePowerBelowCP` function
    private var CountPowerBelowCP: Long = 0L
    private var SumPowerBelowCP: Long = 0L

    // From `CalculateAveragePowerAboveCP` function (and implicitly used in `w_prime_balance_waterworth`)
    private var SumPowerAboveCP: Long = 0L // Sum for calculating average power above CP
    private var CountPowerAboveCP: Long = 0L // Counter for calculating average power above CP (corresponds to `iCpACp` reference)
    private var avPower: Int = 0 // Average power above CP (corresponds to `iavPwr` reference)

    // From `w_prime_balance_waterworth` function
    private var iTLim: Double = 0.0 // Time (duration) while Power is above CP
    private var timeSpent: Long = 0L // Total Time spent in the workout
    private var runningSum: Double = 0.0
    private val nextLevelStep: Long = 1000L // Stepsize of the next level of w-prime modification --> 1000 Joules step
    private var nextUpdateLevel: Long = 0L // The next level at which to update eCP, e_w_prime_mod and ew_prime_test
    private var prevReadingTime: Long = currentTimeMillis // Previous reading timestamp in milliseconds

    // Constructor `init` block to perform initial setup equivalent to C++ global initialization sequence.
    init {
          constrainWPrimeValue() // Ensure we have a realistic value for `w_prime_usr`.

        // After `ConstrainW_PrimeValue` might have modified `CP60` or `w_prime_usr`,
        // re-initialize other dependent class properties to reflect any changes.
        eCP = CP60
        ewPrimeMod = wPrimeUsr
        ewPrimeTest = wPrimeUsr
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

        Timber.d("Time:${timeSpent.toDouble()} ST: ${sampleTime.toDouble()} tau: $tau")

        // w_prime is energy and measured in Joules = Watt*second.
        // Determine the expended energy above CP since the previous measurement (i.e., during SampleTime).
        val wPrimeExpended = max(0, powerAboveCp).toDouble() * sampleTime // Calculates (Watts_above_CP) * (its duration in seconds)

        // Calculate the exponential terms used in the W' balance equation.
        val expTerm1 = exp(timeSpent / tau) // Exponential term 1
        val expTerm2 = exp(-timeSpent / tau) // Exponential term 2

        Timber.d("W prime expended: ${wPrimeExpended.toInt()} exp-term1: $expTerm1 exp-term2: $expTerm2 ")

        runningSum = runningSum + (wPrimeExpended * expTerm1) // Determine the running sum

        Timber.d("Running Sum: $runningSum")

        wPrimeBalance = (iwPrime.toDouble() - (runningSum * expTerm2)).toLong()

        //--------------- extra --------------------------------------------------------------------------------------
        // This section implements logic to dynamically update estimated CP and W' based on depletion levels.
        if (powerAboveCp > 0) {
            calculateAveragePowerAboveCP(iPower)
            iTLim += sampleTime // Time to exhaustion: accurate sum of every second spent above CP
        }

        Timber.d(" [$CountPowerAboveCP]")

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

    fun getCurrentEstimatedCP(): Int {
        return eCP
    }
}
