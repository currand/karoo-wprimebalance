package com.currand60.wprimebalance

import android.app.Application
import com.currand60.wprimebalance.data.WPrimeCalculator
import com.currand60.wprimebalance.managers.ConfigurationManager
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module


val appModule = module {
    singleOf(::WPrimeCalculator)
    singleOf(::ConfigurationManager)
}

class WPrimeApplication: Application() {

    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidLogger()
            androidContext(this@WPrimeApplication)
            modules(appModule)
        }
    }
}