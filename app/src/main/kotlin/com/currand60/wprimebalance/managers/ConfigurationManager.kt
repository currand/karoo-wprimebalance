package com.currand60.wprimebalance.managers

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.currand60.wprimebalance.data.ConfigData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import timber.log.Timber

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class ConfigurationManager(private val context: Context){

    companion object {
        private val W_PRIME_KEY = intPreferencesKey("w_prime")
        private val CRITICAL_POWER_KEY = intPreferencesKey("critical_power")
        private val CALCULATE_CP_KEY = booleanPreferencesKey("calculateCp")
    }

    suspend fun saveConfig(config: ConfigData) {
        Timber.d("Attempting to save configuration to DataStore: W'=${config.wPrime}, CP=${config.criticalPower}, calculateCP=${config.calculateCp}")
        context.dataStore.edit { preferences ->
            preferences[W_PRIME_KEY] = config.wPrime
            preferences[CRITICAL_POWER_KEY] = config.criticalPower
            preferences[CALCULATE_CP_KEY] = config.calculateCp
        }
        Timber.i("Configuration successfully saved to DataStore.")
    }

    suspend fun getConfig(): ConfigData {
        Timber.d("Attempting to retrieve configuration from DataStore.")
        return context.dataStore.data.map { preferences ->
            val config = ConfigData(
                wPrime = preferences[W_PRIME_KEY] ?: 0,
                criticalPower = preferences[CRITICAL_POWER_KEY] ?: 0,
                calculateCp = preferences[CALCULATE_CP_KEY] ?: false
            )
            Timber.d("Retrieved configuration: W'=${config.wPrime}, CP=${config.criticalPower}, calculateCP=${config.calculateCp}")
            config
        }.first()
    }

    fun getConfigFlow(): Flow<ConfigData> {
        return context.dataStore.data.map { preferences ->
            ConfigData(
                wPrime = preferences[W_PRIME_KEY] ?: ConfigData.DEFAULT.wPrime, // Use default if null
                criticalPower = preferences[CRITICAL_POWER_KEY] ?: ConfigData.DEFAULT.criticalPower,
                calculateCp = preferences[CALCULATE_CP_KEY] ?: ConfigData.DEFAULT.calculateCp
            )
        }.distinctUntilChanged() // Add distinctUntilChanged here to avoid unnecessary emissions
    }
}