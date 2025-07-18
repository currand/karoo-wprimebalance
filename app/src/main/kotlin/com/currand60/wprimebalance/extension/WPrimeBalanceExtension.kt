package com.currand60.wprimebalance.extension

import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import com.currand60.wprimebalance.data.WPrimeBalanceDataType
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.KarooExtension
import timber.log.Timber
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalGlanceRemoteViewsApi::class)

class WPrimeBalanceExtension : KarooExtension("wprimebalance", "0.2") {

    lateinit var karooSystem: KarooSystemService

    init {
        Timber.d("WPrimeBalanceExtension created")
    }

    companion object {
        lateinit var instance: WPrimeBalanceExtension
            private set
    }

    @OptIn(ExperimentalAtomicApi::class)
    override val types by lazy {
        listOf(
            WPrimeBalanceDataType(karooSystem, applicationContext,"wprimebalance")
        )
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        karooSystem = KarooSystemService(applicationContext)

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