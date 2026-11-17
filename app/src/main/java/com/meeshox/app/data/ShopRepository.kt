package com.meeshox.app.data

import com.meeshox.app.domain.RiskScorer
import com.meeshox.app.domain.OrderLifecycle
import com.meeshox.app.domain.PasswordHasher
import com.meeshox.app.domain.PaymentMethod
import com.meeshox.app.domain.PaymentProvider
import com.meeshox.app.domain.Pricing
import com.meeshox.app.domain.Serviceability
import com.meeshox.app.domain.Validators
import com.meeshox.app.domain.rupees
import kotlinx.coroutines.flow.Flow

sealed class Outcome<out T> {
    data class Ok<T>(val value: T) : Outcome<T>()
    data class Err(val message: String) : Outcome<Nothing>()
}

/**
 * Single source of truth for the app. This build is LOCAL-FIRST: Room is the data store.
 * The same interface is designed to be backed by the REST API (see docs/ARCHITECTURE.md) once deployed.
 */
class ShopRepository(val dao: ShopDao, val prefs: Prefs, val payments: PaymentProvider, val gateway: com.meeshox.app.payments.RazorpayProvider) {

    /** Razorpay for online methods when a key is configured; sandbox otherwise. COD never touches a gateway. */
    suspend fun gatewayConfig(): com.meeshox.app.payments.GatewayConfig {
        val p = prefs.current()
        return com.meeshox.app.payments.GatewayConfig(p.razorpayKeyId.ifBlank { com.meeshox.app.BuildConfig.RAZORPAY_KEY_ID }, p.paymentServerUrl.ifBlank { com.meeshox.app.BuildConfig.PAYMENT_SERVER_URL })
    }
    private suspend fun providerFor(method: PaymentMethod): PaymentProvider =
        if (method != PaymentMethod.COD && gatewayConfig().configured) gateway else payments
    private fun providerForRef(ref: String): PaymentProvider = if (ref.startsWith("pay_")) gateway else payments


    // ---------------- Auth ----------------
    suspend fun register(name: String, phone: String, email: String, password: String): Outcome<Long> {
        if (name.isBlank()) return Outcome.Err("Enter your name")
        if (!Validators.phone(phone)) return Outcome.Err("Enter a valid 10-digit Indian mobile number")
        if (!Validators.email(email)) return Outcome.Err("Enter a valid email")
        if (!Validators.password(password)) return Outcome.Err("Password needs 8+ characters with letters and numbers")
        if (dao.userByPhone(phone.trim()) != null) return Outcome.Err("An account with this number already exists")
        val salt = PasswordHasher.newSalt()
        val id = dao.insertUser(UserEntity(name = name.trim(), phone = phone.trim(), email = email.trim(), passwordHash = PasswordHasher.hash(password, salt), salt = salt))
        dao.audit(AuditLogEntity(userId = id, action = "REGISTER", detail = "phone=${phone.takeLast(4).padStart(10, '•')}"))
        dao.insertNotification(NotificationEntity(userId = id, title = "Welcome to MEESHO X", body = "Use code WELCOME10 for 10% off your first order."))
        prefs.setUser(id)
        return Outcome.Ok(id)
    }

    suspend fun login(phone: String, password: String): Outcome<Long> {
        val u = dao.userByPhone(phone.trim()) ?: return Outcome.Err("No account found for this number")
        if (!PasswordHasher.verify(password, u.salt, u.passwordHash)) {
            dao.audit(AuditLogEntity(userId = u.id, action = "LOGIN_FAILED", detail = ""))
            return Outcome.Err("Incorrect password")
        }
        dao.audit(AuditLogEntity(userId = u.id, action = "LOGIN", detail = ""))
        prefs.setUser(u.id)
        return Outcome.Ok(u.id)
    }

    suspend fun logout() = prefs.setUser(0)

    suspend fun deleteAccount(uid: Long) {
        dao.clearActiveCart(uid); dao.clearSearch(uid); dao.clearViewed(uid)
        dao.clearMessages(uid, "ASSISTANT"); dao.clearMessages(uid, "SUPPORT")
        dao.audit(AuditLogEntity(userId = null, action = "ACCOUNT_DELETED", detail = "user #$uid"))
        dao.deleteUser(uid)
        prefs.clearAll()
    }

    // ---------------- Catalogue ----------------
    suspend fun recordView(uid: Long, pid: Long) { if (prefs.current().personalization) dao.insertViewed(RecentlyViewedEntity(uid, pid)) }
    suspend fun recordSearch(uid: Long, q: String) { if (q.isNotBlank() && prefs.current().personalization) dao.insertSearch(SearchHistoryEntity(userId = uid, query = q.trim())) }

