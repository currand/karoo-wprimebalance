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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.currand60.wprimebalance.managers.ConfigurationManager
import com.currand60.wprimebalance.theme.AppTheme
import io.hammerhead.karooext.KarooSystemService
import kotlinx.coroutines.launch

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val karooSystem = remember { KarooSystemService(context) }
    val configManager = remember { ConfigurationManager(context) }
    val coroutineScope = rememberCoroutineScope()

    val config = configManager.getConfig()

    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Rider Values",
                style = MaterialTheme.typography.titleMedium
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = config.wPrime.toString(),
                    modifier = Modifier.weight(1f),
                    onValueChange = { config.copy(wPrime = it.toDouble()) },
                    label = { Text("W' in kilojoules") },
                    suffix = { Text("kj") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = config.criticalPower.toString(),
                    modifier = Modifier.weight(1f),
                    onValueChange = { config.copy(criticalPower = it.toInt()) },
                    label = { Text("Critical Power in Watts") },
                    suffix = { Text("W") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = config.threshold.toString(),
                    modifier = Modifier.weight(1f),
                    onValueChange = { config.copy(threshold = it.toInt()) },
                    label = { Text("FTP in Watts") },
                    suffix = { Text("W") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
            }
        }
        FilledTonalButton(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .height(50.dp), onClick = {
                coroutineScope.launch {
                    configManager.saveConfig(config)
                }
            }) {
            Icon(Icons.Default.Check, contentDescription = "Save")
            Spacer(modifier = Modifier.width(5.dp))
            Text("Save")
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
