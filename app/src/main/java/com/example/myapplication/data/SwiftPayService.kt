package com.example.myapplication.data

import android.util.Log
import com.example.myapplication.BuildConfig
import com.example.myapplication.bridge.PaymentData
import com.example.myapplication.data.api.CardDetails
import com.example.myapplication.data.api.InternalTransaction
import com.example.myapplication.data.api.PaymentChannel
import com.example.myapplication.data.api.SwiftPayApi
import com.example.myapplication.data.api.SwiftPayDisbursementRequest
import com.example.myapplication.data.api.SwiftPayDisbursementResponse
import com.example.myapplication.data.api.SwiftPayErrorResponse
import com.example.myapplication.data.api.SwiftPayOrderRequest
import com.example.myapplication.data.api.SwiftPayOrderResponse
import com.example.myapplication.data.api.SwiftPayPaymentLinkRequest
import com.example.myapplication.data.api.SwiftPayPaymentLinkResponse
import com.example.myapplication.data.api.SwiftPayQrRequest
import com.example.myapplication.data.api.SwiftPayQrResponse
import com.example.myapplication.data.api.SwiftPayStatusResponse
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import okhttp3.MediaType.Companion.toMediaType
import java.util.UUID

class SwiftPayService(
    customAccessKey: String? = null,
    customSecretKey: String? = null,
    customApiKey: String? = null,
    customBaseUrl: String? = null,
    customMerchantId: String? = null,
    customTerminalId: String? = null
) {

    private val accessKey = customAccessKey?.takeUnless { it.isBlank() }
        ?: customApiKey?.takeUnless { it.isBlank() }
        ?: BuildConfig.SWIFTPAY_ACCESS_KEY.takeUnless { it.isBlank() }
        ?: BuildConfig.SWIFTPAY_API_KEY
    private val secretKey = customSecretKey?.takeUnless { it.isBlank() }
        ?: BuildConfig.SWIFTPAY_SECRET_KEY
    private val apiKey = accessKey
    private val rawBaseUrl = customBaseUrl?.takeUnless { it.isBlank() } ?: BuildConfig.SWIFTPAY_BASE_URL
    private val baseUrl = if (rawBaseUrl.endsWith("/")) rawBaseUrl else "$rawBaseUrl/"
    private val merchantId = customMerchantId?.takeUnless { it.isBlank() } ?: BuildConfig.SWIFTPAY_MERCHANT_ID
    private val terminalId = customTerminalId?.takeUnless { it.isBlank() } ?: BuildConfig.SWIFTPAY_TERMINAL_ID

    val hasApiKey: Boolean = !apiKey.isNullOrBlank() && apiKey != SwiftPayCredentials.MISSING_KEY

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val okHttpClient: OkHttpClient by lazy {
        val logger = HttpLoggingInterceptor { message -> Log.d("SwiftPayService", message) }
        logger.level = HttpLoggingInterceptor.Level.BODY

        OkHttpClient.Builder()
            .addInterceptor { chain ->
                val original = chain.request()
                val requestBuilder = original.newBuilder()

                if (hasApiKey) {
                    requestBuilder.header("X-Access-Key", accessKey)
                    requestBuilder.header("X-API-Key", accessKey)
                    if (!secretKey.isNullOrBlank()) {
                        requestBuilder.header("X-Secret-Key", secretKey)
                    }
                    if (!merchantId.isNullOrBlank()) {
                        requestBuilder.header("X-Merchant-ID", merchantId)
                    }
                    if (original.header("Authorization") == null) {
                        requestBuilder.header("Authorization", "Bearer $accessKey")
                    }
                }
                chain.proceed(requestBuilder.build())
            }
            .addInterceptor(logger)
            .build()
    }

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    private val api: SwiftPayApi by lazy {
        retrofit.create(SwiftPayApi::class.java)
    }

    private fun <T> parseError(response: Response<T>): String {
        return try {
            val errorBody = response.errorBody()?.string()
            if (!errorBody.isNullOrBlank()) {
                val errorObj = json.decodeFromString<SwiftPayErrorResponse>(errorBody)
                errorObj.message ?: errorObj.detail ?: "HTTP ${response.code()}"
            } else {
                "HTTP ${response.code()}"
            }
        } catch (e: Exception) {
            "HTTP ${response.code()}"
        }
    }

    private fun missingApiKey() = Result.failure<Nothing>(
        Exception("Configuration Error: SwiftPay Credentials missing. Set SWIFTPAY_ACCESS_KEY / SWIFTPAY_SECRET_KEY / SWIFTPAY_MERCHANT_ID in local.properties.")
    )

    // --- Authentication & OTP ---

    suspend fun sendCustomOtp(email: String, code: String): Result<Boolean> {
        return Result.success(true)
    }

    suspend fun onlineLogin(email: String, password: String): Result<Boolean> {
        return Result.success(true)
    }

    suspend fun verifyOnlineCode(email: String, code: String): Result<Boolean> {
        return Result.success(true)
    }

    // --- Order & Payment Creation ---

    suspend fun createCheckout(data: PaymentData): Result<SwiftPayOrderResponse> {
        if (!hasApiKey) return missingApiKey()
        return try {
            val request = SwiftPayOrderRequest(
                amount = data.amount,
                referenceNo = "REF-${UUID.randomUUID().toString().take(8)}",
                description = data.description,
                currency = data.currency
            )
            val response = api.createOrder(apiKey = apiKey, request = request)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception(parseError(response)))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getCheckoutStatus(checkoutId: String): Result<SwiftPayStatusResponse> {
        if (!hasApiKey) return missingApiKey()
        return try {
            val response = api.getOrderStatus(apiKey = apiKey, identifier = checkoutId)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception(parseError(response)))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getPaymentStatus(paymentId: String): Result<SwiftPayStatusResponse> {
        if (!hasApiKey) return missingApiKey()
        return try {
            val response = api.getPaymentStatus(apiKey = apiKey, paymentId = paymentId)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception(parseError(response)))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- Payment Links ---

    suspend fun createPaymentLink(
        amount: Double,
        description: String? = null,
        merchantAliasOverride: String? = null
    ): Result<SwiftPayPaymentLinkResponse> {
        if (!hasApiKey) return missingApiKey()
        return try {
            val ref = "LINK-${UUID.randomUUID().toString().take(8)}"
            val request = SwiftPayPaymentLinkRequest(
                amount = if (amount > 0) amount else null,
                description = description ?: "SwiftPay Payment Link",
                referenceNo = ref
            )
            val response = api.createPaymentLink(apiKey = apiKey, request = request)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val orderReq = SwiftPayOrderRequest(
                    amount = if (amount > 0) amount else 1.0,
                    referenceNo = ref,
                    description = description ?: "SwiftPay Payment Link"
                )
                val orderResp = api.createOrder(apiKey = apiKey, request = orderReq)
                if (orderResp.isSuccessful && orderResp.body() != null) {
                    val body = orderResp.body()!!
                    Result.success(
                        SwiftPayPaymentLinkResponse(
                            id = body.id ?: body.orderId,
                            paymentLinkUrl = body.checkoutUrl ?: body.paymentUrl,
                            status = body.status,
                            amount = body.amount
                        )
                    )
                } else {
                    Result.failure(Exception(parseError(response)))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- Dynamic QR ---

    suspend fun createDynamicQr(amount: Double): Result<SwiftPayQrResponse> {
        if (!hasApiKey) return missingApiKey()
        return try {
            val ref = "QR-${UUID.randomUUID().toString().take(8)}"
            val request = SwiftPayQrRequest(
                amount = amount,
                referenceNo = ref,
                description = "SwiftPay Dynamic QR"
            )
            val response = api.createDynamicQr(apiKey = apiKey, request = request)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val xendResp = api.createXendQrCode(apiKey = apiKey, request = request)
                if (xendResp.isSuccessful && xendResp.body() != null) {
                    Result.success(xendResp.body()!!)
                } else {
                    Result.failure(Exception(parseError(response)))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- Wallet & Balance ---

    suspend fun getWalletBalance(): Double {
        if (!hasApiKey) return 0.0
        return try {
            val response = api.getWalletBalance(apiKey = apiKey)
            if (response.isSuccessful && response.body() != null) {
                response.body()!!.balance ?: response.body()!!.availableBalance ?: 0.0
            } else {
                0.0
            }
        } catch (e: Exception) {
            0.0
        }
    }

    suspend fun getInternalTransactions(): Result<List<InternalTransaction>> {
        if (!hasApiKey) return missingApiKey()
        return try {
            val response = api.getWalletTransactions(apiKey = apiKey)
            if (response.isSuccessful && response.body() != null) {
                val items = response.body()!!.map { tx ->
                    InternalTransaction(
                        transactionId = tx.id ?: tx.referenceNo ?: UUID.randomUUID().toString().take(8),
                        amount = tx.amount ?: 0.0,
                        status = tx.status ?: "SUCCESS",
                        date = tx.createdAt ?: "Today"
                    )
                }
                Result.success(items)
            } else {
                Result.success(emptyList())
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getTransactionUpdates(): Flow<List<InternalTransaction>> = flow {
        while (true) {
            val txs = getInternalTransactions().getOrDefault(emptyList())
            emit(txs)
            delay(10000)
        }
    }

    fun getPaymentChannels(): List<PaymentChannel> {
        return listOf(
            PaymentChannel("GCash", "ACTIVE", "https://api.pay.live.swiftpay.ph/static/gcash.png"),
            PaymentChannel("Maya Wallet", "ACTIVE", "https://api.pay.live.swiftpay.ph/static/maya.png"),
            PaymentChannel("GrabPay", "ACTIVE", "https://api.pay.live.swiftpay.ph/static/grabpay.png"),
            PaymentChannel("ShopeePay", "ACTIVE", "https://api.pay.live.swiftpay.ph/static/shopeepay.png"),
            PaymentChannel("QR Ph", "ACTIVE", "https://api.pay.live.swiftpay.ph/static/qrph.png"),
            PaymentChannel("BDO Unibank", "ACTIVE", "https://api.pay.live.swiftpay.ph/static/bdo.png"),
            PaymentChannel("BPI", "ACTIVE", "https://api.pay.live.swiftpay.ph/static/bpi.png"),
            PaymentChannel("UnionBank", "ACTIVE", "https://api.pay.live.swiftpay.ph/static/unionbank.png"),
            PaymentChannel("Metrobank", "ACTIVE", "https://api.pay.live.swiftpay.ph/static/metrobank.png"),
            PaymentChannel("Visa / Mastercard", "ACTIVE", "https://api.pay.live.swiftpay.ph/static/card.png")
        )
    }

    // --- Card-Present / Vault Processing ---

    suspend fun processVaultPayment(
        amount: Double,
        cardDetails: CardDetails,
        notes: String? = null
    ): Result<SwiftPayStatusResponse> {
        if (!hasApiKey) return missingApiKey()
        return try {
            val ref = "NFC-${UUID.randomUUID().toString().take(8)}"
            val req = SwiftPayOrderRequest(
                amount = amount,
                referenceNo = ref,
                description = notes ?: "Tap-to-Phone NFC Payment",
                details = mapOf(
                    "card_last4" to cardDetails.number.takeLast(4),
                    "exp_month" to cardDetails.expMonth,
                    "exp_year" to cardDetails.expYear
                )
            )
            val response = api.createOrder(apiKey = apiKey, request = req)
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                Result.success(
                    SwiftPayStatusResponse(
                        id = body.id ?: body.orderId,
                        orderId = body.orderId,
                        status = body.status ?: "APPROVED",
                        paymentStatus = "PAYMENT_SUCCESS",
                        amount = amount,
                        currency = "PHP",
                        referenceNo = ref,
                        verificationUrl = body.checkoutUrl ?: body.paymentUrl
                    )
                )
            } else {
                Result.failure(Exception(parseError(response)))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- Disbursements ---

    suspend fun disburse(
        amount: Double,
        bankCode: String,
        accountNumber: String,
        recipientName: String
    ): Result<SwiftPayDisbursementResponse> {
        if (!hasApiKey) return missingApiKey()
        return try {
            val nameParts = recipientName.trim().split(" ")
            val firstName = nameParts.firstOrNull() ?: "Recipient"
            val lastName = if (nameParts.size > 1) nameParts.drop(1).joinToString(" ") else "N/A"

            val req = SwiftPayDisbursementRequest(
                amount = amount,
                referenceNo = "DISB-${UUID.randomUUID().toString().take(8)}",
                bankCode = bankCode,
                accountNumber = accountNumber,
                firstName = firstName,
                lastName = lastName
            )
            val response = api.sendDisbursement(apiKey = apiKey, request = req)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception(parseError(response)))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getDisbursementTransactions(): Result<List<SwiftPayDisbursementResponse>> {
        if (!hasApiKey) return missingApiKey()
        return try {
            val response = api.getDisbursements(apiKey = apiKey)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.success(emptyList())
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
