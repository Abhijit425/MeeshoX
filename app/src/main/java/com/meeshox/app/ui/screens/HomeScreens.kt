package com.meeshox.app.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.meeshox.app.ai.Intent as AiIntent
import com.meeshox.app.ai.QueryParser
import com.meeshox.app.ai.Ranker
import com.meeshox.app.data.ProductEntity
import com.meeshox.app.domain.rupees
import com.meeshox.app.ui.ShopViewModel
import com.meeshox.app.ui.components.*
import com.meeshox.app.ui.go
import com.meeshox.app.ui.theme.BrandGradient
import com.meeshox.app.ui.theme.DealRed
import kotlinx.coroutines.delay

/** Routes a natural-language / voice command to the right screen. */
fun routeCommand(text: String, nav: NavHostController) {
    val q = QueryParser.parse(text)
    when (q.intent) {
        AiIntent.TRACK_ORDER -> nav.go("orders")
        AiIntent.DEALS -> nav.go("deals")
        AiIntent.GIFT, AiIntent.OUTFIT, AiIntent.BUDGET_COMBO, AiIntent.COMPARE, AiIntent.RETURN_HELP -> nav.go("assistant?mode=" + Uri.encode("q:$text"))
        else -> nav.go("products?q=" + Uri.encode(text))
    }
}

