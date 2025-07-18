package com.currand60.wprimebalance.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.ExtensionContext
import timber.log.Timber


class TimberExtension : BeforeAllCallback, AfterAllCallback {

    private val printlnTree = object : Timber.DebugTree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            println("$tag: $message")
        }
    }

    override fun beforeAll(context: ExtensionContext?) {
        Timber.plant(printlnTree)
    }

    override fun afterAll(context: ExtensionContext?) {
        Timber.uproot(printlnTree)
    }
}

@ExtendWith(TimberExtension::class)
class WPrimeCalculatorTest {


    // Declare a lateinit var for the WPrimeCalculator instance
    private lateinit var calculator: WPrimeCalculator
    private lateinit var calculatorTooLow: WPrimeCalculator

    // Define default or common initialization values
    private val defaultInitialCp60 = 140
    private val defaultInitialWPrimeUser = 7200
    private val initialCp60TooLow = 10
    private val initialWPrimeUserTooLow = 500
    private val currentTimeMillis = System.currentTimeMillis()

    @BeforeEach
    fun setUp() {
        // Instantiate WPrimeCalculator here
        // This method will be called before each test method
        calculator = WPrimeCalculator(
            initialEstimatedCP = defaultInitialCp60,
            initialEstimatedWPrimeJoules = defaultInitialWPrimeUser,
            currentTimeMillis = currentTimeMillis
        )
        // You can add more setup logic here if needed,
        // e.g., calling some initial methods on the calculator
        // or setting up mocks if it had dependencies (though this one doesn't).
    }

    @Test
    fun `getPreviousReadingTime should return 0 initially`() {
        // Now you can use the 'calculator' instance directly
        assertEquals(currentTimeMillis, calculator.getPreviousReadingTime(), "Initial previous reading time should be 0")
    }

    @Test
    fun `updateAndGetWPrimeBalance first call should update previousReadingTime`() {
        val currentTime = System.currentTimeMillis()
        calculator.calculateWPrimeBalance(200, currentTime)
        assertEquals(currentTime, calculator.getPreviousReadingTime(), "Previous reading time should be updated to current time after first call")
    }

    @Test
    fun `getCurrentEstimatedCP should return initial eCP`() {
        // The WPrimeCalculator's init block calls constrainWPrimeValue,
        // which might adjust cp60 and eCP based on initialWPrimeUser.
        // For this test, we are checking the eCP value *after* that initialization logic.
        // If constrainWPrimeValue doesn't change initialCp60 (because it's >= 100
        // and wPrimeUser is already realistic), then eCP should be defaultInitialCp60.
        // You might need more specific tests for constrainWPrimeValue's effects.

        // Example: If defaultInitialCp60 = 250 and defaultInitialWPrimeUser = 20000,
        // and these values don't trigger changes in constrainWPrimeValue, then:
        assertEquals(defaultInitialCp60, calculator.getCurrentEstimatedCP(), "Initial estimated CP should match the constrained initial value")
    }

    @Test
    fun `getCurrentEstimatedCP should return constrained eCP`() {
        // The WPrimeCalculator's init block calls constrainWPrimeValue,
        // which might adjust cp60 and eCP based on initialWPrimeUser.
        // For this test, we are checking the eCP value *after* that initialization logic.
        // If constrainWPrimeValue doesn't change initialCp60 (because it's >= 100
        // and wPrimeUser is already realistic), then eCP should be defaultInitialCp60.
        // You might need more specific tests for constrainWPrimeValue's effects.

        // Example: If defaultInitialCp60 = 250 and defaultInitialWPrimeUser = 20000,
        // and these values don't trigger changes in constrainWPrimeValue, then:
        calculatorTooLow = WPrimeCalculator(
            initialEstimatedCP = initialCp60TooLow,
            initialEstimatedWPrimeJoules = initialWPrimeUserTooLow,
            currentTimeMillis = System.currentTimeMillis()
        )
        assertEquals(100, calculatorTooLow.getCurrentEstimatedCP(), "Initial estimated CP should match the constrained initial value")
    }

    // Add more test methods for other functionalities of WPrimeCalculator
    // For example:
    @Test
    fun `updateAndGetWPrimeBalance with power above CP should decrease WPrimeBalance`() {
        val initialBalance = calculator.calculateWPrimeBalance(0, System.currentTimeMillis()) // Initialize prevReadingTime
        var currentOverallTime = System.currentTimeMillis() + 1000 // Start time for the loop, ensure it's after initial call

        val powerSteps: List<Pair<Int, Int>> = listOf(
            (defaultInitialCp60 - 40) to 100000, // Starting baseline
            (defaultInitialCp60 + 35) to 840000,
            (defaultInitialCp60 - 40) to 60000,
            (defaultInitialCp60 + 55) to 60000,
            (defaultInitialCp60 +40) to 60000

        )

        var newBalance = initialBalance // To track the balance through the loop

        for (powerStep in powerSteps) {
            val instantaneousPower = powerStep.first
            val durationForThisStepMillis = powerStep.second
            val stepIntervalMillis = 1000 // Interval for each calculation within the duration

            Timber.d("Processing power step: Power=$instantaneousPower W for ${durationForThisStepMillis / 1000}s")

            @Suppress("UNUSED_VARIABLE")
            for (elapsedTimeInStep in 0 until durationForThisStepMillis step stepIntervalMillis) {
                currentOverallTime += stepIntervalMillis

                newBalance = calculator.calculateWPrimeBalance(instantaneousPower, currentOverallTime)

                Timber.d("Time: ${currentOverallTime / 1000}s (in step), Power: $instantaneousPower W, W'Bal: $newBalance J")
            }
            Timber.d("Completed power step: Power=$instantaneousPower W. Final W'Bal for step: $newBalance J")
        }

        assertTrue(newBalance < initialBalance, "W'Balance should decrease when power is above CP")
    }

    @Test
    fun `updateAndGetWPrimeBalance with power below CP should increase or maintain WPrimeBalance`() {
        // Note: The exact behavior of W'Balance recovery (increase) is complex due to the tau calculation.
        // This test provides a basic check. More specific values would be needed for precise assertions.
        calculator.calculateWPrimeBalance(defaultInitialCp60 + 100, System.currentTimeMillis()) // Deplete some W'
        val balanceAfterDepletion = calculator.calculateWPrimeBalance(defaultInitialCp60 + 100, System.currentTimeMillis() + 10000)


        val powerBelowCp = defaultInitialCp60 - 50
        val currentTime = System.currentTimeMillis() + 20000 // Simulate time passing

        val newBalance = calculator.calculateWPrimeBalance(powerBelowCp, currentTime)

        // Depending on the model, it might increase or stay stable if fully recovered.
        // For simplicity, we check it's not significantly lower than after depletion.
        assertTrue(newBalance >= balanceAfterDepletion - 100, // Allow for small fluctuations or if it hits wPrimeUser
            "W'Balance should recover or maintain when power is below CP. New: $newBalance, AfterDepletion: $balanceAfterDepletion")
    }
}
