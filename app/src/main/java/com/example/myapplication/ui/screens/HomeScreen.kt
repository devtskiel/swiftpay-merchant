package com.example.myapplication.ui.screens

import android.content.Context
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CompareArrows
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.data.SettingsManager
import com.example.myapplication.data.createSwiftPayService
import com.example.myapplication.ui.theme.FastPayActionIcon
import com.example.myapplication.ui.theme.FastPayTextPrimary
import com.example.myapplication.ui.theme.FastPayTextSecondary
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onLaunchMiniApp: (String?) -> Unit,
    onNavigateToWallet: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    onHelpClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }
    var balance by remember { mutableDoubleStateOf(0.0) }
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        scope.launch {
            try {
                val stored = settingsManager.walletBalance.first()
                balance = stored?.toDoubleOrNull() ?: 0.0
                val service = settingsManager.createSwiftPayService()
                val fresh = service.getWalletBalance()
                if (fresh > 0.0 || (balance == 0.0 && fresh > 0.0)) {
                    balance = fresh
                    settingsManager.saveWalletBalance(fresh.toString())
                }
            } catch (_: Exception) {
            } finally {
                isLoading = false
            }
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Color.White)) {
        MeshBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            HeaderSection(
                balance = balance,
                isLoading = isLoading,
                onProfileClick = onProfileClick,
                onHelpClick = onHelpClick
            )

            Spacer(modifier = Modifier.height(24.dp))

            PrimaryActionRow(
                onCashIn = { onLaunchMiniApp(null) },
                onSend = { onLaunchMiniApp("disbursement-page") },
                onScan = { onLaunchMiniApp("qr-page") }
            )

            Spacer(modifier = Modifier.height(32.dp))

            SecondaryToolGrid(onLaunchMiniApp)

            Spacer(modifier = Modifier.height(24.dp))

            PromotionalBanner()

            Spacer(modifier = Modifier.height(24.dp))

            TransactionHistorySection(onNavigateToWallet)

            Spacer(modifier = Modifier.height(24.dp))

            RewardsHubSection()

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun MeshBackground() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(400.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height

            drawCircle(
                color = Color(0xFFE0E7FF),
                center = Offset(x = canvasWidth * 0.1f, y = canvasHeight * 0.2f),
                radius = canvasWidth * 0.4f
            )

            drawCircle(
                color = Color(0xFFEFF6FF),
                center = Offset(x = canvasWidth * 0.85f, y = canvasHeight * 0.1f),
                radius = canvasWidth * 0.5f
            )

            drawCircle(
                color = Color(0xFFF1F5F9),
                center = Offset(x = canvasWidth * 0.5f, y = canvasHeight * 0.4f),
                radius = canvasWidth * 0.6f
            )
        }
    }
}

@Composable
fun HeaderSection(
    balance: Double,
    isLoading: Boolean,
    onProfileClick: () -> Unit,
    onHelpClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column {
            if (isLoading) {
                Box(modifier = Modifier.width(120.dp).height(32.dp).clip(RoundedCornerShape(8.dp)).shimmerEffect())
            } else {
                Text(
                    text = "₱${"%,.2f".format(balance)}",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = FastPayTextPrimary,
                    fontSize = 26.sp
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                Text(
                    text = "Total Balance",
                    style = MaterialTheme.typography.bodySmall,
                    color = FastPayTextSecondary,
                    fontWeight = FontWeight.Medium
                )
                Icon(
                    Icons.Rounded.ArrowDropDown,
                    null,
                    tint = FastPayTextSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onProfileClick) {
                Icon(Icons.Rounded.AccountCircle, null, tint = FastPayTextPrimary, modifier = Modifier.size(28.dp))
            }
            IconButton(onClick = onHelpClick) {
                Icon(Icons.AutoMirrored.Rounded.HelpOutline, null, tint = FastPayTextPrimary, modifier = Modifier.size(26.dp))
            }
        }
    }
}

@Composable
fun PrimaryActionRow(
    onCashIn: () -> Unit,
    onSend: () -> Unit,
    onScan: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        PrimaryActionButton(icon = Icons.Rounded.AccountBalance, label = "Top Up", onClick = onCashIn)
        PrimaryActionButton(icon = Icons.Rounded.FileUpload, label = "Send Money", onClick = onSend)
        PrimaryActionButton(icon = Icons.Rounded.QrCodeScanner, label = "Scan & Pay", onClick = onScan)
    }
}

