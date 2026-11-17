package com.meeshox.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.meeshox.app.ai.ReorderPredictor
import com.meeshox.app.data.ChatMessageEntity
import com.meeshox.app.data.SharedListEntity
import com.meeshox.app.data.SharedListItemEntity
import com.meeshox.app.data.SupportTicketEntity
import com.meeshox.app.domain.OrderLifecycle
import com.meeshox.app.domain.rupees
import com.meeshox.app.ui.ShopViewModel
import com.meeshox.app.ui.components.*
import com.meeshox.app.ui.go
import com.meeshox.app.ui.theme.BrandGradient
import com.meeshox.app.ui.theme.DealRed

@Composable
fun ProfileScreen(vm: ShopViewModel, nav: NavHostController) {
    val user by vm.user.collectAsState()
    val prefs by vm.prefs.collectAsState()
    val uid by vm.uid.collectAsState()
    val seller by vm.dao.sellerForUser(uid).collectAsState(initial = null)
    var confirmDelete by remember { mutableStateOf(false) }
    var langMenu by remember { mutableStateOf(false) }
    val p = prefs ?: return
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().background(BrandGradient).statusBarsPadding().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(60.dp).clip(CircleShape).background(Color.White.copy(alpha = .25f)), contentAlignment = Alignment.Center) {
                Text(user?.name?.take(1)?.uppercase() ?: "?", style = MaterialTheme.typography.headlineSmall, color = Color.White)
            }
            Column(Modifier.padding(start = 14.dp)) {
                Text(user?.name ?: "", style = MaterialTheme.typography.titleLarge, color = Color.White)
                Text("+91 ${user?.phone ?: ""}" + (user?.email?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""), color = Color.White.copy(alpha = .85f), style = MaterialTheme.typography.bodySmall)
            }
        }
        Section("Shopping")
        Item(Icons.Outlined.Inventory2, "My orders", "Track, cancel, return, invoices") { nav.go("orders") }
        Item(Icons.Outlined.ShoppingCart, "Cart") { nav.go("cart") }
        Item(Icons.Outlined.FavoriteBorder, "Wishlist") { nav.go("wishlist") }
        Item(Icons.Outlined.Home, "Saved addresses") { nav.go("addresses") }
        Item(Icons.Outlined.Groups, "Group shopping lists") { nav.go("lists") }
        Item(Icons.Outlined.Notifications, "Notifications") { nav.go("notifications") }
        Item(Icons.Outlined.SupportAgent, "Help & support", "Chatbot, complaints, tickets") { nav.go("support") }

        Section("Personalisation & privacy")
        SwitchItem(Icons.Outlined.AutoAwesome, "Personalised recommendations", "Uses your views, searches, wishlist & orders on this device", p.personalization) {
            vm.io { vm.repo.prefs.setPersonalization(it); if (!it) { vm.dao.clearViewed(uid); vm.dao.clearSearch(uid) }; vm.refreshRecommendations() }
        }
        Item(Icons.Outlined.Psychology, "Personal shopping memory", "Brands, sizes and budget you approve") { nav.go("memory") }
        SwitchItem(Icons.Outlined.LocalShipping, "Order notifications", null, p.orderNotifications) { vm.io { vm.repo.prefs.setOrderNotif(it) } }
        SwitchItem(Icons.Outlined.LocalOffer, "Deal & price-drop notifications", null, p.dealNotifications) { vm.io { vm.repo.prefs.setDealNotif(it) } }
        SwitchItem(Icons.Outlined.DarkMode, "Dark theme", null, p.darkTheme) { vm.io { vm.repo.prefs.setDark(it) } }
        Box {
            Item(Icons.Outlined.Translate, "Language", p.language + " (UI translations coming soon)") { langMenu = true }
            DropdownMenu(langMenu, { langMenu = false }) {
                listOf("English", "हिन्दी", "বাংলা", "தமிழ்", "తెలుగు", "मराठी").forEach { l -> DropdownMenuItem(text = { Text(l) }, onClick = { langMenu = false; vm.io { vm.repo.prefs.setLanguage(l) } }) }
            }
        }

        Section("Business")
        Item(Icons.Outlined.Storefront, if (seller == null) "Become a seller" else "Seller dashboard", seller?.let { "${it.businessName}" + if (it.verified) "" else " · verification pending" }) { nav.go("seller") }
        Item(Icons.Outlined.Payments, "Payment gateway", "Connect Razorpay (key ID & verification server)") { nav.go("payment_settings") }
        Item(Icons.Outlined.AdminPanelSettings, "Admin overview (local)", "Analytics & fraud flags from this device's data") { nav.go("admin") }

        Section("Account")
        Item(Icons.Outlined.Logout, "Log out") { vm.io { vm.repo.logout() } }
        Item(Icons.Outlined.DeleteForever, "Delete account", "Permanently removes your account data from this device", tint = DealRed) { confirmDelete = true }
        Text("MEESHO X v0.1.0 · development build with seeded demo catalogue", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("Delete account?") },
        text = { Text("This deletes your profile, cart, search history, chats and preferences from this device. Order records are kept anonymised for accounting. This can't be undone.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; vm.io { vm.repo.deleteAccount(uid) } }) { Text("Delete", color = DealRed) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } })
}

@Composable private fun Section(t: String) = Text(t, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 16.dp, top = 18.dp, bottom = 4.dp))

