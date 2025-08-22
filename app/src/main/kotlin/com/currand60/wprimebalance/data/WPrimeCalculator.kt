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
private const val MIN_MATCH_JOULE_DROP = 2000L // Minimum drop to qualify as a match
private const val RECOVERY_MARGIN_MS = 15_000L // 15 seconds
private const val EFFORT_POWER_THRESHOLD_PERCENT_CP = 1.05 // 105% of CP to initiate effort block

class WPrimeCalculator(
    private val configurationManager: ConfigurationManager,
) {
    private val calculatorScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

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
    var totalMatches: Int = 0
        private set
    var lastMatchDepletionDuration: Long = 0L // Duration of the effort block when the last match was triggered (ms)
        private set
    var lastMatchJoulesDepleted: Long = 0L // Total Joules depleted in the *last full effort* that qualified as a match
        private set

    private var isInEffortBlock: Boolean = false
    private var wPrimeStartOfCurrentBlock: Long = 0L // W' balance at the start of the current effort block
    private var effortBlockStartTimeMillis: Long = 0L // Timestamp when the current effort block started
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

        prevReadingTime = initialTimestampMillis // Set initial timestamp for ride calculations
        nextUpdateLevel = 0L
        currentCp60 = cP60
        currentWPrimeUsr = wPrimeUsr

        // Reset match calculation variables
        totalMatches = 0
        lastMatchDepletionDuration = 0L
        lastMatchJoulesDepleted = 0L
        isInEffortBlock = false
        wPrimeStartOfCurrentBlock = 0L
        effortBlockStartTimeMillis = 0L
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

    /**
     * Calculates and updates the number of "matches" based on W' balance depletion.
     * A match is defined by a single effort resulting in a >= MIN_MATCH_JOULE_DROP.
     * An effort is defined by an "effort block" which is considered continuous
     * if recovery (power below CP) is less than RECOVERY_MARGIN_MS.
     */
    private fun calculateMatches(instantaneousPower: Int, currentTimeMillis: Long) {
        val sampleTimeMillis = currentTimeMillis - prevReadingTime

        val thresholdPowerForEffort = (currentCp60 * EFFORT_POWER_THRESHOLD_PERCENT_CP).toInt()

        // State 1: Not in an effort block
        if (!isInEffortBlock) {
            // Condition to start a new effort block: Power above threshold
            if (instantaneousPower >= thresholdPowerForEffort) {
                isInEffortBlock = true
                wPrimeStartOfCurrentBlock = wPrimeBalance // Set baseline for this block
                effortBlockStartTimeMillis = currentTimeMillis
                timeBelowCPLimit = 0L // Reset recovery timer
            }
        } else {
            // State 2: In an effort block
            // Check for effort block end (recovery margin)
            if (instantaneousPower < currentCp60) { // If power is below CP
                timeBelowCPLimit += sampleTimeMillis
                if (timeBelowCPLimit >= RECOVERY_MARGIN_MS) {
                    // Effort block has ended due to sufficient recovery
                    val currentBlockDepletion = wPrimeStartOfCurrentBlock - wPrimeBalance

                    if (currentBlockDepletion >= MIN_MATCH_JOULE_DROP) {
                        totalMatches++
                        lastMatchJoulesDepleted = currentBlockDepletion
                        lastMatchDepletionDuration = currentTimeMillis - effortBlockStartTimeMillis
                    }

                    // Reset block variables regardless of whether a match was counted
                    isInEffortBlock = false
                    wPrimeStartOfCurrentBlock = 0L // Clear baseline
                    effortBlockStartTimeMillis = 0L
                    timeBelowCPLimit = 0L // Reset recovery timer
                }
            } else {
                // If power is back above CP, reset recovery timer
                timeBelowCPLimit = 0L
            }
        }
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
