package com.meeshox.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.meeshox.app.data.AddressEntity
import com.meeshox.app.data.CartItemEntity
import com.meeshox.app.data.CouponEntity
import com.meeshox.app.data.Outcome
import com.meeshox.app.data.ProductEntity
import com.meeshox.app.domain.PaymentMethod
import com.meeshox.app.domain.PriceBreakdown
import com.meeshox.app.domain.Pricing
import com.meeshox.app.domain.Serviceability
import com.meeshox.app.domain.Validators
import com.meeshox.app.domain.rupees
import com.meeshox.app.ui.ShopViewModel
import com.meeshox.app.ui.components.*
import com.meeshox.app.ui.go
import com.meeshox.app.ui.theme.DealRed
import com.meeshox.app.ui.theme.Mint

@Composable
fun CartScreen(vm: ShopViewModel, nav: NavHostController) {
    val uid by vm.uid.collectAsState()
    val cartState by vm.dao.cart(uid).collectAsState(initial = null)
    val coupons by vm.dao.coupons().collectAsState(initial = emptyList())
    var products by remember { mutableStateOf<Map<Long, ProductEntity>>(emptyMap()) }
    var coupon by remember { mutableStateOf<CouponEntity?>(null) }
    var showCoupons by remember { mutableStateOf(false) }
    LaunchedEffect(cartState) { cartState?.let { l -> products = vm.dao.products(l.map { it.productId }).associateBy { it.id } } }

    val list = cartState
    val active = list?.filter { !it.savedForLater && products.containsKey(it.productId) } ?: emptyList()
    val saved = list?.filter { it.savedForLater && products.containsKey(it.productId) } ?: emptyList()
    val b = Pricing.breakdown(active.map { Triple(products[it.productId]!!.price, products[it.productId]!!.mrp, it.quantity) }, coupon, false)
    val stockIssues = active.filter { (products[it.productId]?.stock ?: 0) < it.quantity }

    Screen("My cart", nav, bottomBar = {
        if (active.isNotEmpty()) Surface(shadowElevation = 8.dp) {
            Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(b.total.rupees(), style = MaterialTheme.typography.titleLarge)
                    if (b.productDiscount + b.couponDiscount > 0) Text("You save ${(b.productDiscount + b.couponDiscount).rupees()}", color = Mint, style = MaterialTheme.typography.labelSmall)
                }
                Button(onClick = { vm.pendingCoupon = coupon?.code; nav.go("checkout") },
                    enabled = stockIssues.isEmpty(), modifier = Modifier.height(50.dp)) { Text("Place order") }
            }
        }
    }) { pad ->
        when {
            list == null -> Box(Modifier.padding(pad)) { SkeletonGrid() }
            active.isEmpty() && saved.isEmpty() -> Box(Modifier.padding(pad)) {
                EmptyState(Icons.Outlined.ShoppingCart, "Your cart is empty", "Add items you love and they'll show up here.", "Explore products") { nav.go("home") }
            }
            else -> LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (stockIssues.isNotEmpty()) item {
                    Surface(color = DealRed.copy(alpha = 0.1f), shape = RoundedCornerShape(12.dp)) {
                        Text("Some items exceed available stock. Reduce quantity to continue.", color = DealRed, modifier = Modifier.padding(12.dp))
                    }
                }
                items(active, key = { it.id }) { c -> CartRow(vm, nav, c, products[c.productId]!!) }
                item {
                    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.clickable { showCoupons = true }) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.LocalOffer, null)
                            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                                Text(coupon?.code ?: "Apply coupon", style = MaterialTheme.typography.titleSmall)
                                Text(b.couponMessage ?: "${coupons.size} offers available", style = MaterialTheme.typography.bodySmall,
                                    color = if (b.couponDiscount > 0) Mint else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (coupon != null) TextButton(onClick = { coupon = null }) { Text("Remove") } else Icon(Icons.Outlined.ChevronRight, null)
                        }
                    }
                }
                if (active.isNotEmpty()) item { PriceDetails(b, active.sumOf { it.quantity }) }
                if (saved.isNotEmpty()) {
                    item { Text("Saved for later (${saved.size})", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
                    items(saved, key = { it.id }) { c -> CartRow(vm, nav, c, products[c.productId]!!) }
                }
            }
        }
    }
    if (showCoupons) AlertDialog(onDismissRequest = { showCoupons = false }, title = { Text("Coupons") }, text = {
        var code by remember { mutableStateOf("") }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(code, { code = it.uppercase() }, label = { Text("Enter code") }, singleLine = true, modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    val c = coupons.firstOrNull { it.code == code.trim() }
                    if (c == null) vm.toast("Invalid coupon") else { coupon = c; showCoupons = false }
                }) { Text("Apply") }
            }
            coupons.forEach { c ->
                val ok = b.itemsPrice >= c.minOrder
                ListItem(headlineContent = { Text(c.code, fontWeight = FontWeight.Bold) }, supportingContent = { Text(c.description + if (!ok) "\nAdd ${(c.minOrder - b.itemsPrice).rupees()} more" else "") },
                    trailingContent = { TextButton(onClick = { coupon = c; showCoupons = false }, enabled = ok) { Text("Apply") } })
            }
        }
    }, confirmButton = { TextButton(onClick = { showCoupons = false }) { Text("Close") } })
}

