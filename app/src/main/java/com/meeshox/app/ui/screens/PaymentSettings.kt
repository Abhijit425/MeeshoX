package com.meeshox.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.meeshox.app.BuildConfig
import com.meeshox.app.payments.GatewayConfig
import com.meeshox.app.ui.ShopViewModel
import com.meeshox.app.ui.theme.DealRed
import com.meeshox.app.ui.theme.Mint

@Composable
fun PaymentSettingsScreen(vm: ShopViewModel, nav: NavHostController) {
    val prefs by vm.prefs.collectAsState()
    val p = prefs ?: return
    val ctx = LocalContext.current
    var key by remember { mutableStateOf(p.razorpayKeyId) }
    var server by remember { mutableStateOf(p.paymentServerUrl) }
    var error by remember { mutableStateOf<String?>(null) }
    val effective = GatewayConfig(p.razorpayKeyId.ifBlank { BuildConfig.RAZORPAY_KEY_ID }, p.paymentServerUrl.ifBlank { BuildConfig.PAYMENT_SERVER_URL })

    Screen("Payment gateway", nav) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(color = (if (effective.configured) Mint else DealRed).copy(alpha = .12f), shape = RoundedCornerShape(12.dp)) {
                Text(
                    when {
                        !effective.configured -> "Not connected — checkout uses the sandbox (no real money)."
                        effective.testMode -> "Connected: Razorpay TEST mode (${effective.keyId.take(14)}…). " + if (effective.hasServer) "Verification server ON." else "No verification server."
                        else -> "Connected: Razorpay LIVE mode — real payments. " + if (effective.hasServer) "Verification server ON." else "⚠ Add a verification server before accepting real orders."
                    }, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium
                )
            }
            Text("Razorpay Key ID", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(key, { key = it.trim() }, placeholder = { Text("rzp_test_XXXXXXXXXXXX") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("Dashboard → Account & Settings → API Keys. Only the Key ID goes here — never the Key Secret.") })
            Text("Verification server URL (recommended)", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(server, { server = it.trim() }, placeholder = { Text("https://your-server.example.com") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("Runs /server from the repo. It holds the Key Secret, creates orders, verifies signatures and issues refunds.") })
            error?.let { Text(it, color = DealRed) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = {
                    error = when {
                        key.isNotBlank() && !GatewayConfig.KEY_REGEX.matches(key) -> "That doesn't look like a Razorpay Key ID (rzp_test_… or rzp_live_…)"
                        key.contains("secret", true) -> "Never enter the Key Secret in the app"
                        server.isNotBlank() && !server.startsWith("https://") -> "Server URL must start with https://"
                        else -> null
                    }
                    if (error == null) vm.io { vm.repo.prefs.setGateway(key, server.trimEnd('/')); vm.toast("Payment settings saved") }
                }, modifier = Modifier.weight(1f)) { Text("Save") }
                OutlinedButton(onClick = { key = ""; server = ""; vm.io { vm.repo.prefs.setGateway("", ""); vm.toast("Using build defaults / sandbox") } }, modifier = Modifier.weight(1f)) { Text("Reset") }
            }
            HorizontalDivider()
            Text("How to go live", style = MaterialTheme.typography.titleMedium)
            Text("1. Create a Razorpay account and copy your TEST Key ID here. Test payments use test cards/UPI and move no real money.\n" +
                "2. Deploy the verification server from the repo's /server folder with your Key ID + Key Secret, and enter its https URL here.\n" +
                "3. In the Razorpay dashboard, enable payment auto-capture and add the server's /api/payments/webhook as a webhook.\n" +
                "4. After Razorpay activates your account (KYC), switch to your LIVE Key ID on the server and here.",
                style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://dashboard.razorpay.com/app/keys"))) }, modifier = Modifier.fillMaxWidth()) { Text("Open Razorpay dashboard") }
        }
    }
}
