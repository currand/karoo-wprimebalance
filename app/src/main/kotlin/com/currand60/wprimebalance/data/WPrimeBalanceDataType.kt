package com.currand60.wprimebalance.data

import android.content.Context
import androidx.compose.ui.unit.DpSize
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import com.currand60.wprimebalance.extension.streamDataFlow
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
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

@OptIn(ExperimentalGlanceRemoteViewsApi::class)
class WPrimeBalanceDataType(
    private val karooSystem: KarooSystemService,
    extension: String,
) : DataTypeImpl(extension, "wprimebalance") {
    private val glance = GlanceRemoteViews()
    override fun startStream(emitter: Emitter<StreamState>) {
        Timber.d("start speed stream")
        val job = CoroutineScope(Dispatchers.IO).launch {
            karooSystem.streamDataFlow(DataType.Type.SPEED).collect {
                when (it) {
                    is StreamState.Streaming -> {
                        // Transform speed data point into
                        emitter.onNext(
                            it.copy(
                                dataPoint = it.dataPoint.copy(
                                    dataTypeId = dataTypeId,
                                    values = mapOf(DataType.Field.SINGLE to it.dataPoint.singleValue!!),
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
            job.cancel()
        }
    }

    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        Timber.d("Starting speed view with $emitter and config $config")
        val configJob = CoroutineScope(Dispatchers.IO).launch {
            // Show numeric speed data numerically
            emitter.onNext(UpdateGraphicConfig(formatDataTypeId = DataType.Type.SPEED))
            // Toggle header config forever
            repeat(Int.MAX_VALUE) {
                emitter.onNext(UpdateGraphicConfig(showHeader = it % 2 == 0))
                delay(2000)
            }
            awaitCancellation()
        }
        val viewJob = CoroutineScope(Dispatchers.IO).launch {
            karooSystem.streamDataFlow(DataType.Type.SPEED).collect {
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