@Composable
fun PrimaryActionButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            modifier = Modifier.size(72.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = FastPayTextPrimary, modifier = Modifier.size(28.dp))
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = FastPayTextPrimary,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun SecondaryToolGrid(onLaunch: (String?) -> Unit) {
    val tools = listOf(
        Triple(Icons.Rounded.Description, "Pay Bills", null),
        Triple(Icons.Rounded.PhonelinkRing, "Buy Load", null),
        Triple(Icons.Rounded.Token, "Crypto", null),
        Triple(Icons.AutoMirrored.Rounded.CompareArrows, "Spot Trade", null),
        Triple(Icons.Rounded.CardGiftcard, "Rewards", null),
        Triple(Icons.Rounded.AccountBalanceWallet, "Tap to Pay", "card-payment-page"),
        Triple(Icons.Rounded.Terminal, "Developer", "api-docs-page"),
        Triple(Icons.Rounded.GridView, "More", null)
    )

    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        for (i in 0 until 2) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                for (j in 0 until 4) {
                    val index = i * 4 + j
                    val (icon, label, path) = tools[index]
                    ToolItem(icon, label) { onLaunch(path) }
                }
            }
            if (i == 0) Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun ToolItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(80.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, null, tint = Color(0xFF475569), modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = FastPayTextSecondary,
            textAlign = TextAlign.Center,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun PromotionalBanner() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFFEFF6FF)
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(140.dp)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(color = Color(0xFFDBEAFE), center = Offset(size.width * 0.9f, size.height * 0.5f), radius = size.width * 0.3f)
            }

            Column(modifier = Modifier.padding(20.dp).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                Text(
                    text = "SwiftPay Deals",
                    style = MaterialTheme.typography.labelSmall,
                    color = FastPayTextSecondary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Loyalty Rewards\nProgram Is Live!",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = FastPayTextPrimary,
                    lineHeight = 24.sp
                )
                Text(
                    text = "Powered by SwiftPay",
                    style = MaterialTheme.typography.labelSmall,
                    color = FastPayTextSecondary,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(modifier = Modifier.weight(1f))

                Button(
                    onClick = { },
                    colors = ButtonDefaults.buttonColors(containerColor = FastPayActionIcon),
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 24.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text("Get Started", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }

            Box(modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp)) {
                Icon(Icons.Rounded.DonutLarge, null, tint = Color(0xFFF97316).copy(alpha = 0.8f), modifier = Modifier.size(80.dp))
            }
        }
    }
}

@Composable
fun TransactionHistorySection(onClick: () -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { onClick() },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.width(4.dp).height(20.dp).clip(CircleShape).background(Color(0xFF818CF8)))
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Transaction History",
                    style = MaterialTheme.typography.titleMedium.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
                    fontWeight = FontWeight.ExtraBold,
                    color = FastPayTextPrimary
                )
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = Color.LightGray, modifier = Modifier.size(20.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))

        TransactionListItem(label = "QR Ph Payment", amount = -100.0, isOutflow = true)
        Spacer(modifier = Modifier.height(12.dp))
        TransactionListItem(label = "Card Tap Payment", amount = 2500.0, isOutflow = false)

        Spacer(modifier = Modifier.height(20.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFFF8FAFC),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0))
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("SwiftPay API", style = MaterialTheme.typography.labelSmall, color = FastPayTextSecondary)
                    Text(
                        "Instant & Secure\nPayments with SwiftPay",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = FastPayTextPrimary,
                        lineHeight = 22.sp
                    )
                }
                Icon(Icons.AutoMirrored.Rounded.Send, null, tint = FastPayActionIcon, modifier = Modifier.size(48.dp).alpha(0.2f))
            }
        }
    }
}

@Composable
fun TransactionListItem(label: String, amount: Double, isOutflow: Boolean) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = CircleShape,
            color = Color.White,
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            modifier = Modifier.size(32.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    if (isOutflow) Icons.Rounded.NorthEast else Icons.Rounded.SouthWest,
                    null,
                    tint = FastPayTextSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = FastPayTextPrimary, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = "${if(isOutflow) "-" else "+"}₱${"%,.2f".format(Math.abs(amount))}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = FastPayTextPrimary
        )
    }
}

@Composable
fun RewardsHubSection() {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.width(4.dp).height(20.dp).clip(CircleShape).background(Color(0xFF2DD4BF)))
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Rewards Hub",
                    style = MaterialTheme.typography.titleMedium.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
                    fontWeight = FontWeight.ExtraBold,
                    color = FastPayTextPrimary
                )
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = Color.LightGray, modifier = Modifier.size(20.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFFF0FDFA)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Points & Deals", style = MaterialTheme.typography.labelSmall, color = Color(0xFF0D9488), fontWeight = FontWeight.Bold)
                Text(
                    "Earn points with every payment\nand unlock exclusive merchant rewards.",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = FastPayTextPrimary,
                    lineHeight = 22.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Icon(Icons.Rounded.CardGiftcard, null, tint = Color(0xFF2DD4BF), modifier = Modifier.size(60.dp).alpha(0.3f).align(Alignment.End))
            }
        }
    }
}

fun Modifier.shimmerEffect(): Modifier = this.then(
    background(
        brush = Brush.linearGradient(
            colors = listOf(
                Color.LightGray.copy(alpha = 0.3f),
                Color.LightGray.copy(alpha = 0.5f),
                Color.LightGray.copy(alpha = 0.3f)
            )
        )
    )
)
