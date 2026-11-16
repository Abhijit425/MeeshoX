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
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.meeshox.app.ai.ReviewAnalyzer
import com.meeshox.app.ai.SellerTools
import com.meeshox.app.data.DemoSeeder
import com.meeshox.app.data.OrderEntity
import com.meeshox.app.data.Outcome
import com.meeshox.app.data.ProductEntity
import com.meeshox.app.domain.OrderLifecycle
import com.meeshox.app.domain.rupees
import com.meeshox.app.ui.ShopViewModel
import com.meeshox.app.ui.components.*
import com.meeshox.app.ui.go
import com.meeshox.app.ui.theme.DealRed
import com.meeshox.app.ui.theme.Mint

@Composable
fun SellerScreen(vm: ShopViewModel, nav: NavHostController) {
    val uid by vm.uid.collectAsState()
    val seller by vm.dao.sellerForUser(uid).collectAsState(initial = null)
    val s = seller
    if (s == null) { SellerRegistration(vm, nav, uid); return }
    val products by vm.dao.productsBySeller(s.id).collectAsState(initial = emptyList())
    val orderItems by vm.dao.orderItemsForSeller(s.id).collectAsState(initial = emptyList())
    val reviews by vm.dao.reviewsForSeller(s.id).collectAsState(initial = emptyList())
    var orders by remember { mutableStateOf<Map<Long, OrderEntity>>(emptyMap()) }
    var tab by remember { mutableIntStateOf(0) }
    var edit by remember { mutableStateOf<ProductEntity?>(null) }
    LaunchedEffect(orderItems) { orders = vm.dao.ordersByIds(orderItems.map { it.orderId }.distinct()).associateBy { it.id } }
    val valid = orderItems.filter { orders[it.orderId]?.status !in listOf("CANCELLED", null) }
    val revenue = valid.sumOf { it.price * it.quantity }
    val commission = revenue * 0.0 // zero-commission model (configurable server-side)
    val (weekly, forecast) = remember(valid, orders) { SellerTools.forecastWeekly(valid, orders) }

    Screen(s.businessName, nav, actions = { IconButton(onClick = { nav.go("seller_add") }) { Icon(Icons.Outlined.AddBox, "Add product") } }) { pad ->
        Column(Modifier.padding(pad)) {
            if (!s.verified) Surface(color = MaterialTheme.colorScheme.tertiary.copy(alpha = .15f)) {
                Text("Business verification pending: GSTIN ${s.gstin} must be verified by an admin / GST API before listings go live to customers. In this build, your listings are visible immediately for testing.",
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp).fillMaxWidth())
            }
            ScrollableTabRow(tab, edgePadding = 8.dp) {
                listOf("Overview", "Products (${products.size})", "Orders (${orderItems.size})", "Reviews").forEachIndexed { i, t -> Tab(tab == i, { tab = i }, text = { Text(t) }) }
            }
            when (tab) {
                0 -> Column(Modifier.verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Kpi("Revenue", revenue.rupees(), Modifier.weight(1f)); Kpi("Units sold", "${valid.sumOf { it.quantity }}", Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Kpi("Orders", "${valid.map { it.orderId }.distinct().size}", Modifier.weight(1f))
                        Kpi("Payout due", (revenue - commission).rupees(), Modifier.weight(1f))
                    }
                    Card(shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.padding(14.dp)) {
                            Text("Units per week (last 8 weeks)", style = MaterialTheme.typography.titleSmall)
                            BarChart(weekly, (8 downTo 1).map { "W-$it" }, Modifier.fillMaxWidth().height(150.dp).padding(top = 8.dp))
                            Text(forecast?.let { "Forecast next week: ~$it units (4-week moving average)" } ?: "Forecast needs at least 2 weeks of real sales.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
                            EngineBadge("Moving average · actual orders")
                        }
                    }
                    Card(shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.padding(14.dp)) {
                            Text("Inventory alerts", style = MaterialTheme.typography.titleSmall)
                            val low = products.filter { it.stock <= 5 }
                            if (low.isEmpty()) Text("All products have healthy stock.", style = MaterialTheme.typography.bodySmall)
                            low.forEach { p -> Text("• ${p.name}: ${p.stock} left", color = DealRed, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                    Text("Payouts: settlement to bank ••••${s.bankLast4} requires a payment provider with split settlements (e.g. route/marketplace APIs). Not configured in this build.", style = MaterialTheme.typography.labelSmall)
                }
                1 -> LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (products.isEmpty()) item { EmptyState(Icons.Outlined.Inventory2, "No products yet", "List your first product — the description generator helps you write it.", "Add product") { nav.go("seller_add") } }
                    items(products, key = { it.id }) { p ->
                        Card(shape = RoundedCornerShape(14.dp)) {
                            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                ProductArt(p, Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)), iconSize = 26)
                                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                                    Text(p.name, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                                    Text("${p.price.rupees()} · stock ${p.stock} · ${if (p.active) "Live" else "Paused"}", style = MaterialTheme.typography.bodySmall)
                                }
                                IconButton(onClick = { edit = p }) { Icon(Icons.Outlined.Edit, "Edit") }
                            }
                        }
                    }
                }
                2 -> LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (orderItems.isEmpty()) item { EmptyState(Icons.Outlined.Receipt, "No orders yet", "Orders for your products appear here.") }
                    items(orderItems, key = { it.id }) { i ->
                        val o = orders[i.orderId]
                        ListItem(headlineContent = { Text("${i.name} × ${i.quantity}") },
                            supportingContent = { Text("Order #${i.orderId} · ${(i.price * i.quantity).rupees()}" + (o?.let { " · ${fmtDate(it.createdAt)} · ship to ${it.pincode}" } ?: "")) },
                            trailingContent = { o?.let { StatusPill(OrderLifecycle.simulatedStatus(it.createdAt, it.status)) } })
                    }
                }
                else -> LazyColumn(contentPadding = PaddingValues(12.dp)) {
                    val ins = ReviewAnalyzer.analyse(reviews)
                    item {
                        Text("${ins.count} recent reviews · ${ins.average}★ · ${ins.sentiment}", style = MaterialTheme.typography.titleSmall)
                        Text(ins.summary, style = MaterialTheme.typography.bodySmall)
                        Text("Customer demand: top complaint themes → " + ins.complaints.take(3).joinToString { "${it.first} (${it.second})" }.ifEmpty { "none" }, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 6.dp))
                    }
                    items(reviews, key = { it.id }) { r -> ListItem(headlineContent = { Text("${r.rating}★ ${r.userName}") }, supportingContent = { Text(r.text) }) }
                }
            }
        }
    }
    edit?.let { p -> EditProductDialog(p, onDismiss = { edit = null }) { updated -> vm.io { vm.dao.updateProduct(updated); vm.refreshCatalogue(); vm.toast("Product updated") }; edit = null } }
}

