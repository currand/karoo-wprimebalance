package com.currand60.wprimebalance.data

import android.content.Context
import androidx.compose.ui.unit.DpSize
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import com.currand60.wprimebalance.extension.CustomSpeed
import com.currand60.wprimebalance.extension.streamDataFlow
import com.currand60.wprimebalance.managers.ConfigurationManager
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UpdateGraphicConfig
import io.hammerhead.karooext.models.ViewConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.collectLatest
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
    private val glance = GlanceRemoteViews()
    private val configurationManager = ConfigurationManager(context)
    private val calculatorRef = AtomicReference<WPrimeCalculator?>(null)
    private var dataStreamJob: Job? = null
    private var configListenerJob: Job? = null
    private var latestConfig: ConfigData = ConfigData.DEFAULT

    private suspend fun getOrCreateCalculator(currentConfig: ConfigData): WPrimeCalculator {
        var calculator = calculatorRef.load()
        latestConfig = configurationManager.getConfig()
        val currentTimestamp = System.currentTimeMillis()
        if (calculator == null ||
            latestConfig != currentConfig ||
            currentTimestamp - calculator.getPreviousReadingTime() <= LONG_PAUSE_TIMEOUT) {
            calculator = WPrimeCalculator(
                initialCp60 = latestConfig.criticalPower,
                initialWPrimeUser = latestConfig.wPrime
            )
            calculatorRef.store(calculator)
        }
        return calculator
    }
    override fun startStream(emitter: Emitter<StreamState>) {
        Timber.d("start power stream")
        dataStreamJob?.cancel()
        configListenerJob?.cancel()

        configListenerJob = CoroutineScope(Dispatchers.IO).launch {
            configurationManager.configFlow.collectLatest { newConfig ->
                calculatorRef.store(null)
                latestConfig = newConfig
            }

            dataStreamJob = CoroutineScope(Dispatchers.IO).launch {

                val currentConfig = configurationManager.getConfig()
                val sourceDataType = DataType.Type.POWER
                karooSystem.streamDataFlow(sourceDataType).collectLatest {
                    when (it) {
                        is StreamState.Streaming -> {
                            // Transform speed data point into
                            val dataPoint = it.dataPoint
                            val calculator = getOrCreateCalculator(currentConfig)
                            val wPrimBal = calculator.updateAndGetWPrimeBalance(
                                dataPoint.singleValue!!.toInt(),
                                System.currentTimeMillis()
                            )
                            emitter.onNext(
                                it.copy(
                                    dataPoint = it.dataPoint.copy(
                                        dataTypeId = dataTypeId,
                                        values = mapOf(DataType.Field.SINGLE to wPrimBal.toDouble()),
                                    ),
                                ),
                            )
                        }

                        else -> emitter.onNext(it)
                    }
                }
            }
            emitter.setCancellable {
                Timber.d("stop speed stream")
                dataStreamJob?.cancel()
            }
        }
    }

    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        Timber.d("Starting speed view with $emitter and config $config")
        val dataTypeId = DataType.dataTypeId(extension, "wprimebalance")
        val configJob = CoroutineScope(Dispatchers.IO).launch {
            // Show numeric speed data numerically
            emitter.onNext(UpdateGraphicConfig(formatDataTypeId = dataTypeId))
            awaitCancellation()
        }
        val viewJob = CoroutineScope(Dispatchers.IO).launch {
            karooSystem.streamDataFlow(DataType.Type.POWER).collect {
                val speed = (it as? StreamState.Streaming)?.dataPoint?.singleValue?.toInt() ?: 0
                Timber.d("Updating speed view ($emitter) with $speed")
                val result = glance.compose(context, DpSize.Unspecified) {
                    CustomSpeed(speed, config.alignment)
                }
                emitter.updateView(result.remoteViews)
            }
        }
        emitter.setCancellable {
            Timber.d("Stopping speed view with $emitter")
            configJob.cancel()
            viewJob.cancel()
        }
    }
}
