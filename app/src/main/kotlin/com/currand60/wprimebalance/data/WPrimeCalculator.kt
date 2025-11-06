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
import kotlin.math.pow

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

    private var cP60: Double = 0.0 // Your (estimate of) Critical Power, more or less the same as FTP
    private var wPrimeUsr: Double = 0.0 // Your (estimate of) W-prime or a base value
    private var maxPower: Double = 0.0

    // These represent the 'algorithmic' or 'modified' values that change mid-ride
    private var eCP: Double = 0.0 // Algorithmic estimate of Critical Power during intense workout
    private var ewPrimeMod: Double = 0.0 // First order estimate of W-prime modified during intense workout
    private var ewPrimeTest: Double = 0.0 // 20-min-test algorithmic estimate (20 minute @ 5% above eCP) of W-prime for a given eCP!
    private  var wPrimeBalance: Double = 0.0 // Can be negative !!! (initialized to 0 as in C++ global scope)

    // This property controls if the algorithm can update CP and W' mid-ride
    private var useEstimatedCp: Boolean = false

    private var countPowerBelowCP: Long = 0
    private var sumPowerBelowCP: Double = 0.0

    private var sumPowerAboveCP: Double = 0.0
    private var countPowerAboveCP: Long = 0
    private var avPower: Double = 0.0

    private var iTLim: Double = 0.0
    private var timeSpent: Double = 0.0
    private var runningSum: Double = 0.0
    private val nextLevelStep: Long = 1000
    private var nextUpdateLevel: Long = 0
    private var prevReadingTime: Long = 0
    private var currentCp60: Double = 0.0
    private var currentWPrimeUsr: Double = 0.0
    private var currentMpa: Double = 0.0

    // Match calculation related variables
    private var totalMatches: Long = 0
    private var currentEffortJoulesDepleted: Double = 0.0
    private var currentEffortDuration: Long = 0
    private var effortBlockEndTimeMillis: Double = 0.0 // The true ending of a block without recovery time
    private var minEffortJouleDrop: Double = 2000.0
    private var minEffortDuration = 30000L
    private var matchPowerPercent = 1.05
    private var lastEffortDuration: Long = 0 // Duration of the effort block when the last match was triggered (ms)
    private var lastEffortJoulesDepleted: Double = 0.0 // Total Joules depleted in the *last full effort* that qualified as a match
    private var isInEffortBlock: Boolean = false
    private var wPrimeStartOfCurrentBlock: Double = 0.0 // W' balance at the start of the current effort block
    private var currentEffortStartTimeMillis: Long = 0 // Timestamp when the current effort block started
    private var timeBelowCPLimit: Long = 0 // Accumulates time (ms) spent below CP for recovery margin


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
        cP60 = config.criticalPower.toDouble()
        wPrimeUsr = config.wPrime.toDouble()
        useEstimatedCp = config.calculateCp
        maxPower = config.maxPower.toDouble()
        minEffortJouleDrop = config.matchJoulePercent.toDouble() / 100.0 * wPrimeUsr
        minEffortDuration = config.minMatchDuration.toLong() * 1000
        matchPowerPercent = config.matchPowerPercent / 100.0

        // Ensure values are rational if we are calculating them
        if (useEstimatedCp) {
            constrainWPrimeValue()
        }
        // Initialize/re-initialize algorithmic estimates based on (potentially constrained) user values
        eCP = cP60
        currentCp60 = cP60
        currentWPrimeUsr = wPrimeUsr
        currentMpa = maxPower
        ewPrimeMod = wPrimeUsr
        ewPrimeTest = wPrimeUsr

        Timber.d("WPrimeCalculator config applied. CP60: $cP60 W': $wPrimeUsr UseEstimatedCp: $useEstimatedCp")
    }

    suspend fun resetRideState(initialTimestampMillis: Long) {

        val latestConfig = configurationManager.getConfigFlow().first() // Get current value
        applyConfig(latestConfig) // Apply it to update CP60, wPrimeUsr etc.

        wPrimeBalance = wPrimeUsr // W' balance starts at full capacity with the current W'
        countPowerBelowCP = 0L
        sumPowerBelowCP = 0.0
        sumPowerAboveCP = 0.0
        countPowerAboveCP = 0L
        avPower = 0.0
        iTLim = 0.0
        timeSpent = 0.0

        prevReadingTime = initialTimestampMillis // Set initial timestamp for ride calculations
        nextUpdateLevel = 0L
        currentCp60 = cP60
        currentMpa = maxPower
        currentWPrimeUsr = wPrimeUsr

        // Reset match calculation variables
        totalMatches = 0
        lastEffortDuration = 0L
        lastEffortJoulesDepleted = 0.0
        currentEffortJoulesDepleted = 0.0
        currentEffortDuration = 0L
        effortBlockEndTimeMillis = 0.0
        isInEffortBlock = false
        wPrimeStartOfCurrentBlock = 0.0
        currentEffortStartTimeMillis = 0L
        timeBelowCPLimit = 0L


        Timber.d("WPrimeCalculator ride state reset. W' Balance set to $wPrimeBalance J, initial timestamp: $initialTimestampMillis")
    }


    // ------------------------ W'Balance Functions -----------------------------------

    private fun calculateAveragePowerBelowCP(iPower: Double, iCP: Double): Double {

        if (iPower < iCP) {
            sumPowerBelowCP += iPower
            countPowerBelowCP++
        }

        return if (countPowerBelowCP > 0) {
            sumPowerBelowCP / countPowerBelowCP // Calculate and return average power below CP
        } else {
            0.0 // Return 0 if no power readings below CP have been recorded yet
        }
    }


    private fun calculateAveragePowerAboveCP(iPower: Double) {
        sumPowerAboveCP += iPower.toLong()
        countPowerAboveCP++
        // Handle division by zero for the average calculation.
        avPower = if (countPowerAboveCP > 0) {
            sumPowerAboveCP / countPowerAboveCP // Calculate average power above CP
        } else {
            0.0 // Return 0 if no power readings above CP have been recorded yet
        }
    }

    private fun tauWPrimeBalance(iPower: Double, iCP: Double): Double {
        val avgPowerBelowCp = calculateAveragePowerBelowCP(iPower, iCP)
        val deltaCp = (iCP - avgPowerBelowCp)
        return (546.00 * exp(-0.01 * deltaCp) + 316.00)
    }

    // The Waterworth method of calculating W' Balance.
    private fun wPrimeBalanceWaterworth(iPower: Double, iCP: Double, currentWPrimeUsr: Double, currentTimestampMillis: Long) {
        // Determine the individual sample time in seconds, it may/will vary during the workout.
        // Using the provided `currentTimestampMillis` for calculation.
        val sampleTime = (currentTimestampMillis - prevReadingTime) / 1000.0
        prevReadingTime = currentTimestampMillis

        val tau = tauWPrimeBalance(iPower, iCP)
        timeSpent += sampleTime // The summed value of all sample time values during the workout

        val powerAboveCp = (iPower - iCP)

        // Determine the expended energy above CP since the previous measurement (i.e., during SampleTime).
        val wPrimeExpended = max(0.0, powerAboveCp) * sampleTime // Calculates (Watts_above_CP) * (its duration in seconds)

        // Calculate the exponential terms used in the W' balance equation.
        val expTerm1 = exp(timeSpent / tau) // Exponential term 1
        val expTerm2 = exp(-timeSpent / tau) // Exponential term 2

        runningSum = runningSum + (wPrimeExpended * expTerm1) // Determine the running sum

        wPrimeBalance = currentWPrimeUsr - (runningSum * expTerm2)

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
                currentWPrimeUsr - nextUpdateLevel // Adjust `ew_prime_modified` to the new depletion level
            ewPrimeTest = getWPrimeFromTwoParameterAlgorithm(
                (eCP * 1.045),
                CP_TEST_DURATION_S,
                eCP
            ) // 20-Min-test estimate for W-Prime
        }
    }

    private fun constrainWPrimeValue() {

        cP60 = cP60.coerceIn(100.0, 600.0) // Example reasonable range for CP
        wPrimeUsr = wPrimeUsr.coerceIn(5000.0, 50000.0) // Example reasonable range for W'

        // First, determine the "minimal" value for W_Prime according to a 20-min-test estimate, given the `CP60` value.
        val wPrimeEstimate = getWPrimeFromTwoParameterAlgorithm((cP60 * 1.045), 1200.0, cP60)

        if (wPrimeUsr < wPrimeEstimate) {
            wPrimeUsr = wPrimeEstimate // Update `w_prime_usr` to a realistic level if it's too low
        }
    }

    private fun getCpFromTwoParameterAlgorithm(iavPower: Double, iTLim: Double, currentWPrimeUsr: Double): Double {
        val wPrimeDivTLim = (currentWPrimeUsr.toDouble() / iTLim).toInt()

        return if (iavPower > wPrimeDivTLim) {
            (iavPower - wPrimeDivTLim) // Solve 2-parameter algorithm to estimate CP
        } else {
            eCP // Return the class's current `eCP` property.
        }
    }

    private fun getWPrimeFromTwoParameterAlgorithm(iAvPower: Double, iTLim: Double, iCP: Double): Double {
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
    fun calculateWPrimeBalance(instantaneousPower: Double, currentTimeMillis: Long): Double {

        calculateMatches(instantaneousPower, currentTimeMillis)
        calculateMpa()

        wPrimeBalanceWaterworth(instantaneousPower, currentCp60, currentWPrimeUsr, currentTimeMillis)

        if (useEstimatedCp) {
            // Allow the algorithm to update CP and W' values mid-ride
            currentCp60 = eCP
            currentWPrimeUsr = ewPrimeMod
        }
        return wPrimeBalance
    }

    // ---------------- Matches ---------------------
    private fun calculateMatches(instantaneousPower: Double, currentTimeMillis: Long) {
        val sampleTime = (currentTimeMillis - prevReadingTime)
        val minEffortPower = currentCp60 * matchPowerPercent

        if (!isInEffortBlock) {
            if (instantaneousPower > minEffortPower) {
                // Start a new block if power is high enough
                isInEffortBlock = true
                currentEffortStartTimeMillis = currentTimeMillis - sampleTime
                wPrimeStartOfCurrentBlock = wPrimeBalance
                currentEffortDuration = sampleTime // 1s has already occurred when we receive the sample
                currentEffortJoulesDepleted = 0.0
                timeBelowCPLimit = 0L
            }
        } else {
            // We're already in an effort
            if (instantaneousPower <= currentCp60) {
                // Power drops below CP, accumulate time below CP and check for recovery margin
                timeBelowCPLimit += sampleTime

                if (timeBelowCPLimit >= RECOVERY_MARGIN_MS) {
                    if (currentEffortDuration >= minEffortDuration &&
                        currentEffortJoulesDepleted >= minEffortJouleDrop) {
                        // If RECOVERY_MARGIN_MS has elapsed and the match is qualified,
                        // record the match details

                        totalMatches++
                        lastEffortDuration = currentEffortDuration
                        lastEffortJoulesDepleted = currentEffortJoulesDepleted
                    }
                    // RECOVERY_MARGIN_MS has elapsed, reset the effort block
                    isInEffortBlock = false
                    timeBelowCPLimit = 0L
                    currentEffortJoulesDepleted = 0.0
                    currentEffortDuration = 0L
                }
            } else {
                // Power is above CP, accumulate Joules and duration
                currentEffortDuration += sampleTime + timeBelowCPLimit
                currentEffortJoulesDepleted = wPrimeStartOfCurrentBlock - wPrimeBalance
                timeBelowCPLimit = 0L
            }
        }
    }

    // --------------------Max Power Available -----------------------
    fun calculateMpa() {
        currentMpa = if (maxPower > currentCp60) {
            maxPower - (maxPower - currentCp60) * ((currentWPrimeUsr - wPrimeBalance) / currentWPrimeUsr).pow(2)
        } else {
            0.0
        }
    }


    fun getWPrimeBalance(): Long {
        return wPrimeBalance.toLong()
    }

    fun getCurrentCP(): Int {
        return currentCp60.toInt()
    }

    fun getOriginalCP(): Int {
        return cP60.toInt()
    }

    fun getOriginalWPrimeCapacity(): Int {
        return wPrimeUsr.toInt()
    }

    fun getCurrentWPrimeJoules(): Int {
        return currentWPrimeUsr.toInt()
    }

    fun getMatches(): Int {
        return totalMatches.toInt()
    }

    fun getCurrentMatchJoulesDepleted(): Long {
        return if (isInEffortBlock && (currentEffortDuration >= RECOVERY_MARGIN_MS)) currentEffortJoulesDepleted.toLong() else 0L
    }

    fun getCurrentMatchDepletionDuration(): Long {
        return if (isInEffortBlock && (currentEffortDuration >= RECOVERY_MARGIN_MS)) currentEffortDuration else 0L
    }

    fun getLastMatchJoulesDepleted(): Long {
        return lastEffortJoulesDepleted.toLong()
    }

    fun getLastMatchDepletionDuration(): Long {
        return lastEffortDuration
    }

    fun getInEffortBlock(): Boolean {
        return isInEffortBlock && (currentEffortDuration >= RECOVERY_MARGIN_MS)
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

    fun getMpa(): Int {
        return currentMpa.toInt()
    }
}
