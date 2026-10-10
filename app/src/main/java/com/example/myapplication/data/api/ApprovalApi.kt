package com.example.myapplication.data.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

data class ApprovalRequestDto(
    val requestId: String,
    val email: String,
    val deviceId: String,
    val deviceName: String,
    val status: String,
    val createdAt: Long,
    val expiresAt: Long
)

data class CreateApprovalBody(
    val requestId: String?,
    val email: String,
    val deviceId: String,
    val deviceName: String
)

interface ApprovalApi {
    @POST("/approvals")
    suspend fun createApproval(@Body body: CreateApprovalBody): Response<ApprovalRequestDto>

    @GET("/approvals/{id}")
    suspend fun getApproval(@Path("id") id: String): Response<ApprovalRequestDto>

    @GET("/approvals")
    suspend fun getApprovals(@Query("email") email: String): Response<List<ApprovalRequestDto>>

    @POST("/approvals/{id}/approve")
    suspend fun approve(@Path("id") id: String): Response<ApprovalRequestDto>

    @POST("/approvals/{id}/deny")
    suspend fun deny(@Path("id") id: String): Response<ApprovalRequestDto>
}

