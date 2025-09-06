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
 *
 */

private const val CP_TEST_DURATION_S = 1200.0
private const val RECOVERY_MARGIN_MS = 15_000L // Minimum recovery time to start a new effort block

class WPrimeCalculator(
    private val configurationManager: ConfigurationManager,
) {
    private val calculatorScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private var cP60: Int = 0 // Your (estimate of) Critical Power, more or less the same as FTP
    private var wPrimeUsr: Int = 0 // Your (estimate of) W-prime or a base value

    // These represent the 'algorithmic' or 'modified' values that change mid-ride
    private var eCP: Int = 0 // Algorithmic estimate of Critical Power during intense workout
    private var ewPrimeMod: Int = 0 // First order estimate of W-prime modified during intense workout
    private var ewPrimeTest: Int = 0 // 20-min-test algorithmic estimate (20 minute @ 5% above eCP) of W-prime for a given eCP!
    private  var wPrimeBalance: Long = 0L // Can be negative !!! (initialized to 0 as in C++ global scope)

    // This property controls if the algorithm can update CP and W' mid-ride
    private var useEstimatedCp: Boolean = false

    private var countPowerBelowCP: Long = 0L
    private var sumPowerBelowCP: Long = 0L

    private var sumPowerAboveCP: Long = 0L
    private var countPowerAboveCP: Long = 0L
    private var avPower: Int = 0

    private var iTLim: Double = 0.0
    private var timeSpent: Double = 0.0
    private var runningSum: Double = 0.0
    private val nextLevelStep: Long = 1000L
    private var nextUpdateLevel: Long = 0L
    private var prevReadingTime: Long = 0L
    private var currentCp60: Int = 0
    private var currentWPrimeUsr: Int = 0

    // Match calculation related variables
    private var totalMatches: Int = 0
    private var currentEffortJoulesDepleted: Long = 0L
    private var currentEffortDuration: Long = 0L
    private var effortBlockEndTimeMillis = 0L // The true ending of a block without recovery time
    private var minEffortJouleDrop = 2000.0
    private var minEffortDuration = 30000L
    private var matchPowerPercent = 1.05
    private var lastEffortDuration: Long = 0L // Duration of the effort block when the last match was triggered (ms)
    private var lastEffortJoulesDepleted: Long = 0L // Total Joules depleted in the *last full effort* that qualified as a match
    private var isInEffortBlock: Boolean = false
    private var wPrimeStartOfCurrentBlock: Long = 0L // W' balance at the start of the current effort block
    private var currentEffortStartTimeMillis: Long = 0L // Timestamp when the current effort block started
    private var timeBelowCPLimit: Long = 0L // Accumulates time (ms) spent below CP for recovery margin


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
        minEffortJouleDrop = config.matchJoulePercent / 100.0 * wPrimeUsr
        minEffortDuration = config.minMatchDuration * 1000L
        matchPowerPercent = config.matchPowerPercent / 100.0

        // Ensure values are rational if we are calculating them
        if (useEstimatedCp) {
            constrainWPrimeValue()
        }
        // Initialize/re-initialize algorithmic estimates based on (potentially constrained) user values
        eCP = cP60
        currentCp60 = cP60
        currentWPrimeUsr = wPrimeUsr
        ewPrimeMod = wPrimeUsr
        ewPrimeTest = wPrimeUsr

        Timber.d("WPrimeCalculator config applied. CP60: $cP60 W': $wPrimeUsr UseEstimatedCp: $useEstimatedCp")
    }

    suspend fun resetRideState(initialTimestampMillis: Long) {

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

        prevReadingTime = initialTimestampMillis // Set initial timestamp for ride calculations
        nextUpdateLevel = 0L
        currentCp60 = cP60
        currentWPrimeUsr = wPrimeUsr

        // Reset match calculation variables
        totalMatches = 0
        lastEffortDuration = 0L
        lastEffortJoulesDepleted = 0L
        currentEffortJoulesDepleted = 0L
        currentEffortDuration = 0L
        effortBlockEndTimeMillis = 0L
        isInEffortBlock = false
        wPrimeStartOfCurrentBlock = 0L
        currentEffortStartTimeMillis = 0L
        timeBelowCPLimit = 0L


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
    private fun wPrimeBalanceWaterworth(iPower: Int, iCP: Int, currentWPrimeUsr: Int, currentTimestampMillis: Long) {
        // Determine the individual sample time in seconds, it may/will vary during the workout.
        // Using the provided `currentTimestampMillis` for calculation.
        val sampleTime = (currentTimestampMillis - prevReadingTime) / 1000.0
        prevReadingTime = currentTimestampMillis

        val tau = tauWPrimeBalance(iPower, iCP)
        timeSpent += sampleTime // The summed value of all sample time values during the workout

        val powerAboveCp = (iPower - iCP)

        // Determine the expended energy above CP since the previous measurement (i.e., during SampleTime).
        val wPrimeExpended = max(0, powerAboveCp).toDouble() * sampleTime // Calculates (Watts_above_CP) * (its duration in seconds)

        // Calculate the exponential terms used in the W' balance equation.
        val expTerm1 = exp(timeSpent / tau) // Exponential term 1
        val expTerm2 = exp(-timeSpent / tau) // Exponential term 2

        runningSum = runningSum + (wPrimeExpended * expTerm1) // Determine the running sum

        wPrimeBalance = (currentWPrimeUsr.toDouble() - (runningSum * expTerm2)).toLong()

        //--------------- extra --------------------------------------------------------------------------------------
        // This section implements logic to dynamically update estimated CP and W' based on depletion levels.
        if (powerAboveCp > 0) {
            calculateAveragePowerAboveCP(iPower)
            iTLim += sampleTime // Time to exhaustion: accurate sum of every second spent above CP
        }

        // Check if W' balance is further depleted to a new "level" to trigger an update of eCP and ew_prime.
        if ((wPrimeBalance < nextUpdateLevel) && (wPrimeExpended > 0)) {
            nextUpdateLevel -= nextLevelStep // Move to the next lower depletion level
            eCP = getCpFromTwoParameterAlgorithm(
                avPower,
                iTLim,
                currentWPrimeUsr
            ) // Estimate a new `eCP` value
            ewPrimeMod =
                currentWPrimeUsr - nextUpdateLevel.toInt() // Adjust `ew_prime_modified` to the new depletion level
            ewPrimeTest = getWPrimeFromTwoParameterAlgorithm(
                (eCP * 1.045).toInt(),
                CP_TEST_DURATION_S,
                eCP
            ) // 20-Min-test estimate for W-Prime
        }
    }

    private fun constrainWPrimeValue() {

        cP60 = cP60.coerceIn(100, 600) // Example reasonable range for CP
        wPrimeUsr = wPrimeUsr.coerceIn(5000, 50000) // Example reasonable range for W'

        // First, determine the "minimal" value for W_Prime according to a 20-min-test estimate, given the `CP60` value.
        val wPrimeEstimate = getWPrimeFromTwoParameterAlgorithm((cP60 * 1.045).toInt(), 1200.0, cP60)

        if (wPrimeUsr < wPrimeEstimate) {
            wPrimeUsr = wPrimeEstimate // Update `w_prime_usr` to a realistic level if it's too low
        }
    }

    private fun getCpFromTwoParameterAlgorithm(iavPower: Int, iTLim: Double, currentWPrimeUsr: Int): Int {
        val wPrimeDivTLim = (currentWPrimeUsr.toDouble() / iTLim).toInt()

        return if (iavPower > wPrimeDivTLim) {
            (iavPower - wPrimeDivTLim) // Solve 2-parameter algorithm to estimate CP
        } else {
            eCP // Return the class's current `eCP` property.
        }
    }

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
     *
     * @param instantaneousPower The current instantaneous power reading in Watts.
     * @param currentTimeMillis The current timestamp of this power reading in milliseconds.
     * @return The updated W' Prime Balance in Joules.
     */
    fun calculateWPrimeBalance(instantaneousPower: Int, currentTimeMillis: Long): Long {

        calculateMatches(instantaneousPower, currentTimeMillis)

        wPrimeBalanceWaterworth(instantaneousPower, currentCp60, currentWPrimeUsr, currentTimeMillis)

        if (useEstimatedCp) {
            // Allow the algorithm to update CP and W' values mid-ride
            currentCp60 = eCP
            currentWPrimeUsr = ewPrimeMod
        }

        return wPrimeBalance
    }

    private fun calculateMatches(instantaneousPower: Int, currentTimeMillis: Long) {
        val sampleTime = (currentTimeMillis - prevReadingTime)
        val minEffortPower = currentCp60 * matchPowerPercent

        if (!isInEffortBlock && (instantaneousPower > minEffortPower)) {

            isInEffortBlock = true
            currentEffortStartTimeMillis = currentTimeMillis - 1000L
            wPrimeStartOfCurrentBlock = wPrimeBalance
            timeBelowCPLimit = 0L
            currentEffortDuration = 1000L // 1s has already occurred when we receive the sample
            currentEffortJoulesDepleted = 0L


        } else {
            if (isInEffortBlock &&
                (instantaneousPower <= currentCp60) &&
                (currentEffortDuration >= minEffortDuration))
            {
                timeBelowCPLimit += sampleTime

                if (timeBelowCPLimit >= RECOVERY_MARGIN_MS &&
                    currentEffortJoulesDepleted >= minEffortJouleDrop) {

                    totalMatches++
                    lastEffortDuration = currentEffortDuration
                    lastEffortJoulesDepleted = currentEffortJoulesDepleted
                    isInEffortBlock = false
                }
            } else if (isInEffortBlock) {
                currentEffortDuration += sampleTime + timeBelowCPLimit
                currentEffortJoulesDepleted = wPrimeStartOfCurrentBlock - wPrimeBalance
                timeBelowCPLimit = 0L
            }
        }
    }


    fun getWPrimeBalance(): Long {
        return wPrimeBalance
    }

    fun getCurrentCP(): Int {
        return currentCp60
    }

    fun getOriginalCP(): Int {
        return cP60
    }

    fun getOriginalWPrimeCapacity(): Int {
        return wPrimeUsr
    }

    fun getCurrentWPrimeJoules(): Int {
        return currentWPrimeUsr
    }

    fun getMatches(): Int {
        return totalMatches
    }

    fun getCurrentMatchJoulesDepleted(): Long {
        return if (isInEffortBlock && (currentEffortDuration >= minEffortDuration)) currentEffortJoulesDepleted else 0L
    }

    fun getCurrentMatchDepletionDuration(): Long {
        return if (isInEffortBlock && (currentEffortDuration >= minEffortDuration)) currentEffortDuration else 0L
    }

    fun getLastMatchJoulesDepleted(): Long {
        return lastEffortJoulesDepleted
    }

    fun getLastMatchDepletionDuration(): Long {
        return lastEffortDuration
    }

    fun getInEffortBlock(): Boolean {
        return isInEffortBlock && (currentEffortDuration >= minEffortDuration)
    }

    fun calculateTimeToExhaust(avgPower: Int): Int {
        // Return the time to exhaust W' in seconds based on current 10s
        // average power. This is a LINEAR rate and does not use exponential
        // decay as above.

        val powerAboveCp = avgPower - currentCp60 // Use currentCp60 for consistency

        // Handle edge cases more robustly:
        if (powerAboveCp <= 0 || wPrimeBalance <= 0) {
            // If power is below CP, or W' is negative, no time to exhaust
            return 0
        }

        return (wPrimeBalance / powerAboveCp).toInt()
    }
}
