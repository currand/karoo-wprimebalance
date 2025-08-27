package com.currand60.wprimebalance.data

import android.content.Context
import androidx.compose.ui.unit.DpSize
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import com.currand60.wprimebalance.KarooSystemServiceProvider
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UpdateGraphicConfig
import io.hammerhead.karooext.models.ViewConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber
import java.lang.Thread.sleep

@OptIn(ExperimentalGlanceRemoteViewsApi::class)
class MatchDataType(
    private val karooSystem: KarooSystemServiceProvider,
    extension: String,
    private val calculator: WPrimeCalculator
) : DataTypeImpl(extension, TYPE_ID) {
    private val glance = GlanceRemoteViews()

    init {
        Timber.d("MatchDataType created")
    }

    companion object {
        const val TYPE_ID = "matches"
    }

    private val dataScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun startStream(emitter: Emitter<StreamState>) {
        val job = dataScope.launch {
            val wPrimeFlow = karooSystem.streamDataFlow(
                DataType.dataTypeId(extension, WPrimeBalanceDataType.TYPE_ID)
            )
            wPrimeFlow
                .map { streamState ->
                    when (streamState) {
                        is StreamState.Streaming -> {
                            val matches = calculator.getMatches()
                            StreamState.Streaming(
                                DataPoint(
                                    dataTypeId,
                                    values = mapOf(DataType.Field.SINGLE to matches.toDouble()),
                                ),
                            )
                        } else -> {
                        streamState
                    }
                    }
                }.collect { emitter.onNext(it) }
        }
        emitter.setCancellable {
            job.cancel()
        }
    }

    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {

        val configJob = dataScope.launch {
            emitter.onNext(
                UpdateGraphicConfig(showHeader = false)
            )
        }
        val viewJob = dataScope.launch {
            when (config.preview) {
                true -> {
                    repeat(Int.MAX_VALUE) {
                        val matches = (0..100).random()
                        val inEffort = System.currentTimeMillis() % 5 == 0L
                        sleep((2000..3000).random().toLong())
                        val result = glance.compose(context, DpSize.Unspecified) {
                            MatchView(context, inEffort, matches, config.alignment, config.textSize)
                        }
                        emitter.updateView(result.remoteViews)
                    }
                }
                false -> karooSystem.streamDataFlow(
                    DataType.dataTypeId(extension, WPrimeBalanceDataType.TYPE_ID)
                ).collect {
                    val matches = calculator.getMatches()
                    val inEffort = calculator.getInEffortBlock()
                    val result = glance.compose(context, DpSize.Unspecified) {
                        MatchView(context, inEffort, matches, config.alignment, config.textSize)
                    }
                    emitter.updateView(result.remoteViews)
                }
            }
        }
        emitter.setCancellable {
            viewJob.cancel()
            configJob.cancel()
        }
    }
}
