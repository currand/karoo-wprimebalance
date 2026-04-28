
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

    private fun setPrivateField(name: String, value: Any) {
        val field = WPrimeCalculator::class.java.getDeclaredField(name)
        field.isAccessible = true
        field.set(wPrimeCalculator, value)
    }

    private fun invokePrivateMethod(name: String, vararg args: Any): Any? {
        val argTypes = args.map {
            when (it) {
                is java.lang.Double -> Double::class.javaPrimitiveType!!
                is java.lang.Long -> Long::class.javaPrimitiveType!!
                is java.lang.Boolean -> Boolean::class.javaPrimitiveType!!
                else -> it::class.java
            }
        }.toTypedArray()
        val method = WPrimeCalculator::class.java.getDeclaredMethod(name, *argTypes)
        method.isAccessible = true
        return method.invoke(wPrimeCalculator, *args)
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
        private val stepLengthMs = 1000L

        private suspend fun configureAndReset(
            config: ConfigData,
            initialTimestamp: Long
        ) {
            configFlow.value = config
            testDispatcher.scheduler.advanceUntilIdle()
            wPrimeCalculator.resetRideState(initialTimestamp)
            testDispatcher.scheduler.advanceUntilIdle()
        }

        private fun simulatePower(
            power: Int,
            durationMs: Long,
            startTime: Long
        ): Long {
            var currentTime = startTime
            for (elapsedTime in stepLengthMs until durationMs + stepLengthMs step stepLengthMs) {
                currentTime += stepLengthMs
                wPrimeCalculator.calculateWPrimeBalance(power.toDouble(), currentTime)
                testDispatcher.scheduler.advanceUntilIdle()
            }
            return currentTime
        }

        private fun simulateSequence(
            steps: List<Pair<Int, Long>>,
            startTime: Long
        ): Long {
            var currentTime = startTime
            for ((power, durationMs) in steps) {
                currentTime = simulatePower(power, durationMs, currentTime)
            }
            return currentTime
        }

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

        @Test
        fun matchDoesNotStartWhenPowerEqualsMatchThreshold() = runTest(testDispatcher) {
            val initialTimestamp = 10_000L
            val config = ConfigData(
                criticalPower = 300,
                wPrime = 20_000,
                calculateCp = false,
                matchJoulePercent = 10,
                minMatchDuration = 30,
                matchPowerPercent = 105,
                maxPower = 1000
            )
            configureAndReset(config, initialTimestamp)

            simulatePower(power = 315, durationMs = 45_000L, startTime = initialTimestamp)

            assertEquals(0, wPrimeCalculator.getMatches(), "A power value exactly at threshold must not start a match block")
            assertTrue(!wPrimeCalculator.getInEffortBlock(), "The state machine should remain out of effort block")
            assertEquals(0L, wPrimeCalculator.getCurrentMatchDepletionDuration())
            assertEquals(0L, wPrimeCalculator.getCurrentMatchJoulesDepleted())
        }

        @Test
        fun recoveryFinalizesAtExactRecoveryMargin() = runTest(testDispatcher) {
            val initialTimestamp = 20_000L
            val config = ConfigData(
                criticalPower = 350,
                wPrime = 22_300,
                calculateCp = false,
                matchJoulePercent = 10,
                minMatchDuration = 30,
                maxPower = 1000
            )
            configureAndReset(config, initialTimestamp)

            val afterEffort = simulatePower(power = 450, durationMs = 30_000L, startTime = initialTimestamp)
            val after14sRecovery = simulatePower(power = 100, durationMs = 14_000L, startTime = afterEffort)

            assertEquals(0, wPrimeCalculator.getMatches(), "Recovery must not finalize before 15 seconds")
            assertTrue(wPrimeCalculator.getInEffortBlock(), "Effort block should still be active at 14 seconds recovery")

            simulatePower(power = 100, durationMs = 1_000L, startTime = after14sRecovery)

            assertEquals(1, wPrimeCalculator.getMatches(), "Recovery should finalize exactly at 15 seconds")
            assertTrue(!wPrimeCalculator.getInEffortBlock(), "Effort block should be closed after finalization")
            assertEquals(30_000L, wPrimeCalculator.getLastMatchDepletionDuration())
        }

        @Test
        fun matchQualifiesAtExactDurationAndJouleThreshold() = runTest(testDispatcher) {
            val initialTimestamp = 30_000L
            val config = ConfigData(
                criticalPower = 300,
                wPrime = 20_000,
                calculateCp = false,
                matchJoulePercent = 6,
                minMatchDuration = 30,
                maxPower = 1000
            )
            configureAndReset(config, initialTimestamp)

            val afterEffort = simulatePower(power = 500, durationMs = 30_000L, startTime = initialTimestamp)
            simulatePower(power = 100, durationMs = 15_000L, startTime = afterEffort)

            assertEquals(1, wPrimeCalculator.getMatches(), "A block meeting minimum duration and joule depletion should qualify")
            assertEquals(30_000L, wPrimeCalculator.getLastMatchDepletionDuration(), "Match should be recorded at exact 30s boundary")
            assertTrue(
                wPrimeCalculator.getLastMatchJoulesDepleted() >= 1_200L,
                "Joule depletion should meet configured minimum threshold"
            )
        }

        @Test
        fun largeTimestampJumpDuringEffortProducesExpectedAndBoundedLastDuration() = runTest(testDispatcher) {
            val initialTimestamp = 40_000L
            val config = ConfigData(
                criticalPower = 300,
                wPrime = 20_000,
                calculateCp = false,
                matchJoulePercent = 5,
                minMatchDuration = 30,
                maxPower = 1000
            )
            configureAndReset(config, initialTimestamp)

            val afterQualified = simulateSequence(
                steps = listOf(
                    Pair(100, 5_000L),
                    Pair(500, 30_000L),
                    Pair(100, 16_000L),
                ),
                startTime = initialTimestamp
            )
            assertEquals(1, wPrimeCalculator.getMatches())
            val baselineLastDuration = wPrimeCalculator.getLastMatchDepletionDuration()
            val baselineLastJoules = wPrimeCalculator.getLastMatchJoulesDepleted()

            // Start a second effort and inject a large timestamp jump.
            val effortStart = simulatePower(power = 500, durationMs = 1_000L, startTime = afterQualified)
            val t2 = effortStart + 1_200_000L
            wPrimeCalculator.calculateWPrimeBalance(500.0, t2)
            testDispatcher.scheduler.advanceUntilIdle()
            simulatePower(power = 100, durationMs = 15_000L, startTime = t2)

            // Characterization: a pathological time jump must not overwrite last qualified values with garbage.
            assertEquals(1, wPrimeCalculator.getMatches())
            assertEquals(baselineLastDuration, wPrimeCalculator.getLastMatchDepletionDuration())
            assertEquals(baselineLastJoules, wPrimeCalculator.getLastMatchJoulesDepleted())
            assertTrue(baselineLastDuration in 30_000L..31_000L)
        }

        @Test
        fun largeTimestampJumpIsNormalizedInCurrentEffortDurationBeforeRecoveryFinalization() = runTest(testDispatcher) {
            val initialTimestamp = 45_000L
            val config = ConfigData(
                criticalPower = 300,
                wPrime = 20_000,
                calculateCp = false,
                matchJoulePercent = 5,
                minMatchDuration = 30,
                maxPower = 1000
            )
            configureAndReset(config, initialTimestamp)

            val t1 = initialTimestamp + 1_000L
            wPrimeCalculator.calculateWPrimeBalance(500.0, t1)
            testDispatcher.scheduler.advanceUntilIdle()

            val t2 = t1 + 1_200_000L
            wPrimeCalculator.calculateWPrimeBalance(500.0, t2)
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(
                0L,
                wPrimeCalculator.getCurrentMatchDepletionDuration(),
                "With normalized sampling, the effort remains below display threshold instead of exploding into a long duration"
            )
        }

        @Test
        fun nonMonotonicTimestampDoesNotProduceNegativeOrPhantomLastMatch() = runTest(testDispatcher) {
            val initialTimestamp = 50_000L
            val config = ConfigData(
                criticalPower = 300,
                wPrime = 20_000,
                calculateCp = false,
                matchJoulePercent = 10,
                minMatchDuration = 30,
                maxPower = 1000
            )
            configureAndReset(config, initialTimestamp)

            val afterWarmup = simulatePower(power = 450, durationMs = 10_000L, startTime = initialTimestamp)
            wPrimeCalculator.calculateWPrimeBalance(450.0, afterWarmup - 2_000L)
            testDispatcher.scheduler.advanceUntilIdle()
            val afterRecovery = simulatePower(power = 100, durationMs = 16_000L, startTime = afterWarmup)

            simulatePower(power = 450, durationMs = 5_000L, startTime = afterRecovery)

            assertTrue(wPrimeCalculator.getLastMatchDepletionDuration() >= 0L, "Last match duration must never be negative")
            assertTrue(wPrimeCalculator.getLastMatchJoulesDepleted() >= 0L, "Last match joules must never be negative")
            assertTrue(wPrimeCalculator.getMatches() in 0..1, "Non-monotonic timestamp should not fabricate extra matches")
        }

        @Test
        fun duplicateTimestampDoesNotCorruptEffortState() = runTest(testDispatcher) {
            val initialTimestamp = 60_000L
            val config = ConfigData(
                criticalPower = 350,
                wPrime = 22_300,
                calculateCp = false,
                matchJoulePercent = 10,
                minMatchDuration = 30,
                maxPower = 1000
            )
            configureAndReset(config, initialTimestamp)

            val t1 = initialTimestamp + 1_000L
            wPrimeCalculator.calculateWPrimeBalance(450.0, t1)
            testDispatcher.scheduler.advanceUntilIdle()
            wPrimeCalculator.calculateWPrimeBalance(450.0, t1)
            testDispatcher.scheduler.advanceUntilIdle()

            val afterEffort = simulatePower(power = 450, durationMs = 29_000L, startTime = t1)
            simulatePower(power = 100, durationMs = 15_000L, startTime = afterEffort)

            assertEquals(1, wPrimeCalculator.getMatches())
            assertEquals(30_000L, wPrimeCalculator.getLastMatchDepletionDuration())
            assertTrue(wPrimeCalculator.getLastMatchJoulesDepleted() in 2_000L..5_000L)
        }

        @Test
        fun lastMatchValuesRemainStableAfterNonQualifyingEffort() = runTest(testDispatcher) {
            val initialTimestamp = 70_000L
            val config = ConfigData(
                criticalPower = 350,
                wPrime = 22_300,
                calculateCp = false,
                matchJoulePercent = 10,
                minMatchDuration = 30,
                maxPower = 1000
            )
            configureAndReset(config, initialTimestamp)

            val afterQualified = simulateSequence(
                steps = listOf(
                    Pair(100, 5_000L),
                    Pair(450, 30_000L),
                    Pair(100, 16_000L)
                ),
                startTime = initialTimestamp
            )
            val stableDuration = wPrimeCalculator.getLastMatchDepletionDuration()
            val stableJoules = wPrimeCalculator.getLastMatchJoulesDepleted()

            simulateSequence(
                steps = listOf(
                    Pair(100, 900L),
                    Pair(800, 5_000L),
                    Pair(100, 16_000L)
                ),
                startTime = afterQualified
            )

            assertEquals(1, wPrimeCalculator.getMatches(), "Non-qualifying effort should not increment match count")
            assertEquals(stableDuration, wPrimeCalculator.getLastMatchDepletionDuration(), "Last match duration should remain unchanged")
            assertEquals(stableJoules, wPrimeCalculator.getLastMatchJoulesDepleted(), "Last match joules should remain unchanged")
        }

        @Test
        fun lastMatchValuesResetOnRideReset() = runTest(testDispatcher) {
            val initialTimestamp = 80_000L
            val config = ConfigData(
                criticalPower = 350,
                wPrime = 22_300,
                calculateCp = false,
                matchJoulePercent = 10,
                minMatchDuration = 30,
                maxPower = 1000
            )
            configureAndReset(config, initialTimestamp)

            val afterQualified = simulateSequence(
                steps = listOf(
                    Pair(100, 5_000L),
                    Pair(450, 30_000L),
                    Pair(100, 16_000L)
                ),
                startTime = initialTimestamp
            )
            assertEquals(1, wPrimeCalculator.getMatches())
            assertTrue(wPrimeCalculator.getLastMatchDepletionDuration() > 0L)
            assertTrue(wPrimeCalculator.getLastMatchJoulesDepleted() > 0L)

            wPrimeCalculator.resetRideState(afterQualified + 10_000L)
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(0, wPrimeCalculator.getMatches())
            assertEquals(0L, wPrimeCalculator.getLastMatchDepletionDuration())
            assertEquals(0L, wPrimeCalculator.getLastMatchJoulesDepleted())
        }

        @Test
        fun matchAccountingStableWhenCalculateCpEnabled() = runTest(testDispatcher) {
            val initialTimestamp = 90_000L
            val config = ConfigData(
                criticalPower = 300,
                wPrime = 20_000,
                calculateCp = true,
                matchJoulePercent = 10,
                minMatchDuration = 30,
                maxPower = 1000
            )
            configureAndReset(config, initialTimestamp)

            simulateSequence(
                steps = listOf(
                    Pair(100, 60_000L),
                    Pair(450, 35_000L),
                    Pair(100, 16_000L),
                    Pair(450, 35_000L),
                    Pair(100, 16_000L),
                ),
                startTime = initialTimestamp
            )

            assertTrue(wPrimeCalculator.getMatches() in 1..2)
            assertTrue(
                wPrimeCalculator.getLastMatchDepletionDuration() in 30_000L..60_000L,
                "Last match duration should remain in a realistic range when estimated CP is enabled"
            )
            assertTrue(
                wPrimeCalculator.getLastMatchJoulesDepleted() in 1_000L..20_000L,
                "Last match joules should remain bounded when estimated CP is enabled"
            )
        }

        @Test
        fun programmaticRepro_largePowerTimestampGapDoesNotCreateOutlierLastMatchValues() = runTest(testDispatcher) {
            val initialTimestamp = 100_000L
            val config = ConfigData(
                criticalPower = 300,
                wPrime = 20_000,
                calculateCp = false,
                matchJoulePercent = 5,
                minMatchDuration = 30,
                maxPower = 1000
            )
            configureAndReset(config, initialTimestamp)

            val afterWarmup = simulatePower(power = 100, durationMs = 5_000L, startTime = initialTimestamp)
            val startEffort = simulatePower(power = 500, durationMs = 1_000L, startTime = afterWarmup)

            // A realistic reproduction surrogate for delayed/missed device updates while rider is still above threshold.
            val delayedHighPowerTimestamp = startEffort + 15 * 60 * 1000L
            wPrimeCalculator.calculateWPrimeBalance(500.0, delayedHighPowerTimestamp)
            testDispatcher.scheduler.advanceUntilIdle()
            wPrimeCalculator.calculateWPrimeBalance(500.0, delayedHighPowerTimestamp + 1_000L)
            testDispatcher.scheduler.advanceUntilIdle()

            simulatePower(power = 100, durationMs = 16_000L, startTime = delayedHighPowerTimestamp + 1_000L)

            assertEquals(0, wPrimeCalculator.getMatches(), "A delayed timestamp alone should not synthesize a full match effort")
            assertEquals(
                0L,
                wPrimeCalculator.getLastMatchDepletionDuration(),
                "A delayed high-power sample must not create an outlier last match duration"
            )
            assertTrue(
                wPrimeCalculator.getLastMatchJoulesDepleted() == 0L,
                "A delayed high-power sample must not create an outlier last match joule depletion"
            )
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

    @Nested
    @DisplayName("Coverage edge tests")
    inner class CoverageEdgeTests {
        @Test
        fun `calculateTimeToExhaust returns zero when power below cp`() = runTest(testDispatcher) {
            val initialTimestamp = 100_000L
            val config = ConfigData(criticalPower = 300, wPrime = 20000, calculateCp = false, maxPower = 1000)
            configFlow.value = config
            testDispatcher.scheduler.advanceUntilIdle()
            wPrimeCalculator.resetRideState(initialTimestamp)
            testDispatcher.scheduler.advanceUntilIdle()

            val result = wPrimeCalculator.calculateTimeToExhaust(250)
            assertEquals(0, result)
        }

        @Test
        fun `calculateTimeToExhaust returns zero when wPrime balance is depleted`() = runTest(testDispatcher) {
            val initialTimestamp = 100_500L
            val config = ConfigData(criticalPower = 300, wPrime = 20000, calculateCp = false, maxPower = 1000)
            configFlow.value = config
            testDispatcher.scheduler.advanceUntilIdle()
            wPrimeCalculator.resetRideState(initialTimestamp)
            testDispatcher.scheduler.advanceUntilIdle()

            setPrivateField("wPrimeBalance", -10.0)

            val result = wPrimeCalculator.calculateTimeToExhaust(400)
            assertEquals(0, result)
        }

        @Test
        fun `calculateMpa returns zero when max power is not above cp`() = runTest(testDispatcher) {
            val initialTimestamp = 101_000L
            val config = ConfigData(criticalPower = 300, wPrime = 20000, calculateCp = false, maxPower = 250)
            configFlow.value = config
            testDispatcher.scheduler.advanceUntilIdle()
            wPrimeCalculator.resetRideState(initialTimestamp)
            testDispatcher.scheduler.advanceUntilIdle()

            wPrimeCalculator.calculateMpa()
            assertEquals(0, wPrimeCalculator.getMpa())
        }

        @Test
        fun `private two-parameter algorithm methods cover else branches`() = runTest(testDispatcher) {
            val initialTimestamp = 102_000L
            wPrimeCalculator.resetRideState(initialTimestamp)
            testDispatcher.scheduler.advanceUntilIdle()

            setPrivateField("eCP", 321.0)
            setPrivateField("wPrimeUsr", 20000.0)

            val cpEstimate = invokePrivateMethod("getCpFromTwoParameterAlgorithm", 100.0, 10.0, 20000.0) as Double
            assertEquals(321.0, cpEstimate)

            val wPrimeEstimate = invokePrivateMethod("getWPrimeFromTwoParameterAlgorithm", 250.0, 1200.0, 300.0) as Double
            assertEquals(20000.0, wPrimeEstimate)
        }

        @Test
        fun `tauWPrimeBalance covers non-dynamic branch`() = runTest(testDispatcher) {
            val initialTimestamp = 103_000L
            wPrimeCalculator.resetRideState(initialTimestamp)
            testDispatcher.scheduler.advanceUntilIdle()

            setPrivateField("currentWPrimeUsr", 20000.0)
            invokePrivateMethod("tauWPrimeBalance", 250.0, false)

            val currentTauField = WPrimeCalculator::class.java.getDeclaredField("currentTau")
            currentTauField.isAccessible = true
            val currentTau = currentTauField.get(wPrimeCalculator) as Double
            assertEquals(80.0, currentTau)
        }

        @Test
        fun `average power helper methods cover overflow else branches`() = runTest(testDispatcher) {
            val initialTimestamp = 104_000L
            wPrimeCalculator.resetRideState(initialTimestamp)
            testDispatcher.scheduler.advanceUntilIdle()

            setPrivateField("countPowerBelowCP", Long.MAX_VALUE)
            setPrivateField("sumPowerBelowCP", 100.0)
            setPrivateField("countPowerAboveCP", Long.MAX_VALUE)
            setPrivateField("sumPowerAboveCP", 100.0)

            invokePrivateMethod("calculateAveragePowerBelowCP", 250.0)
            invokePrivateMethod("calculateAveragePowerAboveCP", 250.0)

            val avgBelowField = WPrimeCalculator::class.java.getDeclaredField("averagePowerBelowCP")
            avgBelowField.isAccessible = true
            val avgAboveField = WPrimeCalculator::class.java.getDeclaredField("avPower")
            avgAboveField.isAccessible = true

            assertEquals(0.0, avgBelowField.get(wPrimeCalculator))
            assertEquals(0.0, avgAboveField.get(wPrimeCalculator))
        }
    }
}
