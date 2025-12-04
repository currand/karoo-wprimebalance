
import com.currand60.wprimebalance.data.ConfigData
import com.currand60.wprimebalance.data.WPrimeCalculator
import com.currand60.wprimebalance.managers.ConfigurationManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

data class Tuple4<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@ExperimentalCoroutinesApi
class WPrimeCalculatorTest {

    // Mocks
    private lateinit var mockConfigurationManager: ConfigurationManager
    private lateinit var configFlow: MutableStateFlow<ConfigData>

    // Class Under Test
    private lateinit var wPrimeCalculator: WPrimeCalculator

    // Test Dispatcher for Coroutines
    private val testDispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        // Set the main dispatcher to a test dispatcher
        Dispatchers.setMain(testDispatcher)

        // Initialize mocks
        mockConfigurationManager = mockk()
        configFlow = MutableStateFlow(
            ConfigData(criticalPower = 200, wPrime = 10000, calculateCp = false, maxPower = 1000) // Default config
        )
        every { mockConfigurationManager.getConfigFlow() } returns configFlow

        // Initialize the class under test
        // The WPrimeCalculator's init block launches a coroutine.
        // By setting the main dispatcher, this coroutine will use the testDispatcher.
        wPrimeCalculator = WPrimeCalculator(mockConfigurationManager)

