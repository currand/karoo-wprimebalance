package com.currand60.wprimebalance.data

import android.content.Context
import androidx.compose.ui.unit.DpSize
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import com.currand60.wprimebalance.KarooSystemServiceProvider
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UpdateGraphicConfig
import io.hammerhead.karooext.models.ViewConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber

@OptIn(ExperimentalGlanceRemoteViewsApi::class)
class LastMatchJoules(
    private val karooSystem: KarooSystemServiceProvider,
    extension: String,
    private val calculator: WPrimeCalculator
) : DataTypeImpl(extension, TYPE_ID) {

    init {
        Timber.d("LastMatchJoules created")
    }

    companion object {
        const val TYPE_ID = "lastmatchjoules"
    }

    private val dataScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun startStream(emitter: Emitter<StreamState>) {
        val job = dataScope.launch {
            val wPrimeFlow = karooSystem.streamDataFlow(
                DataType.dataTypeId(extension, WPrimeBalanceDataType.TYPE_ID)
            )
            wPrimeFlow
                .map { streamState ->
                when (streamState) {
                    is StreamState.Streaming -> {
                        val lastMatchJoules = calculator.getLastMatchJoulesDepleted()
                        StreamState.Streaming(
                            DataPoint(
                                dataTypeId,
                                values = mapOf(DataType.Field.SINGLE to lastMatchJoules.toDouble()),
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
}
