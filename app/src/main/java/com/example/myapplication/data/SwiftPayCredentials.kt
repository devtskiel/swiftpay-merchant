package com.example.myapplication.data

import com.example.myapplication.BuildConfig
import kotlinx.coroutines.flow.first

data class SwiftPayCredentials(
    val hasBackendSession: Boolean
) {
    val hasApiKey: Boolean = hasBackendSession
}

suspend fun SettingsManager.loadSwiftPayCredentials(): SwiftPayCredentials {
    return SwiftPayCredentials(!backendAccessToken.first().isNullOrBlank())
}

suspend fun SettingsManager.createSwiftPayService(): SwiftPayService {
    return SwiftPayService(
        backendBaseUrl = BuildConfig.PAYMENT_BACKEND_URL,
        accessToken = backendAccessToken.first()
    )
}
