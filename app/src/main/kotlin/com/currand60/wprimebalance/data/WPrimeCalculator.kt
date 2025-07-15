package com.currand60.wprimebalance.data
import kotlin.math.exp
import kotlin.math.max

class WPrimeCalculator(initialCp60: Int, initialWPrimeUser: Int) {

    // Global variables from C++ (now class properties)
    private var tawcMode: Int = 1
    private var cp60: Int = initialCp60 // Initial CP, can be updated by algorithm
    private var eCP: Int = initialCp60 // Algorithmic estimate of Critical Power during intense workout
    private var wPrimeUser: Int = initialWPrimeUser // Your (estimate of) W-prime or a base value
    private var ewPrimeMod: Int = initialWPrimeUser // First order estimate of W-prime modified during intense workout
    private var ewPrimeTest: Int = initialWPrimeUser // 20-min-test algorithmic estimate
    private var wPrimeBalance: Long = 0 // Can be negative !!!

    // Static variables from C++ (now class properties to maintain state)
    private var countPowerBelowCP: Long = 0
    private var sumPowerBelowCP: Long = 0
    private var sumPowerAboveCP: Long = 0
    private var countPowerAboveCP: Long = 0
    private var tLim: Double = 0.0 // Time (duration) while Power is above CP
    private var timeSpent: Double = 0.0 // Total Time spent in the workout
    private var runningSum: Double = 0.0
    private var prevReadingTime: Long = 0 // Timestamp of the previous power reading
    private var avPowerAboveCP: Int = 0 // Average power above CP
    private var nextUpdateLevel: Long = 0 // The next level at which to update eCP, e_w_prime_mod and ew_prime_test

    // Constants
    private val nextLevelStep: Long = 1000 // Stepsize of the next level of w-prime modification

    init {
        // Apply ConstrainW_PrimeValue logic during initialization
        constrainWPrimeValue()
    }

    fun getPreviousReadingTime(): Long {
        return prevReadingTime
    }
    /**
     * Calculates the W' Balance based on the current power reading and timestamp.
     *
     * @param iPower The instantaneous power reading in watts.
     * @param currentTimestampMillis The current timestamp in milliseconds.
     * @return The calculated W' Balance as a float.
     */
    fun updateAndGetWPrimeBalance(iPower: Int, currentTimestampMillis: Long): Long {
        // Initialize prevReadingTime on the first call
        if (prevReadingTime == 0L) {
            prevReadingTime = currentTimestampMillis
        }

        // Determine the individual sample time in seconds
        val sampleTime = (currentTimestampMillis - prevReadingTime) / 1000.0
        prevReadingTime = currentTimestampMillis // Update for the next sample

        // Determine the value for tau
        val tau = tauWPrimeBalance(iPower, eCP)

        timeSpent += sampleTime // The summed value of all sample time values during the workout

        // Power > CP
        val powerAboveCP = max(0, iPower - eCP)

        // Determine the expended energy above CP since the previous measurement
        // (Watts_above_CP) * (its duration in seconds) = expended energy in Joules!
        val wPrimeExpended = powerAboveCP * sampleTime

        // Calculate some terms of the equation
        val expTerm1 = exp(timeSpent / tau)
        val expTerm2 = exp(-timeSpent / tau)

        runningSum = runningSum + (wPrimeExpended * expTerm1)

        // Determine w prime balance
        wPrimeBalance = (wPrimeUser - (runningSum * expTerm2)).toLong()

        // --------------- extra --------------------------------------------------------------------------------------
        // Workout starts at a certain W'= ##,### Joules and CP = ### watts, set by the user; the algorithm increases CP and W' stepwise
        // to more realistic values every time when W'balance is depleted to a certain level; -> 2-Parameter Algorithm updates CP and W'
        if (powerAboveCP > 0) {
            // Average power above CP is to be calculated for future use
            calculateAveragePowerAboveCP(iPower)
            // Time to exhaustion: the accurate sum of every second spent above CP, calculated for future use
            tLim += sampleTime
        }

        // When working above CP, the moment comes that we need to update eCP and ew_prime !!
        // W' balance is further depleted --> test for an update moment
        if ((wPrimeBalance < nextUpdateLevel) && (wPrimeExpended > 0)) {
            nextUpdateLevel -= nextLevelStep // Move down another level of depletion, update eCP, ew_prime_mod and ew_prime_test
            eCP = getCPFromTwoParameterAlgorithm(avPowerAboveCP, tLim, wPrimeUser) // Estimate a new eCP value
            ewPrimeMod = wPrimeUser - nextUpdateLevel.toInt() // Adjust ew_prime_modified
            // 20-Min-test estimate for W-Prime
            ewPrimeTest = getWPrimeFromTwoParameterAlgorithm((eCP * 1.045).toInt(), 1200.0, eCP)
        }
        // -----------------extra -------------------------------------------------------------------------------

        return wPrimeBalance
    }

