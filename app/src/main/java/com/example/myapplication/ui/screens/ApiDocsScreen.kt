package com.example.myapplication.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Webhook
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.ui.theme.FastPayBlack
import com.example.myapplication.ui.theme.FastPayBlue
import com.example.myapplication.ui.theme.FastPayNavy

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiDocsScreen() {
    val isDarkTheme = MaterialTheme.colorScheme.background == FastPayBlack
    val bgColor = if (isDarkTheme) Color(0xFF121212) else Color(0xFFF8F9FA)
    val textColor = if (isDarkTheme) Color.White else FastPayNavy
    val cardColor = if (isDarkTheme) Color(0xFF1E1E1E) else Color.White

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("SwiftPay Developer Portal", color = textColor, fontWeight = FontWeight.Black) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = bgColor)
            )
        },
        containerColor = bgColor
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            DocHeader(
                title = "Integration Overview",
                subtitle = "Build rapid payment solutions using SwiftPay corporate REST APIs.",
                textColor = textColor
            )

            DocSection(
                title = "1. API Credentials",
                icon = Icons.Rounded.Key,
                content = "Access SwiftPay gateway APIs using your Access Key and Secret Key. Keep secret credentials safe and never expose them in frontend scripts.",
                code = "X-Access-Key: ADD7F25B...\nX-Secret-Key: sk_live_891f...\nX-Merchant-ID: 5312",
                cardColor = cardColor,
                textColor = textColor
            )

            DocSection(
                title = "2. Authentication",
                icon = Icons.Rounded.Security,
                content = "API requests use HTTP headers or Bearer tokens. Include X-Access-Key or Bearer <ACCESS_KEY> on all request headers.",
                code = "Authorization: Bearer <SWIFTPAY_ACCESS_KEY>",
                cardColor = cardColor,
                textColor = textColor
            )

            DocSection(
                title = "3. Fast Checkout Order",
                icon = Icons.Rounded.Description,
                content = "Create seamless checkout orders. Supports cards, e-wallets, and online banking.",
                code = "POST /v1/swiftpay/create-order\n{\n  \"amount\": 100.00,\n  \"reference_no\": \"REF-1001\",\n  \"description\": \"Order Checkout\"\n}",
                cardColor = cardColor,
                textColor = textColor
            )

            DocSection(
                title = "4. Dynamic QR Ph",
                icon = Icons.Rounded.Code,
                content = "Generate national standard dynamic QR Ph codes. Amount is embedded automatically for instant settlement.",
                code = "POST /v1/magpie/qr/dynamic\n{\n  \"amount\": 50.00,\n  \"reference_no\": \"QR-1002\"\n}",
                cardColor = cardColor,
                textColor = textColor
            )

            DocSection(
                title = "5. Status Webhooks",
                icon = Icons.Rounded.Webhook,
                content = "Receive instant HTTP POST callbacks whenever a transaction status changes.",
                code = "{\n  \"status\": \"PAYMENT_SUCCESS\",\n  \"order_id\": \"ORD-9921\",\n  \"amount\": 100.00\n}",
                cardColor = cardColor,
                textColor = textColor
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = FastPayBlue),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Need Technical Support?", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Our engineering team is available 24/7. Contact support@swiftpay.ph for custom API integration assistance.",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 13.sp,
                        lineHeight = 20.sp
                    )
                }
            }
            
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
fun DocHeader(title: String, subtitle: String, textColor: Color) {
    Column {
        Text(title, fontSize = 24.sp, fontWeight = FontWeight.Black, color = textColor)
        Spacer(Modifier.height(8.dp))
        Text(subtitle, fontSize = 14.sp, color = Color.Gray, lineHeight = 22.sp)
    }
}

@Composable
fun DocSection(
    title: String,
    icon: ImageVector,
    content: String,
    code: String,
    cardColor: Color,
    textColor: Color
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = cardColor),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = FastPayBlue, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = textColor)
            }
            Spacer(Modifier.height(12.dp))
            Text(content, fontSize = 13.sp, color = Color.Gray, lineHeight = 20.sp)
            Spacer(Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1E1E2F), RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Text(
                    text = code,
                    color = Color(0xFFCE9178),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            }
        }
    }
}
