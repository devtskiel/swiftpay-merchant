package com.example.myapplication.data

import com.example.myapplication.BuildConfig
import com.example.myapplication.data.api.BackendAuthApi
import com.example.myapplication.data.api.BackendSessionResponse
import com.example.myapplication.data.api.RequestOtpBody
import com.example.myapplication.data.api.VerifyOtpBody
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.net.URI

internal fun resolveBackendBaseUrl(configuredUrl: String): String {
    val value = configuredUrl.takeUnless { it.isBlank() } ?: "http://10.0.2.2:3000/"
    val uri = URI(value)
    val localHosts = setOf("localhost", "127.0.0.1", "10.0.2.2")
    require(uri.scheme == "https" || (uri.scheme == "http" && uri.host in localHosts)) {
        "The payment backend must use HTTPS outside local development"
    }
    require(uri.userInfo == null && uri.host != null) { "Invalid payment backend URL" }
    return if (value.endsWith("/")) value else "$value/"
}

class BackendAuthService {
    private val json = Json { ignoreUnknownKeys = true }
    private val baseUrl = resolveBackendBaseUrl(BuildConfig.PAYMENT_BACKEND_URL)

    private val api = Retrofit.Builder()
        .baseUrl(baseUrl)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(BackendAuthApi::class.java)

    suspend fun requestOtp(email: String): Result<Unit> = runCatching {
        val response = api.requestOtp(RequestOtpBody(email))
        if (!response.isSuccessful) throw IllegalStateException("Unable to send sign-in code (${response.code()})")
    }

    suspend fun verifyOtp(email: String, code: String): Result<BackendSessionResponse> = runCatching {
        val response = api.verifyOtp(VerifyOtpBody(email, code))
        if (response.isSuccessful) response.body() ?: throw IllegalStateException("Empty sign-in response")
        else throw IllegalStateException(if (response.code() == 401) "Invalid or expired verification code" else "Sign-in failed (${response.code()})")
    }
}