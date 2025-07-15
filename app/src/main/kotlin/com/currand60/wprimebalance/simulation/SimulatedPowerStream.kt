package com.currand60.wprimebalance.simulation // Or your preferred package

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import timber.log.Timber
import kotlin.random.Random

/**
 * Simulates a stream of power data.
 *
 * @param scope The CoroutineScope in which the simulation will run.
 * @param minPower The minimum power value to simulate (inclusive).
 * @param maxPower The maximum power value to simulate (inclusive).
 * @param intervalMillis The interval in milliseconds at which new power values are emitted.
 * @param pattern The pattern of power simulation.
 */
class SimulatedPowerStream(
    private val scope: CoroutineScope,
    private val minPower: Int = 50, // Watts
    private val maxPower: Int = 400, // Watts
    private val intervalMillis: Long = 1000, // 1 second
    private val pattern: PowerPattern = PowerPattern.RandomWithinRange
) {

    private var simulationJob: Job? = null
    var isRunning: Boolean = false
        private set

    sealed class PowerPattern {
        object RandomWithinRange : PowerPattern() // Simple random power
        data class Steps(
            val steps: List<StepPower>,
            val repeat: Boolean = true
        ) : PowerPattern() // Sequence of power levels for set durations
        // You can add more patterns: SineWave, Constant, etc.
    }

    data class StepPower(val power: Int, val durationMillis: Long)

    /**
     * The flow that emits simulated power values.
     * Collection of this flow will start the simulation if it's not already running
     * for this specific flow collection.
     * If you need a shared stream that starts once and multiple collectors get the same data,
     * you'd use .shareIn() on this flow externally.
     */
    val powerFlow: Flow<Int> = flow {
        isRunning = true
        Timber.d("SimulatedPowerStream: Starting power simulation with pattern: $pattern")

        try {
            when (pattern) {
                is PowerPattern.RandomWithinRange -> {
                    while (currentCoroutineContext().isActive && isRunning) {
                        val simulatedPower = Random.nextInt(minPower, maxPower + 1)
                        Timber.v("SimulatedPowerStream: Emitting random power: $simulatedPower W")
                        emit(simulatedPower)
                        delay(intervalMillis)
                    }
                }
                is PowerPattern.Steps -> {
                    var currentStepIndex = 0
                    while (currentCoroutineContext().isActive && isRunning) {
                        if (pattern.steps.isEmpty()) {
                            Timber.w("SimulatedPowerStream: Steps pattern has no steps defined. Stopping.")
                            break
                        }
                        val step = pattern.steps[currentStepIndex]
                        Timber.v("SimulatedPowerStream: Emitting step power: ${step.power} W for ${step.durationMillis}ms")
                        emit(step.power)
                        delay(step.durationMillis)

                        currentStepIndex++
                        if (currentStepIndex >= pattern.steps.size) {
                            if (pattern.repeat) {
                                currentStepIndex = 0
                                Timber.d("SimulatedPowerStream: Repeating steps.")
                            } else {
                                Timber.d("SimulatedPowerStream: Finished all steps (no repeat).")
                                break // Stop after all steps if not repeating
                            }
                        }
                    }
                }
            }
        } finally {
            isRunning = false // Ensure isRunning is false when the flow completes or is cancelled
            Timber.d("SimulatedPowerStream: Power simulation stopped/completed.")
        }
    }

    /**
     * Starts the simulation. This is implicitly called when `powerFlow` is collected.
     * This method is more for explicit control if you were managing the job outside
     * of direct flow collection, but typically direct collection of powerFlow is preferred.
     *
     * If you want a single shared simulation, you should create the flow, use .shareIn(),
     * and then collect from the SharedFlow.
     */
    fun start() {
        if (simulationJob?.isActive == true) {
            Timber.d("SimulatedPowerStream: Simulation is already running.")
            return
        }
        // This kind of explicit start/stop is harder to manage with cold flows.
        // The `flow { ... }` builder creates a cold flow, meaning it starts emitting
        // ONLY when collected.
        // For a more controllable "hot" source, you'd use a Channel or a SharedFlow/StateFlow.
        // However, for typical testing, collecting the `powerFlow` is sufficient.
        Timber.w("SimulatedPowerStream: Manual start() is less typical for cold flows. Collection of powerFlow initiates emissions.")
        isRunning = true // This will be set true by the flow{} block itself when collected
    }

    /**
     * Stops the simulation.
     * This is achieved by cancelling the scope from which the collecting coroutine was launched,
     * or by the collecting coroutine itself completing.
     */
    fun stop() {
        Timber.d("SimulatedPowerStream: Requesting simulation stop.")
        isRunning = false // This will signal the loop inside the flow to terminate
        // The actual cancellation of the emission loop happens when the collector's scope is cancelled
        // or the 'isActive' check within the loop fails.
    }
}