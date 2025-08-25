package com.currand60.wprimebalance.data

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.preview.ExperimentalGlancePreviewApi
import androidx.glance.preview.Preview
import androidx.glance.text.FontFamily
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextDefaults.defaultTextStyle
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.currand60.wprimebalance.R
import io.hammerhead.karooext.models.ViewConfig


@OptIn(ExperimentalGlancePreviewApi::class)
@Composable
fun MatchView(
    context: Context,
    inEffort: Boolean,
    value: Int,
    alignment: ViewConfig.Alignment,
    textSize: Int,
) {

    // Determine the background color based on the 'inEffort' boolean
    // This color will be applied to the entire Row (the "box").
    val backgroundColor = if (inEffort) Color.Red else Color.Transparent

    val alignment: TextAlign = when (alignment) {
        ViewConfig.Alignment.CENTER -> TextAlign.Center
        ViewConfig.Alignment.LEFT -> TextAlign.Start
        ViewConfig.Alignment.RIGHT -> TextAlign.End
    }

    Box(
        modifier = GlanceModifier.fillMaxSize().cornerRadius(8.dp)
    ) {
        Row(
            modifier = GlanceModifier
                .height(24.dp) // Set the exact height of the box
                .background(backgroundColor) // Apply the dynamic background color to the entire Row
                .fillMaxWidth(), // Make the box fill the available width

            horizontalAlignment = Alignment.CenterHorizontally // Vertically center content
        ) {
            Image(
                provider = ImageProvider(
                    resId = R.drawable.fire_24px
                ),
                contentDescription = context.getString(R.string.match_description),
                modifier = GlanceModifier
                    .fillMaxHeight()
                    .padding(top = 4.dp)

            )
            Text(
                text = context.getString(R.string.match_datatype).uppercase(),
                style = TextStyle(
                    color = ColorProvider(R.color.text_color),
                    fontSize = TextUnit(16f, TextUnitType.Sp),
                    textAlign = alignment,
                    fontFamily = FontFamily.SansSerif
                )
            )
        }
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(top = 21.dp, bottom = 2.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Text for the dynamic integer value
            Text(
                text = value.toString(),
                style = TextStyle(
                    color = ColorProvider(R.color.text_color),
                    fontSize = TextUnit(textSize.toFloat(), TextUnitType.Sp),
                    textAlign = alignment,
                    fontFamily = FontFamily.SansSerif
                ),
                modifier = GlanceModifier
                    .padding(start = 4.dp, end = 4.dp)
            )
        }
    }
}

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 200, heightDp = 150) // Dark background for contrast
@Composable
fun EffortIndicatorPreviewInEffort() {
    MatchView(LocalContext.current, inEffort = true, value = 75, alignment = ViewConfig.Alignment.CENTER, textSize = 54)
}

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 200, heightDp = 150) // Dark background for contrast
@Composable
fun EffortIndicatorPreviewNormal() {
    MatchView(LocalContext.current, inEffort = false, value = 42, alignment = ViewConfig.Alignment.RIGHT, textSize = 54)
}

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 200, heightDp = 150) // Dark background for contrast
@Composable
fun EffortIndicatorPreviewLongValue() {
    MatchView(LocalContext.current, inEffort = true, value = 42, alignment = ViewConfig.Alignment.LEFT, textSize = 54)
}