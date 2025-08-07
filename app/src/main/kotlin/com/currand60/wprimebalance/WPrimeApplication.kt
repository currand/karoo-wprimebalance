package com.currand60.wprimebalance

import android.app.Application
import android.content.Context
import com.currand60.wprimebalance.data.WPrimeCalculator
import com.currand60.wprimebalance.managers.ConfigurationManager
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.OnStreamState
import io.hammerhead.karooext.models.RideState
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UserProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import kotlin.concurrent.atomics.ExperimentalAtomicApi

class KarooSystemServiceProvider(private val context: Context) {
    val karooSystemService: KarooSystemService = KarooSystemService(context)

    private val _connectionState = MutableStateFlow(false)

    init {
        karooSystemService.connect { connected ->
            CoroutineScope(Dispatchers.IO).launch {
                _connectionState.emit(connected)
            }
        }
    }

    fun streamDataFlow(dataTypeId: String): Flow<StreamState> {
        return callbackFlow {
            val listenerId = karooSystemService.addConsumer(OnStreamState.StartStreaming(dataTypeId)) { event: OnStreamState ->
                trySendBlocking(event.state)
            }
            awaitClose {
                karooSystemService.removeConsumer(listenerId)
            }
        }
    }

    fun streamUserProfile(): Flow<UserProfile> {
        return callbackFlow {
            val listenerId = karooSystemService.addConsumer { userProfile: UserProfile ->
                trySendBlocking(userProfile)
            }
            awaitClose {
                karooSystemService.removeConsumer(listenerId)
            }
        }
    }

    fun streamRideState(): Flow<RideState> {
        return callbackFlow {
            val listenerId = karooSystemService.addConsumer() { rideState: RideState ->
                trySendBlocking(rideState)
            }
            awaitClose {
                karooSystemService.removeConsumer(listenerId)
            }
        }
    }
}

@OptIn(ExperimentalAtomicApi::class)
val appModule = module {
    singleOf(::WPrimeCalculator)
    singleOf(::ConfigurationManager)
    singleOf(::KarooSystemServiceProvider)
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