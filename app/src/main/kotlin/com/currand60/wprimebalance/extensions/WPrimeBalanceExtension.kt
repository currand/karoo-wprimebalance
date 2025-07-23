package com.currand60.wprimebalance.extensions

import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import com.currand60.wprimebalance.data.WPrimeBalanceDataType
import com.currand60.wprimebalance.data.WPrimeBalancePercentDataType
import com.currand60.wprimebalance.data.WPrimeCalculatorProvider
import com.currand60.wprimebalance.data.WPrimeDataSource
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.KarooExtension
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.Device
import io.hammerhead.karooext.models.DeviceEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.concurrent.atomics.ExperimentalAtomicApi


@OptIn(ExperimentalGlanceRemoteViewsApi::class, ExperimentalAtomicApi::class)
class WPrimeBalanceExtension : KarooExtension("wprimebalance", "0.0.2") {

    lateinit var karooSystem: KarooSystemService

    init {
        Timber.d("WPrimeBalanceExtension created")
    }

    override val types by lazy {
        listOf(
            WPrimeBalanceDataType(extension),
            WPrimeBalancePercentDataType(karooSystem, extension, WPrimeCalculatorProvider.calculator)
        )
    }

    companion object {
        private var instance: WPrimeBalanceExtension? = null
    }

    override fun startScan(emitter: Emitter<Device>) {
        Timber.d("WPrimeBalance Scan Started")
        val job = CoroutineScope(Dispatchers.IO).launch {
            val dataSource = WPrimeDataSource(karooSystem, applicationContext, extension,
                        WPrimeCalculatorProvider)
                    emitter.onNext(dataSource.source)
        }
        emitter.setCancellable {
            job.cancel()
        }
    }

    override fun connectDevice(uid: String, emitter: Emitter<DeviceEvent>) {
        Timber.d("Connect to $uid")
        WPrimeDataSource(karooSystem, applicationContext, extension, WPrimeCalculatorProvider)
            .connect(emitter)
    }


    override fun onCreate() {
        super.onCreate()
        karooSystem = KarooSystemService(applicationContext)
        instance = this
        WPrimeCalculatorProvider.initialize(applicationContext)

        Timber.d("Service WPrimeBalance created")
        karooSystem.connect { connected ->
            if (connected) {
                Timber.d("Connected to Karoo system")
            }
        }
    }

    override fun onDestroy() {
        karooSystem.disconnect()
        super.onDestroy()
    }
}