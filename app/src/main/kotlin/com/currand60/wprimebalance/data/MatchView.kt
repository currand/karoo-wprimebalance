package com.currand60.wprimebalance.data

import android.annotation.SuppressLint
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
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.preview.ExperimentalGlancePreviewApi
import androidx.glance.preview.Preview
import androidx.glance.text.FontFamily
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.currand60.wprimebalance.R
import io.hammerhead.karooext.models.ViewConfig


@SuppressLint("RestrictedApi")
@OptIn(ExperimentalGlancePreviewApi::class)
@Composable
fun MatchView(
    context: Context,
    inEffort: Boolean,
    value: Int,
    alignment: ViewConfig.Alignment,
    textSize: Int,
) {

    // Light red
    val backgroundColor = if (inEffort) Color(context.getColor(R.color.dark_red)) else Color.Transparent

    val alignment: TextAlign = when (alignment) {
        ViewConfig.Alignment.CENTER -> TextAlign.Center
        ViewConfig.Alignment.LEFT -> TextAlign.Start
        ViewConfig.Alignment.RIGHT -> TextAlign.End
    }

    val textColor = if (inEffort) R.color.white else R.color.text_color

    Column(
        modifier = GlanceModifier.fillMaxSize().cornerRadius(8.dp)
    ) {
        Row(
            modifier = GlanceModifier
                .height(20.dp)
                .background(backgroundColor)
                .fillMaxWidth(),

            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                provider = ImageProvider(
                    resId = R.drawable.fire_24px
                ),
                contentDescription = context.getString(R.string.match_description),
                modifier = GlanceModifier
                    .fillMaxHeight()
            )
            Text(
                text = context.getString(R.string.match_datatype).uppercase(),
                style = TextStyle(
                    color = ColorProvider(textColor),
                    fontSize = TextUnit(16f, TextUnitType.Sp),
                    textAlign = alignment,
                    fontFamily = FontFamily.SansSerif
                )
            )
        }
        Row(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(backgroundColor),
            verticalAlignment = Alignment.Bottom,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                modifier = GlanceModifier.fillMaxWidth(),
                text = value.toString(),
                style = TextStyle(
                    color = ColorProvider(textColor),
                    fontSize = TextUnit(textSize.toFloat(), TextUnitType.Sp),
                    textAlign = alignment,
                    fontFamily = FontFamily.Monospace,
                )
            )
        }
    }
}

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 150, heightDp = 90)
@Composable
fun EffortIndicatorPreviewInEffort() {
    MatchView(LocalContext.current, inEffort = true, value = 75, alignment = ViewConfig.Alignment.CENTER, textSize = 54)
}

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 150, heightDp = 90)
@Composable
fun EffortIndicatorPreviewNormal() {
    MatchView(LocalContext.current, inEffort = false, value = 42, alignment = ViewConfig.Alignment.RIGHT, textSize = 54)
}

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 150, heightDp = 90)
@Composable
fun EffortIndicatorPreviewLongValue() {
    MatchView(LocalContext.current, inEffort = true, value = 42, alignment = ViewConfig.Alignment.LEFT, textSize = 54)
}