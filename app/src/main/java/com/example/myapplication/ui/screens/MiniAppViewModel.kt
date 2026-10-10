package com.example.myapplication.ui.screens

import android.app.Application
import android.nfc.NfcAdapter
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.bridge.PaymentData
import com.example.myapplication.bridge.SwiftPaySDKBridge
import com.example.myapplication.data.SettingsManager
import com.example.myapplication.data.SwiftPayService
import com.example.myapplication.data.TransactionStore
import com.example.myapplication.data.api.InternalTransaction
import com.example.myapplication.data.api.SwiftPayStatusResponse
import com.example.myapplication.data.createSwiftPayService
import com.example.myapplication.data.loadSwiftPayCredentials
import com.example.myapplication.data.mergeTransactions
import com.example.myapplication.data.repository.PaymentRepository
import com.example.myapplication.data.repository.TransactionRepository
import com.example.myapplication.util.findActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface MiniAppUiState {
    object Idle : MiniAppUiState
    data class Processing(val message: String) : MiniAppUiState
    data class Error(val message: String) : MiniAppUiState
    data class PaymentConsent(val data: PaymentData) : MiniAppUiState
    data class PaymentRedirect(val url: String, val data: PaymentData) : MiniAppUiState
    data class PaymentLinkReady(val url: String) : MiniAppUiState
    data class DynamicQrReady(val qrData: String, val amount: Double) : MiniAppUiState

    data class WaitingForNFC(
        val amount: Double,
        val merchantName: String = "Fast Pay Merchant",
        val merchantAddress: String = "Pila, Laguna, PHL",
        val timeLeft: Int = 90,
        val sessionDurationSeconds: Int = 90,
        val errorMessage: String? = null,
        val statusMessage: String? = null
    ) : MiniAppUiState

    data class WaitingForCVV(
        val amount: Double,
        val pan: String,
        val expiry: String,
        val label: String,
        val sessionRef: String
    ) : MiniAppUiState
}

class MiniAppViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsManager = SettingsManager(application)
    private val transactionStore = TransactionStore(application)
    private val repository = TransactionRepository(
        swiftPayService = SwiftPayService(),
        transactionStore = transactionStore
    )
    private val paymentRepository = PaymentRepository(SwiftPayService())

    val walletBalance: StateFlow<Double> = settingsManager.walletBalance
        .map { it?.toDoubleOrNull() ?: 0.0 }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0.0
        )

    private var isLoadingBalance = false

    fun refreshBalance() {
        if (isLoadingBalance) return
        viewModelScope.launch {
            isLoadingBalance = true
            try {
                val service = getService()
                val currentLocalBalance = walletBalance.value
                val freshBalance = service.getWalletBalance()

                if (freshBalance > currentLocalBalance || (currentLocalBalance == 0.0 && freshBalance > 0)) {
                    settingsManager.saveWalletBalance(freshBalance.toString())
                }
            } catch (e: Exception) {
                Log.e("MiniAppViewModel", "Failed to refresh balance", e)
            } finally {
                isLoadingBalance = false
            }
        }
    }

    var uiState by mutableStateOf<MiniAppUiState>(MiniAppUiState.Idle)
        private set

    var transactionStatusMap by mutableStateOf<Map<String, SwiftPayStatusResponse>>(emptyMap())
        private set

    val latestTransactionStatus: Map.Entry<String, SwiftPayStatusResponse>?
        get() = transactionStatusMap.entries.lastOrNull {
            val status = it.value.status?.uppercase()
            status != "PAYMENT_EXPIRED" && status != "EXPIRED"
        }

    var bridge: SwiftPaySDKBridge? = null
    private var nfcTimerJob: Job? = null
    private var threeDSecureTimeoutJob: Job? = null
    private var pendingCheckoutId: String? = null
        set(value) {
            field = value
            if (value != null) {
                viewModelScope.launch {
                    checkPendingPaymentStatus()
                }
            }
        }

    private suspend fun getService(): SwiftPayService {
        return settingsManager.createSwiftPayService()
    }

    fun requestPaymentConsent(data: PaymentData) {
        if (data.amount <= 0) {
            uiState = MiniAppUiState.Error("Invalid payment amount. Amount must be greater than ₱0.00")
            return
        }
        if (data.amount > 1_000_000) {
            uiState = MiniAppUiState.Error("Payment amount exceeds maximum limit of ₱1,000,000")
            return
        }
        uiState = MiniAppUiState.PaymentConsent(data)
    }

    fun onBalanceRequest() {
        viewModelScope.launch {
            val currentLocalBalance = walletBalance.value
            bridge?.sendResponse(currentLocalBalance)

            if (System.currentTimeMillis() - lastLocalCreditTime < 30000) {
                return@launch
            }

            try {
                val freshBalance = getService().getWalletBalance()
                if (freshBalance > currentLocalBalance || (currentLocalBalance == 0.0 && freshBalance > 0)) {
                    settingsManager.saveWalletBalance(freshBalance.toString())
                    bridge?.sendResponse(freshBalance)
                }
            } catch (e: Exception) {
                Log.e("MiniAppViewModel", "Failed to refresh balance from server", e)
            }
        }
    }

    fun onTransactionsRequest() {
        viewModelScope.launch {
            val result = getService().getInternalTransactions()
            val localTransactions = repository.getAllTransactions().first()
            result.onSuccess { transactions ->
                bridge?.sendResponse(mergeTransactions(localTransactions, transactions))
            }.onFailure { error ->
                if (localTransactions.isNotEmpty()) {
                    bridge?.sendResponse(localTransactions)
                } else {
                    val errorMessage = error.message ?: "Failed to fetch transactions"
                    bridge?.sendError(errorMessage)
                }
            }
        }
    }

    fun onPaymentChannelsRequest() {
        viewModelScope.launch {
            val channels = getService().getPaymentChannels()
            bridge?.sendResponse(channels)
        }
    }

    fun onPaymentLinkRequest(data: PaymentData) {
        viewModelScope.launch {
            try {
                uiState = MiniAppUiState.Processing("Generating payment link...")

                val result = getService().createPaymentLink(
                    amount = if (data.amount > 0) data.amount else 0.0,
                    description = data.description
                )

                result.onSuccess { response ->
                    val url = response.paymentLinkUrl
                    if (url != null) {
                        recordLocalTransaction(
                            id = response.id ?: "LINK${System.currentTimeMillis()}",
                            amount = data.amount,
                            status = "PENDING"
                        )

                        uiState = MiniAppUiState.PaymentLinkReady(url)

                        bridge?.sendResponse(mapOf(
                            "paymentLinkUrl" to url,
                            "linkId" to (response.id ?: ""),
                            "description" to (response.description ?: data.description),
                            "hasFixedAmount" to (data.amount > 0),
                            "amount" to data.amount
                        ))

                        Log.i("MiniAppViewModel", "Payment link generated successfully: $url")
                    } else {
                        val errorMsg = "Payment link URL was not provided by SwiftPay server"
                        handlePaymentLinkError(errorMsg)
                    }
                }.onFailure { error ->
                    handlePaymentLinkError(error.message ?: "Failed to generate payment link")
                }
            } catch (e: Exception) {
                handlePaymentLinkError("Unexpected error: ${e.message}")
            }
        }
    }

    private fun handlePaymentLinkError(errorMessage: String) {
        Log.e("MiniAppViewModel", "Payment link error: $errorMessage")
        bridge?.sendError(errorMessage)
        uiState = MiniAppUiState.Error(errorMessage)
    }

    fun onScanNFCCard(amount: Double) {
        if (amount <= 0.0) {
            uiState = MiniAppUiState.Error("Please enter a valid amount before launching Tap to Pay.")
            return
        }

        val webViewContext = bridge?.webView?.context
        val activity = webViewContext?.findActivity()

        if (activity == null || webViewContext == null) {
            uiState = MiniAppUiState.Error("Unable to access device context. Please restart the app and try again.")
            return
        }

        val nfcAdapter = NfcAdapter.getDefaultAdapter(activity)

        if (nfcAdapter == null) {
            uiState = MiniAppUiState.Error("NFC is not supported on this device.")
        } else if (!nfcAdapter.isEnabled) {
            uiState = MiniAppUiState.Error("NFC is disabled. Please enable it in Settings > Connected devices > NFC")
        } else {
            viewModelScope.launch {
                uiState = MiniAppUiState.Processing("Preparing secure NFC session...")
                delay(100)

                bridge?.sendResponse(mapOf("status" to "starting_reader", "message" to "Reader arming..."))

                startNFCTimer(
                    amount = amount,
                    merchantName = "Fast Pay Merchant"
                )
            }
        }
    }

    fun onNfcProgress(message: String) {
        val friendlyMessage = message.toFriendlyNfcMessage()
        viewModelScope.launch {
            (uiState as? MiniAppUiState.WaitingForNFC)?.let { state ->
                uiState = state.copy(statusMessage = friendlyMessage)
            }
        }
    }

    fun onGenerateDynamicQr(amount: Double) {
        viewModelScope.launch {
            uiState = MiniAppUiState.Processing("Generating dynamic QR Ph code...")
            val result = getService().createDynamicQr(amount)

            result.onSuccess { response ->
                val qr = response.qrCode ?: response.qrCodeUrl
                if (qr != null) {
                    recordLocalTransaction(
                        id = response.paymentId ?: "QR${System.currentTimeMillis()}",
                        amount = amount,
                        status = "PENDING"
                    )
                    uiState = MiniAppUiState.DynamicQrReady(qr, amount)
                    bridge?.sendResponse(mapOf("qrCodeBody" to qr))
                } else {
                    val errorMsg = "QR code data was not provided by SwiftPay server"
                    bridge?.sendError(errorMsg)
                    uiState = MiniAppUiState.Error(errorMsg)
                }
            }.onFailure { error ->
                val errorMsg = error.message ?: "QR generation failed"
                bridge?.sendError(errorMsg)
                uiState = MiniAppUiState.Error(errorMsg)
            }
        }
    }

    fun onSendDisbursement(amount: Double, bankCode: String, accountNumber: String, recipientName: String) {
        if (amount <= 0.0) {
            uiState = MiniAppUiState.Error("Please enter a valid transfer amount.")
            return
        }
        viewModelScope.launch {
            uiState = MiniAppUiState.Processing("Processing SwiftPay transfer...")
            val result = getService().disburse(amount, bankCode, accountNumber, recipientName)
            result.onSuccess { response ->
                recordLocalTransaction(
                    id = response.disbursementId ?: response.id ?: "DISB${System.currentTimeMillis()}",
                    amount = -amount,
                    status = response.status ?: "SUCCESS"
                )
                bridge?.sendResponse(mapOf(
                    "status" to "success",
                    "disbursementId" to (response.disbursementId ?: response.id ?: ""),
                    "message" to "Transfer of ₱${"%.2f".format(amount)} to $recipientName ($bankCode) successful!"
                ))
                uiState = MiniAppUiState.Idle
            }.onFailure { error ->
                val errorMsg = error.message ?: "Transfer failed"
                bridge?.sendError(errorMsg)
                uiState = MiniAppUiState.Error(errorMsg)
            }
        }
    }

    private fun startNFCTimer(
        amount: Double,
        merchantName: String = "Fast Pay Merchant",
        merchantAddress: String = "Pila, Laguna, PHL"
    ) {
        nfcTimerJob?.cancel()
        nfcTimerJob = viewModelScope.launch {
            var seconds = NFC_SESSION_SECONDS
            while (seconds >= 0) {
                val currentState = uiState as? MiniAppUiState.WaitingForNFC
                uiState = MiniAppUiState.WaitingForNFC(
                    amount = amount,
                    merchantName = currentState?.merchantName ?: merchantName,
                    merchantAddress = currentState?.merchantAddress ?: merchantAddress,
                    timeLeft = seconds,
                    sessionDurationSeconds = NFC_SESSION_SECONDS,
                    statusMessage = currentState?.statusMessage
                        ?: "Reader ready. Hold contactless card firmly against the back of phone."
                )
                if (seconds == 0) {
                    uiState = MiniAppUiState.WaitingForNFC(
                        amount = amount,
                        merchantName = currentState?.merchantName ?: merchantName,
                        merchantAddress = currentState?.merchantAddress ?: merchantAddress,
                        timeLeft = 0,
                        sessionDurationSeconds = NFC_SESSION_SECONDS,
                        errorMessage = "Scan timed out. Hold card against device for 2-3 seconds and try again."
                    )
                    bridge?.sendError("NFC session timed out after $NFC_SESSION_SECONDS seconds")
                    return@launch
                }
                delay(1000)
                seconds--
            }
        }
    }

    fun retryNfc() {
        val state = uiState as? MiniAppUiState.WaitingForNFC ?: return
        val amount = state.amount
        val merchantName = state.merchantName
        val merchantAddress = state.merchantAddress

        viewModelScope.launch {
            uiState = MiniAppUiState.Processing("Restarting NFC reader...")
            delay(300)
            startNFCTimer(amount, merchantName, merchantAddress)
        }
    }

    private fun start3DSTimeout(checkoutId: String) {
        threeDSecureTimeoutJob?.cancel()
        threeDSecureTimeoutJob = viewModelScope.launch {
            delay(10 * 60 * 1000L)
            if (pendingCheckoutId == checkoutId) {
                pendingCheckoutId = null
                val errorMsg = "3D Secure verification timed out (10 minutes)."
                bridge?.sendError(errorMsg)
                uiState = MiniAppUiState.Error(errorMsg)
            }
        }
    }

    fun onCvvEntered(cvv: String) {
        val state = uiState as? MiniAppUiState.WaitingForCVV ?: return
        val amount = state.amount
        val pan = state.pan
        val expiry = state.expiry
        val label = state.label
        val name = "Fast Pay Merchant"

        viewModelScope.launch {
            try {
                uiState = MiniAppUiState.Processing("Authorizing $label ending in ${pan.takeLast(4)}...")

                val expiryParts = expiry.split("/")
                val month = (expiryParts.getOrNull(0) ?: "12").padStart(2, '0')
                val rawYear = expiryParts.getOrNull(1) ?: "28"
                val year = if (rawYear.length == 2) "20$rawYear" else rawYear

                val result = getService().processVaultPayment(
                    amount = amount,
                    cardDetails = com.example.myapplication.data.api.CardDetails(
                        number = pan,
                        expMonth = month,
                        expYear = year,
                        cvc = cvv
                    ),
                    notes = state.sessionRef
                )

                result.onSuccess { response ->
                    val paymentId = response.id ?: response.orderId
                    val status = response.status?.uppercase() ?: "PENDING"

                    if (response.verificationUrl != null) {
                        recordLocalTransaction(
                            id = paymentId ?: "VAULT${System.currentTimeMillis()}",
                            amount = amount,
                            status = status
                        )

                        pendingCheckoutId = paymentId
                        start3DSTimeout(paymentId ?: "")

                        uiState = MiniAppUiState.PaymentRedirect(
                            url = response.verificationUrl,
                            data = PaymentData(amount, "PHP", name)
                        )
                    } else if (status == "SUCCESS" || status == "APPROVED" || status == "CAPTURED") {
                        recordLocalTransaction(
                            id = paymentId ?: "VAULT${System.currentTimeMillis()}",
                            amount = amount,
                            status = "SUCCESS"
                        )
                        bridge?.sendResponse(mapOf(
                            "status" to "success",
                            "paymentId" to paymentId,
                            "message" to "Payment of ₱${"%.2f".format(amount)} via $label successful!"
                        ))
                        uiState = MiniAppUiState.Idle
                    } else {
                        recordLocalTransaction(
                            id = paymentId ?: "VAULT${System.currentTimeMillis()}",
                            amount = amount,
                            status = status
                        )
                        bridge?.sendResponse(mapOf(
                            "status" to status.lowercase(),
                            "paymentId" to paymentId,
                            "message" to "Payment is $status."
                        ))
                        uiState = MiniAppUiState.Idle
                    }
                }.onFailure { error ->
                    val errorMsg = error.message ?: "Vault payment failed"
                    bridge?.sendError(errorMsg)
                    uiState = MiniAppUiState.Error(errorMsg)
                }
            } catch (e: Exception) {
                val errorMsg = "An unexpected error occurred: ${e.message}"
                bridge?.sendError(errorMsg)
                uiState = MiniAppUiState.Error(errorMsg)
            }
        }
    }

    fun onSaveSettings(data: Map<String, String>) {
        viewModelScope.launch {
            uiState = MiniAppUiState.Processing("Saving settings...")
            data["apiKey"]?.takeIf { it.isNotBlank() }?.let { settingsManager.saveApiKey(it) }
            data["baseUrl"]?.takeIf { it.isNotBlank() }?.let { settingsManager.saveBaseUrl(it) }
            data["merchantId"]?.takeIf { it.isNotBlank() }?.let { settingsManager.saveMerchantId(it) }
            data["terminalId"]?.takeIf { it.isNotBlank() }?.let { settingsManager.saveTerminalId(it) }
            data["merchantAlias"]?.takeIf { it.isNotBlank() }?.let { settingsManager.saveMerchantAlias(it) }

            bridge?.sendResponse(mapOf("status" to "saved", "message" to "SwiftPay Settings saved successfully!"))
            uiState = MiniAppUiState.Idle
        }
    }

    fun getSettings() {
        viewModelScope.launch {
            val credentials = settingsManager.loadSwiftPayCredentials()
            val alias = settingsManager.merchantAlias.first() ?: ""
            bridge?.sendResponse(
                mapOf(
                    "hasApiKey" to credentials.hasApiKey.toString(),
                    "apiKey" to (credentials.apiKey ?: ""),
                    "baseUrl" to (credentials.baseUrl ?: ""),
                    "merchantId" to (credentials.merchantId ?: ""),
                    "terminalId" to (credentials.terminalId ?: ""),
                    "merchantAlias" to alias
                )
            )
        }
    }

    fun approvePayment(data: PaymentData) {
        viewModelScope.launch {
            uiState = MiniAppUiState.Processing("Creating SwiftPay checkout...")
            val result = getService().createCheckout(data)

            result.onSuccess { response ->
                val redirectUrl = response.checkoutUrl ?: response.paymentUrl
                if (redirectUrl != null) {
                    val checkoutId = response.id ?: response.orderId ?: "CHECKOUT${System.currentTimeMillis()}"
                    pendingCheckoutId = checkoutId
                    recordLocalTransaction(
                        id = checkoutId,
                        amount = data.amount,
                        status = "PENDING"
                    )
                    uiState = MiniAppUiState.PaymentRedirect(redirectUrl, data)
                } else {
                    val errorMsg = "Redirect URL was not provided by SwiftPay server"
                    bridge?.sendError(errorMsg)
                    uiState = MiniAppUiState.Error(errorMsg)
                }
            }.onFailure { error ->
                val errorMessage = error.message ?: "Payment initialization failed"
                bridge?.sendError(errorMessage)
                uiState = MiniAppUiState.Error(errorMessage)
            }
        }
    }

    private var lastLocalCreditTime: Long = 0

    private suspend fun recordLocalTransaction(
        id: String,
        amount: Double,
        status: String
    ) {
        val normalizedStatus = normalizePaymentStatus(status)
        var actualAmount = amount
        var shouldCredit = false

        val existingTx = repository.getAllTransactions().first().find { it.transactionId == id }

        if (existingTx != null && normalizePaymentStatus(existingTx.status) == normalizedStatus) {
            return
        }

        if (existingTx != null) {
            if (actualAmount <= 0.0) {
                actualAmount = existingTx.amount
            }
            if (normalizedStatus == "SUCCESS" && normalizePaymentStatus(existingTx.status) != "SUCCESS") {
                shouldCredit = true
            }
        } else {
            if (normalizedStatus == "SUCCESS") {
                shouldCredit = true
            }
        }

        repository.saveTransaction(
            InternalTransaction(
                transactionId = id,
                amount = actualAmount,
                status = normalizedStatus,
                date = TransactionStore.nowLabel()
            )
        )

        if (shouldCredit && actualAmount > 0.0) {
            val currentBalStr = settingsManager.walletBalance.first().takeUnless { it.isNullOrBlank() } ?: "0.0"
            val currentBal = currentBalStr.toDoubleOrNull() ?: 0.0
            val newBal = currentBal + actualAmount

            settingsManager.saveWalletBalance(newBal.toString())
            lastLocalCreditTime = System.currentTimeMillis()
            bridge?.sendResponse(newBal)
        }
    }

    private fun normalizePaymentStatus(rawStatus: String?): String {
        val s = rawStatus?.uppercase() ?: return "PENDING"
        return when {
            s in listOf("SUCCESS", "PAYMENT_SUCCESS", "CAPTURED", "COMPLETED", "APPROVED", "AUTHORIZED") -> "SUCCESS"
            s in listOf("FAILED", "PAYMENT_FAILED", "DECLINED", "REJECTED", "EXPIRED", "PAYMENT_EXPIRED", "CANCELLED", "CANCELED") -> "FAILED"
            else -> "PENDING"
        }
    }

    private val inFlightStatusChecks = mutableSetOf<String>()

    fun fetchPaymentStatus(paymentId: String) {
        if (paymentId.isBlank() || inFlightStatusChecks.contains(paymentId)) return

        val cachedStatus = transactionStatusMap[paymentId]?.status?.uppercase()
        if (cachedStatus in listOf("SUCCESS", "FAILED", "EXPIRED", "CANCELLED", "DECLINED")) {
            return
        }

        inFlightStatusChecks.add(paymentId)
        viewModelScope.launch {
            try {
                val service = getService()
                val response = service.getPaymentStatus(paymentId).getOrNull()
                    ?: service.getCheckoutStatus(paymentId).getOrNull()

                if (response != null) {
                    val status = response.status ?: response.paymentStatus
                    if (status != null) {
                        val currentMap = transactionStatusMap.toMutableMap()
                        currentMap[paymentId] = response
                        transactionStatusMap = currentMap

                        val normalized = normalizePaymentStatus(status)
                        val storedAmount = response.amount ?: 0.0
                        recordLocalTransaction(
                            id = paymentId,
                            amount = storedAmount,
                            status = normalized
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("MiniAppViewModel", "Exception fetching payment status", e)
            } finally {
                inFlightStatusChecks.remove(paymentId)
            }
        }
    }

    fun fetchPaymentStatuses(paymentIds: List<String>) {
        paymentIds.forEach { fetchPaymentStatus(it) }
    }

    fun getPaymentStatusForId(paymentId: String): SwiftPayStatusResponse? {
        return transactionStatusMap[paymentId]
    }

    fun clearTransactionStatus(id: String) {
        if (id.isBlank()) return
        transactionStatusMap = transactionStatusMap.toMutableMap().apply {
            remove(id)
        }
    }

    fun handleDeepLink(linkId: String?, providedStatus: String? = null) {
        if (linkId.isNullOrBlank()) return

        if (!providedStatus.isNullOrBlank()) {
            viewModelScope.launch {
                try {
                    val normalized = normalizePaymentStatus(providedStatus)

                    val is3DSSuccess = providedStatus.uppercase() in listOf(
                        "SUCCESS", "CAPTURED", "COMPLETED", "AUTHORIZED", "APPROVED"
                    )
                    val is3DSFailed = providedStatus.uppercase() in listOf(
                        "FAILED", "DECLINED", "REJECTED", "CANCELLED", "CANCELED"
                    )

                    recordLocalTransaction(id = linkId, amount = 0.0, status = normalized)

                    if (pendingCheckoutId == linkId) {
                        threeDSecureTimeoutJob?.cancel()
                        pendingCheckoutId = null

                        if (is3DSSuccess) {
                            Log.i("MiniAppViewModel", "✅ SwiftPay verification successful for $linkId")
                            bridge?.sendResponse(mapOf(
                                "status" to "success",
                                "paymentId" to linkId,
                                "verificationStatus" to providedStatus
                            ))
                        } else if (is3DSFailed) {
                            Log.w("MiniAppViewModel", "❌ SwiftPay verification failed for $linkId: $providedStatus")
                            bridge?.sendError("SwiftPay verification failed: $providedStatus")
                        } else {
                            bridge?.sendResponse(mapOf(
                                "status" to normalized.lowercase(),
                                "paymentId" to linkId,
                                "verificationStatus" to providedStatus
                            ))
                        }
                    }
                } catch (e: Exception) {
                    Log.e("MiniAppViewModel", "Error processing response for $linkId", e)
                }
            }
            return
        }

        viewModelScope.launch {
            try {
                fetchPaymentStatus(linkId)
                if (pendingCheckoutId != linkId) {
                    checkPendingPaymentStatus()
                }
            } catch (e: Exception) {
                Log.e("MiniAppViewModel", "Error handling deep link for $linkId", e)
            }
        }
    }

    private suspend fun checkPendingPaymentStatus() {
        val checkoutId = pendingCheckoutId ?: return
        var polls = 0
        val maxPolls = 120

        while (pendingCheckoutId == checkoutId && polls < maxPolls) {
            try {
                val service = getService()
                val response = service.getCheckoutStatus(checkoutId).getOrNull()
                    ?: service.getPaymentStatus(checkoutId).getOrNull()

                val rawStatus = response?.status ?: response?.paymentStatus
                val status = rawStatus?.uppercase()

                if (status != null && status in listOf("SUCCESS", "CAPTURED", "COMPLETED", "APPROVED", "AUTHORIZED")) {
                    threeDSecureTimeoutJob?.cancel()
                    pendingCheckoutId = null

                    val currentLocalBalance = walletBalance.value
                    val freshBalance = service.getWalletBalance()
                    if (freshBalance > currentLocalBalance) {
                        settingsManager.saveWalletBalance(freshBalance.toString())
                    }

                    recordLocalTransaction(
                        id = checkoutId,
                        amount = response?.amount ?: 0.0,
                        status = "SUCCESS"
                    )

                    bridge?.sendResponse(mapOf(
                        "status" to "success",
                        "paymentId" to checkoutId,
                        "amount" to (response?.amount ?: 0.0)
                    ))

                    uiState = MiniAppUiState.Idle
                    Log.i("MiniAppViewModel", "✅ Checkout $checkoutId completed successfully via status polling!")
                    break
                } else if (status != null && status in listOf("FAILED", "DECLINED", "REJECTED", "EXPIRED", "CANCELLED")) {
                    threeDSecureTimeoutJob?.cancel()
                    pendingCheckoutId = null

                    recordLocalTransaction(
                        id = checkoutId,
                        amount = response?.amount ?: 0.0,
                        status = "FAILED"
                    )

                    val errorMsg = "Payment $checkoutId failed: $status"
                    bridge?.sendError(errorMsg)
                    uiState = MiniAppUiState.Error(errorMsg)
                    Log.w("MiniAppViewModel", "❌ Checkout $checkoutId failed with status: $status")
                    break
                }
            } catch (e: Exception) {
                Log.e("MiniAppViewModel", "Error checking status for $checkoutId", e)
            }

            delay(3000)
            polls++
        }
    }

    fun dismissConsent() {
        nfcTimerJob?.cancel()
        threeDSecureTimeoutJob?.cancel()
        pendingCheckoutId = null
        uiState = MiniAppUiState.Idle
    }

    fun dismissError() {
        uiState = MiniAppUiState.Idle
    }

    companion object {
        const val NFC_SESSION_SECONDS = 90
    }
}

private fun String.toFriendlyNfcMessage(): String = when (trim()) {
    "Selecting Payment System..." -> "Connecting contactless card..."
    "Accessing Card Application..." -> "Opening card application..."
    "Extracting Card Details..." -> "Checking card details..."
    "Retrieving Security Options..." -> "Reading card security options..."
    "Scanning Card Records..." -> "Scanning card records..."
    else -> this
}
