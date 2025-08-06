package com.currand60.wprimebalance.data

import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import com.currand60.wprimebalance.KarooSystemServiceProvider
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.StreamState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class, ExperimentalGlanceRemoteViewsApi::class)
class WPrimeBalanceTimeToExhaustDataType(
    private val karooSystem: KarooSystemServiceProvider,
    extension: String,
    private val calculator: WPrimeCalculator
) : DataTypeImpl(extension, TYPE_ID) {

    init {
        Timber.d("WPrimeBalanceTimeToExhaustDataType created")
    }

    companion object {
        const val TYPE_ID = "wprimetimetoexhaust"
    }

    private val dataScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun startStream(emitter: Emitter<StreamState>) {
//        val dataTypeId = DataType.dataTypeId(extension, TYPE_ID)
        val job = dataScope.launch {
            karooSystem.streamDataFlow(DataType.Type.SMOOTHED_30S_AVERAGE_POWER)
                .map { data ->
                if (data is StreamState.Streaming) {
                    val timeToExhaust = calculator.calculateTimeToExhaust(data.dataPoint.singleValue!!.toInt())
                    StreamState.Streaming(
                        DataPoint(
                            dataTypeId,
                            values = mapOf(DataType.Field.SINGLE to timeToExhaust.toDouble()),
                        ),
                    )
                } else {
                    StreamState.NotAvailable
                }
            }.collect { emitter.onNext(it) }
        }
        emitter.setCancellable {
            job.cancel()
        }
    }
}
