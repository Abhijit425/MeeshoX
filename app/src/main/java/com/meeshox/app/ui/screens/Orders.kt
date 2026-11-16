package com.meeshox.app.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.meeshox.app.data.NotificationEntity
import com.meeshox.app.data.OrderEntity
import com.meeshox.app.data.OrderItemEntity
import com.meeshox.app.data.Outcome
import com.meeshox.app.domain.OrderLifecycle
import com.meeshox.app.domain.rupees
import com.meeshox.app.ui.ShopViewModel
import com.meeshox.app.ui.components.*
import com.meeshox.app.ui.go
import com.meeshox.app.ui.theme.DealRed
import com.meeshox.app.ui.theme.Mint

/** Advances simulated shipment status (no logistics provider configured) and raises notifications on change. */
suspend fun syncOrderStatuses(vm: ShopViewModel, orders: List<OrderEntity>) {
    val notify = vm.repo.prefs.current().orderNotifications
    for (o in orders) {
        val s = OrderLifecycle.simulatedStatus(o.createdAt, o.status)
        if (s != o.status) {
            val pay = if (s == "DELIVERED" && o.paymentStatus == "COD_PENDING") "PAID" else o.paymentStatus
            vm.dao.updateOrder(o.copy(status = s, paymentStatus = pay, updatedAt = System.currentTimeMillis()))
            if (notify) vm.dao.insertNotification(NotificationEntity(userId = o.userId, title = "Order #${o.id}: ${OrderLifecycle.label(s)}", body = "Simulated shipment update (no courier connected)."))
        }
    }
}

@Composable
fun OrdersScreen(vm: ShopViewModel, nav: NavHostController) {
    val uid by vm.uid.collectAsState()
    val orders by vm.dao.orders(uid).collectAsState(initial = null)
    var itemsByOrder by remember { mutableStateOf<Map<Long, List<OrderItemEntity>>>(emptyMap()) }
    LaunchedEffect(orders?.size) {
        orders?.let { list -> syncOrderStatuses(vm, list); itemsByOrder = list.associate { it.id to vm.dao.orderItems(it.id) } }
    }
    Screen("My orders", nav) { pad ->
        val list = orders
        when {
            list == null -> Box(Modifier.padding(pad)) { SkeletonGrid() }
            list.isEmpty() -> Box(Modifier.padding(pad)) { EmptyState(Icons.Outlined.Inventory2, "No orders yet", "Your orders will appear here.", "Start shopping") { nav.go("home") } }
            else -> LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(list, key = { it.id }) { o ->
                    val its = itemsByOrder[o.id].orEmpty()
                    Card(Modifier.clickable { nav.go("order/${o.id}") }, shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Order #${o.id}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                StatusPill(o.status)
                            }
                            Text(its.joinToString { it.name }.ifEmpty { "…" }, maxLines = 2, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 4.dp))
                            Text("${o.total.rupees()} · ${fmtDate(o.createdAt)} · ${its.sumOf { it.quantity }} items", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatusPill(status: String) {
    val (bg, fg) = when (status) {
        "DELIVERED" -> Mint.copy(alpha = .15f) to Mint
        "CANCELLED", "RETURNED" -> DealRed.copy(alpha = .12f) to DealRed
        "RETURN_REQUESTED" -> Color(0xFFF59E0B).copy(alpha = .15f) to Color(0xFFB45309)
        else -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.primary
    }
    LabelPill(OrderLifecycle.label(status), bg, fg)
}

@Composable
fun OrderDetailScreen(vm: ShopViewModel, nav: NavHostController, id: Long) {
    val order by vm.dao.orderFlow(id).collectAsState(initial = null)
    val uid by vm.uid.collectAsState()
    var items by remember { mutableStateOf<List<OrderItemEntity>>(emptyList()) }
    var returnDialog by remember { mutableStateOf(false) }
    var confirmCancel by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    LaunchedEffect(id) { items = vm.dao.orderItems(id); vm.dao.order(id)?.let { syncOrderStatuses(vm, listOf(it)) } }
    val o = order ?: run { Screen("Order", nav) { Box(Modifier.padding(it).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }; return }

    Screen("Order #${o.id}", nav) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Shipment status", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f)); StatusPill(o.status)
                    }
                    Text("Simulated tracking — no courier provider is connected in this build.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    if (o.status in OrderLifecycle.flow) {
                        val idx = OrderLifecycle.flow.indexOf(o.status)
                        OrderLifecycle.flow.forEachIndexed { i, s ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(16.dp).clip(CircleShape).background(if (i <= idx) Mint else MaterialTheme.colorScheme.outline))
                                Text("  ${OrderLifecycle.label(s)}", style = if (i == idx) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
                                    color = if (i <= idx) LocalContentColor.current else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (i < OrderLifecycle.flow.lastIndex) Box(Modifier.padding(start = 7.dp).width(2.dp).height(18.dp).background(if (i < idx) Mint else MaterialTheme.colorScheme.outline))
                        }
                        Text(if (o.status == "DELIVERED") "Delivered" else "Expected by ${fmtDate(o.expectedDelivery)} (${o.deliveryOption.lowercase()})", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                        Text("Delivery partner: " + if (idx >= 2) "assigned by courier once a logistics provider is configured" else "not yet assigned", style = MaterialTheme.typography.bodySmall)
                    } else Text(when (o.status) {
                        "CANCELLED" -> "This order was cancelled."
                        "RETURN_REQUESTED" -> "Return/exchange requested — pickup pending."
                        else -> OrderLifecycle.label(o.status)
                    })
                }
            }
            Card(shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Text("Items", style = MaterialTheme.typography.titleMedium)
                    items.forEach { i ->
                        Row(Modifier.fillMaxWidth().clickable { nav.go("product/${i.productId}") }.padding(vertical = 6.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(i.name, style = MaterialTheme.typography.bodyMedium)
                                Text(listOfNotNull("Qty ${i.quantity}", i.size.takeIf { it.isNotBlank() }?.let { "Size $it" }, i.color.takeIf { it.isNotBlank() }).joinToString(" · "), style = MaterialTheme.typography.labelSmall)
                            }
                            Text((i.price * i.quantity).rupees())
                        }
                    }
                }
            }
            Card(shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Payment & delivery", style = MaterialTheme.typography.titleMedium)
                    Text("Subtotal ${o.subtotal.rupees()} · Coupon −${o.discount.rupees()} · Delivery ${if (o.deliveryCharge == 0.0) "FREE" else o.deliveryCharge.rupees()}", style = MaterialTheme.typography.bodySmall)
                    Text("Total ${o.total.rupees()} (incl. GST ${o.tax.rupees()})", style = MaterialTheme.typography.titleSmall)
                    Text("${o.paymentMethod.replace('_', ' ').lowercase()} · ${OrderLifecycle.label(o.paymentStatus)} · ref ${o.paymentRef} (sandbox)", style = MaterialTheme.typography.bodySmall)
                    if (o.paymentStatus.startsWith("REFUND")) Text("Refund status: ${OrderLifecycle.label(o.paymentStatus)} — sandbox refunds don't move money.", color = Mint, style = MaterialTheme.typography.bodySmall)
                    Text("Ship to: ${o.addressText}", style = MaterialTheme.typography.bodySmall)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (OrderLifecycle.canCancel(o.status)) OutlinedButton(onClick = { confirmCancel = true }, modifier = Modifier.weight(1f)) { Text("Cancel") }
                if (o.status == "DELIVERED") OutlinedButton(onClick = { returnDialog = true }, modifier = Modifier.weight(1f)) { Text("Return / exchange") }
                Button(onClick = { vm.io { vm.handle(vm.repo.reorder(uid, o.id)) { n -> vm.toast("$n item(s) added to cart"); nav.go("cart") } } }, modifier = Modifier.weight(1f)) { Text("Reorder") }
            }
            OutlinedButton(onClick = {
                val text = buildInvoice(o, items)
                ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT, "MEESHO X invoice #${o.id}").putExtra(Intent.EXTRA_TEXT, text), "Share / save invoice"))
            }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Receipt, null); Text("  Download / share invoice") }
            OutlinedButton(onClick = { nav.go("support") }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.SupportAgent, null); Text("  Need help with this order?") }
        }
    }
    if (confirmCancel) AlertDialog(onDismissRequest = { confirmCancel = false }, title = { Text("Cancel order #${o.id}?") },
        text = { Text("Stock is released and any prepaid amount is refunded to the original method (sandbox).") },
        confirmButton = { TextButton(onClick = { confirmCancel = false; vm.io { vm.handle(vm.repo.cancelOrder(o)) { vm.toast(it) } } }) { Text("Cancel order", color = DealRed) } },
        dismissButton = { TextButton(onClick = { confirmCancel = false }) { Text("Keep order") } })
    if (returnDialog) ReturnDialog(onDismiss = { returnDialog = false }) { reason, exchange ->
        returnDialog = false
        vm.io { vm.handle(vm.repo.requestReturn(o, reason, exchange)) { vm.toast(it) } }
    }
}

