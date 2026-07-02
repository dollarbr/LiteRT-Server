package com.litert.server.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

data class AppSettings(
    val serverPort: Int = 8080,
    val backendPreference: String = "AUTO",
    val hfToken: String = "",
    val lastModelPath: String = "",
    val temperature: Float = 0.7f,
    val topK: Int = 40,
    val topP: Float = 0.9f,
    val maxTokens: Int = 1024
)

class SettingsStore(private val context: Context) {

    private object Keys {
        val SERVER_PORT = intPreferencesKey("server_port")
        val BACKEND = stringPreferencesKey("backend_preference")
        val HF_TOKEN = stringPreferencesKey("hf_token")
        val LAST_MODEL = stringPreferencesKey("last_model_path")
        val TEMPERATURE = floatPreferencesKey("temperature")
        val TOP_K = intPreferencesKey("top_k")
        val TOP_P = floatPreferencesKey("top_p")
        val MAX_TOKENS = intPreferencesKey("max_tokens")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            serverPort = p[Keys.SERVER_PORT] ?: 8080,
            backendPreference = p[Keys.BACKEND] ?: "AUTO",
            hfToken = p[Keys.HF_TOKEN] ?: "",
            lastModelPath = p[Keys.LAST_MODEL] ?: "",
            temperature = p[Keys.TEMPERATURE] ?: 0.7f,
            topK = p[Keys.TOP_K] ?: 40,
            topP = p[Keys.TOP_P] ?: 0.9f,
            maxTokens = p[Keys.MAX_TOKENS] ?: 1024
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setServerPort(port: Int) {
        context.dataStore.edit { it[Keys.SERVER_PORT] = port }
    }

    suspend fun setBackendPreference(value: String) {
        context.dataStore.edit { it[Keys.BACKEND] = value }
    }

    suspend fun setHfToken(token: String) {
        context.dataStore.edit { it[Keys.HF_TOKEN] = token }
    }

    suspend fun setLastModelPath(path: String) {
        context.dataStore.edit { it[Keys.LAST_MODEL] = path }
    }

    suspend fun setSampler(temperature: Float, topK: Int, topP: Float, maxTokens: Int) {
        context.dataStore.edit {
            it[Keys.TEMPERATURE] = temperature
            it[Keys.TOP_K] = topK
            it[Keys.TOP_P] = topP
            it[Keys.MAX_TOKENS] = maxTokens
        }
    }
}
