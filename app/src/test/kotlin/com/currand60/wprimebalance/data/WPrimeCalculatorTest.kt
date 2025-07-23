package com.currand60.wprimebalance.data

import android.content.Context
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
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

    // Now testing the calculator provided by the singleton provider
    private lateinit var calculator: WPrimeCalculator

    // Define default or common initialization values
    private val defaultInitialCp60 = 200
    private val defaultInitialWPrimeUser = 10000

    // Mock context needed for WPrimeCalculatorProvider.initialize
    private val mockContext = mockk<Context>(relaxed = true)

    @BeforeEach
    fun setUp() = runBlocking { // Use runBlocking as some init/reset calls are suspend
        // Initialize the singleton provider first
        WPrimeCalculatorProvider.initialize(mockContext)

        // Get the calculator instance from the provider
        calculator = WPrimeCalculatorProvider.calculator

        // Explicitly configure the calculator for each test scenario's start
        val initialConfig = ConfigData(wPrime = defaultInitialWPrimeUser, criticalPower = defaultInitialCp60)
        calculator.configure(initialConfig, System.currentTimeMillis())
    }

    @Test
    fun `getPreviousReadingTime should return initial timestamp after configure`() {
        // The timestamp is now set during `configure`
        val initialTimestamp = System.currentTimeMillis()
        val config = ConfigData(wPrime = defaultInitialWPrimeUser, criticalPower = defaultInitialCp60)
        calculator.configure(config, initialTimestamp) // Reconfigure for this specific test
        assertEquals(initialTimestamp, calculator.getPreviousReadingTime(), "Initial previous reading time should be the configured timestamp")
    }

    @Test
    fun `calculateWPrimeBalance first call should update previousReadingTime`() {
        val firstCallTime = System.currentTimeMillis() + 1000
        calculator.calculateWPrimeBalance(200, firstCallTime)
        assertEquals(firstCallTime, calculator.getPreviousReadingTime(), "Previous reading time should be updated to current time after first call")
    }

    @Test
    fun `getCurrentEstimatedCP should return initial eCP after configure`() {
        // eCP is initialized to CP60 after configure() runs.
        assertEquals(defaultInitialCp60, calculator.getCurrentCP(), "Initial estimated CP should match the configured initial value")
    }

    @Test
    fun `getCurrentEstimatedCP should return constrained eCP`() {
        val initialCp60TooLow = 10
        val initialWPrimeUserTooLow = 500
        val configTooLow = ConfigData(wPrime = initialWPrimeUserTooLow, criticalPower = initialCp60TooLow)
        calculator.configure(configTooLow, System.currentTimeMillis()) // Reconfigure for this specific test
        // constrainWPrimeValue will set CP60 (and thus eCP) to 100 if it's less than 100.
        assertEquals(100, calculator.getCurrentCP(), "Initial estimated CP should be constrained to 100")
    }

    @Test
    fun `calculateWPrimeBalance with power above CP should decrease WPrimeBalance to negative`() {
        val initialBalanceTimestamp = System.currentTimeMillis()
        val initialConfig = ConfigData(defaultInitialWPrimeUser, defaultInitialCp60, false)
        calculator.configure(initialConfig, initialBalanceTimestamp) // Configure for depletion test

        var currentOverallTime = initialBalanceTimestamp + 1000 // Start time for the loop, ensure it's after initial call

        val powerSteps: List<Pair<Int, Int>> = listOf(
            (defaultInitialCp60 * 4) to 60000, // 4x CP for 60 seconds
        )

        var newBalance: Long = calculator.wPrimeBalance // To track the balance through the loop

        for (powerStep in powerSteps) {
            val instantaneousPower = powerStep.first
            val durationForThisStepMillis = powerStep.second
            val stepIntervalMillis = 1000 // Interval for each calculation within the duration

            Timber.d("Processing power step: Power=$instantaneousPower W for ${durationForThisStepMillis / 1000}s")

            for (elapsedTimeInStep in 0 until durationForThisStepMillis step stepIntervalMillis) {
                currentOverallTime += stepIntervalMillis
                newBalance = calculator.calculateWPrimeBalance(instantaneousPower, currentOverallTime)
            }
            Timber.d("Completed power step: Power=$instantaneousPower W. Final W'Bal for step: $newBalance J")
        }

        assertTrue(newBalance < 0, "W'Balance should be negative when power is heavily above CP")
    }

    @Test
    fun `Enabling eCP update should prevent WPrimeBalance from going too negative`() {
        val initialBalanceTimestamp = System.currentTimeMillis()
        val initialConfig = ConfigData(defaultInitialWPrimeUser, defaultInitialCp60, true) // Enable calculateCp
        calculator.configure(initialConfig, initialBalanceTimestamp) // Configure for this test

        var currentOverallTime = initialBalanceTimestamp + 1000

        val powerSteps: List<Pair<Int, Int>> = listOf(
            (defaultInitialCp60 * 2) to 60000,
        )

        var newBalance: Long = calculator.wPrimeBalance

        for (powerStep in powerSteps) {
            val instantaneousPower = powerStep.first
            val durationForThisStepMillis = powerStep.second
            val stepIntervalMillis = 1000

            Timber.d("Processing power step: Power=$instantaneousPower W for ${durationForThisStepMillis / 1000}s")

            for (elapsedTimeInStep in 0 until durationForThisStepMillis step stepIntervalMillis) {
                currentOverallTime += stepIntervalMillis
                newBalance = calculator.calculateWPrimeBalance(instantaneousPower, currentOverallTime)
            }
            Timber.d("Completed power step: Power=$instantaneousPower W. Final W'Bal for step: $newBalance J")
        }

        assertTrue(newBalance > 0, "W'Balance should be positive at end of test run when eCP updates are enabled")
        assertTrue(calculator.getCurrentCP() > defaultInitialCp60, "eCP should increase when eCP updates are enabled and power is high")
    }

    @Test
    fun `calculateWPrimeBalance with power below CP should increase or maintain WPrimeBalance`() {
        val initialBalanceTimestamp = System.currentTimeMillis()
        val initialConfig = ConfigData(defaultInitialWPrimeUser, defaultInitialCp60, false)
        calculator.configure(initialConfig, initialBalanceTimestamp) // Configure

        // Step 1: Deplete some W'
        val depletionPower = defaultInitialCp60 + 100
        val depletionTime = initialBalanceTimestamp + 1000
        var balanceAfterDepletion: Long = calculator.wPrimeBalance

        repeat(10) { // 10 seconds of depletion
            balanceAfterDepletion = calculator.calculateWPrimeBalance(depletionPower, depletionTime + it * 1000)
        }
        Timber.d("Balance after depletion: $balanceAfterDepletion J")
        assertTrue(balanceAfterDepletion < initialConfig.wPrime, "W'Balance should be depleted")


        // Step 2: Simulate recovery with power below CP
        val recoveryPower = defaultInitialCp60 - 50
        val recoveryTime = depletionTime + 10 * 1000 // Start recovery after depletion
        var newBalance: Long = balanceAfterDepletion

        repeat(30) { // 30 seconds of recovery
            newBalance = calculator.calculateWPrimeBalance(recoveryPower, recoveryTime + it * 1000)
        }
        Timber.d("Balance after recovery: $newBalance J")

        // W'Balance should recover (increase) but not necessarily reach initial W' within 30s.
        // It must be greater than or equal to the balance after depletion.
        assertTrue(newBalance >= balanceAfterDepletion, "W'Balance should recover or maintain when power is below CP. New: $newBalance, AfterDepletion: $balanceAfterDepletion")
    }
}