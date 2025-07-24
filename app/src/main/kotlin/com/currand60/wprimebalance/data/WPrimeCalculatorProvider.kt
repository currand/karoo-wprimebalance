package com.currand60.wprimebalance.data

import android.content.Context
import com.currand60.wprimebalance.managers.ConfigurationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import timber.log.Timber

object WPrimeCalculatorProvider {
    // The single instance of WPrimeCalculator
    val calculator: WPrimeCalculator = WPrimeCalculator()

    // Scope for observing configuration changes
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var isInitialized = false
    private lateinit var configurationManager: ConfigurationManager

    /**
     * Initializes the WPrimeCalculatorProvider and starts observing configuration changes.
     * This method must be called once, typically in the KarooExtension's onCreate.
     * @param context The application context.
     */
    fun initialize(context: Context) {
        if (isInitialized) {
            Timber.w("WPrimeCalculatorProvider already initialized.")
            return
        }
        Timber.d("Initializing WPrimeCalculatorProvider.")
        configurationManager = ConfigurationManager(context) // Initialize config manager

        // Observe configuration changes and reconfigure the calculator
        configurationManager.getConfigFlow() // Assuming getConfig() can be turned into a flow
            .distinctUntilChanged() // Only react when the config truly changes
            .onEach { config ->
                Timber.d("Configuration changed detected: $config. Reconfiguring WPrimeCalculator.")
                calculator.configure(config, System.currentTimeMillis())
            }
            .launchIn(scope) // Launch the collector in the provider's scope

        isInitialized = true
    }
    /**
     * Resets the WPrimeCalculator to its initial configured state.
     * This is useful when a new ride starts or settings are applied.
     */
    suspend fun resetCalculator() {
        if (!isInitialized) {
            Timber.w("WPrimeCalculatorProvider not initialized, cannot reset calculator.")
            return
        }
        val latestConfig = configurationManager.getConfig()
        calculator.configure(latestConfig, System.currentTimeMillis())
        Timber.d("WPrimeCalculator reset to latest config.")
    }
}