package com.example.myapplication.data.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url

interface SwiftPayApi {

    // --- Order / Checkout Endpoints ---
    @POST("v1/swiftpay/create-order")
    suspend fun createOrder(
        @Header("Authorization") auth: String? = null,
        @Header("X-API-Key") apiKey: String? = null,
        @Body request: SwiftPayOrderRequest
    ): Response<SwiftPayOrderResponse>

    @GET("v1/swiftpay/status/{identifier}")
    suspend fun getOrderStatus(
        @Header("Authorization") auth: String? = null,
        @Header("X-API-Key") apiKey: String? = null,
        @Path("identifier") identifier: String
    ): Response<SwiftPayStatusResponse>

    // --- Payments Endpoints ---
    @POST("v1/payments/create")
    suspend fun createPayment(
        @Header("Authorization") auth: String? = null,
        @Header("X-API-Key") apiKey: String? = null,
        @Body request: SwiftPayOrderRequest
    ): Response<SwiftPayOrderResponse>

    @GET("v1/payments/{payment_id}")
    suspend fun getPaymentStatus(
        @Header("Authorization") auth: String? = null,
        @Header("X-API-Key") apiKey: String? = null,
        @Path("payment_id") paymentId: String
    ): Response<SwiftPayStatusResponse>

    // --- Dynamic QR & Payment Links ---
    @POST("v1/magpie/qr/dynamic")
    suspend fun createDynamicQr(
        @Header("Authorization") auth: String? = null,
        @Header("X-API-Key") apiKey: String? = null,
        @Body request: SwiftPayQrRequest
    ): Response<SwiftPayQrResponse>

    @POST("v1/xend/create-qr-code")
    suspend fun createXendQrCode(
        @Header("Authorization") auth: String? = null,
        @Header("X-API-Key") apiKey: String? = null,
        @Body request: SwiftPayQrRequest
    ): Response<SwiftPayQrResponse>

    @POST("v1/xend/create-payment-link")
    suspend fun createPaymentLink(
        @Header("Authorization") auth: String? = null,
        @Header("X-API-Key") apiKey: String? = null,
        @Body request: SwiftPayPaymentLinkRequest
    ): Response<SwiftPayPaymentLinkResponse>

    // --- Disbursements ---
    @POST("v1/swiftpay/disbursements/send")
    suspend fun sendDisbursement(
        @Header("Authorization") auth: String? = null,
        @Header("X-API-Key") apiKey: String? = null,
        @Body request: SwiftPayDisbursementRequest
    ): Response<SwiftPayDisbursementResponse>

    @GET("v1/swiftpay/disbursements")
    suspend fun getDisbursements(
        @Header("Authorization") auth: String? = null,
        @Header("X-API-Key") apiKey: String? = null
    ): Response<List<SwiftPayDisbursementResponse>>

    @GET("v1/swiftpay/institutions")
    suspend fun getInstitutions(
        @Header("Authorization") auth: String? = null,
        @Header("X-API-Key") apiKey: String? = null
    ): Response<List<SwiftPayInstitution>>

    // --- Wallet Endpoints ---
    @GET("v1/wallet/balance")
    suspend fun getWalletBalance(
        @Header("Authorization") auth: String? = null,
        @Header("X-API-Key") apiKey: String? = null,
        @Query("currency") currency: String = "PHP"
    ): Response<WalletBalanceResponse>

    @GET("v1/wallet/transactions")
    suspend fun getWalletTransactions(
        @Header("Authorization") auth: String? = null,
        @Header("X-API-Key") apiKey: String? = null,
        @Query("skip") skip: Int = 0,
        @Query("limit") limit: Int = 20,
        @Query("currency") currency: String? = "PHP"
    ): Response<List<WalletTransaction>>

    // --- Generic / Full URL Fallbacks ---
    @GET
    suspend fun getFullUrl(
        @Url url: String,
        @Header("Authorization") auth: String? = null,
        @Header("X-API-Key") apiKey: String? = null
    ): Response<SwiftPayStatusResponse>
}
