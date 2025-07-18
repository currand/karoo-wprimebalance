package com.currand60.wprimebalance.data

import kotlin.math.exp
import kotlin.math.max // For max(0, power_above_cp)

// ------------ W' Balance calculation -------------------
// Global variables related to Cycling Power and W-Prime
// uint16_t TAWC_Mode = 1;                   // Track Anaerobic Capacity Depletion Mode == TRUE -> APPLY and SHOW
// uint16_t CP60 = 160;                      // Your (estimate of) Critical Power, more or less the same as FTP
// uint16_t eCP = CP60;                      // Algorithmic estimate of Critical Power during intense workout
// uint16_t w_prime_usr = 7500;              // Your (estimate of) W-prime or a base value
// uint16_t ew_prime_mod = w_prime_usr;      // First order estimate of W-prime modified during intense workout
// uint16_t ew_prime_test = w_prime_usr;     // 20-min-test algorithmic estimate (20 minute @ 5% above eCP) of W-prime for a given eCP!
// long int w_prime_balance = 0;             // Can be negative !!!
// bool IsShowWprimeValuesDominant = false;  // Boolean that determines to show W Prime data on Oled or not
//-------------------------------------------------------

/**
 * Kotlin class faithfully reproducing the provided C++ W' Balance calculation logic.
 *
 * Class initialization takes the user's estimated Critical Power (CP) and
 * estimated W' in Joules. These directly map to the C++ global variables `CP60` and `w_prime_usr`.
 *
 * This implementation aims to maintain the structure of C++ functions and their
 * conceptual "global" or `static` variables as closely as possible,
 * without introducing optimizations or major structural changes beyond the language conversion.
 *
 * - C++ `uint16_t` is mapped to Kotlin `Int`.
 * - C++ `long int` and `unsigned long int` are mapped to Kotlin `Long`.
 * - C++ `double` is mapped to Kotlin `Double`.
 * - C++ `bool` is mapped to Kotlin `Boolean`.
 * - C++ `static` variables inside functions are mapped to private class properties to explicitly
 *   preserve their state across function calls, reflecting `static` behavior.
 * - C++ pass-by-reference (`&`) for class-level variables are handled by directly modifying
 *   the corresponding class properties.
 * - C++ `millis()` (Arduino-specific) is replaced by the provided `currentTimestampMillis` parameter
 *   for calculating time deltas, as requested by the prompt.
 */
