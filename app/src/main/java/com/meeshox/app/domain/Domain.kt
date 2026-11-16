package com.meeshox.app.domain

import com.meeshox.app.data.CouponEntity
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object PasswordHasher {
    private const val ITERATIONS = 120_000
    fun newSalt(): String = ByteArray(16).also { SecureRandom().nextBytes(it) }.let { Base64.getEncoder().encodeToString(it) }
    fun hash(password: String, salt: String): String {
        val spec = PBEKeySpec(password.toCharArray(), Base64.getDecoder().decode(salt), ITERATIONS, 256)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return Base64.getEncoder().encodeToString(bytes)
    }
    fun verify(password: String, salt: String, expected: String): Boolean {
        val a = hash(password, salt).toByteArray(); val b = expected.toByteArray()
        if (a.size != b.size) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].toInt() xor b[i].toInt())
        return diff == 0
    }
}

object Validators {
    fun phone(p: String) = Regex("^[6-9]\\d{9}$").matches(p.trim())
    fun email(e: String) = e.isBlank() || Regex("^[\\w.+-]+@[\\w-]+\\.[\\w.]+$").matches(e.trim())
    fun pincode(p: String) = Regex("^[1-8]\\d{5}$").matches(p.trim())
    fun gstin(g: String) = Regex("^\\d{2}[A-Z]{5}\\d{4}[A-Z][1-9A-Z]Z[0-9A-Z]$").matches(g.trim().uppercase())
    fun password(p: String) = p.length >= 8 && p.any { it.isDigit() } && p.any { it.isLetter() }
    fun ifsc(i: String) = Regex("^[A-Z]{4}0[A-Z0-9]{6}$").matches(i.trim().uppercase())
}

/** Pincode serviceability — rule-based: a configured logistics provider would replace this. */
object Serviceability {
    data class Result(val serviceable: Boolean, val zone: String, val extraDays: Int, val codAvailable: Boolean)
    fun check(pincode: String): Result {
        if (!Validators.pincode(pincode)) return Result(false, "Invalid", 0, false)
        val first = pincode.first().digitToInt()
        val metroPrefixes = listOf("110", "400", "560", "600", "700", "500", "411", "380")
        val metro = metroPrefixes.any { pincode.startsWith(it) }
        // Demo rule: a small range is treated as non-serviceable so the error state is testable.
        if (pincode.startsWith("194")) return Result(false, "Remote", 0, false)
        return when {
            metro -> Result(true, "Metro", 0, true)
            first in listOf(7, 8) -> Result(true, "East/North-East", 2, true)
            else -> Result(true, "Regional", 1, true)
        }
    }
}

data class PriceBreakdown(
    val itemsMrp: Double,
    val itemsPrice: Double,
    val productDiscount: Double,
    val couponDiscount: Double,
    val deliveryCharge: Double,
    val taxIncluded: Double,
    val total: Double,
    val couponMessage: String?
)

object Pricing {
    const val FREE_DELIVERY_ABOVE = 499.0
    const val STANDARD_DELIVERY = 49.0
    const val EXPRESS_DELIVERY = 99.0
    const val GST_RATE = 0.18 // prices are GST-inclusive; we show the included component

    fun couponDiscount(c: CouponEntity?, subtotal: Double): Pair<Double, String?> {
        if (c == null) return 0.0 to null
        if (subtotal < c.minOrder) return 0.0 to "Add ₹${(c.minOrder - subtotal).roundToInt()} more to use ${c.code}"
        val raw = if (c.percentOff > 0) subtotal * c.percentOff / 100.0 else c.flatOff
        return min(raw, c.maxDiscount).roundTo2() to "${c.code} applied"
    }

    fun breakdown(lines: List<Triple<Double, Double, Int>>, coupon: CouponEntity?, express: Boolean): PriceBreakdown {
        val mrp = lines.sumOf { it.second * it.third }
        val price = lines.sumOf { it.first * it.third }
        val (cd, msg) = couponDiscount(coupon, price)
        val afterCoupon = max(0.0, price - cd)
        val delivery = when {
            lines.isEmpty() -> 0.0
            express -> EXPRESS_DELIVERY
            afterCoupon >= FREE_DELIVERY_ABOVE -> 0.0
            else -> STANDARD_DELIVERY
        }
        val total = (afterCoupon + delivery).roundTo2()
        val tax = (afterCoupon - afterCoupon / (1 + GST_RATE)).roundTo2()
        return PriceBreakdown(mrp.roundTo2(), price.roundTo2(), (mrp - price).roundTo2(), cd, delivery, tax, total, msg)
    }
}

