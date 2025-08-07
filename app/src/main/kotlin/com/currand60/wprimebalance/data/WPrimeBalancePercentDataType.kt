package com.currand60.wprimebalance.data

import com.currand60.wprimebalance.KarooSystemServiceProvider
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.StreamState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber

class WPrimeBalancePercentDataType(
    private val karooSystem: KarooSystemServiceProvider,
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

    override fun startStream(emitter: Emitter<StreamState>) {
        val job = dataScope.launch {
            val wPrimeFlow = karooSystem.streamDataFlow(
                DataType.dataTypeId(extension, WPrimeBalanceDataType.TYPE_ID)
            )
            wPrimeFlow
                .map { streamState ->
                when (streamState) {
                    is StreamState.Streaming -> {
                        val currentWPrimeJoules = calculator.getCurrentWPrimeJoules()
                        val wPrimeBal = if (currentWPrimeJoules > 0) {
                            (streamState.dataPoint.singleValue ?: 0.0) / currentWPrimeJoules * 100.0
                        } else {
                            Timber.w("WPrimeCalculator's current W' Joules is 0, cannot calculate percentage.")
                            0.0 // Default to 0% or some other sensible value
                        }
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
}
