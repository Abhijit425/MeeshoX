package com.meeshox.app.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.meeshox.app.ai.BudgetPlanner
import com.meeshox.app.ai.ColourMatcher
import com.meeshox.app.ai.PriceIntelligence
import com.meeshox.app.ai.Ranker
import com.meeshox.app.ai.ReviewAnalyzer
import com.meeshox.app.ai.ShoppingAssistant
import com.meeshox.app.data.ChatMessageEntity
import com.meeshox.app.data.DemoSeeder
import com.meeshox.app.data.ProductEntity
import com.meeshox.app.domain.rupees
import com.meeshox.app.ui.ShopViewModel
import com.meeshox.app.ui.components.*
import com.meeshox.app.ui.go
import com.meeshox.app.ui.theme.BrandGradient
import com.meeshox.app.ui.theme.Mint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AiHubScreen(vm: ShopViewModel, nav: NavHostController) {
    val voice = rememberVoiceLauncher { routeCommand(it, nav) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Column(Modifier.fillMaxWidth().background(BrandGradient).statusBarsPadding().padding(20.dp)) {
            Icon(Icons.Filled.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(36.dp))
            Text("AI Shopping World", style = MaterialTheme.typography.headlineSmall, color = Color.White)
            Text("Describe what you want instead of scrolling through thousands of products.", color = Color.White.copy(alpha = .9f))
            Spacer(Modifier.height(10.dp))
            Surface(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().clickable { nav.go("assistant") }) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Ask: phone under ₹20,000 with best camera…", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    IconButton(onClick = voice) { Icon(Icons.Outlined.Mic, "Voice", tint = MaterialTheme.colorScheme.primary) }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("Runs fully on-device with transparent rules — no paid AI API required. Results are rule-based, not generative AI.", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = .8f))
        }
        val tools = listOf(
            Triple(Icons.Outlined.Forum, "Shopping assistant", "assistant"),
            Triple(Icons.Outlined.CameraAlt, "Visual / screenshot search", "visual"),
            Triple(Icons.Outlined.Checkroom, "Fashion stylist", "assistant?mode=stylist"),
            Triple(Icons.Outlined.Redeem, "Gift finder", "assistant?mode=gift"),
            Triple(Icons.Outlined.Savings, "Smart budget shopping", "budget"),
            Triple(Icons.Outlined.Radar, "Live deal radar", "deals"),
            Triple(Icons.Outlined.CompareArrows, "Compare up to 5", "compare"),
            Triple(Icons.Outlined.Replay, "Smart reorder", "reorder"),
            Triple(Icons.Outlined.Psychology, "Shopping memory", "memory"),
            Triple(Icons.Outlined.Groups, "Group shopping lists", "lists")
        )
        tools.chunked(2).forEach { row ->
            Row(Modifier.padding(horizontal = 12.dp, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (icon, label, route) ->
                    Card(Modifier.weight(1f).clickable { nav.go(route) }, shape = RoundedCornerShape(18.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            Box(Modifier.size(42.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                                Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                            }
                            Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 10.dp))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun AssistantScreen(vm: ShopViewModel, nav: NavHostController, mode: String) {
    val uid by vm.uid.collectAsState()
    val channel = "ASSISTANT"
    val messages by vm.dao.messages(uid, channel).collectAsState(initial = emptyList())
    val catalogue by vm.catalogue.collectAsState()
    val categories by vm.categories.collectAsState()
    var input by remember { mutableStateOf("") }
    var thinking by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val assistant = remember { ShoppingAssistant() }
    val voice = rememberVoiceLauncher { input = it }

    fun send(text: String) {
        if (text.isBlank()) return
        input = ""; thinking = true
        vm.io {
            vm.dao.insertMessage(ChatMessageEntity(userId = uid, channel = channel, fromUser = true, text = text))
            vm.repo.recordSearch(uid, text)
            val reply = assistant.reply(text, catalogue.ifEmpty { vm.dao.allProducts() }, categories, vm.dao.ordersOnce(uid))
            vm.dao.insertMessage(ChatMessageEntity(userId = uid, channel = channel, fromUser = false, text = reply.text + (reply.action?.let { "\n[[${it}]]" } ?: ""), productIds = reply.productIds.joinToString(",")))
            thinking = false
        }
    }
    LaunchedEffect(mode) { if (mode.startsWith("q:")) send(mode.removePrefix("q:")) }
    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size) }

    val suggestions = when (mode) {
        "gift" -> listOf("Birthday gift for my mother under ₹1,500", "Anniversary gift for wife under 3000", "Gift for my father under ₹2,000", "Gift for a baby under 1000")
        "stylist" -> listOf("Suggest a wedding outfit under ₹3,000", "Party outfit for women under 2500", "Festive look for men under ₹4,000", "Casual college outfit under 1500")
        else -> listOf("Smartphone under ₹20,000 with best camera and battery", "Red shoes under ₹1,000", "Show today's best deals", "Best combo under ₹5,000", "Track my last order")
    }
    Screen(when (mode) { "gift" -> "AI gift finder"; "stylist" -> "AI fashion stylist"; else -> "Shopping assistant" }, nav,
        actions = { IconButton(onClick = { vm.io { vm.dao.clearMessages(uid, channel) } }) { Icon(Icons.Outlined.DeleteSweep, "Clear chat") } }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().imePadding()) {
            LazyColumn(Modifier.weight(1f), state = listState, contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    Column {
                        EngineBadge()
                        Text("I search the real catalogue, compare specs, prices and ratings, and explain my picks. I don't generate free-form AI text.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
                    }
                }
                items(messages, key = { it.id }) { m -> ChatBubble(m, catalogue, nav, vm) }
                if (thinking) item { Text("Searching catalogue…", style = MaterialTheme.typography.bodySmall) }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                suggestions.forEach { s -> SuggestionChip(onClick = { send(s) }, label = { Text(s) }) }
            }
            Row(Modifier.padding(8.dp).navigationBarsPadding(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(input, { input = it }, Modifier.weight(1f), placeholder = { Text("Describe what you need…") }, maxLines = 3,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send), keyboardActions = KeyboardActions(onSend = { send(input) }),
                    trailingIcon = { IconButton(onClick = voice) { Icon(Icons.Outlined.Mic, "Voice") } })
                IconButton(onClick = { send(input) }) { Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = MaterialTheme.colorScheme.primary) }
            }
        }
    }
}

@Composable
private fun ChatBubble(m: ChatMessageEntity, catalogue: List<ProductEntity>, nav: NavHostController, vm: ShopViewModel) {
    val action = Regex("\\[\\[(.+)]]").find(m.text)?.groupValues?.get(1)
    val text = m.text.replace(Regex("\\n?\\[\\[.+]]"), "").replace("**", "")
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (m.fromUser) Alignment.End else Alignment.Start) {
        Surface(color = if (m.fromUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(18.dp), modifier = Modifier.widthIn(max = 320.dp)) {
            Text(text, color = if (m.fromUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
        }
        val ids = m.productIds.split(",").mapNotNull { it.toLongOrNull() }
        val ps = ids.mapNotNull { id -> catalogue.firstOrNull { it.id == id } }
        if (ps.isNotEmpty()) {
            LazyRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ps, key = { it.id }) { p ->
                    Card(Modifier.width(150.dp).clickable { nav.go("product/${p.id}") }, shape = RoundedCornerShape(14.dp)) {
                        ProductArt(p, Modifier.fillMaxWidth().height(90.dp), iconSize = 40)
                        Column(Modifier.padding(8.dp)) {
                            Text(p.name, maxLines = 2, style = MaterialTheme.typography.labelSmall)
                            Text(p.price.rupees(), style = MaterialTheme.typography.titleSmall)
                            RatingChip(p.rating)
                            TextButton(onClick = { vm.toggleCompare(p.id) }, contentPadding = PaddingValues(0.dp)) { Text("Compare") }
                        }
                    }
                }
            }
            if (ps.size > 1) TextButton(onClick = { ps.take(5).forEach { if (it.id !in vm.compare.value) vm.toggleCompare(it.id) }; nav.go("compare") }) { Text("Compare these side by side") }
        }
        if (action != null) TextButton(onClick = { if (action.startsWith("order:")) nav.go("order/${action.removePrefix("order:")}") else nav.go(action) }) {
            Text(if (action.startsWith("order:")) "Open tracking" else "Open orders")
        }
    }
}

// ------------------------------------------------------------------ Visual search

private fun dominantColour(bmp: Bitmap): Pair<Long, List<Pair<String, Int>>> {
    val small = Bitmap.createScaledBitmap(bmp, 48, 48, true)
    val counts = HashMap<String, Int>()
    var r = 0L; var g = 0L; var b = 0L; var n = 0
    for (x in 0 until small.width) for (y in 0 until small.height) {
        val c = small.getPixel(x, y)
        val cr = (c shr 16) and 0xFF; val cg = (c shr 8) and 0xFF; val cb = c and 0xFF
        val max = maxOf(cr, cg, cb); val min = minOf(cr, cg, cb)
        // weight centre pixels more (subject usually centred) and skip flat white/grey backgrounds
        val centre = x in 12..35 && y in 12..35
        if (max > 235 && max - min < 18 && !centre) continue
        val argb = 0xFF000000L or (cr.toLong() shl 16) or (cg.toLong() shl 8) or cb.toLong()
        val name = ColourMatcher.nearestName(argb, DemoSeeder.colourPalette)
        val w = if (centre) 3 else 1
        counts[name] = (counts[name] ?: 0) + w
        if (centre) { r += cr; g += cg; b += cb; n++ }
    }
    val ranked = counts.entries.sortedByDescending { it.value }.map { it.key to it.value }
    val top = ranked.firstOrNull()?.first
    val hex = top?.let { DemoSeeder.colourPalette[it] } ?: (0xFF000000L or ((r / maxOf(n, 1)) shl 16) or ((g / maxOf(n, 1)) shl 8) or (b / maxOf(n, 1)))
    val total = ranked.sumOf { it.second }.coerceAtLeast(1)
    return hex to ranked.take(4).map { it.first to it.second * 100 / total }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VisualSearchScreen(vm: ShopViewModel, nav: NavHostController) {
    val ctx = LocalContext.current
    val catalogue by vm.catalogue.collectAsState()
    val categories by vm.categories.collectAsState()
    val wish by vm.wishlist.collectAsState()
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var colours by remember { mutableStateOf<List<Pair<String, Int>>>(emptyList()) }
    var hex by remember { mutableStateOf<Long?>(null) }
    var cat by remember { mutableStateOf<Long?>(null) }
    var busy by remember { mutableStateOf(false) }

    suspend fun analyse(b: Bitmap) {
        busy = true
        val res = withContext(Dispatchers.Default) { dominantColour(b) }
        hex = res.first; colours = res.second; busy = false
    }
    val scope = rememberCoroutineScope()
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
            val b = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            if (b == null) vm.toast("Couldn't read that image") else { bitmap = b; analyse(b) }
        }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { b: Bitmap? ->
        if (b != null) { bitmap = b; scope.launch { analyse(b) } }
    }
    val results = remember(hex, cat, catalogue) { hex?.let { ColourMatcher.match(catalogue, it, cat, 30) } ?: emptyList() }

    Screen("Visual search", nav) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Card(shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Shop from a photo or screenshot", style = MaterialTheme.typography.titleMedium)
                        Text("Upload an Instagram/Pinterest screenshot or take a photo. MEESHO X extracts the dominant colours on-device and finds catalogue items with matching colours.", style = MaterialTheme.typography.bodySmall)
                        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(onClick = { gallery.launch("image/*") }, modifier = Modifier.weight(1f)) { Icon(Icons.Outlined.Image, null); Text(" Upload") }
                            OutlinedButton(onClick = { try { camera.launch(null) } catch (e: Exception) { vm.toast("No camera app available") } }, modifier = Modifier.weight(1f)) { Icon(Icons.Outlined.CameraAlt, null); Text(" Camera") }
                        }
                        Spacer(Modifier.height(8.dp))
                        EngineBadge("On-device colour analysis")
                        Text("Product-type and shape recognition need an image model, which isn't bundled in this build — pick a category below to narrow results.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
            bitmap?.let { b ->
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(b.asImageBitmap(), null, Modifier.size(110.dp).clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Crop)
                        Column(Modifier.padding(start = 12.dp)) {
                            if (busy) CircularProgressIndicator(Modifier.size(24.dp))
                            Text("Detected colours", style = MaterialTheme.typography.titleSmall)
                            colours.forEach { (name, pct) ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(14.dp).clip(CircleShape).background(Color(DemoSeeder.colourPalette[name] ?: 0xFF888888)))
                                    Text("  $name · $pct%", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
                item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(cat == null, { cat = null }, label = { Text("All") })
                        categories.forEach { c -> FilterChip(cat == c.id, { cat = c.id }, label = { Text(c.name) }) }
                    }
                }
                if (colours.size > 1) item {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Match colour:", style = MaterialTheme.typography.bodySmall)
                        colours.forEach { (name, _) -> AssistChip(onClick = { hex = DemoSeeder.colourPalette[name] }, label = { Text(name) }) }
                    }
                }
            }
            items(results.chunked(2)) { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { p: ProductEntity -> ProductCard(p, p.id in wish, { nav.go("product/${p.id}") }, { vm.toggleWishlist(p.id) }, Modifier.weight(1f)) }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Compare

@Composable
fun CompareScreen(vm: ShopViewModel, nav: NavHostController) {
    val ids by vm.compare.collectAsState()
    val catalogue by vm.catalogue.collectAsState()
    val ps = ids.mapNotNull { id -> catalogue.firstOrNull { it.id == id } }
    var reviewMap by remember { mutableStateOf<Map<Long, com.meeshox.app.ai.ReviewInsights>>(emptyMap()) }
    LaunchedEffect(ids) { reviewMap = ids.associateWith { ReviewAnalyzer.analyse(vm.dao.reviewsOnce(it)) } }
    Screen("Compare (${ps.size}/5)", nav, actions = { if (ps.isNotEmpty()) TextButton(onClick = { vm.compare.value = emptyList() }) { Text("Clear") } }) { pad ->
        if (ps.isEmpty()) Box(Modifier.padding(pad)) { EmptyState(Icons.Outlined.CompareArrows, "Nothing to compare", "Tap the compare icon on up to 5 product pages, or ask the assistant.", "Ask assistant") { nav.go("assistant") } }
        else {
            val specKeys = ps.flatMap { it.specMap().keys }.distinct()
            val best = mutableMapOf<String, Long>()
            best["price"] = ps.minByOrNull { it.price }!!.id
            best["rating"] = ps.maxByOrNull { it.rating }!!.id
            specKeys.forEach { k -> ps.filter { Ranker.specNumber(it, k) > 0 }.maxByOrNull { Ranker.specNumber(it, k) }?.let { best[k] = it.id } }
            val rows: List<Pair<String, (ProductEntity) -> String>> = listOf<Pair<String, (ProductEntity) -> String>>(
                "price" to { p: ProductEntity -> p.price.rupees() }, "mrp" to { p: ProductEntity -> p.mrp.rupees() }, "discount" to { p: ProductEntity -> "${p.discountPercent}%" },
                "rating" to { p: ProductEntity -> "${p.rating}★ (${p.ratingCount})" }, "delivery" to { p: ProductEntity -> "${p.deliveryDays} days" }, "stock" to { p: ProductEntity -> if (p.stock > 0) "In stock" else "Out" },
                "sentiment" to { p: ProductEntity -> reviewMap[p.id]?.sentiment ?: "…" }, "top complaint" to { p: ProductEntity -> reviewMap[p.id]?.complaints?.firstOrNull()?.first ?: "—" }
            ) + specKeys.map { k -> k to { p: ProductEntity -> p.specMap()[k] ?: "—" } }
            Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState()).padding(12.dp)) {
                Row {
                    Spacer(Modifier.width(100.dp))
                    ps.forEach { p ->
                        Column(Modifier.width(150.dp).padding(4.dp).clickable { nav.go("product/${p.id}") }) {
                            ProductArt(p, Modifier.fillMaxWidth().height(90.dp).clip(RoundedCornerShape(12.dp)), iconSize = 40)
                            Text(p.name, maxLines = 2, style = MaterialTheme.typography.labelSmall)
                            TextButton(onClick = { vm.toggleCompare(p.id) }, contentPadding = PaddingValues(0.dp)) { Text("Remove") }
                        }
                    }
                }
                rows.forEach { (label, f) ->
                    Row(Modifier.padding(vertical = 2.dp)) {
                        Text(label.replaceFirstChar { it.uppercase() }, Modifier.width(100.dp).padding(4.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        ps.forEach { p ->
                            val win = best[label] == p.id && ps.size > 1
                            Surface(color = if (win) Mint.copy(alpha = .15f) else Color.Transparent, shape = RoundedCornerShape(6.dp), modifier = Modifier.width(150.dp).padding(horizontal = 4.dp)) {
                                Text(f(p), Modifier.padding(4.dp), style = MaterialTheme.typography.bodySmall, color = if (win) Mint else LocalContentColor.current)
                            }
                        }
                    }
                    HorizontalDivider()
                }
                Text("Green = best in row. Review sentiment from keyword rules over actual reviews.", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

// ------------------------------------------------------------------ Budget

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BudgetScreen(vm: ShopViewModel, nav: NavHostController) {
    val catalogue by vm.catalogue.collectAsState()
    val categories by vm.categories.collectAsState()
    val wish by vm.wishlist.collectAsState()
    var budget by remember { mutableStateOf("5000") }
    var selected by remember { mutableStateOf(setOf<Long>()) }
    var plan by remember { mutableStateOf<List<ProductEntity>?>(null) }
    Screen("Smart budget shopping", nav) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                OutlinedTextField(budget, { budget = it.filter(Char::isDigit).take(7) }, label = { Text("Your budget (₹)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                Text("Pick categories (optional)", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    categories.forEach { c -> FilterChip(c.id in selected, { selected = if (c.id in selected) selected - c.id else selected + c.id }, label = { Text(c.name) }) }
                }
                Button(onClick = { plan = BudgetPlanner.plan(catalogue, budget.toDoubleOrNull() ?: 0.0, selected) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Build my best combo") }
                EngineBadge("Greedy value optimiser · on-device")
            }
            plan?.let { list ->
                val total = list.sumOf { it.price }
                item {
                    Card(shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.padding(14.dp)) {
                            Text("${list.size} items · ${total.rupees()} of ${(budget.toDoubleOrNull() ?: 0.0).rupees()}", style = MaterialTheme.typography.titleMedium)
                            Text("Saves ${list.sumOf { it.mrp - it.price }.rupees()} vs MRP · avg rating ${String.format("%.1f", list.map { it.rating }.average().takeIf { !it.isNaN() } ?: 0.0)}★", color = Mint, style = MaterialTheme.typography.bodySmall)
                            Button(onClick = { list.forEach { p: ProductEntity -> if (p.sizeList().isEmpty()) vm.addToCart(p.id, "", p.colorList().firstOrNull() ?: "") else vm.toast("${p.name}: choose a size on its page") } },
                                enabled = list.isNotEmpty(), modifier = Modifier.padding(top = 8.dp)) { Text("Add all to cart") }
                        }
                    }
                }
                items(list, key = { it.id }) { p: ProductEntity -> ProductListRow(p, p.id in wish, { nav.go("product/${p.id}") }, { vm.toggleWishlist(p.id) }) }
                if (list.isEmpty()) item { EmptyState(Icons.Outlined.Savings, "Budget too low", "Try a higher budget or fewer categories.") }
            }
        }
    }
}

// ------------------------------------------------------------------ Deal radar

@Composable
fun DealRadarScreen(vm: ShopViewModel, nav: NavHostController) {
    val catalogue by vm.catalogue.collectAsState()
    val wish by vm.wishlist.collectAsState()
    var scored by remember { mutableStateOf<List<Pair<ProductEntity, com.meeshox.app.ai.DealInsight>>?>(null) }
    LaunchedEffect(catalogue) {
        if (catalogue.isEmpty()) return@LaunchedEffect
        scored = withContext(Dispatchers.IO) {
            catalogue.filter { it.stock > 0 }.map { p: ProductEntity -> p to PriceIntelligence.analyse(p, vm.dao.priceHistory(p.id), vm.dao.seller(p.sellerId)?.rating ?: 4.0) }
                .filter { it.second.hasHistory && it.second.score >= 60 }.sortedByDescending { it.second.score }.take(40)
        }
    }
    Screen("Live deal radar", nav) { pad ->
        val list = scored
        if (list == null) Box(Modifier.padding(pad)) { SkeletonGrid() }
        else LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Text("Deals ranked by score: current price vs recorded price history, rating and seller rating. Products without enough history are excluded.", style = MaterialTheme.typography.bodySmall)
                EngineBadge("Price-history rules")
            }
            items(list, key = { it.first.id }) { (p, d) ->
                Card(Modifier.clickable { nav.go("product/${p.id}") }, shape = RoundedCornerShape(16.dp)) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        ProductArt(p, Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)), iconSize = 32)
                        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                            Text(p.name, maxLines = 1, style = MaterialTheme.typography.titleSmall)
                            PriceRow(p)
                            Text("Avg ${d.average.rupees()} · low ${d.lowest.rupees()}", style = MaterialTheme.typography.labelSmall)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${d.score}", style = MaterialTheme.typography.headlineSmall, color = Mint)
                            Text(d.verdict, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            if (list.isEmpty()) item { EmptyState(Icons.Outlined.Radar, "No standout deals", "Nothing beats its recorded price history right now.") }
        }
    }
}
