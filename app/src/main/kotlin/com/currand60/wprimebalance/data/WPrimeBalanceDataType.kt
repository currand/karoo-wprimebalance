package com.currand60.wprimebalance.data

import com.currand60.wprimebalance.KarooSystemServiceProvider
import io.hammerhead.karooext.extension.DataTypeImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import timber.log.Timber


class WPrimeBalanceDataType(
    private val karooSystem: KarooSystemServiceProvider,
    extension: String,
    private val calculator: WPrimeCalculator
) : DataTypeImpl(extension, TYPE_ID) {

    init {
        Timber.d("WPrimeBalanceDataType created")
    }
    companion object {
        const val TYPE_ID = "wprimebalanceraw"
    }
    private val dataScope = CoroutineScope(Dispatchers.IO + SupervisorJob())


}
