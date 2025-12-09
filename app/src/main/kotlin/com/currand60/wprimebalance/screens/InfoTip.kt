package com.currand60.wprimebalance.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.glance.preview.ExperimentalGlancePreviewApi
import androidx.glance.preview.Preview

@Composable
fun InfoTip(
    modifier: Modifier = Modifier,
    tipText: String,
    tipId: String // Unique ID for this tip, currently used for accessibility and potential future persistence
) {
    var showInfoDialog by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .size(24.dp) // Fixed size for the circular background
            .background(MaterialTheme.colorScheme.primary, CircleShape) // Primary color background
            .border(1.dp, MaterialTheme.colorScheme.onPrimary, CircleShape) // Border for contrast
            .clickable {
                showInfoDialog = true // Show the information dialog
            },
        contentAlignment = Alignment.Center // Center the 'i' icon within the circle
    ) {
        Icon(
            imageVector = Icons.Default.Info, // The 'i' icon
            contentDescription = "Information about $tipId", // Accessibility description
            tint = MaterialTheme.colorScheme.onPrimary, // Icon color contrasting with primary
            modifier = Modifier.size(16.dp) // Size of the 'i' icon itself
        )
    }

    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = {
                showInfoDialog = false // Dismiss dialog if tapped outside or back button pressed
           },
            title = { Text(tipId) },
            text = { Text(tipText) },
            confirmButton = {
                TextButton(onClick = { showInfoDialog = false }) {
                    Text("Got it!") // Button to explicitly dismiss the dialog
                }
            }
        )
    }
}

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview()
@Composable
private fun InfoTipPreview() {
    InfoTip(
        tipText = "This is a sample tip text",
        tipId = "Preview"
    )
}