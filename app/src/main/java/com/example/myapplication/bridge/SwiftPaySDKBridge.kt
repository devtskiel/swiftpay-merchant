package com.example.myapplication.bridge

import android.webkit.JavascriptInterface
import android.webkit.WebView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import com.example.myapplication.data.api.InternalTransaction
import com.example.myapplication.data.api.PaymentChannel

class SwiftPaySDKBridge(
    val webView: WebView,
    private val scope: CoroutineScope,
    private val onPaymentRequest: (PaymentData) -> Unit,
    private val onBalanceRequest: () -> Unit,
    private val onTransactionsRequest: () -> Unit,
    private val onPaymentChannelsRequest: () -> Unit,
    private val onPaymentLinkRequest: (PaymentData) -> Unit,
    private val onSaveSettings: (Map<String, String>) -> Unit,
    private val onGetSettings: () -> Unit,
    private val onScanNFCCard: (Double) -> Unit,
    private val onGenerateDynamicQr: (Double) -> Unit,
    private val onSendDisbursement: ((Double, String, String, String) -> Unit)? = null
) {

    private val json = Json { ignoreUnknownKeys = true }

    @JavascriptInterface
    fun postMessage(message: String) {
        scope.launch(Dispatchers.Main) {
            try {
                val request = json.decodeFromString<BridgeRequest>(message)
                handleRequest(request)
            } catch (e: Exception) {
                sendError("Invalid message format: ${e.message}")
            }
        }
    }

    private fun handleRequest(request: BridgeRequest) {
        when (request.action) {
            "get_balance" -> {
                onBalanceRequest()
            }
            "get_transactions" -> {
                onTransactionsRequest()
            }
            "get_payment_channels" -> {
                onPaymentChannelsRequest()
            }
            "request_payment" -> {
                val data = request.data?.let {
                    json.decodeFromJsonElement<PaymentData>(it)
                }
                if (data != null) {
                    onPaymentRequest(data)
                } else {
                    sendError("Missing payment data")
                }
            }
            "create_payment_link" -> {
                val data = request.data?.let {
                    json.decodeFromJsonElement<PaymentData>(it)
                }
                if (data != null) {
                    onPaymentLinkRequest(data)
                } else {
                    sendError("Missing payment link data")
                }
            }
            "save_settings" -> {
                val data = request.data?.jsonObject
                if (data != null) {
                    val map = mutableMapOf<String, String>()
                    data["apiKey"]?.jsonPrimitive?.content?.let { map["apiKey"] = it }
                    data["baseUrl"]?.jsonPrimitive?.content?.let { map["baseUrl"] = it }
                    data["merchantId"]?.jsonPrimitive?.content?.let { map["merchantId"] = it }
                    data["terminalId"]?.jsonPrimitive?.content?.let { map["terminalId"] = it }
                    data["merchantAlias"]?.jsonPrimitive?.content?.let { map["merchantAlias"] = it }

                    onSaveSettings(map)
                } else {
                    sendError("Missing settings data")
                }
            }
            "get_settings" -> {
                onGetSettings()
            }
            "scan_nfc_card" -> {
                val amount = request.data?.jsonObject?.get("amount")?.jsonPrimitive?.doubleOrNull ?: 0.0
                onScanNFCCard(amount)
            }
            "generate_dynamic_qr" -> {
                val amount = request.data?.jsonObject?.get("amount")?.jsonPrimitive?.doubleOrNull ?: 0.0
                onGenerateDynamicQr(amount)
            }
            "send_disbursement" -> {
                val amount = request.data?.jsonObject?.get("amount")?.jsonPrimitive?.doubleOrNull ?: 0.0
                val bankCode = request.data?.jsonObject?.get("bankCode")?.jsonPrimitive?.content ?: "GCASH"
                val accountNumber = request.data?.jsonObject?.get("accountNumber")?.jsonPrimitive?.content ?: ""
                val recipientName = request.data?.jsonObject?.get("recipientName")?.jsonPrimitive?.content ?: "Recipient"
                if (onSendDisbursement != null) {
                    onSendDisbursement.invoke(amount, bankCode, accountNumber, recipientName)
                } else {
                    sendError("Disbursement handler not configured")
                }
            }
            else -> sendError("Unknown action: ${request.action}")
        }
    }

    fun sendResponse(data: Any?) {
        scope.launch(Dispatchers.Main) {
            val responseData: JsonElement? = when (data) {
                null -> null
                is JsonElement -> data
                is String -> json.encodeToJsonElement(data)
                is Number -> when (data) {
                    is Int -> json.encodeToJsonElement(data)
                    is Long -> json.encodeToJsonElement(data)
                    is Float -> json.encodeToJsonElement(data)
                    is Double -> json.encodeToJsonElement(data)
                    else -> json.encodeToJsonElement(data.toDouble())
                }
                is Boolean -> json.encodeToJsonElement(data)
                is UserProfile -> json.encodeToJsonElement(data)
                is PaymentData -> json.encodeToJsonElement(data)
                is InternalTransaction -> json.encodeToJsonElement(data)
                is PaymentChannel -> json.encodeToJsonElement(data)
                is List<*> -> {
                    val elementList: List<JsonElement> = data.map { item ->
                        when (item) {
                            is InternalTransaction -> json.encodeToJsonElement(item)
                            is PaymentChannel -> json.encodeToJsonElement(item)
                            is String -> json.encodeToJsonElement(item)
                            is Number -> when (item) {
                                is Int -> json.encodeToJsonElement(item)
                                is Long -> json.encodeToJsonElement(item)
                                is Float -> json.encodeToJsonElement(item)
                                is Double -> json.encodeToJsonElement(item.toDouble())
                                else -> json.encodeToJsonElement(item.toDouble())
                            }
                            is Boolean -> json.encodeToJsonElement(item)
                            else -> json.encodeToJsonElement(item.toString())
                        }
                    }
                    kotlinx.serialization.json.JsonArray(elementList)
                }
                is Map<*, *> -> {
                    val stringMap = data.entries.associate { entry -> entry.key.toString() to entry.value.toString() }
                    json.encodeToJsonElement(stringMap)
                }
                else -> try {
                    json.encodeToJsonElement(data.toString())
                } catch (_: Exception) {
                    json.encodeToJsonElement(data.toString())
                }
            }

            val response = BridgeResponse(
                status = "success",
                data = responseData
            )
            val jsonResponse = json.encodeToString(response)
            evaluateJavaScriptResponse(jsonResponse)
        }
    }

    fun sendError(message: String) {
        scope.launch(Dispatchers.Main) {
            val response = BridgeResponse(
                status = "error",
                message = message
            )
            val jsonResponse = json.encodeToString(response)
            evaluateJavaScriptResponse(jsonResponse)
        }
    }

    private fun evaluateJavaScriptResponse(jsonResponse: String) {
        try {
            val escapedJson = jsonResponse
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")

            webView.evaluateJavascript("window.onSwiftPayResponse(\"$escapedJson\")", null)
        } catch (e: Exception) {
            android.util.Log.e("SwiftPaySDKBridge", "Error evaluating JavaScript response", e)
        }
    }
}
