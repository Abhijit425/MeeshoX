package com.meeshox.app.payments

import android.app.Activity
import com.meeshox.app.domain.PaymentMethod
import com.meeshox.app.domain.PaymentProvider
import com.meeshox.app.domain.PaymentResult
import com.meeshox.app.domain.Payer
import com.razorpay.Checkout
import com.razorpay.PaymentData
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.lang.ref.WeakReference
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToLong

/** Gateway settings. The key ID is public by design; the key SECRET never goes in the app. */
data class GatewayConfig(val keyId: String, val serverUrl: String) {
    val configured: Boolean get() = KEY_REGEX.matches(keyId)
    val testMode: Boolean get() = keyId.startsWith("rzp_test_")
    val hasServer: Boolean get() = serverUrl.startsWith("https://")
    companion object { val KEY_REGEX = Regex("^rzp_(test|live)_[A-Za-z0-9]{8,}$") }
}

/**
 * Bridges Razorpay's Activity callbacks (delivered to MainActivity) to a suspending call.
 */
object RazorpayBridge {
    private var activityRef: WeakReference<Activity>? = null
    private var pending: CompletableDeferred<Result>? = null

    sealed class Result {
        data class Success(val paymentId: String, val orderId: String?, val signature: String?) : Result()
        data class Error(val code: Int, val message: String) : Result()
    }

    fun attach(a: Activity) { activityRef = WeakReference(a) }
    fun detach(a: Activity) { if (activityRef?.get() === a) activityRef = null }
    fun activity(): Activity? = activityRef?.get()

    fun start(): CompletableDeferred<Result> = CompletableDeferred<Result>().also { pending?.cancel(); pending = it }

    fun onSuccess(paymentId: String?, data: PaymentData?) {
        pending?.complete(Result.Success(paymentId ?: data?.paymentId ?: "", data?.orderId, data?.signature)); pending = null
    }

    fun onError(code: Int, response: String?) {
        val msg = try {
            JSONObject(response ?: "").optJSONObject("error")?.optString("description")?.takeIf { it.isNotBlank() }
        } catch (e: Exception) { null } ?: when (code) {
            Checkout.PAYMENT_CANCELED -> "Payment cancelled"
            Checkout.NETWORK_ERROR -> "Network error during payment"
            else -> response?.take(160) ?: "Payment failed"
        }
        pending?.complete(Result.Error(code, msg)); pending = null
    }
}

/**
 * Real payment gateway: Razorpay Standard Checkout (UPI, cards, net banking, wallets).
 *
 * With a payment server configured (see /server), the flow is the secure one:
 *   server creates an Order → app opens Checkout with order_id → server verifies the HMAC signature.
 * Without a server, payments still go through Razorpay, but the result is only client-confirmed
 * and is stored as PAID_UNVERIFIED (enable auto-capture in the Razorpay dashboard).
 */
class RazorpayProvider(private val config: suspend () -> GatewayConfig) : PaymentProvider {
    override val name = "Razorpay"
    override val isSandbox = false

