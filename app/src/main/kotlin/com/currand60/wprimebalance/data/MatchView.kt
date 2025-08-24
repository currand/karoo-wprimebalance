package com.currand60.wprimebalance.data

import android.content.Context
import android.util.AttributeSet
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
import androidx.glance.layout.wrapContentSize
import androidx.glance.preview.ExperimentalGlancePreviewApi
import androidx.glance.preview.Preview
import androidx.glance.text.FontFamily
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextDefaults
import androidx.glance.text.TextStyle
import com.currand60.wprimebalance.R


@OptIn(ExperimentalGlancePreviewApi::class)
@Composable
fun MatchView(
    inEffort: Boolean,
    value: Int,
) {
    // Determine the background color based on the 'inEffort' boolean
    // This color will be applied to the entire Row (the "box").
    val backgroundColor = if (inEffort) Color.Red else Color.Transparent
    val context = LocalContext.current

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
                    color = TextDefaults.defaultTextColor,
                    fontSize = TextUnit(18f, TextUnitType.Sp),
                ),
            )
        }
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(top = 21.dp, bottom = 2.dp),
//                .wrapContentSize(),
            verticalAlignment = Alignment.Bottom,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Text for the dynamic integer value
            Text(
//                text = value.toString(),
                text = "42",
                style = TextStyle(
                    color = TextDefaults.defaultTextColor, // White text color for contrast
                    fontSize = TextUnit(56f, TextUnitType.Sp), // Font size for the label
                    textAlign = TextAlign.Center,
                    fontFamily = FontFamily.Monospace,
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
    MatchView(inEffort = true, value = 75)
}

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 200, heightDp = 150) // Dark background for contrast
@Composable
fun EffortIndicatorPreviewNormal() {
    MatchView(inEffort = false, value = 42)
}

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 200, heightDp = 150) // Dark background for contrast
@Composable
fun EffortIndicatorPreviewLongValue() {
    MatchView(inEffort = true, value = 12345)
}