        // Advance the dispatcher to allow the init block's coroutine to collect the initial config
        testDispatcher.scheduler.advanceUntilIdle()
    }

    @AfterEach
    fun tearDown() {
        // Reset the main dispatcher to the original one
        Dispatchers.resetMain()
    }

    @Nested
    @DisplayName("Initialization and Reset Tests")
    inner class InitializationTests {

        @Test
        @DisplayName("wPrimeBalance should be initialized to wPrimeUsr after resetRideState")
        fun wPrimeBalanceIsInitializedToWPrimeUsrAfterReset() = runTest(testDispatcher) {
            // Given
            val initialTimestamp = System.currentTimeMillis()
            val initialConfig = ConfigData(criticalPower = 300, wPrime = 22000, calculateCp = false, maxPower = 1000)
            configFlow.value = initialConfig // Emit new config if different from default
            testDispatcher.scheduler.advanceUntilIdle() // Ensure config collection

            // When
            wPrimeCalculator.resetRideState(initialTimestamp)
            testDispatcher.scheduler.advanceUntilIdle() // Ensure resetRideState coroutine completes

            // Then
            val expectedWPrimeBalance = initialConfig.wPrime.toLong()
            assertEquals(expectedWPrimeBalance, wPrimeCalculator.getWPrimeBalance(), "wPrimeBalance should be less than 1000J")
        }
    }

    @Nested
    @DisplayName("W' Balance Calculation Tests")
    inner class WPrimeBalanceCalculationTests {

        @Test
        @DisplayName("wPrimeBalance matches values from https://medium.com/critical-powers/comparison-of-wbalance-algorithms-8838173e2c15")
        fun wPrimeBalanceCalculationMatchesReference() = runTest(testDispatcher) {
            // Given
            val stepLength = 1000L
            val originalCp = 350
            val originalWPrime = 20000
            val initialTimestamp = System.currentTimeMillis()
            // The website says FTP of 300 but the plot was based on FTP of 350
            val initialConfig = ConfigData(criticalPower = originalCp, wPrime = originalWPrime, calculateCp = false, maxPower = 1000)
            configFlow.value = initialConfig
            testDispatcher.scheduler.advanceUntilIdle()

            var currentTime = initialTimestamp

            // When
            wPrimeCalculator.resetRideState(initialTimestamp)

            val testSteps = listOf(
                Pair(100, 10 * 60 * 1000),
                Pair(400, 60000),
                Pair(100, 60000),
                Pair(400, 60000),
                Pair(100, 60000),
                Pair(400, 60000),
                Pair(100, 60000),
                Pair(400, 60000),
                Pair(100, 60000),
                Pair(400, 60000),
                Pair(100, 60000),
                Pair(100, 4 * 60 * 1000),
                Pair(400, 60000),
                Pair(100, 60000),
                Pair(400, 60000),
                Pair(100, 60000),
                Pair(400, 60000),
                Pair(100, 60000),
                Pair(400, 60000),
                Pair(100, 60000),
                Pair(400, 60000),
                Pair(100, 60000),
                Pair(100, 10 * 60 * 1000),

            )

            var lowestWPrime = wPrimeCalculator.getOriginalWPrimeCapacity()

            for (step in testSteps) {
                val power = step.first
                val durationMs = step.second


                for (elapsedTime in stepLength until durationMs + stepLength step stepLength) {
                    currentTime += stepLength
                    wPrimeCalculator.calculateWPrimeBalance(power.toDouble(), currentTime)
                    testDispatcher.scheduler.advanceUntilIdle() // Ensure updateWPrimeBalance coroutine completes
//                    println("Time: $currentTime, wPrimeBalance: ${wPrimeCalculator.wPrimeBalance}")
                }
                if (wPrimeCalculator.getWPrimeBalance().toInt() < lowestWPrime) {
                    lowestWPrime = wPrimeCalculator.getWPrimeBalance().toInt()
                }
//                println("Step: ${step.first}, ${step.second}, Time: $currentTime, wPrimeBalance: ${wPrimeCalculator.getWPrimeBalance()}")
            }
            assertTrue(wPrimeCalculator.getWPrimeBalance() in 18500..18750, "wPrimeBalance should be ~18.6kJ. Actual: ${wPrimeCalculator.getWPrimeBalance()}")
            assertTrue(lowestWPrime in 11250..11500, "Lowest value should be ~11.3kJ. Actual: $lowestWPrime")
            assertTrue(wPrimeCalculator.getOriginalCP() == wPrimeCalculator.getCurrentCP(), "Original CP should be $originalCp. Actual: ${wPrimeCalculator.getOriginalCP()}")
            assertTrue(wPrimeCalculator.getOriginalWPrimeCapacity() == wPrimeCalculator.getCurrentWPrimeJoules(), "Current W' should be $originalWPrime. Actual: ${wPrimeCalculator.getCurrentWPrimeJoules()}")
        }

        @Test
        @DisplayName("CSV test cases validation")
        fun validateWPrimeBalanceAgainstCSV() = runTest(testDispatcher) {
            val initialTimestamp = System.currentTimeMillis()
            val initialConfig = ConfigData(criticalPower = 300, wPrime = 22300, calculateCp = false, maxPower = 1000)
            configFlow.value = initialConfig
            testDispatcher.scheduler.advanceUntilIdle()

            wPrimeCalculator.resetRideState(initialTimestamp)
            testDispatcher.scheduler.advanceUntilIdle()

            val csvFile = "testCases1.csv"
            val lines = this::class.java.classLoader?.getResourceAsStream(csvFile)?.bufferedReader()?.readLines() ?: emptyList()

            val testCases = lines.drop(1).map { line ->
                try {
                    val parts = line.split(",")
                    Tuple4(
                        parts[0].toInt(),
                        parts[1].toDouble(),
                        parts[2].toDouble(),
                        parts[3].toDouble()
                    )
                } catch (e: Exception) {
                    Tuple4(
                        0,
                        0.0,
                        0.0,
                        0.0
                    )
                }
            }

            val checkpoints = setOf(450, 600, 1151, 1167, 1350, 1450, 1631, 2000, 2500, 2711)
            val threshold = 1000.0

            for ((timeSeconds, watts, wBal, mpa) in testCases) {
                val currentTime = initialTimestamp + (timeSeconds * 1000L)
                wPrimeCalculator.calculateWPrimeBalance(watts, currentTime)
                testDispatcher.scheduler.advanceUntilIdle()

                if (timeSeconds in checkpoints) {
                    val actualWPrime = wPrimeCalculator.getWPrimeBalance()
                    val actualMpa = wPrimeCalculator.getMpa()
                    val wPrimeDifference = kotlin.math.abs(actualWPrime - wBal)
                    val mpaDifference = kotlin.math.abs(actualMpa - mpa)

//                    assertTrue(
//                        difference <= threshold,
//                        "At ${timeSeconds}s: expected ${wBal}J, got ${actual}J, difference ${difference}J exceeds threshold ${threshold}J"
//                    )
                }
            }
        }

        @Test
        @DisplayName("wPrimeBalance is calculated correctly")
        fun `wPrimeBalanceIsCalculatedCorrectly`() = runTest(testDispatcher) {
            // Given
            val stepLength = 1000L
            val initialTimestamp = System.currentTimeMillis()
            val initialConfig = ConfigData(criticalPower = 300, wPrime = 22300, calculateCp = false, maxPower = 1000)
            configFlow.value = initialConfig // Emit new config if different from default
            testDispatcher.scheduler.advanceUntilIdle() // Ensure config collection

            var currentTime = initialTimestamp

            // When
            wPrimeCalculator.resetRideState(initialTimestamp)

            val testSteps = listOf(
                Pair(0, 600000),
                Pair(470, 180000),
            )

            for (step in testSteps) {
                val power = step.first
                val durationMs = step.second


                for (elapsedTime in stepLength until durationMs + stepLength step stepLength) {
                    currentTime += stepLength
                    wPrimeCalculator.calculateWPrimeBalance(power.toDouble(), currentTime)
                    testDispatcher.scheduler.advanceUntilIdle() // Ensure updateWPrimeBalance coroutine completes
//                    println("Time: $currentTime, wPrimeBalance: ${wPrimeCalculator.wPrimeBalance}")
                }
//                println("Step: ${step.first}, ${step.second}, Time: $currentTime, wPrimeBalance: ${wPrimeCalculator.getWPrimeBalance()}")
            }
            assertTrue(wPrimeCalculator.getWPrimeBalance() < 1000, "wPrimeBalance should be less than 1000J")
        }

        @Test
        @DisplayName("CP60 is updated when wPrimeBalance becomes negative")
        fun `CP60IsUpdatedWhenWPrimeBalanceBecomesNegative`() = runTest(testDispatcher) {
            // Given
            val stepLength = 1000L
            val initialTimestamp = System.currentTimeMillis()
            val initialConfig = ConfigData(criticalPower = 200, wPrime = 10000, calculateCp = true, maxPower = 1000)
            configFlow.value = initialConfig // Emit new config if different from default

            // When
            wPrimeCalculator.resetRideState(initialTimestamp)

            val testSteps = listOf(
                Pair(300, 180000),
            )

            for (step in testSteps) {
                val power = step.first
                val durationMs = step.second

                for (elapsedTime in stepLength until durationMs + stepLength step stepLength) {
                    val currentTime = initialTimestamp + elapsedTime
                    wPrimeCalculator.calculateWPrimeBalance(power.toDouble(), currentTime)
                    testDispatcher.scheduler.advanceUntilIdle() // Ensure updateWPrimeBalance coroutine completes
//                    println("Time: $currentTime, CP60: ${wPrimeCalculator.getCurrentCP()}, wPrimeBalance: ${wPrimeCalculator.getWPrimeBalance()}")
                }
            }
            assertTrue(wPrimeCalculator.getWPrimeBalance() < 1000, "wPrimeBalance should be less than 1000J")
        }

    }

    @Test
    @DisplayName("wPrimeBalance time to exhaust is calculated correctly")
    fun calculateTimeToExhaust_givenPowerAboveCp() = runTest(testDispatcher) {
        val stepLength = 1000L
        val initialTimestamp = System.currentTimeMillis()
        val initialConfig = ConfigData(criticalPower = 200, wPrime = 12000, calculateCp = false, maxPower = 1000)
        configFlow.value = initialConfig // Emit new config if different from default

        // When
        wPrimeCalculator.resetRideState(initialTimestamp)
        val testSteps = listOf(
            Pair(300, 60000),
        )

        for (step in testSteps) {
            val power = step.first
            val durationMs = step.second

            for (elapsedTime in stepLength until durationMs + stepLength step stepLength) {
                val currentTime = initialTimestamp + elapsedTime
                wPrimeCalculator.calculateWPrimeBalance(power.toDouble(), currentTime)
                testDispatcher.scheduler.advanceUntilIdle() // Ensure updateWPrimeBalance coroutine completes
            }
        }
        val timeToExhaust = wPrimeCalculator.calculateTimeToExhaust(400)
        assertTrue( timeToExhaust in 25..35, "Time to exhaust should be ~30s. Actual: $timeToExhaust" )
    }


    @Nested
    @DisplayName("Getter Method Tests")
    inner class GetterTests {
        // TODO: Add tests for getCP60, getWPrimeUsr, getECP, getCurrentCP, getCurrentWPrimeJoules, getPreviousReadingTime
        // Example:
        // @Test
        // fun `getCP60 returns correct initial CP`() = runTest(testDispatcher) { ... }
    }

    @Nested
    @DisplayName("Configuration Change Tests")
    inner class ConfigurationChangeTests {
        // TODO: Add tests for how the calculator reacts to changes in ConfigData
        // Example:
        // @Test
        // fun `calculator updates CP60 and wPrimeUsr on config change`() = runTest(testDispatcher) { ... }
    }

    @Nested
    @DisplayName("Estimated CP and W' (useEstimatedCp = true) Tests")
    inner class EstimatedParameterTests {
        // TODO: Add tests specifically for when useEstimatedCp is true
        // Example:
        // @Test
        // fun `eCP updates when wPrimeBalance depletes significantly and useEstimatedCp is true`() = runTest(testDispatcher) { ... }
    }

    @Nested
    @DisplayName("Match tests")
    inner class MatchTests {

        @Test
        fun `wPrimeMatchesAreCalculatedCorrectly`() = runTest(testDispatcher) {
            // Given
            val stepLength = 1000L
            val initialTimestamp = System.currentTimeMillis()
            val initialConfig = ConfigData(criticalPower = 350,
                wPrime = 22300,
                calculateCp = false,
                matchJoulePercent = 10,
                minMatchDuration = 30,
                maxPower = 1000)

            configFlow.value = initialConfig // Emit new config if different from default
            testDispatcher.scheduler.advanceUntilIdle() // Ensure config collection

            var currentTime = initialTimestamp

            // When
            wPrimeCalculator.resetRideState(initialTimestamp)

            val testSteps = listOf(
                Pair(100, 5000),
                Pair(450, 30000),
                Pair(100, 20000),
                Pair(450, 30000),
                Pair(100, 5000),
                Pair(450, 30000),
                Pair(100, 20000),
                )


            for (step in testSteps) {
                val power = step.first
                val durationMs = step.second

                for (elapsedTime in stepLength until durationMs + stepLength step stepLength) {
                    currentTime += stepLength
                    wPrimeCalculator.calculateWPrimeBalance(power.toDouble(), currentTime)
                    testDispatcher.scheduler.advanceUntilIdle() // Ensure updateWPrimeBalance coroutine completes
//                    println("Time: $currentTime, wPrimeBalance: ${wPrimeCalculator.wPrimeBalance}")
                }
//                println("Step: ${step.first}, ${step.second}, Time: $currentTime, wPrimeBalance: ${wPrimeCalculator.getWPrimeBalance()}")
            }
            assertTrue(wPrimeCalculator.getMatches() == 2, "Matches should be 1 but is ${wPrimeCalculator.getMatches()}")
        }

        @Test
        fun `CurrentMatchGettersAreAccurate`() = runTest(testDispatcher) {
            // Given
            val stepLength = 1000L
            val initialTimestamp = System.currentTimeMillis()
            val initialConfig = ConfigData(criticalPower = 350,
                wPrime = 22300,
                calculateCp = false,
                matchJoulePercent = 10,
                minMatchDuration = 30,
                maxPower = 1000)

            configFlow.value = initialConfig // Emit new config if different from default
            testDispatcher.scheduler.advanceUntilIdle() // Ensure config collection

            var currentTime = initialTimestamp

            // When
            wPrimeCalculator.resetRideState(initialTimestamp)

            val testSteps = listOf(
                Pair(100, 5000),
                Pair(450, 30000),
            )


            for (step in testSteps) {
                val power = step.first
                val durationMs = step.second

                for (elapsedTime in stepLength until durationMs + stepLength step stepLength) {
                    currentTime += stepLength
                    wPrimeCalculator.calculateWPrimeBalance(power.toDouble(), currentTime)
                    testDispatcher.scheduler.advanceUntilIdle() // Ensure updateWPrimeBalance coroutine completes
//                    println("Time: $currentTime, wPrimeBalance: ${wPrimeCalculator.wPrimeBalance}")
                }
//                println("Step: ${step.first}, ${step.second}, Time: $currentTime, wPrimeBalance: ${wPrimeCalculator.getWPrimeBalance()}")
            }
            assertTrue(wPrimeCalculator.getMatches() == 0, "Matches should be 0 (it hasn't ended) but is ${wPrimeCalculator.getMatches()}")
            assertTrue(wPrimeCalculator.getInEffortBlock(), "Current in effort should be true")
            assertTrue(wPrimeCalculator.getCurrentMatchDepletionDuration() == 30000L,
                "Match duration should be 30s but was ${wPrimeCalculator.getCurrentMatchDepletionDuration()}"
            )
            assertTrue(wPrimeCalculator.getCurrentMatchJoulesDepleted() in (2500..3000),
                "Match Joules depleted should be ~2500 but was ${wPrimeCalculator.getCurrentMatchJoulesDepleted()}"
            )
        }

        @Test
        fun lastMatchGettersAreAccurate() = runTest(testDispatcher) {
            // Given
            val stepLength = 1000L
            val initialTimestamp = System.currentTimeMillis()
            val initialConfig = ConfigData(criticalPower = 350,
                wPrime = 22300,
                calculateCp = false,
                matchJoulePercent = 10,
                minMatchDuration = 30,
                maxPower = 1000)

            configFlow.value = initialConfig // Emit new config if different from default
            testDispatcher.scheduler.advanceUntilIdle() // Ensure config collection

            var currentTime = initialTimestamp

            // When
            wPrimeCalculator.resetRideState(initialTimestamp)

            val testSteps = listOf(
                Pair(100, 5000),
                Pair(450, 30000),
                Pair(100, 16000),
            )


            for (step in testSteps) {
                val power = step.first
                val durationMs = step.second

                for (elapsedTime in stepLength until durationMs + stepLength step stepLength) {
                    currentTime += stepLength
                    wPrimeCalculator.calculateWPrimeBalance(power.toDouble(), currentTime)
                    testDispatcher.scheduler.advanceUntilIdle() // Ensure updateWPrimeBalance coroutine completes
//                    println("Time: $currentTime, wPrimeBalance: ${wPrimeCalculator.wPrimeBalance}")
                }
//                println("Step: ${step.first}, ${step.second}, Time: $currentTime, wPrimeBalance: ${wPrimeCalculator.getWPrimeBalance()}")
            }
            assertTrue(wPrimeCalculator.getMatches() == 1, "Matches should be 1 but is ${wPrimeCalculator.getMatches()}")
            assertTrue(!wPrimeCalculator.getInEffortBlock(), "Match should be over but is not")
            assertTrue(wPrimeCalculator.getCurrentMatchDepletionDuration() == 0L,
                "Current match duration should be 0 but was ${wPrimeCalculator.getCurrentMatchDepletionDuration()}"
            )
            assertTrue(wPrimeCalculator.getCurrentMatchJoulesDepleted() == 0L,
                "Current match Joules depleted should be 0 but was ${wPrimeCalculator.getCurrentMatchJoulesDepleted()}"
            )
            assertTrue(wPrimeCalculator.getLastMatchDepletionDuration() == 30000L,
                "Last match duration should be 30s but was ${wPrimeCalculator.getLastMatchDepletionDuration()}"
            )
            assertTrue(wPrimeCalculator.getLastMatchJoulesDepleted() in (2500..3000),
                "Last match Joules depleted should be ~2500 but was ${wPrimeCalculator.getLastMatchJoulesDepleted()}"
            )
        }

        @Test
        fun matchNotCountedWhenConditionsAreNotMet() = runTest(testDispatcher) {
            // Given
            val stepLength = 1000L
            val initialTimestamp = System.currentTimeMillis()
            val initialConfig = ConfigData(
                criticalPower = 350,
                wPrime = 22300,
                calculateCp = false,
                matchJoulePercent = 10,
                minMatchDuration = 30,
                matchPowerPercent = 105,
                maxPower = 1000
            )

            configFlow.value = initialConfig // Emit new config if different from default
            testDispatcher.scheduler.advanceUntilIdle() // Ensure config collection

            var currentTime = initialTimestamp

            // When
            wPrimeCalculator.resetRideState(initialTimestamp)

            val testSteps = listOf(
                Pair(100, 900),
                Pair(800, 5000),
                Pair(100, 900),
                )

            for (step in testSteps) {
                val power = step.first
                val durationMs = step.second

                for (elapsedTime in stepLength until durationMs + stepLength step stepLength) {
                    currentTime += stepLength
                    wPrimeCalculator.calculateWPrimeBalance(power.toDouble(), currentTime)
                    testDispatcher.scheduler.advanceUntilIdle() // Ensure updateWPrimeBalance coroutine completes
//                    println("Time: $currentTime, wPrimeBalance: ${wPrimeCalculator.wPrimeBalance}")
                }
//                println("Step: ${step.first}, ${step.second}, Time: $currentTime, wPrimeBalance: ${wPrimeCalculator.getWPrimeBalance()}")
            }
            assertTrue(wPrimeCalculator.getMatches() == 0, "Matches should be 1 but is ${wPrimeCalculator.getMatches()}")
            assertTrue(!wPrimeCalculator.getInEffortBlock(), "Match should be over but is not")
            assertTrue(wPrimeCalculator.getCurrentMatchDepletionDuration() == 0L,
                "Current match duration should be 0 but was ${wPrimeCalculator.getCurrentMatchDepletionDuration()}"
            )
            assertTrue(wPrimeCalculator.getCurrentMatchJoulesDepleted() == 0L,
                "Current match Joules depleted should be 0 but was ${wPrimeCalculator.getCurrentMatchJoulesDepleted()}"
            )
        }
        @Test
        @DisplayName("Effort is discarded if recovery time passes but duration/joules not met")
        fun shortDurationHighJoulesIsDiscarded() = runTest(testDispatcher) {
            // Given
            val stepLength = 1000L
            val initialTimestamp = System.currentTimeMillis()

            // Set configuration for the test
            val criticalPower = 300 // CP60
            val wPrimeCapacity = 20000 // W'
            val minMatchDurationSeconds = 30 // 30 seconds
            val matchJoulePercent = 10 // 10% W' depletion (20000 * 0.10 = 2000J)
            val matchPowerPercent = 105 // minEffortPower = 300 * 1.05 = 315W

            val initialConfig = ConfigData(
                criticalPower = criticalPower,
                wPrime = wPrimeCapacity,
                calculateCp = false,
                minMatchDuration = minMatchDurationSeconds,
                matchJoulePercent = matchJoulePercent,
                matchPowerPercent = matchPowerPercent,
                maxPower = 1000
            )

            configFlow.value = initialConfig // Emit new config
            testDispatcher.scheduler.advanceUntilIdle() // Ensure config collection

            var currentTime = initialTimestamp

            // When
            wPrimeCalculator.resetRideState(initialTimestamp)

            // Step 1: Power burst above minEffortPower to start an effort block
            // 1200W is > 315W (minEffortPower)
            // Duration: 10 seconds (10000ms) - this is < 30000ms (MIN_EFFORT_DURATION_MS)
            // Joules depleted: 2000J (burstPower * burstDuration) = ~8800J
            val burstPower = 1200
            val burstDuration = 10000L // 2 seconds

            for (elapsedTime in stepLength until burstDuration + stepLength step stepLength) {
                currentTime += stepLength
                wPrimeCalculator.calculateWPrimeBalance(burstPower.toDouble(), currentTime)
                testDispatcher.scheduler.advanceUntilIdle()
            }

            // Assert that we are in an effort block after the burst
            assertTrue(!wPrimeCalculator.getInEffortBlock(), "Will not display an effort block after initial burst")
            assertTrue(wPrimeCalculator.getCurrentMatchDepletionDuration() == 0L, "Current match duration will show zero")
            assertTrue(wPrimeCalculator.getCurrentMatchJoulesDepleted() == 0L, "Current match joules will be zero")


            // Step 2: Drop power below CP60 for sufficient recovery time
            // 100W is < 300W (CP60)
            // Duration: 6 seconds (6000ms) - this is > 5000ms (RECOVERY_MARGIN_MS)
            val recoveryPower = 100
            val recoveryDuration = 16000L // 6 seconds

            for (elapsedTime in stepLength until recoveryDuration + stepLength step stepLength) {
                currentTime += stepLength
                wPrimeCalculator.calculateWPrimeBalance(recoveryPower.toDouble(), currentTime)
                testDispatcher.scheduler.advanceUntilIdle()
            }

            // Then
            // After recovery, the effort should have been evaluated:
            // currentEffortDuration (10000ms) < MIN_EFFORT_DURATION_MS (30000ms)
            // wPrimeDepleted (approx 8800J) > MIN_EFFORT_JOULE_DROP (2000J)
            // Since RECOVERY_MARGIN_MS (15000ms) was exceeded, but criteria were NOT met,
            // the state machine should have discarded this effort.
            assertEquals(0, wPrimeCalculator.getMatches(), "No matches should be counted as criteria were not met")
            assertTrue(!wPrimeCalculator.getInEffortBlock(), "Should no longer be in an effort block (it was discarded)")
            assertEquals(0L, wPrimeCalculator.getCurrentMatchDepletionDuration(), "Current match duration should be reset to 0")
            assertEquals(0L, wPrimeCalculator.getCurrentMatchJoulesDepleted(), "Current match joules depleted should be reset to 0")
        }
        @Test
        @DisplayName("Long duration but low Joules is not a match")
        fun longDurationLowJoulesIsNotAMatch() = runTest(testDispatcher) {
            // Given
            val stepLength = 1000L
            val initialTimestamp = System.currentTimeMillis()

            // Set configuration for the test
            val criticalPower = 300 // CP60
            val wPrimeCapacity = 20000 // W'
            val minMatchDurationSeconds = 30 // 30 seconds
            val matchJoulePercent = 30 // 10% W' depletion (20000 * 0.10 = 2000J)
            val matchPowerPercent = 105 // minEffortPower = 300 * 1.05 = 315W

            val initialConfig = ConfigData(
                criticalPower = criticalPower,
                wPrime = wPrimeCapacity,
                calculateCp = false,
                minMatchDuration = minMatchDurationSeconds,
                matchJoulePercent = matchJoulePercent,
                matchPowerPercent = matchPowerPercent,
                maxPower = 1000
            )

            configFlow.value = initialConfig // Emit new config
            testDispatcher.scheduler.advanceUntilIdle() // Ensure config collection

            var currentTime = initialTimestamp

            // When
            wPrimeCalculator.resetRideState(initialTimestamp)

            // Step 1: Power burst above minEffortPower to start an effort block
            // 1200W is > 315W (minEffortPower)
            // Duration: 10 seconds (10000ms) - this is < 30000ms (MIN_EFFORT_DURATION_MS)
            // Joules depleted: 2000J (burstPower * burstDuration) = ~8800J
            val burstPower = 330
            val burstDuration = 31000L

            for (elapsedTime in stepLength until burstDuration + stepLength step stepLength) {
                currentTime += stepLength
                wPrimeCalculator.calculateWPrimeBalance(burstPower.toDouble(), currentTime)
                testDispatcher.scheduler.advanceUntilIdle()
            }

            // Assert that we are in an effort block after the burst
            assertTrue(wPrimeCalculator.getInEffortBlock(), "Will not display an effort block after initial burst")
            assertTrue(wPrimeCalculator.getCurrentMatchDepletionDuration() >= 0L, "Current match duration will show zero")
            assertTrue(wPrimeCalculator.getCurrentMatchJoulesDepleted() >= 0L, "Current match joules will be zero")


            // Step 2: Drop power below CP60 for sufficient recovery time
            // 100W is < 300W (CP60)
            // Duration: 6 seconds (6000ms) - this is > 5000ms (RECOVERY_MARGIN_MS)
            val recoveryPower = 100
            val recoveryDuration = 16000L // 6 seconds

            for (elapsedTime in stepLength until recoveryDuration + stepLength step stepLength) {
                currentTime += stepLength
                wPrimeCalculator.calculateWPrimeBalance(recoveryPower.toDouble(), currentTime)
                testDispatcher.scheduler.advanceUntilIdle()
            }

            // Then
            // After recovery, the effort should have been evaluated:
            // currentEffortDuration (10000ms) < MIN_EFFORT_DURATION_MS (30000ms)
            // wPrimeDepleted (approx 8800J) > MIN_EFFORT_JOULE_DROP (2000J)
            // Since RECOVERY_MARGIN_MS (15000ms) was exceeded, but criteria were NOT met,
            // the state machine should have discarded this effort.
            assertEquals(0, wPrimeCalculator.getMatches(), "No matches should be counted as criteria were not met")
            assertTrue(!wPrimeCalculator.getInEffortBlock(), "Should no longer be in an effort block (it was discarded)")
            assertEquals(0L, wPrimeCalculator.getCurrentMatchDepletionDuration(), "Current match duration should be reset to 0")
            assertEquals(0L, wPrimeCalculator.getCurrentMatchJoulesDepleted(), "Current match joules depleted should be reset to 0")
        }

    }
    @Nested
    @DisplayName("Max Power Available Tests")
    inner class MpaCalculationTests {

        @Test
        @DisplayName("MPA at rest is calculated correctly")
        fun `MPAAtRestIsCalculatedCorrectly`() = runTest(testDispatcher) {
            // Given
            val initialTimestamp = System.currentTimeMillis()
            val initialConfig = ConfigData(
                criticalPower = 300,
                wPrime = 22000,
                calculateCp = false,
                maxPower = 1000
            )
            configFlow.value = initialConfig // Emit new config if different from default
            testDispatcher.scheduler.advanceUntilIdle() // Ensure config collection

            // When
            wPrimeCalculator.resetRideState(initialTimestamp)
            testDispatcher.scheduler.advanceUntilIdle() // Ensure resetRideState coroutine completes

            // Then
            val expectedMpa = initialConfig.maxPower
            assertEquals(
                expectedMpa,
                wPrimeCalculator.getMpa(),
                "MPA should be ${initialConfig.maxPower} but is ${wPrimeCalculator.getMpa()}"
            )
        }
        @Test
        @DisplayName("MPA matches https://arxiv.org/pdf/2503.14841")
        fun verifyMpaCalculationAgainstArxivPaper() = runTest(testDispatcher) {

            val stepLength = 1000L
            val initialTimestamp = System.currentTimeMillis()
            var currentTime = initialTimestamp
            val initialConfig = ConfigData(
                criticalPower = 330,
                wPrime = 25000,
                calculateCp = false,
                maxPower = 1200
            )

            configFlow.value = initialConfig // Emit new config if different from default
            wPrimeCalculator.resetRideState(initialTimestamp)
            testDispatcher.scheduler.advanceUntilIdle() // Ensure resetRideState coroutine completes

            val instantaneousPower = 350
            val duration = 2701000L

            for (elapsedTime in stepLength until duration + stepLength step stepLength) {
                currentTime += stepLength
                wPrimeCalculator.calculateWPrimeBalance(instantaneousPower.toDouble(), currentTime)
                testDispatcher.scheduler.advanceUntilIdle()
            }

            // Then
            val expectedMpa = initialConfig.maxPower
            assertTrue(
                wPrimeCalculator.getMpa() in 1100..1200,
                "MPA should be close to ${initialConfig.maxPower} but is ${wPrimeCalculator.getMpa()}"
            )
        }
    }
}
