package com.currand60.wprimebalance.data

import android.content.Context
import com.currand60.wprimebalance.KarooSystemServiceProvider
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UpdateNumericConfig
import io.hammerhead.karooext.models.ViewConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber

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
            karooSystem.streamDataFlow(DataType.Type.SMOOTHED_10S_AVERAGE_POWER)
                .map { data ->
                if (data is StreamState.Streaming) {
                    val avgPower = data.dataPoint.singleValue?.toInt() ?: 0
                    val timeToExhaust = calculator.calculateTimeToExhaust(avgPower)
                    StreamState.Streaming(
                        DataPoint(
                            dataTypeId,
                            values = mapOf(DataType.Field.SINGLE to timeToExhaust.toDouble() * 1000.0),
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
    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        emitter.onNext(UpdateNumericConfig(formatDataTypeId = DataType.Type.ELAPSED_TIME))
    }
}