@Composable
private fun CartRow(vm: ShopViewModel, nav: NavHostController, c: CartItemEntity, p: ProductEntity) {
    Card(shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.clickable { nav.go("product/${p.id}") }) {
                ProductArt(p, Modifier.size(80.dp).clip(RoundedCornerShape(12.dp)), iconSize = 34)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(p.name, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                    Text(listOfNotNull(c.size.takeIf { it.isNotBlank() }?.let { "Size $it" }, c.color.takeIf { it.isNotBlank() }).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                    PriceRow(p)
                    if (p.stock < c.quantity) Text(if (p.stock == 0) "Out of stock" else "Only ${p.stock} available", color = DealRed, style = MaterialTheme.typography.labelSmall)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                if (!c.savedForLater) {
                    OutlinedIconButton(onClick = { vm.io { vm.handle(vm.repo.setQuantity(c, c.quantity - 1)) {} } }) { Icon(Icons.Outlined.Remove, "Less") }
                    Text("${c.quantity}", Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.titleMedium)
                    OutlinedIconButton(onClick = { vm.io { vm.handle(vm.repo.setQuantity(c, c.quantity + 1)) {} } }) { Icon(Icons.Outlined.Add, "More") }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { vm.io { vm.dao.updateCartItem(c.copy(savedForLater = !c.savedForLater)) } }) { Text(if (c.savedForLater) "Move to cart" else "Save for later") }
                TextButton(onClick = { vm.io { vm.dao.deleteCartItem(c.id) } }) { Text("Remove") }
            }
        }
    }
}

