
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
            ConfigData(criticalPower = 200, wPrime = 10000, calculateCp = false) // Default config
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
        fun `wPrimeBalance is initialized correctly after resetRideState`() = runTest(testDispatcher) {
            // Given
            val initialTimestamp = System.currentTimeMillis()
            val initialConfig = ConfigData(criticalPower = 300, wPrime = 22000, calculateCp = false)
            configFlow.value = initialConfig // Emit new config if different from default
            testDispatcher.scheduler.advanceUntilIdle() // Ensure config collection

            // When
            wPrimeCalculator.resetRideState(initialTimestamp)
            testDispatcher.scheduler.advanceUntilIdle() // Ensure resetRideState coroutine completes

            // Then
            val expectedWPrimeBalance = initialConfig.wPrime.toLong()
            assertEquals(expectedWPrimeBalance, wPrimeCalculator.wPrimeBalance, "wPrimeBalance should be less than 1000J")
        }
    }

    @Nested
    @DisplayName("W' Balance Calculation Tests")
    inner class WPrimeBalanceCalculationTests {

        @Test
        @DisplayName("wPrimeBalance is calculated correctly")
        fun `wPrimeBalance is calculated correctly`() = runTest(testDispatcher) {
            // Given
            val stepLength = 1000L
            val initialTimestamp = System.currentTimeMillis()
            val initialConfig = ConfigData(criticalPower = 300, wPrime = 22300, calculateCp = false)
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


                for (elapsedTime in stepLength until durationMs step stepLength) {
                    currentTime += stepLength
                    wPrimeCalculator.calculateWPrimeBalance(power, currentTime)
                    testDispatcher.scheduler.advanceUntilIdle() // Ensure updateWPrimeBalance coroutine completes
//                    println("Time: $currentTime, wPrimeBalance: ${wPrimeCalculator.wPrimeBalance}")
                }
                println("Step: ${step.first}, ${step.second}, Time: $currentTime, wPrimeBalance: ${wPrimeCalculator.wPrimeBalance}")
            }
            assertTrue(wPrimeCalculator.wPrimeBalance < 1000, "wPrimeBalance should be less than 1000J")
        }

        @Test
        @DisplayName("CP60 is updated when wPrimeBalance becomes negative")
        fun `CP60 is updated when wPrimeBalance becomes negative`() = runTest(testDispatcher) {
            // Given
            val stepLength = 1000L
            val initialTimestamp = System.currentTimeMillis()
            val initialConfig = ConfigData(criticalPower = 200, wPrime = 10000, calculateCp = true)
            configFlow.value = initialConfig // Emit new config if different from default

            // When
            wPrimeCalculator.resetRideState(initialTimestamp)

            val testSteps = listOf(
                Pair(300, 180000),
            )

            for (step in testSteps) {
                val power = step.first
                val durationMs = step.second

                for (elapsedTime in stepLength until durationMs step stepLength) {
                    val currentTime = initialTimestamp + elapsedTime
                    wPrimeCalculator.calculateWPrimeBalance(power, currentTime)
                    testDispatcher.scheduler.advanceUntilIdle() // Ensure updateWPrimeBalance coroutine completes
//                    println("Time: $currentTime, CP60: ${wPrimeCalculator.CP60}, wPrimeBalance: ${wPrimeCalculator.wPrimeBalance}")
                }
            }
            assertTrue(wPrimeCalculator.wPrimeBalance < 1000, "wPrimeBalance should be less than 1000J")
        }

    }

    @Test
    @DisplayName("wPrimeBalance time to exhaust is calculated correctly")
    fun `wPrimeBalance time to exhaust is calculated correctly`() = runTest(testDispatcher) {
        val stepLength = 1000L
        val initialTimestamp = System.currentTimeMillis()
        val initialConfig = ConfigData(criticalPower = 200, wPrime = 12000, calculateCp = false)
        configFlow.value = initialConfig // Emit new config if different from default

        // When
        wPrimeCalculator.resetRideState(initialTimestamp)
        val testSteps = listOf(
            Pair(300, 60000),
        )

        for (step in testSteps) {
            val power = step.first
            val durationMs = step.second

            for (elapsedTime in stepLength until durationMs step stepLength) {
                val currentTime = initialTimestamp + elapsedTime
                wPrimeCalculator.calculateWPrimeBalance(power, currentTime)
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

    // --- Helper Methods (Optional) ---
    // private fun createDefaultConfig(): ConfigData {
    //     return ConfigData(criticalPower = 250, wPrime = 20000, calculateCp = false)
    // }
}
