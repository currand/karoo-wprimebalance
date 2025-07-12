package com.currand60.wprimebalance.extension

import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.KarooExtension
import javax.inject.Inject
import timber.log.Timber


class WPrimeBalanceExtension : KarooExtension("wprimebalance", "0.1") {

    @Inject
    lateinit var karooSystem: KarooSystemService



}