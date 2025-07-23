package com.currand60.wprimebalance.data

import android.content.Context
import com.currand60.wprimebalance.extensions.streamDataFlow
import io.hammerhead.karooext.KarooSystemService
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
import kotlin.concurrent.atomics.ExperimentalAtomicApi


@OptIn(ExperimentalAtomicApi::class)
class WPrimeBalancePercentDataType(
    private val karooSystem: KarooSystemService,
    extension: String,
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
            val wPrimeFlow = karooSystem.streamDataFlow("wprimebalance::wprimebalanceraw")
            wPrimeFlow
                .map { streamState ->
                when (streamState) {
                    is StreamState.Streaming -> {
                        val wPrimeBal = streamState.dataPoint.singleValue!! / 16800.0 * 100.0
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
