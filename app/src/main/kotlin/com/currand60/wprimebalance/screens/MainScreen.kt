package com.currand60.wprimebalance.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp


@Composable
fun MainScreen() {
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var wPrimeConfigHasUnsavedChanges by remember { mutableStateOf(false) }
    var matchConfigHasUnsavedChanges by remember { mutableStateOf(false) }
    var showUnsavedChangesDialog by remember { mutableStateOf(false) }
    var pendingTabIndex by remember { mutableIntStateOf(selectedTabIndex) }

    val tabs = listOf("W' Balance","Matches")


    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = selectedTabIndex,
            modifier = Modifier.fillMaxWidth(),
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = {
                        if (!wPrimeConfigHasUnsavedChanges && !matchConfigHasUnsavedChanges) {
                            // No unsaved changes or switching within the same tab, just navigate
                            selectedTabIndex = index
                        } else {
                            pendingTabIndex = index
                            showUnsavedChangesDialog = true
                        }

                    },
                    text = { Text(text = title, fontSize = 12.sp) },
                )
            }
        }

        when (selectedTabIndex) {
            0 -> WPrimeConfigScreen(
                onUnsavedChangesChange = { hasChanges ->
                    wPrimeConfigHasUnsavedChanges = hasChanges
                }
            )
            1 -> MatchConfigScreen(
                onUnsavedChangesChange = { hasChanges ->
                    matchConfigHasUnsavedChanges = hasChanges
                }
            )
        }
    }
    // Confirmation dialog for unsaved changes
    if (showUnsavedChangesDialog) {
        AlertDialog(
            onDismissRequest = { showUnsavedChangesDialog = false },
            title = { Text("Unsaved Changes") },
            text = { Text("You have unsaved changes. Do you want to discard them?") },
            confirmButton = {
                TextButton(onClick = {
                    showUnsavedChangesDialog = false
                    wPrimeConfigHasUnsavedChanges = false
                    matchConfigHasUnsavedChanges = false
                    selectedTabIndex = pendingTabIndex // Proceed with navigation
                }) {
                    Text("Discard")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showUnsavedChangesDialog = false // Stay on the current tab
                }) {
                    Text("Cancel")
                }
            }
        )
    }

}

@Preview(name = "karoo", device = "spec:width=480px,height=800px,dpi=300")
@Composable
private fun PreviewTabLayout() {
    MainScreen()
}