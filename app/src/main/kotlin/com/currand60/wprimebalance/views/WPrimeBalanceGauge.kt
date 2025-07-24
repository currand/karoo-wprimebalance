// File: app/src/main/kotlin/com/currand60/wprimebalance/screens/WPrimeBalanceGauge.kt
package com.currand60.wprimebalance.views

import android.annotation.SuppressLint
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import io.hammerhead.karooext.models.ViewConfig

@SuppressLint("RestrictedApi")
@Composable
fun WPrimeBalanceGauge(wPrimePercent: Double, dataAlignment: ViewConfig.Alignment) {
    val backgroundColor = ColorProvider(
        when {
            wPrimePercent < 25.0 -> Color.Red
            wPrimePercent < 75.0 -> Color.Yellow
            else -> Color.Green
        }
    )

    Box(

        modifier = GlanceModifier.fillMaxSize()
            .background(backgroundColor)
            .padding(4.dp), // Padding around the content inside the box
        // Align the content (the Text) in the center of the Box.
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "${wPrimePercent.toInt()}%",
            // Set text color to black to ensure good contrast on any of the colored backgrounds.
            style = TextStyle(color = ColorProvider(Color.Black))
        )
    }
}