@Composable private fun Item(icon: ImageVector, title: String, sub: String? = null, tint: Color? = null, onClick: () -> Unit) {
    ListItem(headlineContent = { Text(title, color = tint ?: LocalContentColor.current) }, supportingContent = { if (sub != null) Text(sub) },
        leadingContent = { Icon(icon, null, tint = tint ?: MaterialTheme.colorScheme.onSurfaceVariant) },
        trailingContent = { Icon(Icons.Outlined.ChevronRight, null) }, modifier = Modifier.clickable(onClick = onClick))
}

@Composable private fun SwitchItem(icon: ImageVector, title: String, sub: String?, value: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(headlineContent = { Text(title) }, supportingContent = { if (sub != null) Text(sub) }, leadingContent = { Icon(icon, null) },
        trailingContent = { Switch(value, onChange) }, modifier = Modifier.clickable { onChange(!value) })
}

@Composable
fun ShoppingMemoryScreen(vm: ShopViewModel, nav: NavHostController) {
    val prefs by vm.prefs.collectAsState()
    val p = prefs ?: return
    var brands by remember { mutableStateOf(p.favouriteBrands) }
    var size by remember { mutableStateOf(p.preferredSize) }
    var budget by remember { mutableStateOf(p.budget) }
    Screen("Personal shopping memory", nav) { pad ->
        Column(Modifier.padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Only what you approve here is remembered. It's stored on this device and used to rank recommendations.", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(brands, { brands = it }, label = { Text("Favourite brands (comma separated)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(size, { size = it }, label = { Text("Usual clothing size (e.g. M, 32)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(budget, { budget = it.filter(Char::isDigit) }, label = { Text("Typical budget per item (₹)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { vm.io { vm.repo.prefs.setMemory(brands.trim(), size.trim(), budget.trim()); vm.refreshRecommendations(); vm.toast("Saved") } }, modifier = Modifier.weight(1f)) { Text("Save") }
                OutlinedButton(onClick = { brands = ""; size = ""; budget = ""; vm.io { vm.repo.prefs.setMemory("", "", ""); vm.toast("Memory cleared") } }, modifier = Modifier.weight(1f)) { Text("Forget all") }
            }
        }
    }
}