    override suspend fun pay(amount: Double, method: PaymentMethod, payer: Payer): PaymentResult {
        val cfg = config()
        if (!cfg.configured) return PaymentResult(false, "FAILED", "", "Razorpay key not configured", name)
        val paise = (amount * 100).roundToLong()
        var keyId = cfg.keyId
        var orderId: String? = null
        if (cfg.hasServer) {
            val res = post(cfg.serverUrl, "/api/payments/order", JSONObject().put("amount", paise).put("currency", "INR").put("receipt", "mx_${System.currentTimeMillis()}"))
                ?: return PaymentResult(false, "FAILED", "", "Couldn't reach the payment server to create an order", name)
            orderId = res.optString("order_id").takeIf { it.isNotBlank() }
                ?: return PaymentResult(false, "FAILED", "", res.optString("error", "Payment server didn't return an order"), name)
            res.optString("key_id").takeIf { GatewayConfig.KEY_REGEX.matches(it) }?.let { keyId = it }
        }
        val activity = RazorpayBridge.activity() ?: return PaymentResult(false, "FAILED", "", "App not in foreground", name)
        val deferred = RazorpayBridge.start()
        val options = JSONObject().apply {
            put("name", "MEESHO X")
            put("description", payer.description.ifBlank { "Order payment" })
            put("currency", "INR")
            put("amount", paise)
            orderId?.let { put("order_id", it) }
            put("theme", JSONObject().put("color", "#6D28D9"))
            put("prefill", JSONObject().apply {
                if (payer.phone.isNotBlank()) put("contact", "+91" + payer.phone)
                if (payer.email.isNotBlank()) put("email", payer.email)
                if (payer.name.isNotBlank()) put("name", payer.name)
                when (method) {
                    PaymentMethod.UPI -> put("method", "upi")
                    PaymentMethod.CREDIT_CARD, PaymentMethod.DEBIT_CARD -> put("method", "card")
                    PaymentMethod.NET_BANKING -> put("method", "netbanking")
                    PaymentMethod.WALLET -> put("method", "wallet")
                    PaymentMethod.COD -> Unit
                }
            })
            put("retry", JSONObject().put("enabled", true).put("max_count", 3))
            put("send_sms_hash", true)
        }
        try {
            withContext(Dispatchers.Main) {
                val co = Checkout()
                co.setKeyID(keyId)
                co.open(activity, options)
            }
        } catch (e: Exception) {
            return PaymentResult(false, "FAILED", "", "Couldn't open Razorpay checkout: ${e.message}", name)
        }
        return when (val r = deferred.await()) {
            is RazorpayBridge.Result.Error -> PaymentResult(false, "FAILED", "", r.message, name)
            is RazorpayBridge.Result.Success -> {
                if (cfg.hasServer && r.orderId != null && r.signature != null) {
                    val v = post(cfg.serverUrl, "/api/payments/verify", JSONObject()
                        .put("razorpay_order_id", r.orderId).put("razorpay_payment_id", r.paymentId).put("razorpay_signature", r.signature))
                    if (v?.optBoolean("verified") == true) PaymentResult(true, "PAID", r.paymentId, "Payment verified by server (Razorpay ${if (cfg.testMode) "test" else "live"} mode)", name)
                    else PaymentResult(false, "VERIFICATION_FAILED", r.paymentId,
                        "Payment ${r.paymentId} received but server verification failed — contact support; do not pay again.", name)
                } else PaymentResult(true, "PAID_UNVERIFIED", r.paymentId,
                    "Paid via Razorpay (${if (cfg.testMode) "test" else "live"} mode). Client-confirmed; no verification server configured.", name)
            }
        }
    }

    override suspend fun refund(reference: String, amount: Double): PaymentResult {
        val cfg = config()
        if (!cfg.hasServer) return PaymentResult(false, "REFUND_REQUIRED", reference,
            "Refund for $reference must be issued from the Razorpay dashboard (no payment server configured)", name)
        val res = post(cfg.serverUrl, "/api/payments/refund", JSONObject().put("payment_id", reference).put("amount", (amount * 100).roundToLong()))
        val id = res?.optString("refund_id").orEmpty()
        return if (id.isNotBlank()) PaymentResult(true, "REFUND_INITIATED", id, "Razorpay refund $id initiated", name)
        else PaymentResult(false, "REFUND_REQUIRED", reference, "Refund request failed: ${res?.optString("error") ?: "server unreachable"}. Issue it from the Razorpay dashboard.", name)
    }

    private suspend fun post(base: String, path: String, body: JSONObject): JSONObject? = withContext(Dispatchers.IO) {
        try {
            val conn = URL(base.trimEnd('/') + path).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"; conn.connectTimeout = 15_000; conn.readTimeout = 20_000; conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }
            val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            conn.disconnect()
            JSONObject(text.ifBlank { "{}" })
        } catch (e: Exception) { null }
    }
}
