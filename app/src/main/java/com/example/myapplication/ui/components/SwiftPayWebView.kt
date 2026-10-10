package com.example.myapplication.ui.components

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.myapplication.bridge.PaymentData
import com.example.myapplication.bridge.SwiftPaySDKBridge

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SwiftPayWebView(
    url: String,
    modifier: Modifier = Modifier,
    onPaymentRequest: (PaymentData) -> Unit,
    onBalanceRequest: () -> Unit,
    onTransactionsRequest: () -> Unit,
    onPaymentChannelsRequest: () -> Unit,
    onPaymentLinkRequest: (PaymentData) -> Unit,
    onSaveSettings: (Map<String, String>) -> Unit,
    onGetSettings: () -> Unit,
    onScanNFCCard: (Double) -> Unit,
    onGenerateDynamicQr: (Double) -> Unit,
    onSendDisbursement: ((Double, String, String, String) -> Unit)? = null,
    onBridgeReady: (SwiftPaySDKBridge) -> Unit
) {
    val scope = rememberCoroutineScope()
    var webView: WebView? by remember { mutableStateOf(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var isLoading by remember { mutableStateOf(true) }
    var pageError: String? by remember { mutableStateOf(null) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, webView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> webView?.onPause()
                Lifecycle.Event.ON_RESUME -> webView?.onResume()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            webView?.destroy()
        }
    }

    Box(modifier = modifier) {
        if (pageError != null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Load Error: $pageError",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            allowFileAccess = true
                            allowContentAccess = true
                            loadWithOverviewMode = true
                            useWideViewPort = true
                        }

                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                val requestUrl = request?.url?.toString() ?: return false

                                if (requestUrl.startsWith("swiftpay-showcase://") || requestUrl.startsWith("swiftpay://")) {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(requestUrl))
                                    context.startActivity(intent)
                                    return true
                                }

                                if (requestUrl.startsWith("http://") || requestUrl.startsWith("https://")) {
                                    return false
                                }

                                return try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(requestUrl))
                                    context.startActivity(intent)
                                    true
                                } catch (e: Exception) {
                                    true
                                }
                            }

                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                isLoading = true
                                pageError = null
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                isLoading = false
                                canGoBack = view?.canGoBack() == true
                            }

                            override fun onReceivedError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                error: WebResourceError?
                            ) {
                                if (request?.isForMainFrame == true) {
                                    pageError = error?.description?.toString() ?: "Network connection error"
                                    isLoading = false
                                }
                            }

                            override fun onReceivedHttpError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                errorResponse: WebResourceResponse?
                            ) {
                                if (request?.isForMainFrame == true) {
                                    pageError = "HTTP Error ${errorResponse?.statusCode}: ${errorResponse?.reasonPhrase}"
                                    isLoading = false
                                }
                            }
                        }

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                progress = newProgress / 100f
                            }
                        }

                        val bridge = SwiftPaySDKBridge(
                            webView = this,
                            scope = scope,
                            onPaymentRequest = onPaymentRequest,
                            onBalanceRequest = onBalanceRequest,
                            onTransactionsRequest = onTransactionsRequest,
                            onPaymentChannelsRequest = onPaymentChannelsRequest,
                            onPaymentLinkRequest = onPaymentLinkRequest,
                            onSaveSettings = onSaveSettings,
                            onGetSettings = onGetSettings,
                            onScanNFCCard = onScanNFCCard,
                            onGenerateDynamicQr = onGenerateDynamicQr,
                            onSendDisbursement = onSendDisbursement
                        )

                        addJavascriptInterface(bridge, "SwiftPaySDK")
                        addJavascriptInterface(bridge, "MayaSDK")
                        onBridgeReady(bridge)

                        loadUrl(url)
                        webView = this
                    }
                },
                update = { view ->
                    if (view.url != url) {
                        view.loadUrl(url)
                    }
                }
            )
        }

        AnimatedVisibility(
            visible = isLoading && pageError == null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