@Composable
fun PriceDetails(b: PriceBreakdown, count: Int, express: Boolean = false) {
    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Price details ($count items)", style = MaterialTheme.typography.titleSmall)
            Line("Total MRP", b.itemsMrp.rupees())
            if (b.productDiscount > 0) Line("Discount on MRP", "−" + b.productDiscount.rupees(), Mint)
            if (b.couponDiscount > 0) Line("Coupon discount", "−" + b.couponDiscount.rupees(), Mint)
            Line(if (express) "Express delivery" else "Delivery charges", if (b.deliveryCharge == 0.0) "FREE" else b.deliveryCharge.rupees(), if (b.deliveryCharge == 0.0) Mint else null)
            HorizontalDivider()
            Line("Order total", b.total.rupees(), bold = true)
            Text("Includes GST of ${b.taxIncluded.rupees()} (18%, indicative)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!express && b.deliveryCharge > 0) Text("Free delivery on orders above ${Pricing.FREE_DELIVERY_ABOVE.rupees()}", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun Line(label: String, value: String, color: androidx.compose.ui.graphics.Color? = null, bold: Boolean = false) {
    Row {
        Text(label, Modifier.weight(1f), fontWeight = if (bold) FontWeight.Bold else null)
        Text(value, color = color ?: LocalContentColor.current, fontWeight = if (bold) FontWeight.Bold else null)
    }
}

@Composable
fun CheckoutScreen(vm: ShopViewModel, nav: NavHostController, buyNowId: Long, buyNowSize: String, buyNowColor: String) {
    val uid by vm.uid.collectAsState()
    val addresses by vm.dao.addresses(uid).collectAsState(initial = emptyList())
    val cartItems by vm.dao.cart(uid).collectAsState(initial = emptyList())
    val coupons by vm.dao.coupons().collectAsState(initial = emptyList())
    var products by remember { mutableStateOf<Map<Long, ProductEntity>>(emptyMap()) }
    var addressId by remember { mutableLongStateOf(0L) }
    var express by remember { mutableStateOf(false) }
    var method by remember { mutableStateOf(PaymentMethod.UPI) }
    var handle by remember { mutableStateOf("success@sandbox") }
    var gw by remember { mutableStateOf<com.meeshox.app.payments.GatewayConfig?>(null) }
    LaunchedEffect(Unit) { gw = vm.repo.gatewayConfig() }
    val live = gw?.configured == true
    var couponCode by remember { mutableStateOf(vm.pendingCoupon) }
    var placing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var addAddress by remember { mutableStateOf(false) }

    val buyNow = if (buyNowId > 0) CartItemEntity(userId = uid, productId = buyNowId, quantity = 1, size = buyNowSize, color = buyNowColor) else null
    val lines = buyNow?.let { listOf(it) } ?: cartItems.filter { !it.savedForLater }
    LaunchedEffect(lines.map { it.productId }) { products = vm.dao.products(lines.map { it.productId }).associateBy { it.id } }
    LaunchedEffect(addresses) { if (addressId == 0L || addresses.none { it.id == addressId }) addressId = addresses.firstOrNull()?.id ?: 0L }
    val address = addresses.firstOrNull { it.id == addressId }
    val coupon = coupons.firstOrNull { it.code == couponCode }
    val ready = lines.isNotEmpty() && lines.all { products.containsKey(it.productId) }
    val b = if (ready) Pricing.breakdown(lines.map { Triple(products[it.productId]!!.price, products[it.productId]!!.mrp, it.quantity) }, coupon, express) else null
    val svc = address?.let { Serviceability.check(it.pincode) }

    Screen("Checkout", nav, bottomBar = {
        Surface(shadowElevation = 8.dp) {
            Column(Modifier.navigationBarsPadding().padding(12.dp)) {
                error?.let { Text(it, color = DealRed, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 6.dp)) }
                Button(onClick = {
                    val a = address ?: run { error = "Add a delivery address"; return@Button }
                    if (!live && method == PaymentMethod.UPI && !handle.contains("@")) { error = "Enter a UPI ID like name@bank"; return@Button }
                    placing = true; error = null
                    vm.io {
                        val r = vm.repo.placeOrder(uid, a, coupon?.code, express, method, handle, buyNow)
                        placing = false
                        when (r) {
                            is Outcome.Ok -> { vm.pendingCoupon = null; vm.refreshCatalogue(); vm.handle(r) { nav.navigate("success/${it.orderId}") { popUpTo("home") } } }
                            is Outcome.Err -> error = r.message
                        }
                    }
                }, enabled = !placing && b != null && svc?.serviceable != false, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    if (placing) { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp); Text(if (live && method != PaymentMethod.COD) "  Waiting for Razorpay…" else "  Placing order…") }
                    else Text(if (method == PaymentMethod.COD) "Place order · ${b?.total?.rupees() ?: ""}" else "Pay ${b?.total?.rupees() ?: ""}" + if (live) " with Razorpay" else " (sandbox)")
                }
            }
        }
    }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Step("1", "Delivery address")
            if (addresses.isEmpty()) OutlinedButton(onClick = { addAddress = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Add, null); Text(" Add address") }
            addresses.forEach { a ->
                Card(shape = RoundedCornerShape(14.dp), modifier = Modifier.clickable { addressId = a.id }) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                        RadioButton(a.id == addressId, { addressId = a.id })
                        Column {
                            Text(a.fullName, style = MaterialTheme.typography.titleSmall)
                            Text("${a.line1}, ${a.city}, ${a.state} - ${a.pincode}", style = MaterialTheme.typography.bodySmall)
                            Text("📞 ${a.phone}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            if (addresses.isNotEmpty()) TextButton(onClick = { addAddress = true }) { Text("+ Add another address") }
            svc?.let { s ->
                Text(if (s.serviceable) "✓ Serviceable (${s.zone})" + (if (s.codAvailable) " · COD available" else "") else "✗ We don't deliver to ${address?.pincode} yet",
                    color = if (s.serviceable) Mint else DealRed, style = MaterialTheme.typography.bodySmall)
            }

            Step("2", "Delivery option")
            val maxDays = lines.mapNotNull { products[it.productId]?.deliveryDays }.maxOrNull() ?: 5
            val extra = svc?.extraDays ?: 0
            DeliveryOption("Standard", "By ${fmtDate(System.currentTimeMillis() + (maxDays + extra) * 86_400_000L)} · free above ₹499", !express) { express = false }
            DeliveryOption("Express", "By ${fmtDate(System.currentTimeMillis() + maxOf(1, maxDays + extra - 2) * 86_400_000L)} · ₹99", express) { express = true }

            Step("3", "Order summary")
            lines.forEach { l -> products[l.productId]?.let { p ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProductArt(p, Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)), iconSize = 22)
                    Text("${p.name} × ${l.quantity}", Modifier.weight(1f).padding(horizontal = 8.dp), maxLines = 1, style = MaterialTheme.typography.bodySmall)
                    Text((p.price * l.quantity).rupees(), style = MaterialTheme.typography.titleSmall)
                }
            } }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Coupon: ", style = MaterialTheme.typography.titleSmall)
                var expanded by remember { mutableStateOf(false) }
                Box {
                    AssistChip(onClick = { expanded = true }, label = { Text(couponCode ?: "Select coupon") })
                    DropdownMenu(expanded, { expanded = false }) {
                        DropdownMenuItem(text = { Text("No coupon") }, onClick = { couponCode = null; expanded = false })
                        coupons.forEach { c -> DropdownMenuItem(text = { Text("${c.code} — ${c.description}") }, onClick = { couponCode = c.code; expanded = false }) }
                    }
                }
            }
            b?.let { PriceDetails(it, lines.sumOf { l -> l.quantity }, express) }

            Step("4", "Payment")
            val g = gw
            Surface(color = (if (live) Mint else MaterialTheme.colorScheme.tertiary).copy(alpha = 0.15f), shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().clickable { nav.go("payment_settings") }) {
                Text(
                    when {
                        g == null -> "Checking payment gateway…"
                        !g.configured -> "SANDBOX MODE — no Razorpay key set, so no real money is charged. Tap to connect Razorpay. (A UPI ID containing \"fail\" simulates a decline.)"
                        g.testMode -> "Razorpay TEST mode — real gateway, test money. Use Razorpay test cards/UPI (e.g. success@razorpay)." + if (g.hasServer) " Server verification ON." else " No verification server."
                        else -> "Razorpay LIVE — real money will be charged." + if (g.hasServer) " Payments are verified by your server." else " ⚠ No verification server configured."
                    } + " Card details are entered only in Razorpay's secure checkout, never stored by MEESHO X.",
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(10.dp))
            }
            PaymentMethod.entries.forEach { m ->
                val codBlocked = m == PaymentMethod.COD && (svc?.codAvailable == false || (b?.total ?: 0.0) > 50_000)
                Row(Modifier.fillMaxWidth().clickable(enabled = !codBlocked) { method = m }, verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(method == m, { method = m }, enabled = !codBlocked)
                    Text(m.label + if (codBlocked) " (unavailable)" else "")
                }
            }
            when (method) {
                PaymentMethod.UPI -> if (live) Text("Razorpay checkout opens with UPI (apps, QR or UPI ID).", style = MaterialTheme.typography.bodySmall)
                    else OutlinedTextField(handle, { handle = it.trim() }, label = { Text("UPI ID (sandbox)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                PaymentMethod.CREDIT_CARD, PaymentMethod.DEBIT_CARD -> Text(if (live) "Enter card details in Razorpay's PCI-DSS checkout (OTP/3-D Secure)." else "Sandbox will authorise the payment — no card details are asked for.", style = MaterialTheme.typography.bodySmall)
                PaymentMethod.NET_BANKING -> Text(if (live) "Choose your bank in Razorpay checkout." else "Sandbox will authorise the payment.", style = MaterialTheme.typography.bodySmall)
                PaymentMethod.WALLET -> Text(if (live) "Choose a wallet in Razorpay checkout." else "Sandbox will authorise the payment.", style = MaterialTheme.typography.bodySmall)
                PaymentMethod.COD -> Text("Pay in cash or UPI at delivery.", style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(80.dp))
        }
    }
    if (addAddress) AddressDialog(onDismiss = { addAddress = false }) { a ->
        vm.io { val id = vm.dao.insertAddress(a.copy(userId = uid)); addressId = id }
        addAddress = false
    }
}

@Composable private fun Step(n: String, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primary) { Text(n, color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.padding(horizontal = 9.dp, vertical = 2.dp)) }
        Text("  $title", style = MaterialTheme.typography.titleMedium)
    }
}

