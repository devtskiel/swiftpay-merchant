package com.example.myapplication.data

import com.example.myapplication.BuildConfig
import com.example.myapplication.util.DebugLogger
import kotlinx.coroutines.flow.first

data class SwiftPayCredentials(
    val accessKey: String?,
    val secretKey: String?,
    val apiKey: String?,
    val baseUrl: String?,
    val merchantId: String?,
    val terminalId: String?
) {
    val hasAccessKey: Boolean = !accessKey.isNullOrBlank() && accessKey != MISSING_KEY
    val hasApiKey: Boolean = !apiKey.isNullOrBlank() && apiKey != MISSING_KEY

    companion object {
        const val MISSING_KEY = "MISSING_KEY"
    }
}

suspend fun SettingsManager.loadSwiftPayCredentials(): SwiftPayCredentials {
    val storedAccessKey = accessKey.first()
    val storedSecretKey = secretKey.first()
    val storedBaseUrl = baseUrl.first()
    val storedMerchantId = merchantId.first()
    val storedTerminalId = terminalId.first()

    val accessKeyVal = storedAccessKey.takeUnless { it.isNullOrBlank() } ?: BuildConfig.SWIFTPAY_ACCESS_KEY
    val secretKeyVal = storedSecretKey.takeUnless { it.isNullOrBlank() } ?: BuildConfig.SWIFTPAY_SECRET_KEY

    val credentials = SwiftPayCredentials(
        accessKey = accessKeyVal,
        secretKey = secretKeyVal,
        apiKey = accessKeyVal,
        baseUrl = storedBaseUrl.takeUnless { it.isNullOrBlank() } ?: BuildConfig.SWIFTPAY_BASE_URL,
        merchantId = storedMerchantId.takeUnless { it.isNullOrBlank() } ?: BuildConfig.SWIFTPAY_MERCHANT_ID,
        terminalId = storedTerminalId.takeUnless { it.isNullOrBlank() } ?: BuildConfig.SWIFTPAY_TERMINAL_ID
    )

    DebugLogger.logCredentialsLoaded(credentials.hasAccessKey || credentials.hasApiKey, true)
    return credentials
}

suspend fun SettingsManager.createSwiftPayService(): SwiftPayService {
    val credentials = loadSwiftPayCredentials()
    return SwiftPayService(
        customAccessKey = credentials.accessKey,
        customSecretKey = credentials.secretKey,
        customApiKey = credentials.apiKey,
        customBaseUrl = credentials.baseUrl,
        customMerchantId = credentials.merchantId,
        customTerminalId = credentials.terminalId
    )
}