    /**
     * Helper function to calculate average power below CP.
     * Note: This function's average is a running average of ALL power readings below CP,
     * not just those in the current window.
     */
    private fun calculateAveragePowerBelowCP(iPower: Int, iCP: Int): Int {
        if (iPower < iCP) {
            sumPowerBelowCP += iPower.toLong()
            countPowerBelowCP++
        }
        return if (countPowerBelowCP > 0) (sumPowerBelowCP / countPowerBelowCP).toInt() else 0
    }

    /**
     * Helper function to calculate average power above CP.
     * Updates internal class members.
     */
    private fun calculateAveragePowerAboveCP(iPower: Int) {
        sumPowerAboveCP += iPower.toLong()
        countPowerAboveCP++
        avPowerAboveCP = if (countPowerAboveCP > 0) (sumPowerAboveCP / countPowerAboveCP).toInt() else 0
    }

    /**
     * Determines the value for tau_w_prime_balance.
     */
    private fun tauWPrimeBalance(iPower: Int, iCP: Int): Double {
        val avgPowerBelowCP = calculateAveragePowerBelowCP(iPower, iCP)
        val deltaCP = (iCP - avgPowerBelowCP).toDouble()
        return (546.00 * exp(-0.01 * deltaCP) + 316.00)
    }

    /**
     * Check and Set starting value of w_prime to realistic numbers!!
     * This logic is applied during initialization.
     */
    private fun constrainWPrimeValue() {
        if (cp60 < 100) {
            cp60 = 100
            eCP = 100 // Also update eCP
        }
        // First determine the "minimal" value for W_Prime according to a 20-min-test estimate, given the iCP value!
        val wPrimeEstimate = getWPrimeFromTwoParameterAlgorithm((cp60 * 1.045).toInt(), 1200.0, cp60)
        if (wPrimeUser < wPrimeEstimate) {
            wPrimeUser = wPrimeEstimate
            ewPrimeMod = wPrimeEstimate // Also update modified W'
            ewPrimeTest = wPrimeEstimate // Also update test W'
        }
    }

    /**
     * Solves 2-parameter algorithm to estimate CP.
     */
    private fun getCPFromTwoParameterAlgorithm(iavPower: Int, iTlim: Double, iwPrime: Int): Int {
        val wPrimeDivTlim = iwPrime.toDouble() / iTlim
        return if (iavPower > wPrimeDivTlim) { // test for out of scope
            (iavPower - wPrimeDivTlim).toInt()
        } else {
            eCP // Something went wrong don't allow an update of CP, use current eCP
        }
    }

    /**
     * Solves 2-parameter algorithm to estimate new W-Prime.
     */
    private fun getWPrimeFromTwoParameterAlgorithm(iavPower: Int, iTlim: Double, iCP: Int): Int {
        return if (iavPower > iCP) { // test for out of scope
            ((iavPower - iCP) * iTlim).toInt()
        } else {
            wPrimeUser // Something went wrong don't allow an update of w_prime, use user W'
        }
    }
}
