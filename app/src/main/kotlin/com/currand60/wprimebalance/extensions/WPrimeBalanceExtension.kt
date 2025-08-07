package com.currand60.wprimebalance.extensions

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
import io.hammerhead.karooext.models.RideState
import io.hammerhead.karooext.models.SystemNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import timber.log.Timber
import kotlin.concurrent.atomics.ExperimentalAtomicApi


@OptIn( ExperimentalAtomicApi::class)
class WPrimeBalanceExtension : KarooExtension("wprimebalance", "0.1.0") {

    private val karooSystem: KarooSystemServiceProvider by inject()
    private val wPrimeCalculator: WPrimeCalculator by inject()
    private val wPrimeDataSource: WPrimeDataSource by lazy { WPrimeDataSource(karooSystem, extension, wPrimeCalculator) }
    private var job: Job? = null
    var previousRideState: RideState = RideState.Idle

    private val dataScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        Timber.d("WPrimeBalanceExtension created")
    }

    override val types by lazy {
        listOf(
            WPrimeBalanceDataType(karooSystem, extension, wPrimeCalculator),
            WPrimeBalancePercentDataType(karooSystem, extension, wPrimeCalculator),
            WPrimeBalanceTimeToExhaustDataType(karooSystem, extension, wPrimeCalculator)
        )
    }

    override fun startScan(emitter: Emitter<Device>) {
        Timber.d("WPrimeBalance Scan Started")
        val scanJob = dataScope.launch {
            emitter.onNext(wPrimeDataSource.source)
        }
        emitter.setCancellable {
            scanJob.cancel()
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

    override fun onCreate() {
        super.onCreate()
        job = dataScope.launch {
            karooSystem.streamRideState().collect { rideState ->
                if (previousRideState != rideState && rideState == RideState.Idle) {
                    if (wPrimeCalculator.getOriginalWPrimeCapacity() < wPrimeCalculator.getCurrentWPrimeJoules()) {
                        karooSystem.karooSystemService.dispatch(
                            SystemNotification(
                            "new-wprime-capacity",
                            "New W' Capacity",
                                subText = "During your ride, a new W' Capacity was calculated to be: ${wPrimeCalculator.getCurrentWPrimeJoules()}J"
                            )
                        )
                    }
                    if (wPrimeCalculator.getOriginalCP() < wPrimeCalculator.getCurrentCP()) {
                        karooSystem.karooSystemService.dispatch(
                            SystemNotification(
                            "new-CP60",
                            "New CP60",
                            subText = "During your ride, a new CP60 was calculated to be: ${wPrimeCalculator.getCurrentCP()}W"
                            )
                        )
                    }
                }
                previousRideState = rideState
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        job?.cancel()
        karooSystem.karooSystemService.disconnect()
        Timber.d("WPrimeBalanceExtension destroyed")
    }
}