@Composable
fun rememberVoiceLauncher(onText: (String) -> Unit): () -> Unit {
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let(onText)
        }
    }
    return {
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Try: \"red shoes under 1000\"")
        try { launcher.launch(i) } catch (e: Exception) {
            android.widget.Toast.makeText(ctx, "Speech recognition isn't available on this device", android.widget.Toast.LENGTH_LONG).show()
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(vm: ShopViewModel, nav: NavHostController) {
    val catalogue by vm.catalogue.collectAsState()
    val categories by vm.categories.collectAsState()
    val wish by vm.wishlist.collectAsState()
    val recent by vm.recentlyViewed.collectAsState()
    val recommended by vm.recommended.collectAsState()
    val cartCount by vm.cartCount.collectAsState()
    val unread by vm.unread.collectAsState()
    val user by vm.user.collectAsState()
    val prefs by vm.prefs.collectAsState()
    val voice = rememberVoiceLauncher { routeCommand(it, nav) }
    LaunchedEffect(Unit) { vm.refreshRecommendations() }

    val open: (Long) -> Unit = { nav.go("product/$it") }
    val wishFn: (Long) -> Unit = { vm.toggleWishlist(it) }

    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        item {
            Column(Modifier.fillMaxWidth().background(BrandGradient).statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("MEESHO X", color = Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp, letterSpacing = 2.sp)
                        Text("Namaste, ${user?.name?.substringBefore(' ') ?: ""} 👋", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = { nav.go("notifications") }) {
                        BadgedBox(badge = { if (unread > 0) Badge { Text("$unread") } }) { Icon(Icons.Outlined.Notifications, "Notifications", tint = Color.White) }
                    }
                    IconButton(onClick = { nav.go("cart") }) {
                        BadgedBox(badge = { if (cartCount > 0) Badge { Text("$cartCount") } }) { Icon(Icons.Outlined.ShoppingCart, "Cart", tint = Color.White) }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth().clickable { nav.go("search") }) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Search or describe what you want…", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
                        IconButton(onClick = voice) { Icon(Icons.Outlined.Mic, "Voice search", tint = MaterialTheme.colorScheme.primary) }
                        IconButton(onClick = { nav.go("visual") }) { Icon(Icons.Outlined.CameraAlt, "Visual search", tint = MaterialTheme.colorScheme.primary) }
                    }
                }
            }
        }
        if (catalogue.isEmpty()) { item { SkeletonGrid() }; return@LazyColumn }
        item {
            LazyRow(contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(categories, key = { it.id }) { c ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(64.dp).clickable { nav.go("products?cat=${c.id}") }) {
                        Box(Modifier.size(56.dp).clip(CircleShape).background(Color(c.colorHex).copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                            Icon(categoryIcon(c.icon), null, tint = Color(c.colorHex))
                        }
                        Text(c.name, style = MaterialTheme.typography.labelSmall, maxLines = 1, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
        item {
            val banners = listOf(
                Triple("AI Shopping World", "Describe it. We'll find it.", "assistant"),
                Triple("Smart Budget Shopping", "Best combo for your budget", "budget"),
                Triple("Live Deal Radar", "Deals backed by price history", "deals"),
                Triple("Shop from a screenshot", "Match colours from any photo", "visual")
            )
            val pager = rememberPagerState { banners.size }
            LaunchedEffect(pager) { while (true) { delay(4000); pager.animateScrollToPage((pager.currentPage + 1) % banners.size) } }
            HorizontalPager(pager, contentPadding = PaddingValues(horizontal = 16.dp), pageSpacing = 10.dp) { i ->
                val (t, s, r) = banners[i]
                val colors = listOf(listOf(Color(0xFF4C1D95), Color(0xFFDB2777)), listOf(Color(0xFF065F46), Color(0xFF0EA5E9)), listOf(Color(0xFF9A3412), Color(0xFFF59E0B)), listOf(Color(0xFF1E3A8A), Color(0xFF7C3AED)))[i]
                Box(Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(20.dp)).background(Brush.horizontalGradient(colors)).clickable { nav.go(r) }.padding(20.dp)) {
                    Column(Modifier.align(Alignment.CenterStart)) {
                        Text(t, color = Color.White, style = MaterialTheme.typography.titleLarge)
                        Text(s, color = Color.White.copy(alpha = 0.9f))
                        Spacer(Modifier.height(10.dp))
                        Surface(color = Color.White, shape = RoundedCornerShape(50)) { Text("Try now →", modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = Color.Black) }
                    }
                    Icon(Icons.Filled.AutoAwesome, null, tint = Color.White.copy(alpha = 0.25f), modifier = Modifier.size(96.dp).align(Alignment.CenterEnd))
                }
            }
        }
        item {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuickAction(Icons.Outlined.AutoAwesome, "Ask AI", Modifier.weight(1f)) { nav.go("assistant") }
                QuickAction(Icons.Outlined.Redeem, "Gift finder", Modifier.weight(1f)) { nav.go("assistant?mode=gift") }
                QuickAction(Icons.Outlined.Checkroom, "Stylist", Modifier.weight(1f)) { nav.go("assistant?mode=stylist") }
                QuickAction(Icons.Outlined.Replay, "Reorder", Modifier.weight(1f)) { nav.go("reorder") }
            }
        }
        val flash = catalogue.filter { it.discountPercent >= 40 && it.stock > 0 }.sortedByDescending { it.discountPercent }.take(10)
        if (flash.isNotEmpty()) {
            item { FlashHeader { nav.go("products?deals=true") } }
            item { ProductRow(flash, wish, open, wishFn) }
        }
        if (recent.isNotEmpty()) {
            val rv = recent.mapNotNull { id -> catalogue.firstOrNull { it.id == id } }
            item { SectionHeader("Recently viewed") }
            item { ProductRow(rv, wish, open, wishFn) }
        }
        item { SectionHeader("Trending now", "Most popular this week", "See all") { nav.go("products?q=") } }
        item { ProductRow(catalogue.sortedByDescending { it.popularity }.take(10), wish, open, wishFn) }
        item { SectionHeader("Best sellers", "Most rated by buyers") }
        item { ProductRow(catalogue.sortedByDescending { it.ratingCount }.take(10), wish, open, wishFn) }
        if (wish.isNotEmpty()) {
            item { SectionHeader("From your wishlist", action = "Open") { nav.go("wishlist") } }
            item { ProductRow(wish.mapNotNull { id -> catalogue.firstOrNull { it.id == id } }.take(10), wish, open, wishFn) }
        }
        item {
            SectionHeader(
                if (prefs?.personalization != false) "Recommended for you" else "Popular picks",
                if (prefs?.personalization != false) "Based on your views, wishlist, searches & orders" else "Personalisation is off"
            )
        }
        val gridItems = recommended.chunked(2)
        items(gridItems) { pair ->
            Row(Modifier.padding(horizontal = 16.dp, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { p -> ProductCard(p, p.id in wish, { open(p.id) }, { wishFn(p.id) }, Modifier.weight(1f)) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun FlashHeader(onAll: () -> Unit) {
    var left by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            val now = System.currentTimeMillis()
            val endOfDay = (now / 86_400_000 + 1) * 86_400_000 - 19_800_000 // midnight IST
            left = ((if (endOfDay > now) endOfDay else endOfDay + 86_400_000) - now) / 1000
            delay(1000)
        }
    }
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Bolt, null, tint = DealRed)
        Text("Flash deals", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 4.dp))
        Spacer(Modifier.width(8.dp))
        LabelPill(String.format("Ends in %02d:%02d:%02d", left / 3600, (left / 60) % 60, left % 60), DealRed.copy(alpha = 0.12f), DealRed)
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onAll) { Text("See all") }
    }
}

@Composable
private fun QuickAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Surface(modifier.clickable(onClick = onClick), shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer) {
        Column(Modifier.padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

@Composable
fun CategoriesScreen(vm: ShopViewModel, nav: NavHostController) {
    val categories by vm.categories.collectAsState()
    val catalogue by vm.catalogue.collectAsState()
    Screen("Categories", nav, back = false) { pad ->
        LazyVerticalGrid(GridCells.Fixed(3), Modifier.padding(pad), contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(categories, key = { it.id }) { c ->
                val count = catalogue.count { it.categoryId == c.id }
                Card(Modifier.clickable { nav.go("products?cat=${c.id}") }, shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color(c.colorHex).copy(alpha = 0.18f), Color.Transparent))).padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(categoryIcon(c.icon), null, tint = Color(c.colorHex), modifier = Modifier.size(36.dp))
                        Text(c.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                        Text("$count items", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun SearchScreen(vm: ShopViewModel, nav: NavHostController) {
    var text by remember { mutableStateOf("") }
    val uid by vm.uid.collectAsState()
    var history by remember { mutableStateOf(listOf<String>()) }
    val catalogue by vm.catalogue.collectAsState()
    val focus = remember { FocusRequester() }
    LaunchedEffect(uid) { history = vm.dao.searchHistory(uid).map { it.query }.distinct(); focus.requestFocus() }
    val voice = rememberVoiceLauncher { text = it; vm.io { vm.repo.recordSearch(uid, it) }; routeCommand(it, nav) }
    fun submit(q: String) { if (q.isBlank()) return; vm.io { vm.repo.recordSearch(uid, q) }; routeCommand(q, nav) }
    val suggestions = remember(text, catalogue) {
        if (text.length < 2) emptyList() else catalogue.filter { it.name.contains(text, true) || it.tags.contains(text.lowercase()) }.take(6)
    }
    Screen("Search", nav) { pad ->
        Column(Modifier.padding(pad).padding(16.dp)) {
            OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth().focusRequester(focus), placeholder = { Text("e.g. phone under 20k with best camera") },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                trailingIcon = { IconButton(onClick = voice) { Icon(Icons.Outlined.Mic, "Voice") } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { submit(text) }), singleLine = true)
            suggestions.forEach { p ->
                ListItem(headlineContent = { Text(p.name, maxLines = 1) }, supportingContent = { Text(p.price.rupees()) },
                    leadingContent = { Icon(Icons.Outlined.NorthWest, null) }, modifier = Modifier.clickable { nav.go("product/${p.id}") })
            }
            if (text.isBlank()) {
                Text("Try natural language", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
                listOf("Find red shoes under ₹1,000", "Smartphone under 20000 with best camera and battery", "Show today's best deals", "Track my last order", "Find a birthday gift for my mother")
                    .forEach { s -> SuggestionChip(onClick = { text = s; submit(s) }, label = { Text(s) }, modifier = Modifier.padding(vertical = 2.dp)) }
                if (history.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 16.dp)) {
                        Text("Recent searches", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        TextButton(onClick = { vm.io { vm.dao.clearSearch(uid) }; history = emptyList() }) { Text("Clear") }
                    }
                    history.take(8).forEach { h -> ListItem(headlineContent = { Text(h) }, leadingContent = { Icon(Icons.Outlined.History, null) }, modifier = Modifier.clickable { text = h; submit(h) }) }
                }
            }
        }
    }
}

enum class SortBy(val label: String) { RELEVANCE("Relevance"), POPULAR("Popularity"), PRICE_LOW("Price: low to high"), PRICE_HIGH("Price: high to low"), RATING("Rating"), DISCOUNT("Discount"), NEWEST("Newest") }

data class Filters(
    val minPrice: Float = 0f, val maxPrice: Float = 100000f, val minRating: Float = 0f, val minDiscount: Int = 0,
    val brands: Set<String> = emptySet(), val inStockOnly: Boolean = false, val maxDeliveryDays: Int = 10
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProductListScreen(vm: ShopViewModel, nav: NavHostController, catId: Long, query: String, dealsOnly: Boolean) {
    val catalogue by vm.catalogue.collectAsState()
    val categories by vm.categories.collectAsState()
    val wish by vm.wishlist.collectAsState()
    val prefs by vm.prefs.collectAsState()
    var sort by remember { mutableStateOf(if (query.isBlank()) SortBy.POPULAR else SortBy.RELEVANCE) }
    var filters by remember { mutableStateOf(Filters(minDiscount = if (dealsOnly) 40 else 0)) }
    var showFilters by remember { mutableStateOf(false) }
    var showSort by remember { mutableStateOf(false) }
    var pageSize by remember { mutableIntStateOf(20) }
    val grid = prefs?.gridLayout ?: true

    val base = remember(catalogue, catId, query) {
        when {
            query.isNotBlank() -> Ranker.search(catalogue, QueryParser.parse(query), 200)
            catId > 0 -> catalogue.filter { it.categoryId == catId }
            else -> catalogue
        }
    }
    val brands = remember(base) { base.map { it.brand }.distinct().sorted() }
    val list = remember(base, sort, filters) {
        base.filter { p ->
            p.price >= filters.minPrice && p.price <= filters.maxPrice && p.rating >= filters.minRating && p.discountPercent >= filters.minDiscount &&
                (filters.brands.isEmpty() || p.brand in filters.brands) && (!filters.inStockOnly || p.stock > 0) && p.deliveryDays <= filters.maxDeliveryDays
        }.let { l ->
            when (sort) {
                SortBy.RELEVANCE -> l; SortBy.POPULAR -> l.sortedByDescending { it.popularity }; SortBy.PRICE_LOW -> l.sortedBy { it.price }
                SortBy.PRICE_HIGH -> l.sortedByDescending { it.price }; SortBy.RATING -> l.sortedByDescending { it.rating }
                SortBy.DISCOUNT -> l.sortedByDescending { it.discountPercent }; SortBy.NEWEST -> l.sortedByDescending { it.createdAt }
            }
        }
    }
    val title = when { dealsOnly -> "Flash deals"; query.isNotBlank() -> "\"$query\""; catId > 0 -> categories.firstOrNull { it.id == catId }?.name ?: "Products"; else -> "All products" }
    Screen(title, nav, actions = {
        IconButton(onClick = { vm.io { vm.repo.prefs.setGrid(!grid) } }) { Icon(if (grid) Icons.Outlined.ViewList else Icons.Outlined.GridView, "Layout") }
        IconButton(onClick = { nav.go("cart") }) { Icon(Icons.Outlined.ShoppingCart, "Cart") }
    }) { pad ->
        Column(Modifier.padding(pad)) {
            Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                AssistChip(onClick = { showSort = true }, label = { Text(sort.label) }, leadingIcon = { Icon(Icons.Outlined.Sort, null) })
                AssistChip(onClick = { showFilters = true }, label = { Text("Filters" + if (filters != Filters()) " •" else "") }, leadingIcon = { Icon(Icons.Outlined.FilterList, null) })
                Spacer(Modifier.weight(1f))
                Text("${list.size} items", style = MaterialTheme.typography.labelSmall)
            }
            if (catalogue.isEmpty()) SkeletonGrid()
            else if (list.isEmpty()) EmptyState(Icons.Outlined.SearchOff, "No products found", "Try removing some filters or searching differently.", "Reset filters") { filters = Filters() }
            else LazyVerticalGrid(
                GridCells.Fixed(if (grid) 2 else 1), contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val shown = list.take(pageSize)
                items(shown, key = { it.id }) { p ->
                    if (grid) ProductCard(p, p.id in wish, { nav.go("product/${p.id}") }, { vm.toggleWishlist(p.id) })
                    else ProductListRow(p, p.id in wish, { nav.go("product/${p.id}") }, { vm.toggleWishlist(p.id) })
                }
                if (list.size > pageSize) item(span = { GridItemSpan(maxLineSpan) }) {
                    // infinite scroll: next page loads when this footer becomes visible
                    LaunchedEffect(pageSize) { delay(250); pageSize += 20 }
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp)) }
                }
            }
        }
    }
    if (showSort) ModalBottomSheet(onDismissRequest = { showSort = false }) {
        Text("Sort by", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
        SortBy.entries.forEach { s ->
            ListItem(headlineContent = { Text(s.label) }, leadingContent = { RadioButton(sort == s, { sort = s; showSort = false }) }, modifier = Modifier.clickable { sort = s; showSort = false })
        }
        Spacer(Modifier.height(24.dp))
    }
    if (showFilters) ModalBottomSheet(onDismissRequest = { showFilters = false }) {
        var f by remember { mutableStateOf(filters) }
        val maxBase = (base.maxOfOrNull { it.price } ?: 1000.0).toFloat().coerceAtLeast(100f)
        Column(Modifier.padding(horizontal = 16.dp).navigationBarsPadding()) {
            Text("Filters", style = MaterialTheme.typography.titleMedium)
            Text("Price: ${f.minPrice.toDouble().rupees()} – ${minOf(f.maxPrice, maxBase).toDouble().rupees()}", modifier = Modifier.padding(top = 12.dp))
            RangeSlider(value = f.minPrice..minOf(f.maxPrice, maxBase), onValueChange = { f = f.copy(minPrice = it.start, maxPrice = it.endInclusive) }, valueRange = 0f..maxBase)
            Text("Customer rating", modifier = Modifier.padding(top = 8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0f, 3f, 3.5f, 4f, 4.5f).forEach { r -> FilterChip(f.minRating == r, { f = f.copy(minRating = r) }, label = { Text(if (r == 0f) "Any" else "$r★ & up") }) }
            }
            Text("Discount", modifier = Modifier.padding(top = 8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0, 10, 30, 50).forEach { d -> FilterChip(f.minDiscount == d, { f = f.copy(minDiscount = d) }, label = { Text(if (d == 0) "Any" else "$d%+") }) }
            }
            Text("Delivery", modifier = Modifier.padding(top = 8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(10 to "Any", 3 to "Within 3 days", 5 to "Within 5 days").forEach { (d, l) -> FilterChip(f.maxDeliveryDays == d, { f = f.copy(maxDeliveryDays = d) }, label = { Text(l) }) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(f.inStockOnly, { f = f.copy(inStockOnly = it) }); Text("In stock only") }
            if (brands.isNotEmpty()) {
                Text("Brand")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    brands.forEach { b -> FilterChip(b in f.brands, { f = f.copy(brands = if (b in f.brands) f.brands - b else f.brands + b) }, label = { Text(b) }) }
                }
            }
            Row(Modifier.padding(vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { f = Filters() }, modifier = Modifier.weight(1f)) { Text("Clear") }
                Button(onClick = { filters = f; showFilters = false; pageSize = 20 }, modifier = Modifier.weight(1f)) { Text("Apply") }
            }
        }
    }
}

@Composable
fun WishlistScreen(vm: ShopViewModel, nav: NavHostController) {
    val wish by vm.wishlist.collectAsState()
    val catalogue by vm.catalogue.collectAsState()
    val wished = wish.mapNotNull { id -> catalogue.firstOrNull { it.id == id } }
    Screen("Wishlist (${wished.size})", nav, back = false, actions = { IconButton(onClick = { nav.go("lists") }) { Icon(Icons.Outlined.Groups, "Shared lists") } }) { pad ->
        if (wished.isEmpty()) Box(Modifier.padding(pad)) {
            EmptyState(Icons.Outlined.FavoriteBorder, "Your wishlist is empty", "Tap the heart on any product to save it here.", "Start shopping") { nav.go("home") }
        } else LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(wished, key = { it.id }) { p ->
                Card(shape = RoundedCornerShape(16.dp)) {
                    Row(Modifier.clickable { nav.go("product/${p.id}") }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        ProductArt(p, Modifier.size(84.dp).clip(RoundedCornerShape(12.dp)), iconSize = 36)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(p.name, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                            PriceRow(p)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = {
                                    if (p.sizeList().isNotEmpty()) nav.go("product/${p.id}") else vm.addToCart(p.id, "", p.colorList().firstOrNull() ?: "")
                                }, enabled = p.stock > 0) { Text(if (p.stock > 0) "Move to cart" else "Out of stock") }
                                TextButton(onClick = { vm.toggleWishlist(p.id) }) { Text("Remove") }
                            }
                        }
                    }
                }
            }
        }
    }
}