    // ---------------- Cart ----------------
    suspend fun addToCart(uid: Long, pid: Long, qty: Int, size: String, color: String): Outcome<Unit> {
        val p = dao.product(pid) ?: return Outcome.Err("Product not found")
        if (p.sizeList().isNotEmpty() && size.isBlank()) return Outcome.Err("Please select a size")
        if (p.stock <= 0) return Outcome.Err("Out of stock")
        val existing = dao.findCartItem(uid, pid, size, color)
        val newQty = (existing?.quantity ?: 0) + qty
        if (newQty > p.stock) return Outcome.Err("Only ${p.stock} in stock")
        if (newQty > 10) return Outcome.Err("Maximum 10 per item")
        if (existing != null) dao.updateCartItem(existing.copy(quantity = newQty))
        else dao.insertCartItem(CartItemEntity(userId = uid, productId = pid, quantity = qty, size = size, color = color))
        return Outcome.Ok(Unit)
    }

    suspend fun setQuantity(item: CartItemEntity, qty: Int): Outcome<Unit> {
        if (qty <= 0) { dao.deleteCartItem(item.id); return Outcome.Ok(Unit) }
        val p = dao.product(item.productId) ?: return Outcome.Err("Product not found")
        if (qty > p.stock) return Outcome.Err("Only ${p.stock} in stock")
        if (qty > 10) return Outcome.Err("Maximum 10 per item")
        dao.updateCartItem(item.copy(quantity = qty)); return Outcome.Ok(Unit)
    }

    suspend fun toggleWishlist(uid: Long, pid: Long, inList: Boolean) {
        if (inList) dao.removeWishlist(uid, pid) else dao.addWishlist(WishlistEntity(uid, pid))
    }

    // ---------------- Checkout ----------------
    data class PlacedOrder(val orderId: Long, val paymentMessage: String)

    suspend fun placeOrder(
        uid: Long, address: AddressEntity, couponCode: String?, express: Boolean,
        method: PaymentMethod, payerHandle: String, buyNow: CartItemEntity? = null
    ): Outcome<PlacedOrder> {
        val svc = Serviceability.check(address.pincode)
        if (!svc.serviceable) return Outcome.Err("Sorry, we don't deliver to ${address.pincode} yet")
        if (method == PaymentMethod.COD && !svc.codAvailable) return Outcome.Err("COD not available for this pincode")
        val items = buyNow?.let { listOf(it) } ?: dao.activeCartOnce(uid)
        if (items.isEmpty()) return Outcome.Err("Your cart is empty")
        val products = dao.products(items.map { it.productId }).associateBy { it.id }
        for (i in items) {
            val p = products[i.productId] ?: return Outcome.Err("A product is no longer available")
            if (!p.active || p.stock < i.quantity) return Outcome.Err("${p.name} has only ${p.stock} left — update your cart")
        }
        val coupon = couponCode?.let { dao.coupon(it) }
        val b = Pricing.breakdown(items.map { Triple(products[it.productId]!!.price, products[it.productId]!!.mrp, it.quantity) }, coupon, express)
        if (method == PaymentMethod.COD && b.total > 50_000) return Outcome.Err("COD is limited to orders up to ₹50,000")

        val provider = providerFor(method)
        val payer = dao.user(uid).let { u -> com.meeshox.app.domain.Payer(payerHandle, u?.name ?: "", u?.phone ?: "", u?.email ?: "", "${items.sumOf { it.quantity }} item(s) on MEESHO X") }
        val pay = provider.pay(b.total, method, payer)
        if (!pay.success) {
            dao.audit(AuditLogEntity(userId = uid, action = "PAYMENT_FAILED", detail = "${provider.name} ${method.name} ${pay.status} ${pay.reference}"))
            return Outcome.Err(if (pay.status == "VERIFICATION_FAILED") pay.message else "Payment failed: ${pay.message}")
        }
        // reserve stock atomically per item
        for (i in items) if (dao.decrementStock(i.productId, i.quantity) == 0) {
            val r = provider.refund(pay.reference, b.total)
            return Outcome.Err("Stock changed during checkout. ${r.message}")
        }
        val user = dao.user(uid)
        val accountAgeDays = user?.let { (System.currentTimeMillis() - it.createdAt) / 86_400_000 } ?: 0
        val past = dao.ordersOnce(uid)
        val risk = RiskScorer.score(b.total, dao.ordersSince(uid, System.currentTimeMillis() - 86_400_000), dao.returnCount(uid), past.size,
            method == PaymentMethod.COD, accountAgeDays, items.maxOf { it.quantity })
        val maxDays = items.maxOf { products[it.productId]!!.deliveryDays } + svc.extraDays
        val days = if (express) maxOf(1, maxDays - 2) else maxDays
        val addrText = "${address.fullName}, ${address.line1}, ${address.city}, ${address.state} - ${address.pincode} · ${address.phone}"
        val oid = dao.insertOrder(OrderEntity(
            userId = uid, addressText = addrText, pincode = address.pincode, subtotal = b.itemsPrice, discount = b.couponDiscount,
            deliveryCharge = b.deliveryCharge, tax = b.taxIncluded, total = b.total, couponCode = coupon?.code, paymentMethod = method.name,
            paymentStatus = pay.status, paymentRef = pay.reference, status = "PLACED", deliveryOption = if (express) "EXPRESS" else "STANDARD",
            expectedDelivery = System.currentTimeMillis() + days * 86_400_000L, riskScore = risk.score, riskFlags = risk.flags.joinToString("; ")
        ))
        dao.insertOrderItems(items.map { i ->
            val p = products[i.productId]!!
            OrderItemEntity(orderId = oid, productId = p.id, sellerId = p.sellerId, name = p.name, price = p.price, quantity = i.quantity, size = i.size, color = i.color)
        })
        if (buyNow == null) dao.clearActiveCart(uid)
        dao.insertNotification(NotificationEntity(userId = uid, title = "Order #$oid placed", body = "${b.total.rupees()} · ${method.label}. Track it from Orders."))
        dao.audit(AuditLogEntity(userId = uid, action = "ORDER_PLACED", detail = "#$oid ${b.total} risk=${risk.score}"))
        return Outcome.Ok(PlacedOrder(oid, pay.message))
    }