@Composable private fun DeliveryOption(title: String, sub: String, selected: Boolean, onClick: () -> Unit) {
    Card(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected, onClick)
            Column { Text(title, style = MaterialTheme.typography.titleSmall); Text(sub, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
fun AddressDialog(onDismiss: () -> Unit, onSave: (AddressEntity) -> Unit) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var line by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var state by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("New address") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("Full name") }, singleLine = true)
            OutlinedTextField(phone, { if (it.length <= 10) phone = it.filter(Char::isDigit) }, label = { Text("Mobile") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
            OutlinedTextField(line, { line = it }, label = { Text("House no., street, area") })
            OutlinedTextField(city, { city = it }, label = { Text("City") }, singleLine = true)
            OutlinedTextField(state, { state = it }, label = { Text("State") }, singleLine = true)
            OutlinedTextField(pin, { if (it.length <= 6) pin = it.filter(Char::isDigit) }, label = { Text("Pincode") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            err?.let { Text(it, color = DealRed) }
        }
    }, confirmButton = {
        TextButton(onClick = {
            err = when {
                name.isBlank() || line.isBlank() || city.isBlank() || state.isBlank() -> "Fill in all fields"
                !Validators.phone(phone) -> "Invalid mobile number"
                !Validators.pincode(pin) -> "Invalid pincode"
                else -> null
            }
            if (err == null) onSave(AddressEntity(userId = 0, fullName = name.trim(), phone = phone, line1 = line.trim(), city = city.trim(), state = state.trim(), pincode = pin))
        }) { Text("Save") }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
fun AddressesScreen(vm: ShopViewModel, nav: NavHostController) {
    val uid by vm.uid.collectAsState()
    val addresses by vm.dao.addresses(uid).collectAsState(initial = emptyList())
    var add by remember { mutableStateOf(false) }
    Screen("Saved addresses", nav, actions = { IconButton(onClick = { add = true }) { Icon(Icons.Outlined.Add, "Add") } }) { pad ->
        if (addresses.isEmpty()) Box(Modifier.padding(pad)) { EmptyState(Icons.Outlined.Home, "No saved addresses", "Add an address for faster checkout.", "Add address") { add = true } }
        else LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(addresses, key = { it.id }) { a ->
                Card(shape = RoundedCornerShape(14.dp)) {
                    ListItem(headlineContent = { Text(a.fullName) }, supportingContent = { Text("${a.line1}, ${a.city}, ${a.state} - ${a.pincode}\n${a.phone}") },
                        trailingContent = { IconButton(onClick = { vm.io { vm.dao.deleteAddress(a.id) } }) { Icon(Icons.Outlined.Delete, "Delete") } })
                }
            }
        }
    }
    if (add) AddressDialog({ add = false }) { a -> vm.io { vm.dao.insertAddress(a.copy(userId = uid)) }; add = false }
}

@Composable
fun OrderSuccessScreen(vm: ShopViewModel, nav: NavHostController, id: Long) {
    val order by vm.dao.orderFlow(id).collectAsState(initial = null)
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Filled.CheckCircle, null, tint = Mint, modifier = Modifier.size(96.dp))
        Text("Order placed!", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 12.dp))
        order?.let { o ->
            Text("Order #${o.id} · ${o.total.rupees()}", style = MaterialTheme.typography.titleMedium)
            Text("Payment: ${o.paymentMethod.replace('_', ' ').lowercase()} · ${o.paymentStatus.replace('_', ' ').lowercase()} · ${if (o.paymentRef.startsWith("pay_")) "Razorpay" else "sandbox"} ref ${o.paymentRef}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
            Text("Expected by ${fmtDate(o.expectedDelivery)}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
        }
        Spacer(Modifier.height(24.dp))
        Button(onClick = { nav.navigate("order/$id") { popUpTo("home") } }, modifier = Modifier.fillMaxWidth()) { Text("Track order") }
        OutlinedButton(onClick = { nav.navigate("home") { popUpTo("home") { inclusive = true } } }, modifier = Modifier.fillMaxWidth()) { Text("Continue shopping") }
    }
}