class WPrimeCalculator(
    initialEstimatedCP: Int,          // Corresponds to C++ `CP60`
    initialEstimatedWPrimeJoules: Int // Corresponds to C++ `w_prime_usr`
) {
    // --- Global variables from C++ code, translated as mutable class properties ---
    var TAWC_Mode: Int = 1 // Track Anaerobic Capacity Depletion Mode == TRUE -> APPLY and SHOW
    var CP60: Int = initialEstimatedCP // Your (estimate of) Critical Power, more or less the same as FTP
    var eCP: Int // Algorithmic estimate of Critical Power during intense workout
    var w_prime_usr: Int = initialEstimatedWPrimeJoules // Your (estimate of) W-prime or a base value
    var ew_prime_mod: Int // First order estimate of W-prime modified during intense workout
    var ew_prime_test: Int // 20-min-test algorithmic estimate (20 minute @ 5% above eCP) of W-prime for a given eCP!
    var w_prime_balance: Long = 0L // Can be negative !!! (initialized to 0 as in C++ global scope)

    // --- Static variables from C++ functions, translated as private class properties to preserve state ---
    // From `CalculateAveragePowerBelowCP` function
    private var CountPowerBelowCP: Long = 0L
    private var SumPowerBelowCP: Long = 0L

    // From `CalculateAveragePowerAboveCP` function (and implicitly used in `w_prime_balance_waterworth`)
    private var SumPowerAboveCP: Long = 0L // Sum for calculating average power above CP
    private var CountPowerAboveCP: Long = 0L // Counter for calculating average power above CP (corresponds to `iCpACp` reference)
    private var avPower: Int = 0 // Average power above CP (corresponds to `iavPwr` reference)

    // From `w_prime_balance_waterworth` function
    private var T_lim: Double = 0.0 // Time (duration) while Power is above CP
    private var TimeSpent: Double = 0.0 // Total Time spent in the workout
    private var running_sum: Double = 0.0
    private val NextLevelStep: Long = 1000L // Stepsize of the next level of w-prime modification --> 1000 Joules step
    private var NextUpdateLevel: Long = 0L // The next level at which to update eCP, e_w_prime_mod and ew_prime_test
    private var PrevReadingTime: Long = 0L // Previous reading timestamp in milliseconds

    // Constructor `init` block to perform initial setup equivalent to C++ global initialization sequence.
    init {
        // C++ globals `eCP`, `ew_prime_mod`, `ew_prime_test` are initialized from `CP60` and `w_prime_usr`.
        // The `ConstrainW_PrimeValue` function is typically called in C++ after initial global setup.
        // We'll call it here to apply constraints to `CP60` and `w_prime_usr` immediately.
        ConstrainW_PrimeValue() // This function directly modifies the class properties `CP60` and `w_prime_usr`.

        // After `ConstrainW_PrimeValue` might have modified `CP60` or `w_prime_usr`,
        // re-initialize other dependent class properties to reflect any changes.
        eCP = CP60
        ew_prime_mod = w_prime_usr
        ew_prime_test = w_prime_usr
    }

    // ------------------------ W'Balance Functions -----------------------------------

    // C++: uint16_t CalculateAveragePowerBelowCP(uint16_t iPower, uint16_t iCP);
    private fun CalculateAveragePowerBelowCP(iPower: Int, iCP: Int): Int {
        // C++ `static` variables `CountPowerBelowCP` and `SumPowerBelowCP` are now class properties.
        if (iPower < iCP) {
            SumPowerBelowCP += iPower.toLong() // Add `iPower` to `SumPowerBelowCP` (converted to Long to prevent overflow)
            CountPowerBelowCP++ // Increment `CountPowerBelowCP`
        }
        // Handle division by zero scenario if `CountPowerBelowCP` is 0.
        // The original C++ might lead to division by zero; returning 0 is a safe interpretation.
        return if (CountPowerBelowCP > 0) {
            (SumPowerBelowCP / CountPowerBelowCP).toInt() // Calculate and return average power below CP
        } else {
            0 // Return 0 if no power readings below CP have been recorded yet
        }
    }

    // C++: void CalculateAveragePowerAboveCP(uint16_t iPower, uint16_t &iavPwr, unsigned long int &iCpACp);
    // In Kotlin, `iavPwr` (conceptually `this.avPower`) and `iCpACp` (conceptually `this.CountPowerAboveCP`)
    // are class properties that this function directly modifies to reflect the pass-by-reference behavior.
    private fun CalculateAveragePowerAboveCP(iPower: Int) {
        // C++ `static` variable `SumPowerAboveCP` is now a class property.
        SumPowerAboveCP += iPower.toLong()
        CountPowerAboveCP++ // This is the variable corresponding to `iCpACp` reference in C++
        // Handle division by zero for the average calculation.
        avPower = if (CountPowerAboveCP > 0) { // This is the variable corresponding to `iavPwr` reference in C++
            (SumPowerAboveCP / CountPowerAboveCP).toInt() // Calculate average power above CP
        } else {
            0 // Return 0 if no power readings above CP have been recorded yet
        }
    }

    // C++: double tau_w_prime_balance(uint16_t iPower, uint16_t iCP);
    private fun tau_w_prime_balance(iPower: Int, iCP: Int): Double {
        val avg_power_below_cp = CalculateAveragePowerBelowCP(iPower, iCP)
        val delta_cp = (iCP - avg_power_below_cp).toDouble()
        // Faithful reproduction of the C++ formula for tau
        return (546.00 * exp(-0.01 * delta_cp) + 316.00)
    }

    // C++: void w_prime_balance_waterworth(uint16_t iPower, uint16_t iCP, uint16_t iw_prime);
    // Modified to accept `currentTimestampMillis` as per the prompt's requirement
    // to use the provided timestamp for time delta calculations instead of a global `millis()` call.
    private fun w_prime_balance_waterworth(iPower: Int, iCP: Int, iw_prime: Int, currentTimestampMillis: Long) {
        var power_above_cp: Int = 0 // Power > CP
        var w_prime_expended: Double = 0.0 // Expended energy in Joules
        var ExpTerm1: Double = 0.0
        var ExpTerm2: Double = 0.0

        // C++ `static` variables (e.g., `T_lim`, `TimeSpent`, `running_sum`, `NextUpdateLevel`, `PrevReadingTime`)
        // are now class properties and retain their state across calls to this function.

        // Determine the individual sample time in seconds, it may/will vary during the workout.
        // C++: double SampleTime  = double(millis()-PrevReadingTime)/1000;
        // Using the provided `currentTimestampMillis` for calculation.
        val SampleTime: Double = (currentTimestampMillis - PrevReadingTime).toDouble() / 1000.0
        PrevReadingTime = currentTimestampMillis // Update `PrevReadingTime` for the next sample

        val tau = tau_w_prime_balance(iPower, iCP) // Determine the value for tau
        TimeSpent += SampleTime // The summed value of all sample time values during the workout

        power_above_cp = (iPower - iCP)

        // #ifdef DEBUGAIR
        // Serial.printf("Time:%6.1f ST: %4.2f tau: %f ", TimeSpent, SampleTime , tau);
        // #endif

        // w_prime is energy and measured in Joules = Watt*second.
        // Determine the expended energy above CP since the previous measurement (i.e., during SampleTime).
        // C++: w_prime_expended = double(max(0, power_above_cp))*SampleTime;
        w_prime_expended = max(0, power_above_cp).toDouble() * SampleTime // Calculates (Watts_above_CP) * (its duration in seconds)

        // Calculate the exponential terms used in the W' balance equation.
        ExpTerm1 = exp(TimeSpent / tau) // Exponential term 1
        ExpTerm2 = exp(-TimeSpent / tau) // Exponential term 2

        // #ifdef DEBUGAIR
        // Serial.printf("W prime expended: %3.0f exp-term1: %f exp-term2: %f ", w_prime_expended , ExpTerm1, ExpTerm2);
        // #endif

        running_sum = running_sum + (w_prime_expended * ExpTerm1) // Determine the running sum

        // #ifdef DEBUGAIR
        // Serial.printf("Running Sum: %f ", running_sum);
        // #endif

        // C++: w_prime_balance = (long int)( (double)iw_prime - (running_sum*ExpTerm2) ) ;
        // Calculate W' balance and cast the result to `Long` (equivalent to C++ `long int`).
        w_prime_balance = (iw_prime.toDouble() - (running_sum * ExpTerm2)).toLong()

        // #ifdef DEBUGAIR
        // Serial.printf(" w_prime_balance: %d ", w_prime_balance);
        // #endif

        //--------------- extra --------------------------------------------------------------------------------------
        // This section implements logic to dynamically update estimated CP and W' based on depletion levels.
        if (power_above_cp > 0) {
            // C++: CalculateAveragePowerAboveCP(iPower, avPower, CountPowerAboveCP);
            // In Kotlin, `avPower` and `CountPowerAboveCP` are class properties directly modified by the function.
            CalculateAveragePowerAboveCP(iPower)
            T_lim += SampleTime // Time to exhaustion: accurate sum of every second spent above CP
        }

        // #ifdef DEBUGAIR
        // Serial.printf(" [%d]\n", CountPowerAboveCP);
        // #endif

        // Check if W' balance is further depleted to a new "level" to trigger an update of eCP and ew_prime.
        if ((w_prime_balance < NextUpdateLevel) && (w_prime_expended > 0)) {
            NextUpdateLevel -= NextLevelStep // Move to the next lower depletion level
            // C++: eCP = GetCPfromTwoParameterAlgorithm(avPower, T_lim, iw_prime);
            // Note: C++ passes `double T_lim` to an `unsigned long` parameter; `T_lim.toLong()` mimics this.
            eCP = GetCPfromTwoParameterAlgorithm(avPower, T_lim.toLong(), iw_prime) // Estimate a new `eCP` value
            // C++: ew_prime_mod = w_prime_usr - NextUpdateLevel;
            // `NextUpdateLevel` is `long int` in C++, converting to `Int` for `ew_prime_mod`.
            ew_prime_mod = w_prime_usr - NextUpdateLevel.toInt() // Adjust `ew_prime_modified` to the new depletion level
            // C++: ew_prime_test = GetWPrimefromTwoParameterAlgorithm(uint16_t(eCP*1.045), double(1200), eCP);
            ew_prime_test = GetWPrimefromTwoParameterAlgorithm((eCP * 1.045).toInt(), 1200.0, eCP) // 20-Min-test estimate for W-Prime

            // #ifdef DEBUGAIR
            // Serial.printf("Update of eCP - ew_prime %5d - avPower: %3d - T-lim:%6.1f --> eCP: %3d ", ew_prime_mod, avPower, T_lim, eCP);
            // Serial.printf("--> Test estimate of W-Prime: %d \n", ew_prime_test );
            // #endif
        }
        //-----------------extra -------------------------------------------------------------------------------
    } // end w_prime_balance_waterworth

    // C++: void ConstrainW_PrimeValue(uint16_t &iCP, uint16_t &iw_prime);
    // This function directly modifies the class properties `CP60` and `w_prime_usr`
    // to reflect the C++ pass-by-reference behavior on global-like variables.
    private fun ConstrainW_PrimeValue() {
        if (CP60 < 100) {
            CP60 = 100 // Update `CP60` to the lowest allowed level
        }
        // First, determine the "minimal" value for W_Prime according to a 20-min-test estimate, given the `CP60` value.
        // C++: uint16_t w_prime_estimate = GetWPrimefromTwoParameterAlgorithm(uint16_t(iCP*1.045), double(1200), iCP);
        val w_prime_estimate = GetWPrimefromTwoParameterAlgorithm((CP60 * 1.045).toInt(), 1200.0, CP60)

        if (w_prime_usr < w_prime_estimate) {
            w_prime_usr = w_prime_estimate // Update `w_prime_usr` to a realistic level if it's too low
        }
    } // end ConstrainW_PrimeValue

    // C++: uint16_t GetCPfromTwoParameterAlgorithm(uint16_t iav_Power, unsigned long iT_lim, uint16_t iw_prime);
    private fun GetCPfromTwoParameterAlgorithm(iav_Power: Int, iT_lim: Long, iw_prime: Int): Int {
        // C++: uint16_t WprimeDivTlim = uint16_t( double(iw_prime)/iT_lim );
        val WprimeDivTlim = (iw_prime.toDouble() / iT_lim.toDouble()).toInt() // Type cast for correct calculations

        if (iav_Power > WprimeDivTlim) { // Check for valid scope
            return (iav_Power - WprimeDivTlim) // Solve 2-parameter algorithm to estimate CP
        } else {
            // C++: return eCP; // If something went wrong, return the global `eCP`.
            return eCP // Return the class's current `eCP` property.
        }
    } // end GetCPfromTwoParameterAlgorithm

    // C++: uint16_t GetWPrimefromTwoParameterAlgorithm(uint16_t iav_Power, double iT_lim, uint16_t iCP);
    private fun GetWPrimefromTwoParameterAlgorithm(iav_Power: Int, iT_lim: Double, iCP: Int): Int {
        if (iav_Power > iCP) { // Check for valid scope
            // C++: return (iav_Power-iCP)*((uint16_t)iT_lim);
            return (iav_Power - iCP) * iT_lim.toInt() // Solve 2-parameter algorithm to estimate new W-Prime
        } else {
            // C++: return w_prime_usr; // If something went wrong, return the global `w_prime_usr`.
            return w_prime_usr // Return the class's current `w_prime_usr` property.
        }
    } // end GetWPrimefromTwoParameterAlgorithm

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
        w_prime_balance_waterworth(instantaneousPower, CP60, w_prime_usr, currentTimeMillis)

        // Return the updated W' Prime Balance, which is a class property modified by the above function.
        return w_prime_balance
    }

    fun getPreviousReadingTime(): Long {
        return PrevReadingTime
    }

    fun getCurrentEstimatedCP(): Int {
        return eCP
    }
}
