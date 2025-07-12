package com.currand60.wprimebalance.managers

import android.content.Context
import android.content.SharedPreferences
import com.currand60.wprimebalance.data.ConfigData
import kotlinx.serialization.json.Json
import timber.log.Timber


class ConfigurationManager(context: Context){
    private val prefs: SharedPreferences = context.getSharedPreferences("wprimebal_config", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true}

    fun saveConfig(config: ConfigData) {
        val jsonString = json.encodeToString(config)
        prefs.edit().putString("config", jsonString).apply()
        Timber.d("Config saved:", jsonString.toString())
    }

    fun getConfig(): ConfigData {
        val jsonString = prefs.getString("config", null)
        return if (jsonString != null) {
            Timber.d("Config output:", jsonString.toString())
            json.decodeFromString(jsonString)
        } else {
            Timber.d("Error loading config file")
            ConfigData()
        }
    }
}