package com.currand60.wprimebalance.data

import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.StreamState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import timber.log.Timber

@OptIn(ExperimentalGlanceRemoteViewsApi::class)
class CurrentMpa(
    extension: String,
    private val calculator: WPrimeCalculator
) : DataTypeImpl(extension, TYPE_ID) {

    init {
        Timber.d("CurrentMpa created")
    }

    companion object {
        const val TYPE_ID = "currentmaxpoweravailable"
    }

    private val dataScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private fun makeFlow(): Flow<StreamState> = flow {
        while (true) {
            val value = calculator.getMpa().toDouble()
            emit(StreamState.Streaming(
                DataPoint(
                    dataTypeId,
                    mapOf(DataType.Field.SINGLE to value),
                    extension
                )
            ))
            delay(1000)
        }
    }.flowOn(Dispatchers.IO)
    
    override fun startStream(emitter: Emitter<StreamState>) {
        val job = dataScope.launch {
                makeFlow()
                    .map { streamState ->
                    if (streamState is StreamState.Streaming) {
                        StreamState.Streaming(
                            DataPoint(
                                dataTypeId,
                                values = mapOf(DataType.Field.SINGLE to streamState.dataPoint.singleValue!!),
                            ),
                        )
                    } else {
                        streamState
                    }
                }
                .onEach { Timber.d("CurrentMaximumPowerAvailable: $it") }
                .collect { emitter.onNext(it) }
        }
        emitter.setCancellable {
            job.cancel()
        }
    }
}
