package com.currand60.wprimebalance.extension

import com.currand60.wprimebalance.data.WPrimeBalanceDataType
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.KarooExtension
import javax.inject.Inject
import timber.log.Timber


class WPrimeBalanceExtension : KarooExtension("wprimebalance", "0.1") {
    @Inject
    lateinit var karooSystem: KarooSystemService

    companion object {
        lateinit var instance: WPrimeBalanceExtension
            private set
    }

    override val types by lazy {
        listOf(
            WPrimeBalanceDataType(karooSystem, "wprimebalance")
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