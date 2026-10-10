package com.example.myapplication.data

import com.example.myapplication.BuildConfig
import com.example.myapplication.data.api.ApprovalApi
import com.example.myapplication.data.api.ApprovalRequestDto
import com.example.myapplication.data.api.CreateApprovalBody
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class ApprovalService {
    private val api: ApprovalApi

    init {
        val logger = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
        val apiKey = BuildConfig.APP_SERVER_KEY.takeUnless { it.isBlank() }
        val apiKeyInterceptor = Interceptor { chain ->
            val reqBuilder = chain.request().newBuilder()
            apiKey?.let { reqBuilder.addHeader("x-api-key", it) }
            chain.proceed(reqBuilder.build())
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(apiKeyInterceptor)
            .addInterceptor(logger)
            .build()
        
        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()

        val rawBase = BuildConfig.APP_SERVER_URL.takeUnless { it.isBlank() } ?: "http://10.0.2.2:3000"
        val base = if (rawBase.endsWith("/")) rawBase else "$rawBase/"
        val retrofit = Retrofit.Builder()
            .baseUrl(base)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
        api = retrofit.create(ApprovalApi::class.java)
    }

    suspend fun createApproval(email: String, deviceId: String, deviceName: String): ApprovalRequestDto? {
        return try {
            val body = CreateApprovalBody(null, email, deviceId, deviceName)
            val res = api.createApproval(body)
            if (res.isSuccessful) res.body() else null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getApproval(id: String): ApprovalRequestDto? {
        return try {
            val res = api.getApproval(id)
            if (res.isSuccessful) res.body() else null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getApprovals(email: String): List<ApprovalRequestDto> {
        return try {
            val res = api.getApprovals(email)
            if (res.isSuccessful) res.body() ?: emptyList() else emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun approve(id: String): ApprovalRequestDto? {
        return try {
            val res = api.approve(id)
            if (res.isSuccessful) res.body() else null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun deny(id: String): ApprovalRequestDto? {
        return try {
            val res = api.deny(id)
            if (res.isSuccessful) res.body() else null
        } catch (e: Exception) {
            null
        }
    }
}

