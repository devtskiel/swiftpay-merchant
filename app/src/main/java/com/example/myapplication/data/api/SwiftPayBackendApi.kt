package com.example.myapplication.data.api

import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

@Serializable
data class BackendOrderRequest(
    val amount: Double,
    val referenceNo: String,
    val currency: String = "PHP",
    val details: Map<String, String> = emptyMap()
)

@Serializable
data class BackendOrderResponse(
    val paymentId: String,
    val referenceNo: String,
    val status: String,
    val redirectUrl: String
)

@Serializable
data class BackendStatusResponse(
    val paymentId: String? = null,
    val status: String? = null
)

@Serializable
data class BackendWalletBalanceResponse(
    val balance: Double,
    val accountNumber: String
)

@Serializable
data class BackendPaymentLinkRequest(
    val amount: Double,
    val currency: String = "PHP",
    val title: String? = null,
    val description: String? = null,
    val referenceNo: String? = null
)

@Serializable
data class BackendPaymentLinkResponse(
    val id: String? = null,
    val code: String,
    val paymentUrl: String,
    val linkStatus: String? = null,
    val status: String? = null,
    val amount: Double? = null,
    val description: String? = null
)

@Serializable
data class BackendDisbursementRequest(
    val amount: Double,
    val referenceNo: String,
    val channel: String = "INSTAPAY",
    val institutionCode: String,
    val recipientInformation: BackendRecipientInformation,
    val remarks: String? = null
)

@Serializable
data class BackendRecipientInformation(
    val accountNumber: String,
    val fullName: String
)

@Serializable
data class BackendDisbursementResponse(
    val id: String,
    val referenceNo: String? = null,
    val status: String = "PENDING"
)

@Serializable
data class BackendDisbursementRecord(
    val id: String? = null,
    val merchantReferenceNo: String? = null,
    val status: String? = null,
    val amount: Double? = null,
    val createdOn: String? = null,
    val createdAt: String? = null,
    val creditInformation: BackendCreditInformation? = null
)

@Serializable
data class BackendCreditInformation(val amount: Double? = null)

interface SwiftPayBackendApi {
    @GET("v1/wallet/balance")
    suspend fun getWalletBalance(): Response<BackendWalletBalanceResponse>

    @POST("v1/payments/orders")
    suspend fun createOrder(@Body request: BackendOrderRequest): Response<BackendOrderResponse>

    @GET("v1/payments/{paymentToken}/status")
    suspend fun getPaymentStatus(@Path("paymentToken") paymentToken: String): Response<BackendStatusResponse>

    @POST("v1/payment-links")
    suspend fun createPaymentLink(@Body request: BackendPaymentLinkRequest): Response<BackendPaymentLinkResponse>

    @GET("v1/payment-links/{code}")
    suspend fun getPaymentLink(@Path("code") code: String): Response<BackendPaymentLinkResponse>

    @DELETE("v1/payment-links/{code}")
    suspend fun invalidatePaymentLink(@Path("code") code: String): Response<Unit>

    @POST("v1/disbursements")
    suspend fun sendDisbursement(@Body request: BackendDisbursementRequest): Response<BackendDisbursementResponse>

    @GET("v1/disbursements")
    suspend fun getDisbursements(): Response<List<BackendDisbursementRecord>>

    @GET("v1/disbursements/{id}")
    suspend fun getDisbursement(@Path("id") id: String): Response<BackendDisbursementRecord>
}