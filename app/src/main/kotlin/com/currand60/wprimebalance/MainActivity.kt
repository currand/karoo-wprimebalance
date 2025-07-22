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
        if (BuildConfig.DEBUG) { // Make sure BuildConfig.DEBUG is available (it usually is by default)
            Timber.plant(Timber.DebugTree())
            Timber.d("Timber initialized in Debug mode.")
        } else {
            // For release builds, you might want a non-logging tree or a crash reporting tree
            // Timber.plant(CrashReportingTree()) // Example for a custom release tree
            Timber.d("Timber initialized in Release mode (no DebugTree planted).")
        }
        setContent { Main() }
    }
}
