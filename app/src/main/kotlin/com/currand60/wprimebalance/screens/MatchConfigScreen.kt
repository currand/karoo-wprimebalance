package com.currand60.wprimebalance.screens

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.currand60.wprimebalance.R
import com.currand60.wprimebalance.data.ConfigData
import com.currand60.wprimebalance.managers.ConfigurationManager
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import timber.log.Timber

@Preview(name = "karoo", device = "spec:width=480px,height=800px,dpi=300")
@Composable
fun MatchConfigScreen(
    onUnsavedChangesChange: (Boolean) -> Unit = {}
) {

    val context = LocalContext.current
    val configManager: ConfigurationManager = koinInject()
    val coroutineScope = rememberCoroutineScope()

    var showUnsavedChangesDialogForBack by remember { mutableStateOf(false) }
    var fieldHasChanges by remember { mutableStateOf(false) }


    var originalConfig by remember { mutableStateOf(ConfigData.DEFAULT) }
    var currentConfig by remember { mutableStateOf(ConfigData.DEFAULT) }
    var minMatchDuration by remember { mutableStateOf("") }
    var matchJoulePercent by remember { mutableStateOf("") }
    var matchPowerPercent by remember { mutableStateOf("") }

    var minMatchDurationError by remember { mutableStateOf(false) }
    var matchJoulePercentError by remember { mutableStateOf(false) }
    var matchPowerPercentError by remember { mutableStateOf(false) }

    Timber.d("MainScreen created/recomposed.")

    val loadedConfig by produceState(initialValue = ConfigData.DEFAULT, key1 = configManager) {
        Timber.d("Starting to load initial config via produceState.")
        value = configManager.getConfig()
        Timber.d("Initial config loaded: $value")
    }

    LaunchedEffect(loadedConfig) {
        // Timber.d("Configuration or Karoo FTP change detected. loadedConfig: $loadedConfig, karooFtp: $karooFtp") // Conditional log

        val newConfig = loadedConfig.copy() // Create a mutable copy to update

        minMatchDuration = newConfig.minMatchDuration.toString()
        matchJoulePercent = newConfig.matchJoulePercent.toString()
        matchPowerPercent = newConfig.matchPowerPercent.toString()

        // Only update currentConfig once after all derivations
        currentConfig = newConfig
        originalConfig = newConfig
        fieldHasChanges = false
    }

    // Effect to observe changes in currentConfig and update hasUnsavedChanges
    LaunchedEffect(currentConfig, originalConfig, fieldHasChanges, matchJoulePercentError, matchPowerPercentError) {
        // Only consider unsaved changes if there are no input errors
        val hasChanges = if (!minMatchDurationError && !matchJoulePercentError && !matchPowerPercentError) {
            currentConfig != originalConfig && fieldHasChanges
        } else {
            // If there are validation errors, consider it as having unsaved changes
            // to prevent accidental discard of potentially valid parts.
            true
        }
        onUnsavedChangesChange(hasChanges)
    }

    val scrollState = rememberScrollState()
    val isScrolledToBottom = scrollState.value == scrollState.maxValue

    // Intercept back presses
    BackHandler(enabled = currentConfig != originalConfig && !minMatchDurationError && !matchJoulePercentError && !matchPowerPercentError) {
        showUnsavedChangesDialogForBack = true
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ){
        Column(
            modifier = Modifier
                .padding(10.dp)
                .background(MaterialTheme.colorScheme.background)
                .fillMaxSize()
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(5.dp))
        {
            Text(text="Match Settings",
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))

            //Effort Duration input field
            Text("Minimum Match Duration", Modifier.padding(start = 5.dp))
            OutlinedTextField(
                value = minMatchDuration, // Bound to the string input state
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 5.dp, end = 5.dp),
                onValueChange = { newValue ->
                    Timber.d("Duration input changed: $newValue")
                    fieldHasChanges = (newValue != currentConfig.minMatchDuration.toString())
                    minMatchDuration = newValue // Always update the string state first
                    val parsedValue = newValue.toIntOrNull()
                    if (parsedValue != null && parsedValue in (0..3600)) {
                        currentConfig = currentConfig.copy(minMatchDuration = parsedValue) // Update numerical state only if valid
                        minMatchDurationError = false
                        Timber.d("Duration parsed successfully: $parsedValue, currentConfig: $currentConfig")
                    } else {
                        // If parsing fails, set error, but the UI still shows the (invalid) newValue
                        minMatchDurationError = true
                        Timber.w("Invalid Duration input: '$newValue'. Error status: $minMatchDurationError")
                    }
                },
                placeholder = { Text("${currentConfig.minMatchDuration}") },
                suffix = { Text("Seconds") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                isError = minMatchDurationError,
                supportingText = {
                    if (minMatchDurationError) {
                        Text("Please enter a valid number (0-3600s)")
                    }
                },
            )
            Text("Percent W' Drop", Modifier.padding(start = 5.dp))
            OutlinedTextField(
                value = matchJoulePercent, // Bound to the string input state
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 5.dp, end = 5.dp),
                onValueChange = { newValue ->
                    Timber.d("Percent input changed: $newValue")
                    fieldHasChanges = (newValue != currentConfig.matchJoulePercent.toString())
                    matchJoulePercent = newValue // Always update the string state
                    val parsedValue = newValue.toIntOrNull()
                    if (parsedValue != null && parsedValue in (5..100)) {
                        currentConfig = currentConfig.copy(matchJoulePercent = parsedValue)
                        matchJoulePercentError = false
                        Timber.d("Percent parsed successfully: $parsedValue, currentConfig: $currentConfig")
                    } else {
                        matchJoulePercentError = true
                        Timber.w("Invalid Percent input: '$newValue'. Error status: $matchJoulePercentError")
                    }
                },
                placeholder = { Text("${currentConfig.matchJoulePercent}") },
                suffix = { Text("%") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                isError = matchJoulePercentError,
                supportingText = {
                    if (matchJoulePercentError) {
                        Text("Please enter a valid integer (5-100%")
                    }
                },
            )
            Text("Minimum Power", Modifier.padding(start = 5.dp))
            OutlinedTextField(
                value = matchPowerPercent, // Bound to the string input state
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 5.dp, end = 5.dp),
                onValueChange = { newValue ->
                    Timber.d("Percent input changed: $newValue")
                    fieldHasChanges = (newValue != currentConfig.matchPowerPercent.toString())
                    matchPowerPercent = newValue // Always update the string state
                    val parsedValue = newValue.toIntOrNull()
                    if (parsedValue != null && parsedValue in (100..200)) {
                        currentConfig = currentConfig.copy(matchPowerPercent = parsedValue)
                        matchPowerPercentError = false
                        Timber.d("Percent parsed successfully: $parsedValue, currentConfig: $currentConfig")
                    } else {
                        matchPowerPercentError = true
                        Timber.w("Invalid Percent input: '$newValue'. Error status: $matchPowerPercentError")
                    }
                },
                placeholder = { Text("${currentConfig.matchPowerPercent}") },
                suffix = { Text("%") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                isError = matchPowerPercentError,
                supportingText = {
                    if (matchPowerPercentError) {
                        Text("Please enter a valid integer (100-200%)")
                    }
                },
            )
            Box (modifier = Modifier
                .fillMaxWidth()
                .padding(5.dp)
            ) {
                Text("A 'match' is defined as a high rate of drop in W' Balance over time." +
                        " Enter in the minimum duration you want to consider a match (up to 3600s), the " +
                        "amount of W' balance drop to count as a match (as a percentage of total), and " +
                    "the minimum power needed to trigger an effort (as a percentage of CP60/FTP)"
                )
            }
            // Save Button
            FilledTonalButton(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .height(50.dp)
                    .padding(vertical = 8.dp),
                onClick = {
                    val configToSave = currentConfig

                    if (!minMatchDurationError && !matchJoulePercentError) {
                        coroutineScope.launch {
                            Timber.d("Attempting to save config: $configToSave")
                            configManager.saveConfig(configToSave)
                            originalConfig = configToSave
                            onUnsavedChangesChange(false)

                            Timber.i("Configuration save initiated.")
                        }
                    } else {
                        Timber.w("Save blocked due to input validation errors.")
                    }
                },
                enabled = !matchJoulePercentError && !minMatchDurationError
            ) {
                Icon(Icons.Default.Check, contentDescription = "Save")
                Spacer(modifier = Modifier.width(5.dp))
                Text("Save")
            }

        }
        Spacer(modifier = Modifier.height(20.dp))
        if (isScrolledToBottom) {
            Image(
                painter = painterResource(id = R.drawable.back), // Load your drawable
                contentDescription = "Back", // For accessibility
                modifier = Modifier
                    .align(Alignment.BottomStart) // Aligns the image to the bottom-left
                    .padding(bottom = 10.dp) // Add some padding from the screen edges
                    .size(54.dp) // Set a suitable size for the clickable area and image
                    .clickable {
                        if (currentConfig != originalConfig && !minMatchDurationError && !matchJoulePercentError && !matchPowerPercentError) {
                            showUnsavedChangesDialogForBack = true
                        } else {
                            val activity = context as? ComponentActivity
                            activity?.onBackPressedDispatcher?.onBackPressed()
                        }
                    }
            )
        }
        if (showUnsavedChangesDialogForBack) {
            AlertDialog(
                onDismissRequest = { showUnsavedChangesDialogForBack = false },
                title = { Text("Unsaved Changes") },
                text = { Text("You have unsaved changes. Do you want to discard them?") },
                confirmButton = {
                    TextButton(onClick = {
                        showUnsavedChangesDialogForBack = false
                        // Discard changes by resetting currentConfig to originalConfig
                        currentConfig = originalConfig.copy()
                        // Re-set input fields to reflect originalConfig values
                        minMatchDuration = originalConfig.minMatchDuration.toString()
                        matchJoulePercent = originalConfig.matchJoulePercent.toString()
                        matchPowerPercent = originalConfig.matchPowerPercent.toString()
                        // Clear any errors that might have been present
                        minMatchDurationError = false
                        matchJoulePercentError = false
                        matchPowerPercentError = false
                        onUnsavedChangesChange(false) // Explicitly signal no unsaved changes
                        val activity = context as? ComponentActivity
                        activity?.onBackPressedDispatcher?.onBackPressed() // Proceed with back navigation
                    }) {
                        Text("Discard")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showUnsavedChangesDialogForBack = false // Stay on the current screen
                    }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

