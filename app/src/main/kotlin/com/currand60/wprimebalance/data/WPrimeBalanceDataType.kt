package com.currand60.wprimebalance.data

import android.content.Context
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import com.currand60.wprimebalance.extensions.streamDataFlow
import com.currand60.wprimebalance.managers.ConfigurationManager
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.StreamState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

private const val LONG_PAUSE_TIMEOUT = 30000


@ExperimentalAtomicApi
@OptIn(ExperimentalGlanceRemoteViewsApi::class)
class WPrimeBalanceDataType(
    private val karooSystem: KarooSystemService,
    private val context: Context,
    extension: String,
) : DataTypeImpl(extension, "wprimebalance") {

    private val configurationManager = ConfigurationManager(context)
    private val calculatorRef = AtomicReference<WPrimeCalculator?>(null)
    private var latestConfig: ConfigData = ConfigData.DEFAULT
    private var lastCp60: Int = 0
    private val dataTypeScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        Timber.d("WPrimeBalanceDataType created")
    }

    private suspend fun getOrCreateCalculator(): WPrimeCalculator {
        var calculator = calculatorRef.load()
        Timber.d("Calculator instance: $calculator")
        val currentTimestamp = System.currentTimeMillis()
        if (calculator == null ||
            currentTimestamp - calculator.getPreviousReadingTime() >= LONG_PAUSE_TIMEOUT) {
            Timber.d("Current timestamps: $currentTimestamp - ${calculator?.getPreviousReadingTime()}")
            Timber.d("Creating a new calculator")
            latestConfig = configurationManager.getConfig()
            Timber.d("Latest config: $latestConfig")
            calculator = WPrimeCalculator(
                // If there was a long pause, use the last CP60 value calculated mid-ride, otherwise use
                // the value from ConfigManager
                initialEstimatedCP = lastCp60.takeIf { it > 0 } ?: latestConfig.criticalPower,
                initialEstimatedWPrimeJoules = latestConfig.wPrime,
                currentTimeMillis = System.currentTimeMillis(),
                useEstimatedCp = latestConfig.calculateCp
            )
            calculatorRef.store(calculator)
        }
        return calculator
    }

    override fun startStream(emitter: Emitter<StreamState>) {
        Timber.d("start W' Balance stream")
        val job = dataTypeScope.launch {
            karooSystem.streamDataFlow(DataType.Type.POWER)
                .catch {
                    Timber.e(it, "Error streaming power")
                    emitter.onError(it)
                }
                .collect {
                when (it) {
                    is StreamState.Streaming -> {
                        val power = it.dataPoint.singleValue?.toInt() ?: 0
                        val calculator = getOrCreateCalculator()
                        Timber.d("Updating W' Prime with wPrimeBal: $power")
                        val wPrimeBal = calculator.calculateWPrimeBalance(power, System.currentTimeMillis())
                        lastCp60 = calculator.getCurrentEstimatedCP()
                        Timber.d("W' Prime: $wPrimeBal, last CP: $lastCp60")
                        emitter.onNext(
                            it.copy(
                                dataPoint = it.dataPoint.copy(
                                    dataTypeId = dataTypeId,
                                    values = mapOf(DataType.Field.SINGLE to wPrimeBal.toDouble()),
                                    ),
                            ),
                        )
                        Timber.d("Power W' Balance: $it")
                    }
                    else -> emitter.onNext(it)
                }
            }
        }
        emitter.setCancellable {
            Timber.d("stop W' Balance stream")
            job.cancel()
        }
    }
}