@Composable
fun NotificationsScreen(vm: ShopViewModel, nav: NavHostController) {
    val uid by vm.uid.collectAsState()
    val list by vm.dao.notifications(uid).collectAsState(initial = emptyList())
    LaunchedEffect(Unit) { vm.dao.markNotificationsRead(uid) }
    Screen("Notifications", nav) { pad ->
        if (list.isEmpty()) Box(Modifier.padding(pad)) { EmptyState(Icons.Outlined.NotificationsNone, "All caught up", "Order and deal updates will show here.") }
        else LazyColumn(Modifier.padding(pad)) {
            items(list, key = { it.id }) { n ->
                ListItem(headlineContent = { Text(n.title) }, supportingContent = { Text(n.body + "\n" + fmtDateTime(n.at)) },
                    leadingContent = { Icon(Icons.Outlined.Notifications, null, tint = if (n.read) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary) })
                HorizontalDivider()
            }
        }
    }
}

// ------------------------------------------------------------------ Support

private fun supportReply(text: String, orders: List<com.meeshox.app.data.OrderEntity>): Pair<String, Boolean> {
    val t = text.lowercase()
    val last = orders.maxByOrNull { it.createdAt }
    return when {
        Regex("human|agent|executive|person|call me").containsMatchIn(t) -> "I've escalated this to a human support agent and created a ticket. Note: in this build tickets are stored locally until a support desk is connected." to true
        Regex("where|track|status|deliver").containsMatchIn(t) -> (last?.let { "Your latest order #${it.id} is ${OrderLifecycle.label(it.status).lowercase()}, expected by ${fmtDate(it.expectedDelivery)}. Open Orders for the full timeline." } ?: "You don't have any orders yet.") to false
        Regex("refund").containsMatchIn(t) -> (last?.let { "Order #${it.id} payment status: ${OrderLifecycle.label(it.paymentStatus)}. Refunds go back to the original payment method — sandbox refunds don't move real money." } ?: "No orders found.") to false
        Regex("return|exchange|size").containsMatchIn(t) -> "You can return or exchange delivered items within the product's return window: Orders → select order → Return / exchange. The assistant checks eligibility automatically." to false
        Regex("cancel").containsMatchIn(t) -> "Orders can be cancelled before they ship: Orders → select order → Cancel. Prepaid amounts are refunded automatically." to false
        Regex("complain|damaged|broken|wrong|fake|bad").containsMatchIn(t) -> "Sorry about that. I've registered a complaint ticket so our team can review it." to true
        Regex("pay|upi|card|cod").containsMatchIn(t) -> "Online payments go through Razorpay (UPI, cards, net banking, wallets) when a key is configured; otherwise a sandbox is used. Cash on delivery is also available." to false
        else -> "I can help with order tracking, returns, refunds, cancellations and complaints. Type \"talk to a human\" to escalate." to false
    }
}