    suspend fun cancelOrder(o: OrderEntity): Outcome<String> {
        val current = OrderLifecycle.simulatedStatus(o.createdAt, o.status)
        if (!OrderLifecycle.canCancel(current)) return Outcome.Err("This order has already shipped and can't be cancelled")
        dao.orderItems(o.id).forEach { dao.incrementStock(it.productId, it.quantity) }
        var payStatus = o.paymentStatus
        var msg = "Order cancelled."
        if (o.paymentStatus == "PAID" || o.paymentStatus == "PAID_UNVERIFIED") { val r = providerForRef(o.paymentRef).refund(o.paymentRef, o.total); payStatus = r.status; msg += " ${r.message}." }
        dao.updateOrder(o.copy(status = "CANCELLED", paymentStatus = payStatus, updatedAt = System.currentTimeMillis()))
        dao.insertNotification(NotificationEntity(userId = o.userId, title = "Order #${o.id} cancelled", body = msg))
        return Outcome.Ok(msg)
    }

    suspend fun requestReturn(o: OrderEntity, reason: String, exchange: Boolean): Outcome<String> {
        val current = OrderLifecycle.simulatedStatus(o.createdAt, o.status)
        val items = dao.orderItems(o.id)
        val minReturn = dao.products(items.map { it.productId }).minOfOrNull { it.returnDays } ?: 0
        val deliveredAt = o.createdAt + 8 * 60 * 60 * 1000 // simulated delivery time
        if (!OrderLifecycle.canReturn(current, deliveredAt, minReturn)) {
            return Outcome.Err(if (minReturn == 0) "Items in this order are not returnable" else if (current != "DELIVERED") "Returns open after delivery" else "Return window of $minReturn days has passed")
        }
        val refund = if (exchange || o.paymentStatus == "COD_PENDING") null else providerForRef(o.paymentRef).refund(o.paymentRef, o.total)
        dao.updateOrder(o.copy(status = "RETURN_REQUESTED", paymentStatus = refund?.status ?: o.paymentStatus, updatedAt = System.currentTimeMillis()))
        dao.insertTicket(SupportTicketEntity(userId = o.userId, orderId = o.id, subject = if (exchange) "Exchange request" else "Return request", message = reason))
        val msg = if (exchange) "Exchange requested. Pickup will be scheduled once a logistics provider is configured." else "Return requested. ${refund?.message ?: "Refund to bank/UPI after pickup."}"
        dao.insertNotification(NotificationEntity(userId = o.userId, title = "Order #${o.id}: ${if (exchange) "exchange" else "return"} requested", body = msg))
        return Outcome.Ok(msg)
    }

    suspend fun reorder(uid: Long, orderId: Long): Outcome<Int> {
        var added = 0
        for (i in dao.orderItems(orderId)) if (addToCart(uid, i.productId, i.quantity, i.size, i.color) is Outcome.Ok) added++
        return if (added == 0) Outcome.Err("Items are out of stock") else Outcome.Ok(added)
    }

    // ---------------- Seller ----------------
    suspend fun registerSeller(uid: Long, business: String, gstin: String, city: String, ifsc: String, account: String): Outcome<Long> {
        if (business.isBlank()) return Outcome.Err("Enter business name")
        if (!Validators.gstin(gstin)) return Outcome.Err("Invalid GSTIN format (e.g. 19AABCK1234F1Z5)")
        if (!Validators.ifsc(ifsc)) return Outcome.Err("Invalid IFSC code")
        if (account.length !in 9..18 || !account.all { it.isDigit() }) return Outcome.Err("Invalid bank account number")
        val id = dao.insertSeller(SellerEntity(userId = uid, businessName = business.trim(), gstin = gstin.trim().uppercase(), city = city.trim(),
            rating = 0.0, verified = false, bankLast4 = account.takeLast(4)))
        dao.audit(AuditLogEntity(userId = uid, action = "SELLER_REGISTERED", detail = "seller #$id (verification pending)"))
        return Outcome.Ok(id)
    }

    fun ordersFlow(uid: Long): Flow<List<OrderEntity>> = dao.orders(uid)
}
