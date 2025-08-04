package com.currand60.wprimebalance.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.currand60.wprimebalance.KarooSystemServiceProvider
import com.currand60.wprimebalance.data.ConfigData
import com.currand60.wprimebalance.managers.ConfigurationManager
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import timber.log.Timber

@Preview(showBackground = true, widthDp = 240, heightDp = 400)
@Composable
fun MainScreen() {

    val karooSystem = koinInject<KarooSystemServiceProvider>()
    val configManager: ConfigurationManager = koinInject()
    val coroutineScope = rememberCoroutineScope()

    var currentConfig by remember { mutableStateOf(ConfigData.DEFAULT) }
    var wPrimeInput by remember { mutableStateOf("") }
    var criticalPowerInput by remember { mutableStateOf("") }

    var wPrimeError by remember { mutableStateOf(false) }
    var criticalPowerError by remember { mutableStateOf(false) }

    Timber.d("MainScreen created/recomposed.")

    val loadedConfig by produceState(initialValue = ConfigData.DEFAULT, key1 = configManager) {
        Timber.d("Starting to load initial config via produceState.")
        value = configManager.getConfig()
        Timber.d("Initial config loaded: $value")
    }

    val karooFtp by produceState(initialValue = 0, key1 = karooSystem) {
        Timber.d("Starting to load Karoo FTP via produceState.")
        karooSystem.streamUserProfile()
            .distinctUntilChanged()
            .collect { profile ->
                value = profile.ftp
                Timber.d("Karoo FTP loaded: $value")
            }
    }

    LaunchedEffect(loadedConfig) {
        if (currentConfig != loadedConfig) {
            currentConfig = loadedConfig
            Timber.d("currentConfig and input fields updated from loadedConfig: $currentConfig")
        }
    }

    LaunchedEffect(karooSystem) {
        if (currentConfig.useKarooFtp && karooFtp > 0) {
            criticalPowerInput = karooFtp.toString()
            // Also update the currentConfig's criticalPower so saving uses this value
            currentConfig = currentConfig.copy(criticalPower = karooFtp)
            criticalPowerError = false // Clear error if FTP is valid
        } else if (currentConfig.useKarooFtp) {
            criticalPowerInput = ""
        }
    }

    LaunchedEffect(loadedConfig) {
        Timber.d("loadedConfig changed: $loadedConfig. Updating currentConfig and inputs.")
        currentConfig = loadedConfig
        wPrimeInput = loadedConfig.wPrime.toString()

        if (loadedConfig.useKarooFtp && karooFtp > 0) {
            criticalPowerInput = karooFtp.toString()
            currentConfig = currentConfig.copy(criticalPower = karooFtp)
        } else if (loadedConfig.useKarooFtp) {
            criticalPowerInput = ""
        }
        else { // Not using Karoo FTP
            criticalPowerInput = loadedConfig.criticalPower.toString()
        }
        Timber.d("currentConfig and input fields updated from loadedConfig: $currentConfig, CP_Input: $criticalPowerInput")
    }

    LaunchedEffect(currentConfig.useKarooFtp, karooFtp) {
        Timber.d("useKarooFtp or karooFtp changed. useKarooFtp: ${currentConfig.useKarooFtp}, karooFtp: $karooFtp")
        if (currentConfig.useKarooFtp) {
            if (karooFtp > 0) {
                criticalPowerInput = karooFtp.toString()
                // Update the config's CP value if we just switched to using a valid Karoo FTP
                currentConfig = currentConfig.copy(criticalPower = karooFtp)
                criticalPowerError = false
            } else {
                criticalPowerInput = "" // Or currentConfig.criticalPower.toString()
            }
        } else {
            // Not using Karoo FTP, set input to the manually stored criticalPower
            criticalPowerInput = currentConfig.criticalPower.toString()
        }
        Timber.d("CP Input updated due to useKarooFtp/karooFtp change: $criticalPowerInput")
    }


        Box(
        modifier = Modifier.fillMaxSize()
    ){
        Column(
            modifier = Modifier
                .padding(10.dp)
                .background(MaterialTheme.colorScheme.background)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(5.dp))
        {
            Text(text="W' Balance Settings",
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))

            // W' (W-prime) input field
            Text("W' in Joules", Modifier.padding(start = 5.dp))
            OutlinedTextField(
                value = wPrimeInput, // Bound to the string input state
                modifier = Modifier.fillMaxWidth()
                    .padding(start = 5.dp, end = 5.dp),
                onValueChange = { newValue ->
                    Timber.d("W' input changed: $newValue")
                    wPrimeInput = newValue // Always update the string state first
                    val parsedValue = newValue.toIntOrNull()
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
                placeholder = { Text("${currentConfig.wPrime}") },
                suffix = { Text("J") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                isError = wPrimeError,
                supportingText = {
                    if (wPrimeError) {
                        Text("Please enter a valid number (e.g., 20.5)")
                    }
                }
            )
            Text("Critical Power", Modifier.padding(start = 5.dp))
            // Critical Power input field
            OutlinedTextField(
                value = criticalPowerInput, // Bound to the string input state
                modifier = Modifier.fillMaxWidth()
                    .padding(start = 5.dp, end = 5.dp),
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
                placeholder = {
                    // Show a more dynamic placeholder
                    if (currentConfig.useKarooFtp && karooFtp > 0) {
                        Text("$karooFtp (from Karoo)")
                    } else {
                        Text("${currentConfig.criticalPower}")
                    }
                },
                suffix = { Text("W") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                isError = criticalPowerError,
                supportingText = {
                    if (criticalPowerError) {
                        Text("Please enter a valid integer")
                    } else if (currentConfig.useKarooFtp && karooFtp <= 0) {
                        Text("Karoo FTP is 0 or not set. Enter CP manually.")
                    }
                },
                enabled = if (currentConfig.useKarooFtp) {
                    karooFtp <= 0 // Enable if using Karoo FTP AND Karoo FTP is 0 or less
                } else {
                    true // Enable if not using Karoo FTP at all
                }
            )
            Row(
                modifier = Modifier.padding(start = 5.dp),
            ) {
                Switch(
                    checked = currentConfig.calculateCp,
                    onCheckedChange = {
                        currentConfig = currentConfig.copy(calculateCp = !currentConfig.calculateCp)
                        Timber.d("CP calculation toggle: ${currentConfig.calculateCp}")
                    }
                )
                Text(
                    modifier = Modifier.padding(start = 5.dp)
                        .align(Alignment.CenterVertically),
                    text = "Estimate CP mid-ride",
                )
            }
            Row(
                modifier = Modifier.padding(start = 5.dp),
            ) {
                Switch(
                    checked = currentConfig.useKarooFtp,
                    onCheckedChange = { isChecked ->
                        Timber.d("Karoo FTP toggle changed: $isChecked")
                        currentConfig = currentConfig.copy(useKarooFtp = isChecked)
                    }
                )
                Text(
                    modifier = Modifier.padding(start = 5.dp)
                        .align(Alignment.CenterVertically),
                    text = "Use the Karoo FTP?",
                )
            }
            Box (modifier = Modifier
                .fillMaxWidth()
                .padding(5.dp)
            ) {
                Text("Enter your W' in Joules and CP if you know them. If you are unsure " +
                        "Enter your best guess and select the option to calculate it mid-ride.")
            }
            // Save Button
            FilledTonalButton(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .height(50.dp)
                    .padding(vertical = 8.dp),
                onClick = {
                    Timber.d("Save button clicked. Current errors: W'=$wPrimeError, CP=$criticalPowerError")
                    val configToSave = if (currentConfig.useKarooFtp && karooFtp > 0) {
                        currentConfig.copy(criticalPower = karooFtp)
                    } else {
                        currentConfig
                    }

                    if (!wPrimeError && !criticalPowerError) { // Add check for criticalPower if it's manually entered
                        coroutineScope.launch {
                            Timber.d("Attempting to save config: $configToSave")
                            configManager.saveConfig(configToSave)
                            Timber.i("Configuration save initiated.")
                        }
                    } else {
                        Timber.w("Save blocked due to input validation errors.")
                    }
                },
                enabled = !wPrimeError && !criticalPowerError
            ) {
                Icon(Icons.Default.Check, contentDescription = "Save")
                Spacer(modifier = Modifier.width(5.dp))
                Text("Save")
            }

        }
        Spacer(modifier = Modifier.height(20.dp))
    }
}