@Composable
private fun Kpi(label: String, value: String, modifier: Modifier) {
    Card(modifier, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun EditProductDialog(p: ProductEntity, onDismiss: () -> Unit, onSave: (ProductEntity) -> Unit) {
    var price by remember { mutableStateOf(p.price.toInt().toString()) }
    var mrp by remember { mutableStateOf(p.mrp.toInt().toString()) }
    var stock by remember { mutableStateOf(p.stock.toString()) }
    var active by remember { mutableStateOf(p.active) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Edit ${p.name}") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(price, { price = it.filter(Char::isDigit) }, label = { Text("Selling price ₹") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            OutlinedTextField(mrp, { mrp = it.filter(Char::isDigit) }, label = { Text("MRP ₹ (for discount)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            OutlinedTextField(stock, { stock = it.filter(Char::isDigit) }, label = { Text("Stock") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            Row(verticalAlignment = Alignment.CenterVertically) { Switch(active, { active = it }); Text("  Listing live") }
        }
    }, confirmButton = {
        TextButton(onClick = {
            val pr = price.toDoubleOrNull() ?: return@TextButton; val m = maxOf(mrp.toDoubleOrNull() ?: pr, pr)
            onSave(p.copy(price = pr, mrp = m, stock = stock.toIntOrNull() ?: p.stock, active = active))
        }) { Text("Save") }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun SellerRegistration(vm: ShopViewModel, nav: NavHostController, uid: Long) {
    var business by remember { mutableStateOf("") }
    var gstin by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var ifsc by remember { mutableStateOf("") }
    var account by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    Screen("Become a seller", nav) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Sell to customers across India with zero commission (demo policy).", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(business, { business = it }, label = { Text("Business name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(gstin, { gstin = it.uppercase().take(15) }, label = { Text("GSTIN") }, singleLine = true, modifier = Modifier.fillMaxWidth(), supportingText = { Text("15 characters, e.g. 19AABCK1234F1Z5") })
            OutlinedTextField(city, { city = it }, label = { Text("Pickup city") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(ifsc, { ifsc = it.uppercase().take(11) }, label = { Text("Bank IFSC") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(account, { account = it.filter(Char::isDigit).take(18) }, label = { Text("Bank account number") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), supportingText = { Text("Only the last 4 digits are stored on this device") })
            error?.let { Text(it, color = DealRed) }
            Button(onClick = { vm.io { when (val r = vm.repo.registerSeller(uid, business, gstin, city, ifsc, account)) { is Outcome.Ok -> vm.toast("Seller account created — verification pending"); is Outcome.Err -> error = r.message } } },
                modifier = Modifier.fillMaxWidth()) { Text("Register") }
            Text("Business document upload & GST verification need a backend + verification provider; this build validates formats only.", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SellerAddProductScreen(vm: ShopViewModel, nav: NavHostController) {
    val uid by vm.uid.collectAsState()
    val seller by vm.dao.sellerForUser(uid).collectAsState(initial = null)
    val categories by vm.categories.collectAsState()
    val catalogue by vm.catalogue.collectAsState()
    var name by remember { mutableStateOf("") }
    var brand by remember { mutableStateOf("") }
    var cat by remember { mutableLongStateOf(1L) }
    var material by remember { mutableStateOf("") }
    var colours by remember { mutableStateOf("") }
    var sizes by remember { mutableStateOf("") }
    var features by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var keywords by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var mrp by remember { mutableStateOf("") }
    var stock by remember { mutableStateOf("10") }
    val band = remember(cat, catalogue) { SellerTools.priceRecommendation(cat, null, catalogue) }
    Screen("Add product", nav) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("Product name") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(brand, { brand = it }, label = { Text("Brand") }, modifier = Modifier.fillMaxWidth())
            Text("Category", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { categories.forEach { c -> FilterChip(cat == c.id, { cat = c.id }, label = { Text(c.name) }) } }
            OutlinedTextField(material, { material = it }, label = { Text("Material / key spec") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(colours, { colours = it }, label = { Text("Colours (comma separated, e.g. Red, Navy)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(sizes, { sizes = it }, label = { Text("Sizes (optional, e.g. S,M,L)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(features, { features = it }, label = { Text("Features (comma separated)") }, modifier = Modifier.fillMaxWidth())
            OutlinedButton(onClick = {
                val l = SellerTools.generateListing(name, brand, categories.firstOrNull { it.id == cat }?.name ?: "", material, colours, features)
                title = l.title; desc = l.description; keywords = (l.keywords + l.tags).distinct().joinToString(",")
            }, enabled = name.isNotBlank() && brand.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.AutoAwesome, null); Text("  Generate title, description & keywords") }
            EngineBadge("Template generator · editable")
            OutlinedTextField(title, { title = it }, label = { Text("Listing title") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(desc, { desc = it }, label = { Text("Description") }, minLines = 4, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(keywords, { keywords = it }, label = { Text("Keywords / tags") }, modifier = Modifier.fillMaxWidth())
            band?.let { (lo, mid, hi) -> Text("Pricing recommendation: comparable products sell between ${lo.rupees()} and ${hi.rupees()} (median ${mid.rupees()}).", style = MaterialTheme.typography.bodySmall, color = Mint) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(price, { price = it.filter(Char::isDigit) }, label = { Text("Price ₹") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(mrp, { mrp = it.filter(Char::isDigit) }, label = { Text("MRP ₹") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(stock, { stock = it.filter(Char::isDigit) }, label = { Text("Stock") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
            Text("Product photos, background removal and photo enhancement need an image pipeline/storage backend — not included in this build; listings use generated colour artwork.", style = MaterialTheme.typography.labelSmall)
            Button(onClick = {
                val s = seller ?: run { vm.toast("Register as a seller first"); return@Button }
                val pr = price.toDoubleOrNull()
                if (title.isBlank() || pr == null || pr <= 0) { vm.toast("Enter a title and price"); return@Button }
                val colourList = colours.split(",").map { it.trim().replaceFirstChar { c -> c.uppercase() } }.filter { it.isNotEmpty() }
                val hex = colourList.firstNotNullOfOrNull { DemoSeeder.colourPalette[it] } ?: 0xFF7C3AED
                vm.io {
                    vm.dao.insertProduct(ProductEntity(name = title.trim(), brand = brand.trim(), categoryId = cat, subType = name.trim().lowercase().split(" ").last(),
                        description = desc.trim(), price = pr, mrp = maxOf(mrp.toDoubleOrNull() ?: pr, pr), rating = 0.0, ratingCount = 0, stock = stock.toIntOrNull() ?: 0,
                        sellerId = s.id, colors = colourList.joinToString(","), sizes = sizes.replace(" ", ""), colorHex = hex, popularity = 0, deliveryDays = 5,
                        tags = keywords.lowercase(), specs = if (material.isNotBlank()) "material=$material" else ""))
                    vm.refreshCatalogue(); vm.toast("Product listed")
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { nav.popBackStack() }
                }
            }, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("Publish listing") }
        }
    }
}

// ------------------------------------------------------------------ Admin (local overview)

@Composable
fun AdminLiteScreen(vm: ShopViewModel, nav: NavHostController) {
    val uid by vm.uid.collectAsState()
    var orders by remember { mutableStateOf<List<OrderEntity>>(emptyList()) }
    LaunchedEffect(uid) { orders = vm.dao.ordersOnce(uid) }
    val valid = orders.filter { it.status != "CANCELLED" }
    val gmv = valid.sumOf { it.subtotal }
    val revenue = valid.sumOf { it.total }
    val returns = orders.count { it.status.startsWith("RETURN") }
    Screen("Admin overview (local)", nav) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("The full admin panel is a separate web app (Next.js) backed by the server API — see docs. This screen summarises data stored on this device only.", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { Kpi("GMV", gmv.rupees(), Modifier.weight(1f)); Kpi("Revenue", revenue.rupees(), Modifier.weight(1f)) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Kpi("Orders", "${orders.size}", Modifier.weight(1f))
                Kpi("AOV", if (valid.isEmpty()) "—" else (revenue / valid.size).rupees(), Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Kpi("Return rate", if (orders.isEmpty()) "—" else "${returns * 100 / orders.size}%", Modifier.weight(1f))
                Kpi("Cancelled", "${orders.count { it.status == "CANCELLED" }}", Modifier.weight(1f))
            }
            Text("Fraud & risk review queue", style = MaterialTheme.typography.titleMedium)
            Text("Explainable rule scores. Flags are for human review only — customers are never auto-blocked.", style = MaterialTheme.typography.bodySmall)
            val flagged = orders.filter { it.riskScore > 0 }
            if (flagged.isEmpty()) Text("No orders flagged.", style = MaterialTheme.typography.bodySmall)
            flagged.forEach { o ->
                Card(Modifier.clickable { nav.go("order/${o.id}") }, shape = RoundedCornerShape(12.dp)) {
                    ListItem(headlineContent = { Text("Order #${o.id} · risk ${o.riskScore}/100") }, supportingContent = { Text(o.riskFlags) })
                }
            }
        }
    }
}
