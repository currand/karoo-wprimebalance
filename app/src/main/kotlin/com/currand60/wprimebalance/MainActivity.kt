package com.currand60.wprimebalance

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.currand60.wprimebalance.screens.MainScreen
import com.currand60.wprimebalance.theme.AppTheme
import timber.log.Timber

@Composable
fun Main() {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
        MainScreen()
    }
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
            Timber.d("Timber initialized in Debug mode.")
        } else {
            Timber.d("Timber initialized in Release mode (no DebugTree planted).")
        }
        setContent {
            AppTheme {
                Main()
            }
        }
    }
}
