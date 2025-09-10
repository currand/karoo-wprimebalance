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

class ConfigurationManager(
    private val context: Context,
){


    companion object {
        private val W_PRIME_KEY = intPreferencesKey("w_prime")
        private val CRITICAL_POWER_KEY = intPreferencesKey("critical_power")
        private val CALCULATE_CP_KEY = booleanPreferencesKey("calculateCp")
        private val USE_KAROO_FTP_KEY = booleanPreferencesKey("useKarooFtp")
        private val MATCH_JOULE_PERCENT_KEY = intPreferencesKey("matchJoulePercent")
        private val MIN_MATCH_DURATION_KEY = intPreferencesKey("minMatchDuration")
        private val MATCH_POWER_PERCENT_KEY = intPreferencesKey("matchPowerPercent")

    }

    suspend fun saveConfig(config: ConfigData) {
        Timber.d("Attempting to save configuration to DataStore: $config")
        context.dataStore.edit { preferences ->
            preferences[W_PRIME_KEY] = config.wPrime
            preferences[CRITICAL_POWER_KEY] = config.criticalPower
            preferences[CALCULATE_CP_KEY] = config.calculateCp
            preferences[USE_KAROO_FTP_KEY] = config.useKarooFtp
            preferences[MATCH_JOULE_PERCENT_KEY] = config.matchJoulePercent
            preferences[MIN_MATCH_DURATION_KEY] = config.minMatchDuration
            preferences[MATCH_POWER_PERCENT_KEY] = config.matchPowerPercent

        }
        Timber.i("Configuration successfully saved to DataStore.")
    }

    suspend fun getConfig(): ConfigData {
        Timber.d("Attempting to retrieve configuration from DataStore.")
        return context.dataStore.data.map { preferences ->
            val config = ConfigData(
                wPrime = preferences[W_PRIME_KEY] ?: ConfigData.DEFAULT.wPrime,
                criticalPower = preferences[CRITICAL_POWER_KEY] ?: ConfigData.DEFAULT.criticalPower,
                calculateCp = preferences[CALCULATE_CP_KEY] ?: ConfigData.DEFAULT.calculateCp,
                useKarooFtp = preferences[USE_KAROO_FTP_KEY] ?: ConfigData.DEFAULT.useKarooFtp,
                matchJoulePercent = preferences[MATCH_JOULE_PERCENT_KEY] ?: ConfigData.DEFAULT.matchJoulePercent,
                minMatchDuration = preferences[MIN_MATCH_DURATION_KEY] ?: ConfigData.DEFAULT.minMatchDuration,
                matchPowerPercent = preferences[MATCH_POWER_PERCENT_KEY] ?: ConfigData.DEFAULT.matchPowerPercent
            )
            Timber.d("Retrieved configuration: $config")
            config
        }.first()
    }

    fun getConfigFlow(): Flow<ConfigData> {
        return context.dataStore.data.map { preferences ->
            ConfigData(
                wPrime = preferences[W_PRIME_KEY] ?: ConfigData.DEFAULT.wPrime, // Use default if null
                criticalPower = preferences[CRITICAL_POWER_KEY] ?: ConfigData.DEFAULT.criticalPower,
                calculateCp = preferences[CALCULATE_CP_KEY] ?: ConfigData.DEFAULT.calculateCp,
                useKarooFtp = preferences[USE_KAROO_FTP_KEY] ?: ConfigData.DEFAULT.useKarooFtp,
                matchJoulePercent = preferences[MATCH_JOULE_PERCENT_KEY] ?: ConfigData.DEFAULT.matchJoulePercent,
                minMatchDuration = preferences[MIN_MATCH_DURATION_KEY] ?: ConfigData.DEFAULT.minMatchDuration,
                matchPowerPercent = preferences[MATCH_POWER_PERCENT_KEY] ?: ConfigData.DEFAULT.matchPowerPercent

            )
        }.distinctUntilChanged() // Add distinctUntilChanged here to avoid unnecessary emissions
    }
}