@Composable
fun SupportScreen(vm: ShopViewModel, nav: NavHostController) {
    val uid by vm.uid.collectAsState()
    val msgs by vm.dao.messages(uid, "SUPPORT").collectAsState(initial = emptyList())
    val tickets by vm.dao.tickets(uid).collectAsState(initial = emptyList())
    var input by remember { mutableStateOf("") }
    var tab by remember { mutableIntStateOf(0) }
    fun send(t: String) {
        if (t.isBlank()) return
        input = ""
        vm.io {
            vm.dao.insertMessage(ChatMessageEntity(userId = uid, channel = "SUPPORT", fromUser = true, text = t))
            val orders = vm.dao.ordersOnce(uid)
            val (reply, ticket) = supportReply(t, orders)
            if (ticket) vm.dao.insertTicket(SupportTicketEntity(userId = uid, orderId = orders.maxByOrNull { it.createdAt }?.id, subject = if (t.contains("human", true)) "Escalation" else "Complaint", message = t,
                status = if (t.contains("human", true)) "ESCALATED" else "OPEN"))
            vm.dao.insertMessage(ChatMessageEntity(userId = uid, channel = "SUPPORT", fromUser = false, text = reply))
        }
    }
    Screen("Help & support", nav) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().imePadding()) {
            TabRow(tab) {
                Tab(tab == 0, { tab = 0 }, text = { Text("Chat") })
                Tab(tab == 1, { tab = 1 }, text = { Text("Tickets (${tickets.size})") })
            }
            if (tab == 0) {
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { EngineBadge("Support bot · keyword rules") }
                    item { Bubble("Hi! Ask me about tracking, returns, refunds or complaints.", false) }
                    items(msgs, key = { it.id }) { m -> Bubble(m.text, m.fromUser) }
                }
                Row(Modifier.padding(horizontal = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Where is my order?", "Refund status", "Return an item", "Item arrived damaged", "Talk to a human").forEach { s -> SuggestionChip(onClick = { send(s) }, label = { Text(s) }) }
                }
                Row(Modifier.padding(8.dp).navigationBarsPadding(), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(input, { input = it }, Modifier.weight(1f), placeholder = { Text("Type your question") })
                    IconButton(onClick = { send(input) }) { Icon(Icons.AutoMirrored.Filled.Send, "Send") }
                }
            } else LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (tickets.isEmpty()) item { EmptyState(Icons.Outlined.ConfirmationNumber, "No tickets", "Complaints and escalations appear here.") }
                items(tickets, key = { it.id }) { t ->
                    Card(shape = RoundedCornerShape(14.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Row { Text("#${t.id} ${t.subject}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f)); LabelPill(t.status) }
                            Text(t.message, style = MaterialTheme.typography.bodySmall)
                            Text(fmtDateTime(t.createdAt) + (t.orderId?.let { " · order #$it" } ?: ""), style = MaterialTheme.typography.labelSmall)
                            if (t.status != "RESOLVED") TextButton(onClick = { vm.io { vm.dao.updateTicket(t.copy(status = "RESOLVED")) } }) { Text("Mark resolved") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Bubble(text: String, mine: Boolean) {
    Box(Modifier.fillMaxWidth(), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
        Surface(color = if (mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(16.dp), modifier = Modifier.widthIn(max = 300.dp)) {
            Text(text, Modifier.padding(10.dp), color = if (mine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
        }
    }
}

// ------------------------------------------------------------------ Group shopping lists

@Composable
fun SharedListsScreen(vm: ShopViewModel, nav: NavHostController) {
    val uid by vm.uid.collectAsState()
    val user by vm.user.collectAsState()
    val lists by vm.dao.lists(uid).collectAsState(initial = emptyList())
    val wish by vm.wishlist.collectAsState()
    val catalogue by vm.catalogue.collectAsState()
    var open by remember { mutableStateOf<SharedListEntity?>(null) }
    var newName by remember { mutableStateOf("") }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    Screen(open?.name ?: "Group shopping lists", nav) { pad ->
        val l = open
        if (l == null) LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Text("Create a list for a wedding, trip or Diwali shopping and share it with family. Collaborators on other phones need the cloud backend (see docs); on this device anyone can add items.", style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(newName, { newName = it }, label = { Text("New list name") }, singleLine = true, modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        if (newName.isNotBlank()) { val n = newName.trim(); newName = ""
                            vm.io { vm.dao.insertList(SharedListEntity(ownerId = uid, name = n, shareCode = (100000..999999).random().toString())) } }
                    }) { Text("Create") }
                }
            }
            items(lists, key = { it.id }) { s ->
                Card(Modifier.clickable { open = s }, shape = RoundedCornerShape(14.dp)) {
                    ListItem(headlineContent = { Text(s.name) }, supportingContent = { Text("Share code ${s.shareCode}") }, leadingContent = { Icon(Icons.Outlined.Groups, null) },
                        trailingContent = { IconButton(onClick = { vm.io { vm.dao.deleteList(s.id) } }) { Icon(Icons.Outlined.Delete, "Delete") } })
                }
            }
        } else {
            val listItems by vm.dao.listItems(l.id).collectAsState(initial = emptyList())
            LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { open = null }) { Text("← All lists") }
                        Button(onClick = {
                            val names = listItems.mapNotNull { i -> catalogue.firstOrNull { it.id == i.productId }?.let { "• ${it.name} — ${it.price.rupees()}" } }
                            val txt = "Join my MEESHO X list \"${l.name}\" (code ${l.shareCode}):\n" + names.joinToString("\n")
                            ctx.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, txt), "Share list"))
                        }) { Text("Share") }
                    }
                    val total = listItems.sumOf { i -> catalogue.firstOrNull { it.id == i.productId }?.price ?: 0.0 }
                    Text("${listItems.size} items · ${total.rupees()}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                }
                items(listItems, key = { it.productId }) { i ->
                    catalogue.firstOrNull { it.id == i.productId }?.let { p ->
                        ListItem(headlineContent = { Text(p.name) }, supportingContent = { Text("${p.price.rupees()} · added by ${i.addedBy}") },
                            modifier = Modifier.clickable { nav.go("product/${p.id}") },
                            trailingContent = { IconButton(onClick = { vm.io { vm.dao.removeListItem(l.id, p.id) } }) { Icon(Icons.Outlined.RemoveCircleOutline, "Remove") } })
                    }
                }
                item { Text("Add from your wishlist", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp)) }
                items(wish.filter { w -> listItems.none { it.productId == w } }) { pid ->
                    catalogue.firstOrNull { it.id == pid }?.let { p ->
                        ListItem(headlineContent = { Text(p.name) }, supportingContent = { Text(p.price.rupees()) },
                            trailingContent = { IconButton(onClick = { vm.io { vm.dao.insertListItem(SharedListItemEntity(l.id, p.id, user?.name ?: "me")) } }) { Icon(Icons.Outlined.AddCircleOutline, "Add") } })
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Smart reorder

@Composable
fun SmartReorderScreen(vm: ShopViewModel, nav: NavHostController) {
    val uid by vm.uid.collectAsState()
    var suggestions by remember { mutableStateOf<List<ReorderPredictor.Suggestion>?>(null) }
    LaunchedEffect(uid) {
        val items = vm.dao.allOrderItemsForUser(uid)
        val orders = vm.dao.ordersOnce(uid).filter { it.status != "CANCELLED" }.associateBy { it.id }
        val products = vm.dao.products(items.map { it.productId }.distinct()).associateBy { it.id }
        suggestions = ReorderPredictor.suggest(items.filter { it.orderId in orders }, orders, products)
    }
    Screen("Smart reorder", nav) { pad ->
        val s = suggestions
        if (s == null) Box(Modifier.padding(pad)) { SkeletonGrid() }
        else if (s.isEmpty()) Box(Modifier.padding(pad)) { EmptyState(Icons.Outlined.Replay, "Nothing to reorder yet", "Buy groceries, beauty or baby essentials and we'll predict when you'll need them again — from your actual order history.") }
        else LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { EngineBadge("Interval average from your orders") }
            items(s, key = { it.productId }) { r ->
                Card(shape = RoundedCornerShape(14.dp)) {
                    ListItem(headlineContent = { Text(r.name) },
                        supportingContent = {
                            Text("Bought ${r.timesBought}× · " + when {
                                r.avgIntervalDays == null -> "need 2+ orders to predict timing"
                                (r.dueInDays ?: 0) <= 0 -> "due now (every ~${r.avgIntervalDays} days)"
                                else -> "likely needed in ${r.dueInDays} days"
                            })
                        },
                        trailingContent = { Button(onClick = { vm.addToCart(r.productId, "", "") }) { Text("Add") } },
                        modifier = Modifier.clickable { nav.go("product/${r.productId}") })
                }
            }
        }
    }
}
