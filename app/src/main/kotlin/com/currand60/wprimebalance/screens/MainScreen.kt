package com.currand60.wprimebalance.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.currand60.wprimebalance.data.ConfigData
import com.currand60.wprimebalance.managers.ConfigurationManager
import com.currand60.wprimebalance.theme.AppTheme
import io.hammerhead.karooext.KarooSystemService
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import timber.log.Timber
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val karooSystem = remember { KarooSystemService(context) }
    val configManager = remember { ConfigurationManager(context) }
    val coroutineScope = rememberCoroutineScope()

    var currentConfig by remember { mutableStateOf(ConfigData()) }
    var wPrimeInput by remember { mutableStateOf("") }
    var criticalPowerInput by remember { mutableStateOf("") }
    var thresholdInput by remember { mutableStateOf("") }

    var wPrimeError by remember { mutableStateOf(false) }
    var criticalPowerError by remember { mutableStateOf(false) }
    var thresholdError by remember { mutableStateOf(false) }

    Timber.d("MainScreen created/recomposed.")

    val loadedConfig by produceState(initialValue = ConfigData(), key1 = configManager) {
        Timber.d("Starting to load initial config via produceState.")
        value = configManager.getConfig()
        Timber.d("Initial config loaded: $value")
    }

    LaunchedEffect(loadedConfig) {
        if (currentConfig != loadedConfig) {
            currentConfig = loadedConfig
            // Initialize the input string states from the loaded numerical values
            wPrimeInput = loadedConfig.wPrime.toString()
            criticalPowerInput = loadedConfig.criticalPower.toString()
            thresholdInput = loadedConfig.threshold.toString()
            Timber.d("currentConfig and input fields updated from loadedConfig: $currentConfig")
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
//            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Rider Values",
                style = MaterialTheme.typography.titleMedium
            )

            // W' (W-prime) input field
            OutlinedTextField(
                value = wPrimeInput, // Bound to the string input state
                modifier = Modifier.fillMaxWidth(),
                onValueChange = { newValue ->
                    Timber.d("W' input changed: $newValue")
                    wPrimeInput = newValue // Always update the string state first
                    val parsedValue = newValue.toDoubleOrNull()
                    if (parsedValue != null) {
                        currentConfig = currentConfig.copy(wPrime = parsedValue) // Update numerical state only if valid
                        wPrimeError = false
                        Timber.d("W' parsed successfully: $parsedValue, currentConfig: $currentConfig")
                    } else {
                        // If parsing fails, set error, but the UI still shows the (invalid) newValue
                        wPrimeError = newValue.isNotBlank()
                        Timber.w("Invalid W' input: '$newValue'. Error status: $wPrimeError")
                    }
                },
                label = { Text("W' in kilojoules") },
                suffix = { Text("kj") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                isError = wPrimeError,
                supportingText = {
                    if (wPrimeError) {
                        Text("Please enter a valid number (e.g., 20.5)")
                    }
                }
            )

            // Critical Power input field
            OutlinedTextField(
                value = criticalPowerInput, // Bound to the string input state
                modifier = Modifier.fillMaxWidth(),
                onValueChange = { newValue ->
                    Timber.d("CP input changed: $newValue")
                    criticalPowerInput = newValue // Always update the string state
                    val parsedValue = newValue.toIntOrNull()
                    if (parsedValue != null) {
                        currentConfig = currentConfig.copy(criticalPower = parsedValue)
                        criticalPowerError = false
                        Timber.d("CP parsed successfully: $parsedValue, currentConfig: $currentConfig")
                    } else {
                        criticalPowerError = newValue.isNotBlank()
                        Timber.w("Invalid CP input: '$newValue'. Error status: $criticalPowerError")
                    }
                },
                label = { Text("Critical Power in Watts") },
                suffix = { Text("W") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                isError = criticalPowerError,
                supportingText = {
                    if (criticalPowerError) {
                        Text("Please enter a valid integer")
                    }
                }
            )

            // FTP (Threshold) input field
            OutlinedTextField(
                value = thresholdInput, // Bound to the string input state
                modifier = Modifier.fillMaxWidth(),
                onValueChange = { newValue ->
                    Timber.d("FTP input changed: $newValue")
                    thresholdInput = newValue // Always update the string state
                    val parsedValue = newValue.toIntOrNull()
                    if (parsedValue != null) {
                        currentConfig = currentConfig.copy(threshold = parsedValue)
                        thresholdError = false
                        Timber.d("FTP parsed successfully: $parsedValue, currentConfig: $currentConfig")
                    } else {
                        thresholdError = newValue.isNotBlank()
                        Timber.w("Invalid FTP input: '$newValue'. Error status: $thresholdError")
                    }
                },
                label = { Text("FTP in Watts") },
                suffix = { Text("W") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                isError = thresholdError,
                supportingText = {
                    if (thresholdError) {
                        Text("Please enter a valid integer")
                    }
                }
            )
            // Save Button
            FilledTonalButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .padding(vertical = 8.dp),
                onClick = {
                    Timber.d("Save button clicked. Current errors: W'=$wPrimeError, CP=$criticalPowerError, FTP=$thresholdError")
                    if (!wPrimeError && !criticalPowerError && !thresholdError) {
                        coroutineScope.launch {
                            Timber.d("Attempting to save currentConfig: $currentConfig")
                            configManager.saveConfig(currentConfig)
                            // Optional: Provide user feedback after saving, e.g., a Toast.
                            // karooSystem.showToast("Configuration Saved!")
                            Timber.i("Configuration save initiated.")
                        }
                    } else {
                        Timber.w("Save blocked due to input validation errors.")
                        // Optional: Inform the user about validation errors if they try to save.
                        // karooSystem.showToast("Please correct the errors before saving.")
                    }
                },
                enabled = !wPrimeError && !criticalPowerError && !thresholdError
            ) {
                Icon(Icons.Default.Check, contentDescription = "Save")
                Spacer(modifier = Modifier.width(5.dp))
                Text("Save")
            }

        }
    }
}



@Preview(
    widthDp = 256,
    heightDp = 426,
)

@Composable
fun DefaultPreview() {
    AppTheme {
        MainScreen()
    }
}