@Composable
private fun ReturnDialog(onDismiss: () -> Unit, onSubmit: (String, Boolean) -> Unit) {
    val reasons = listOf("Size doesn't fit", "Received damaged item", "Different from description", "Quality not as expected", "Changed my mind")
    var reason by remember { mutableStateOf(reasons.first()) }
    var exchange by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Smart return assistant") }, text = {
        Column {
            Text("Eligibility is checked against each item's return window.", style = MaterialTheme.typography.bodySmall)
            reasons.forEach { r -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { reason = r }) { RadioButton(reason == r, { reason = r }); Text(r) } }
            Row(verticalAlignment = Alignment.CenterVertically) { Switch(exchange, { exchange = it }); Text("  Exchange instead of refund") }
            if (reason == "Size doesn't fit" && !exchange) Text("Tip: an exchange for another size is usually faster than a refund.", style = MaterialTheme.typography.labelSmall, color = Mint)
        }
    }, confirmButton = { TextButton(onClick = { onSubmit(reason, exchange) }) { Text("Submit") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

fun buildInvoice(o: OrderEntity, items: List<OrderItemEntity>): String = buildString {
    appendLine("MEESHO X — TAX INVOICE (demo)")
    appendLine("Order #${o.id}   Date: ${fmtDate(o.createdAt)}")
    appendLine("Ship to: ${o.addressText}")
    appendLine("----------------------------------------")
    items.forEach { appendLine("${it.name}  x${it.quantity}  ${(it.price * it.quantity).rupees()}") }
    appendLine("----------------------------------------")
    appendLine("Subtotal: ${o.subtotal.rupees()}")
    if (o.discount > 0) appendLine("Coupon (${o.couponCode}): -${o.discount.rupees()}")
    appendLine("Delivery: ${o.deliveryCharge.rupees()}")
    appendLine("Total: ${o.total.rupees()}  (incl. GST ${o.tax.rupees()})")
    appendLine("Payment: ${o.paymentMethod} / ${o.paymentStatus} / ${o.paymentRef} (sandbox)")
    appendLine("This is a demo invoice generated by a development build.")
}
