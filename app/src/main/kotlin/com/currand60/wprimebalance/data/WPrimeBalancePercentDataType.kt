package com.currand60.wprimebalance.data

import android.content.Context
import androidx.compose.ui.unit.DpSize
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import com.currand60.wprimebalance.BuildConfig
import com.currand60.wprimebalance.extensions.streamDataFlow
import com.currand60.wprimebalance.views.WPrimeBalanceGauge
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.ViewConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class, ExperimentalGlanceRemoteViewsApi::class)
class WPrimeBalancePercentDataType(
    private val karooSystem: KarooSystemService,
    extension: String,
    private val calculator: WPrimeCalculator
) : DataTypeImpl(extension, TYPE_ID) {

    init {
        Timber.d("WPrimeBalancePercentDataType created")
    }

    companion object {
        const val TYPE_ID = "wprimepercent"
    }

    private val dataScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val viewScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val glanceRemoteViews = GlanceRemoteViews()

    override fun startStream(emitter: Emitter<StreamState>) {
        val job = dataScope.launch {
            val wPrimeFlow = karooSystem.streamDataFlow(
                DataType.dataTypeId(extension, WPrimeBalanceDataType.TYPE_ID)
            )
            wPrimeFlow
                .map { streamState ->
                when (streamState) {
                    is StreamState.Streaming -> {
                        val wPrimeBal = (streamState.dataPoint.singleValue ?: 0.0) / calculator.getCurrentWPrimeJoules() * 100.0
                        StreamState.Streaming(
                            DataPoint(
                                dataTypeId,
                                values = mapOf(DataType.Field.SINGLE to wPrimeBal),
                            ),
                        )
                    } else -> {
                        streamState
                    }
                }
            }.collect { emitter.onNext(it) }
        }
        emitter.setCancellable {
            job.cancel()
        }
    }

    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter): Unit {
        if (BuildConfig.DEBUG) {
            Timber.d("Starting W' Balance Percentage view with $emitter and config $config")
        }

        val viewJob = viewScope.launch {
            val wPrimeRawFlow = karooSystem.streamDataFlow(DataType.dataTypeId(extension, WPrimeBalanceDataType.TYPE_ID))

            wPrimeRawFlow.collect { streamState ->
                val currentWPrimeBalance = (streamState as? StreamState.Streaming)?.dataPoint?.singleValue ?: 0.0
                val totalWPrimeCapacity = calculator.getCurrentWPrimeJoules().toDouble()

                val wPrimePercentage: Double = if (totalWPrimeCapacity > 0) {
                    (currentWPrimeBalance / totalWPrimeCapacity) * 100.0
                } else {
                    0.0
                }

                if (BuildConfig.DEBUG) {
                    Timber.d("Updating W' Balance Percentage view with $wPrimePercentage %")
                }

                val result = glanceRemoteViews.compose(context, DpSize.Unspecified) {
                    WPrimeBalanceGauge(wPrimePercentage, config.alignment)
                }
                emitter.updateView(result.remoteViews)
            }
            awaitCancellation()
        }

        emitter.setCancellable {
            if (BuildConfig.DEBUG) {
                Timber.d("Stopping W' Balance Percentage view with $emitter")
            }
            viewJob.cancel()
        }
    }

}
