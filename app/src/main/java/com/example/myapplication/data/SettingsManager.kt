package com.example.myapplication.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.example.myapplication.util.DebugLogger
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsManager(private val context: Context) {

    companion object {
        val SWIFTPAY_ACCESS_KEY = stringPreferencesKey("swiftpay_access_key")
        val SWIFTPAY_SECRET_KEY = stringPreferencesKey("swiftpay_secret_key")
        val SWIFTPAY_API_KEY = stringPreferencesKey("swiftpay_api_key")
        val SWIFTPAY_BASE_URL = stringPreferencesKey("swiftpay_base_url")
        val SWIFTPAY_MERCHANT_ID = stringPreferencesKey("swiftpay_merchant_id")
        val SWIFTPAY_TERMINAL_ID = stringPreferencesKey("swiftpay_terminal_id")
        val WALLET_BALANCE = stringPreferencesKey("wallet_balance")
        val MERCHANT_ALIAS = stringPreferencesKey("merchant_alias")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val IS_LOGGED_IN = stringPreferencesKey("is_logged_in")
        val LOGGED_IN_EMAIL = stringPreferencesKey("logged_in_email")
        val SESSION_TOKEN = stringPreferencesKey("session_token")
    }

    val accessKey: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[SWIFTPAY_ACCESS_KEY] ?: preferences[SWIFTPAY_API_KEY]
    }

    val secretKey: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[SWIFTPAY_SECRET_KEY]
    }

    val apiKey: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[SWIFTPAY_ACCESS_KEY] ?: preferences[SWIFTPAY_API_KEY]
    }

    val baseUrl: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[SWIFTPAY_BASE_URL]
    }

    val merchantId: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[SWIFTPAY_MERCHANT_ID]
    }

    val terminalId: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[SWIFTPAY_TERMINAL_ID]
    }

    val merchantAlias: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[MERCHANT_ALIAS]
    }

    suspend fun saveAccessKey(key: String) {
        context.dataStore.edit { preferences ->
            preferences[SWIFTPAY_ACCESS_KEY] = key
            preferences[SWIFTPAY_API_KEY] = key
        }
    }

    suspend fun saveSecretKey(key: String) {
        context.dataStore.edit { preferences ->
            preferences[SWIFTPAY_SECRET_KEY] = key
        }
    }

    suspend fun saveApiKey(key: String) {
        context.dataStore.edit { preferences ->
            preferences[SWIFTPAY_ACCESS_KEY] = key
            preferences[SWIFTPAY_API_KEY] = key
        }
    }

    suspend fun saveBaseUrl(url: String) {
        context.dataStore.edit { preferences ->
            preferences[SWIFTPAY_BASE_URL] = url
        }
    }

    suspend fun saveMerchantId(mid: String) {
        context.dataStore.edit { preferences ->
            preferences[SWIFTPAY_MERCHANT_ID] = mid
        }
    }

    suspend fun saveTerminalId(tid: String) {
        context.dataStore.edit { preferences ->
            preferences[SWIFTPAY_TERMINAL_ID] = tid
        }
    }

    suspend fun saveWalletBalance(balance: String) {
        context.dataStore.edit { preferences ->
            preferences[WALLET_BALANCE] = balance
        }
    }

    val walletBalance: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[WALLET_BALANCE]
    }

    suspend fun saveMerchantAlias(alias: String) {
        context.dataStore.edit { preferences ->
            preferences[MERCHANT_ALIAS] = alias
        }
    }

    suspend fun saveThemeMode(mode: String) {
        context.dataStore.edit { preferences ->
            preferences[THEME_MODE] = mode
        }
    }

    val themeMode: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[THEME_MODE]
    }

    val isLoggedIn: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[IS_LOGGED_IN] == "true"
    }

    val loggedInEmail: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[LOGGED_IN_EMAIL]
    }

    suspend fun setLoggedIn(email: String, loggedIn: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[IS_LOGGED_IN] = if (loggedIn) "true" else "false"
            if (loggedIn) {
                preferences[LOGGED_IN_EMAIL] = email
                DebugLogger.logLoginSuccess(email)
            } else {
                preferences.remove(LOGGED_IN_EMAIL)
                DebugLogger.logLogout(email)
            }
        }
    }

    suspend fun clearSession() {
        context.dataStore.edit { preferences ->
            preferences[IS_LOGGED_IN] = "false"
            preferences.remove(LOGGED_IN_EMAIL)
        }
    }

    suspend fun saveSessionToken(session: SessionToken) {
        context.dataStore.edit { preferences ->
            val json = Json.encodeToString(session)
            preferences[SESSION_TOKEN] = json
        }
    }

    val sessionToken: Flow<SessionToken?> = context.dataStore.data.map { preferences ->
        val json = preferences[SESSION_TOKEN]
        if (json != null) {
            try {
                Json.decodeFromString<SessionToken>(json)
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }

    suspend fun getSessionToken(): Flow<SessionToken?> {
        return sessionToken
    }

    suspend fun clearSessionToken() {
        context.dataStore.edit { preferences ->
            preferences.remove(SESSION_TOKEN)
        }
    }
}
