package com.currand60.wprimebalance.managers

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.currand60.wprimebalance.data.ConfigData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import timber.log.Timber

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class ConfigurationManager(private val context: Context){

    companion object {
        private val W_PRIME_KEY = intPreferencesKey("w_prime")
        private val CRITICAL_POWER_KEY = intPreferencesKey("critical_power")
        private val THRESHOLD_KEY = intPreferencesKey("threshold")
    }

    private val json = Json { ignoreUnknownKeys = true}
    val configFlow: Flow<ConfigData> = context.dataStore.data.map {
        preferences ->
        ConfigData(
            wPrime = preferences[W_PRIME_KEY] ?: 0,
            criticalPower = preferences[CRITICAL_POWER_KEY] ?: 0,
            threshold = preferences[THRESHOLD_KEY] ?: 0
        )
    }
    .distinctUntilChanged()

        suspend fun saveConfig(config: ConfigData) {
            Timber.d("Attempting to save configuration to DataStore: W'=${config.wPrime}, CP=${config.criticalPower}, FTP=${config.threshold}")
            context.dataStore.edit { preferences ->
                preferences[W_PRIME_KEY] = config.wPrime
                preferences[CRITICAL_POWER_KEY] = config.criticalPower
                preferences[THRESHOLD_KEY] = config.threshold
            }
            Timber.i("Configuration successfully saved to DataStore.")
        }

    suspend fun getConfig(): ConfigData {
        Timber.d("Attempting to retrieve configuration from DataStore.")
        return context.dataStore.data.map { preferences ->
            val config = ConfigData(
                wPrime = preferences[W_PRIME_KEY] ?: 0,
                criticalPower = preferences[CRITICAL_POWER_KEY] ?: 0,
                threshold = preferences[THRESHOLD_KEY] ?: 0
            )
            Timber.d("Retrieved configuration: W'=${config.wPrime}, CP=${config.criticalPower}, FTP=${config.threshold}")
            config
        }.first()
    }
}