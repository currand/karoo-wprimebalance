package com.currand60.wprimebalance.data

import com.currand60.wprimebalance.extensions.streamDataFlow
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.ConnectionStatus
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.Device
import io.hammerhead.karooext.models.DeviceEvent
import io.hammerhead.karooext.models.OnConnectionStatus
import io.hammerhead.karooext.models.OnDataPoint
import io.hammerhead.karooext.models.StreamState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.concurrent.atomics.ExperimentalAtomicApi

sealed interface WPrimeDevice {
    val source: Device
    fun connect(emitter: Emitter<DeviceEvent>)
}



@ExperimentalAtomicApi
class WPrimeDataSource(
    private val karooSystem: KarooSystemService,
    extension: String,
    private val calculator: WPrimeCalculator
) : WPrimeDevice {

    private val dataTypeScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override val source by lazy {
        Device(
            extension,
            PREFIX,
            listOf(DataType.dataTypeId(extension, WPrimeBalanceDataType.TYPE_ID)),
            "W' Balance",
        )
    }

    init {
        Timber.d("WPrimeDataSource Created")
    }

    override fun connect(emitter: Emitter<DeviceEvent>) {
        val job = dataTypeScope.launch {
            emitter.onNext(OnConnectionStatus(ConnectionStatus.CONNECTED))
            Timber.d("start W' Balance stream")

            calculator.resetRideState(System.currentTimeMillis())

            karooSystem.streamDataFlow(DataType.Type.POWER).collect {
//                    val power = 400
//                    delay(1000)
                when (it) {
                    is StreamState.Streaming -> {
                        val power = it.dataPoint.singleValue?.toInt() ?: 0
                        val wPrimeBal = calculator.calculateWPrimeBalance(power,
                            System.currentTimeMillis())
                        Timber.d("Updating W' Prime with wPrimeBal: $wPrimeBal")
                        emitter.onNext(
                            OnDataPoint(
                                DataPoint(
                                    dataTypeId = source.dataTypes.first(),
                                    values = mapOf(DataType.Field.SINGLE to wPrimeBal.toDouble()),
                                    sourceId = source.uid
                                )
                            )
                        )
                    } else -> {
                    StreamState.NotAvailable
                }
            }
        }
        awaitCancellation()
        }
        emitter.setCancellable {
            job.cancel()
        }
    }
    companion object {
        const val PREFIX = "wprimedevice"
    }
}