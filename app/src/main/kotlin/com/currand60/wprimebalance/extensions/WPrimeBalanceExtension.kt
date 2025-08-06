package com.currand60.wprimebalance.extensions

import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import com.currand60.wprimebalance.KarooSystemServiceProvider
import com.currand60.wprimebalance.data.WPrimeBalanceDataType
import com.currand60.wprimebalance.data.WPrimeBalancePercentDataType
import com.currand60.wprimebalance.data.WPrimeBalanceTimeToExhaustDataType
import com.currand60.wprimebalance.data.WPrimeCalculator
import com.currand60.wprimebalance.data.WPrimeDataSource
import io.hammerhead.karooext.extension.KarooExtension
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.Device
import io.hammerhead.karooext.models.DeviceEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import timber.log.Timber
import kotlin.concurrent.atomics.ExperimentalAtomicApi


@OptIn(ExperimentalGlanceRemoteViewsApi::class, ExperimentalAtomicApi::class)
class WPrimeBalanceExtension : KarooExtension("wprimebalance", "0.0.3") {

    private val karooSystem: KarooSystemServiceProvider by inject()
    private val wPrimeCalculator: WPrimeCalculator by inject()
    private val wPrimeDataSource: WPrimeDataSource by lazy { WPrimeDataSource(karooSystem, extension, wPrimeCalculator) }

    init {
        Timber.d("WPrimeBalanceExtension created")
    }

    override val types by lazy {
        listOf(
            WPrimeBalanceDataType(extension),
            WPrimeBalancePercentDataType(karooSystem, extension, wPrimeCalculator),
            WPrimeBalanceTimeToExhaustDataType(karooSystem, extension, wPrimeCalculator)
        )
    }

    override fun startScan(emitter: Emitter<Device>) {
        Timber.d("WPrimeBalance Scan Started")
        val job = CoroutineScope(Dispatchers.IO).launch {
            emitter.onNext(wPrimeDataSource.source)
        }
        emitter.setCancellable {
            job.cancel()
        }
    }

    override fun connectDevice(uid: String, emitter: Emitter<DeviceEvent>) {
        Timber.d("Connect to $uid")
        if (uid == wPrimeDataSource.source.uid) {
            wPrimeDataSource.connect(emitter)
        } else {
            Timber.w("Attempted to connect to unknown device UID: $uid")
        }

    }
}