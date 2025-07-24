package com.currand60.wprimebalance.data

import io.hammerhead.karooext.extension.DataTypeImpl
import timber.log.Timber


class WPrimeBalanceDataType(
    extension: String,
) : DataTypeImpl(extension, TYPE_ID) {

    init {
        Timber.d("WPrimeBalanceDataType created")
    }
    companion object {
        const val TYPE_ID = "wprimebalanceraw"
    }
}