fun Double.roundTo2() = (this * 100).roundToInt() / 100.0
fun Double.rupees(): String {
    val v = this.roundToInt()
    val s = v.toString()
    if (s.length <= 3) return "₹$s"
    val last3 = s.takeLast(3)
    var rest = s.dropLast(3)
    val parts = mutableListOf<String>()
    while (rest.length > 2) { parts.add(0, rest.takeLast(2)); rest = rest.dropLast(2) }
    if (rest.isNotEmpty()) parts.add(0, rest)
    return "₹" + parts.joinToString(",") + "," + last3
}

// ------------------------- Payments -------------------------

enum class PaymentMethod(val label: String) { UPI("UPI"), CREDIT_CARD("Credit card"), DEBIT_CARD("Debit card"), NET_BANKING("Net banking"), WALLET("Wallet"), COD("Cash on delivery") }

data class PaymentResult(val success: Boolean, val status: String, val reference: String, val message: String)

/** Abstraction so a real gateway (Razorpay, Cashfree, PayU …) can be plugged in server-side. */
interface PaymentProvider {
    val name: String
    val isSandbox: Boolean
    suspend fun pay(amount: Double, method: PaymentMethod, payerHandle: String): PaymentResult
    suspend fun refund(reference: String, amount: Double): PaymentResult
}

/**
 * SANDBOX ONLY. No money moves. Card numbers / CVVs are never collected or stored —
 * only the payer handle (e.g. a test UPI id) is used to simulate outcomes:
 *  - UPI ids containing "fail" are declined, everything else succeeds.
 */
class SandboxPaymentProvider : PaymentProvider {
    override val name = "MEESHO X Sandbox"
    override val isSandbox = true
    override suspend fun pay(amount: Double, method: PaymentMethod, payerHandle: String): PaymentResult {
        kotlinx.coroutines.delay(900)
        val ref = "SBX" + System.currentTimeMillis().toString().takeLast(9)
        if (method == PaymentMethod.COD) return PaymentResult(true, "COD_PENDING", ref, "Pay ${amount.rupees()} on delivery")
        if (payerHandle.contains("fail", ignoreCase = true)) return PaymentResult(false, "FAILED", ref, "Sandbox decline (handle contains 'fail')")
        return PaymentResult(true, "PAID", ref, "Sandbox payment authorised")
    }
    override suspend fun refund(reference: String, amount: Double): PaymentResult {
        kotlinx.coroutines.delay(400)
        return PaymentResult(true, "REFUND_INITIATED", "R$reference", "Sandbox refund of ${amount.rupees()} initiated")
    }
}

// ------------------------- Orders -------------------------

object OrderLifecycle {
    val flow = listOf("PLACED", "CONFIRMED", "SHIPPED", "OUT_FOR_DELIVERY", "DELIVERED")
    private const val HOUR = 60L * 60 * 1000

    /**
     * Without a configured shipping provider the status is SIMULATED from elapsed time
     * (demo: each stage takes ~2 hours) — the UI labels this clearly.
     */
    fun simulatedStatus(createdAt: Long, stored: String, now: Long = System.currentTimeMillis()): String {
        if (stored !in flow) return stored
        val idx = ((now - createdAt) / (2 * HOUR)).toInt().coerceIn(0, flow.lastIndex)
        return flow[max(idx, flow.indexOf(stored))]
    }
    fun canCancel(status: String) = status in listOf("PLACED", "CONFIRMED")
    fun canReturn(status: String, deliveredAt: Long, returnDays: Int, now: Long = System.currentTimeMillis()): Boolean =
        status == "DELIVERED" && returnDays > 0 && now - deliveredAt <= returnDays * 24 * HOUR
    fun label(s: String) = s.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
}

// ------------------------- Fraud / risk -------------------------

/** Explainable rule-based risk scoring. Flags are for HUMAN REVIEW only — never auto-blocks a customer. */
object RiskScorer {
    data class Risk(val score: Int, val flags: List<String>)
    fun score(orderTotal: Double, ordersLast24h: Int, returnsCount: Int, totalOrders: Int, isCod: Boolean, accountAgeDays: Long, quantityMax: Int): Risk {
        val flags = mutableListOf<String>()
        var s = 0
        if (orderTotal > 25_000 && accountAgeDays < 2) { s += 30; flags += "High value order on a new account" }
        if (ordersLast24h >= 5) { s += 25; flags += "$ordersLast24h orders in 24h" }
        if (totalOrders >= 4 && returnsCount.toDouble() / totalOrders > 0.5) { s += 30; flags += "Return rate above 50%" }
        if (isCod && orderTotal > 10_000) { s += 15; flags += "High value COD" }
        if (quantityMax >= 8) { s += 15; flags += "Bulk quantity of a single item" }
        return Risk(min(s, 100), flags)
    }
}
