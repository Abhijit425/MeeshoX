package com.meeshox.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavHostController
import com.meeshox.app.ai.DealInsight
import com.meeshox.app.ai.PriceIntelligence
import com.meeshox.app.ai.Recommender
import com.meeshox.app.ai.ReviewAnalyzer
import com.meeshox.app.ai.SizeAdvisor
import com.meeshox.app.data.DemoSeeder
import com.meeshox.app.data.PriceHistoryEntity
import com.meeshox.app.data.ProductEntity
import com.meeshox.app.data.ReviewEntity
import com.meeshox.app.data.SellerEntity
import com.meeshox.app.domain.Serviceability
import com.meeshox.app.domain.rupees
import com.meeshox.app.ui.ShopViewModel
import com.meeshox.app.ui.components.*
import com.meeshox.app.ui.go
import com.meeshox.app.ui.theme.DealRed
import com.meeshox.app.ui.theme.Mint
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun ProductDetailScreen(vm: ShopViewModel, nav: NavHostController, id: Long) {
    val product by vm.dao.productFlow(id).collectAsState(initial = null)
    val reviews by vm.dao.reviews(id).collectAsState(initial = emptyList())
    val catalogue by vm.catalogue.collectAsState()
    val wish by vm.wishlist.collectAsState()
    val uid by vm.uid.collectAsState()
    val compare by vm.compare.collectAsState()
    var seller by remember { mutableStateOf<SellerEntity?>(null) }
    var history by remember { mutableStateOf<List<PriceHistoryEntity>>(emptyList()) }
    var coPurchased by remember { mutableStateOf<List<Long>>(emptyList()) }
    var size by remember { mutableStateOf("") }
    var colour by remember { mutableStateOf("") }
    var pincode by remember { mutableStateOf("") }
    var pinResult by remember { mutableStateOf<String?>(null) }
    var zoom by remember { mutableStateOf(false) }
    var sizeDialog by remember { mutableStateOf(false) }
    var writeReview by remember { mutableStateOf(false) }

    LaunchedEffect(id, uid) {
        vm.repo.recordView(uid, id)
        val p = vm.dao.product(id) ?: return@LaunchedEffect
        seller = vm.dao.seller(p.sellerId)
        history = vm.dao.priceHistory(id)
        colour = p.colorList().firstOrNull() ?: ""
        // real co-purchase signal from this user's orders containing the product
        val orderIds = vm.dao.allOrderItemsForUser(uid).filter { it.productId == id }.map { it.orderId }.toSet()
        coPurchased = vm.dao.allOrderItemsForUser(uid).filter { it.orderId in orderIds && it.productId != id }.map { it.productId }
    }
    val p = product
    if (p == null) { Screen("Product", nav) { Box(Modifier.padding(it).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }; return }
    val insights = remember(reviews) { ReviewAnalyzer.analyse(reviews) }
    val deal = remember(p, history, seller) { PriceIntelligence.analyse(p, history, seller?.rating ?: 4.0) }
    val similar = remember(p, catalogue) { Recommender.similar(p, catalogue) }
    val together = remember(p, catalogue, coPurchased) { Recommender.boughtTogether(p, catalogue, coPurchased) }

    Screen(p.brand, nav, actions = {
        IconButton(onClick = { vm.toggleCompare(p.id) }) { Icon(Icons.Outlined.CompareArrows, "Compare", tint = if (p.id in compare) MaterialTheme.colorScheme.primary else LocalContentColor.current) }
        IconButton(onClick = { vm.toggleWishlist(p.id) }) { Icon(if (p.id in wish) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, "Wishlist", tint = if (p.id in wish) DealRed else LocalContentColor.current) }
        IconButton(onClick = { nav.go("cart") }) { Icon(Icons.Outlined.ShoppingCart, "Cart") }
    }, bottomBar = {
        Surface(shadowElevation = 8.dp) {
            Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { vm.addToCart(p.id, size, colour) }, enabled = p.stock > 0, modifier = Modifier.weight(1f).height(50.dp)) {
                    Icon(Icons.Outlined.AddShoppingCart, null); Spacer(Modifier.width(6.dp)); Text("Add to cart")
                }
                Button(onClick = {
                    if (p.sizeList().isNotEmpty() && size.isBlank()) vm.toast("Please select a size")
                    else nav.go("checkout?buyNow=${p.id}&size=$size&color=$colour")
                }, enabled = p.stock > 0, modifier = Modifier.weight(1f).height(50.dp)) { Text(if (p.stock > 0) "Buy now" else "Out of stock") }
            }
        }
    }) { pad ->
        LazyColumn(Modifier.padding(pad)) {
            item {
                val pager = rememberPagerState { 4 }
                Box {
                    HorizontalPager(pager) { i ->
                        ProductArt(p, Modifier.fillMaxWidth().height(320.dp).clickable { zoom = true }, variant = i, iconSize = 120)
                    }
                    Row(Modifier.align(Alignment.BottomCenter).padding(10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        repeat(4) { i -> Box(Modifier.size(if (pager.currentPage == i) 10.dp else 7.dp).clip(CircleShape).background(if (pager.currentPage == i) MaterialTheme.colorScheme.primary else Color.White)) }
                    }
                    LabelPill("Tap to zoom", modifier = Modifier.align(Alignment.TopEnd).padding(8.dp))
                }
            }
            item {
                Column(Modifier.padding(16.dp)) {
                    Text(p.name, style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RatingChip(p.rating, p.ratingCount)
                        Spacer(Modifier.width(8.dp))
                        if (deal.hasHistory) LabelPill("Deal score ${deal.score}/100 · ${deal.verdict}", if (deal.score >= 60) Mint.copy(alpha = .15f) else MaterialTheme.colorScheme.surfaceVariant, if (deal.score >= 60) Mint else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(10.dp))
                    PriceRow(p, big = true)
                    Text("Inclusive of all taxes", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        when { p.stock == 0 -> "Out of stock"; p.stock <= 5 -> "Hurry, only ${p.stock} left!"; else -> "In stock" },
                        color = if (p.stock in 0..5) DealRed else Mint, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            if (p.colorList().isNotEmpty()) item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Text("Colour: $colour", style = MaterialTheme.typography.titleSmall)
                    Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        p.colorList().forEach { c ->
                            val hex = DemoSeeder.colourPalette[c] ?: p.colorHex
                            Box(Modifier.size(36.dp).clip(CircleShape).background(Color(hex))
                                .border(if (c == colour) 3.dp else 1.dp, if (c == colour) MaterialTheme.colorScheme.primary else Color.LightGray, CircleShape)
                                .clickable { colour = c })
                        }
                    }
                }
            }
            if (p.sizeList().isNotEmpty()) item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Select size", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        TextButton(onClick = { sizeDialog = true }) { Icon(Icons.Outlined.Straighten, null); Text(" Find my size") }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        p.sizeList().forEach { s -> FilterChip(size == s, { size = s }, label = { Text(s) }) }
                    }
                }
            }
            item {
                Card(Modifier.padding(16.dp).fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.LocalShipping, null); Text("  Delivery", style = MaterialTheme.typography.titleSmall) }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(pincode, { if (it.length <= 6) pincode = it.filter(Char::isDigit) }, label = { Text("Pincode") }, singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                            TextButton(onClick = {
                                val r = Serviceability.check(pincode)
                                pinResult = if (!r.serviceable) "✗ Not deliverable to this pincode" else
                                    "✓ Delivery by ${fmtDate(System.currentTimeMillis() + (p.deliveryDays + r.extraDays) * 86_400_000L)} (${r.zone})" + if (r.codAvailable) " · COD available" else ""
                            }) { Text("Check") }
                        }
                        Text(pinResult ?: "Usually delivered in ${p.deliveryDays}-${p.deliveryDays + 2} days", style = MaterialTheme.typography.bodySmall)
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        Text(if (p.returnDays > 0) "↩ ${p.returnDays}-day easy returns & exchange" else "↩ This item is not returnable", style = MaterialTheme.typography.bodySmall)
                        Text("💳 UPI, cards, net banking, wallets (Razorpay) & cash on delivery", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            seller?.let { s ->
                item {
                    ListItem(
                        headlineContent = { Text("Sold by ${s.businessName}") },
                        supportingContent = { Text("${s.city} · Seller rating ${if (s.rating > 0) s.rating else "new"}" + if (s.verified) " · Verified" else " · Verification pending") },
                        leadingContent = { Icon(Icons.Outlined.Storefront, null) },
                        trailingContent = { if (s.verified) Icon(Icons.Filled.Verified, null, tint = Mint) }
                    )
                }
            }
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Text("Product details", style = MaterialTheme.typography.titleMedium)
                    Text(p.description, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 6.dp))
                    p.specMap().forEach { (k, v) ->
                        Row(Modifier.padding(vertical = 2.dp)) {
                            Text(k.replaceFirstChar { it.uppercase() }, Modifier.width(110.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(v)
                        }
                    }
                }
            }
            item { PriceHistoryCard(deal, history, p) }
            if (together.isNotEmpty()) {
                item { SectionHeader("Frequently bought together", if (coPurchased.isNotEmpty()) "From your orders + complementary items" else "Complementary items (rule-based)") }
                item {
                    val bundle = listOf(p) + together
                    Card(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            bundle.forEach { b ->
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp).clickable { if (b.id != p.id) nav.go("product/${b.id}") }) {
                                    ProductArt(b, Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)), iconSize = 22)
                                    Text(b.name, Modifier.weight(1f).padding(horizontal = 8.dp), maxLines = 1, style = MaterialTheme.typography.bodySmall)
                                    Text(b.price.rupees(), style = MaterialTheme.typography.titleSmall)
                                }
                            }
                            Button(onClick = {
                                together.forEach { b -> if (b.sizeList().isEmpty() && b.stock > 0) vm.addToCart(b.id, "", b.colorList().firstOrNull() ?: "") }
                                vm.addToCart(p.id, size, colour)
                            }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Add all · ${bundle.sumOf { it.price }.rupees()}") }
                            Text("Items needing a size are skipped — open them to choose.", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            item { ReviewInsightsCard(insights, onWrite = { writeReview = true }) }
            items(reviews.take(6).size) { i -> ReviewRow(reviews[i]) }
            item { SectionHeader("Similar products") }
            item { ProductRow(similar, wish, { nav.go("product/$it") }, { vm.toggleWishlist(it) }) }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (zoom) Dialog(onDismissRequest = { zoom = false }) {
        var scale by remember { mutableFloatStateOf(1f) }
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp)).pointerInput(Unit) {
            detectTransformGestures { _, _, z, _ -> scale = (scale * z).coerceIn(1f, 4f) }
        }) {
            ProductArt(p, Modifier.fillMaxSize().graphicsLayer(scaleX = scale, scaleY = scale), iconSize = 160)
            Text("Pinch to zoom", Modifier.align(Alignment.BottomCenter).padding(8.dp), style = MaterialTheme.typography.labelSmall)
        }
    }
    if (sizeDialog) SizeDialog(p, insights.fit, onDismiss = { sizeDialog = false }) { size = it; sizeDialog = false }
    if (writeReview) WriteReviewDialog(onDismiss = { writeReview = false }) { stars, text ->
        writeReview = false
        vm.io {
            val bought = vm.dao.allOrderItemsForUser(uid).any { it.productId == p.id }
            vm.dao.insertReview(ReviewEntity(productId = p.id, userName = vm.user.value?.name ?: "You", rating = stars, text = text, verified = bought, createdAt = System.currentTimeMillis()))
            val all = vm.dao.reviewsOnce(p.id)
            vm.dao.updateProduct(p.copy(rating = (all.map { it.rating }.average() * 10).roundToInt() / 10.0, ratingCount = p.ratingCount + 1))
            vm.toast(if (bought) "Review posted (verified purchase)" else "Review posted")
        }
    }
}

@Composable
private fun PriceHistoryCard(deal: DealInsight, history: List<PriceHistoryEntity>, p: ProductEntity) {
    Card(Modifier.padding(16.dp).fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.QueryStats, null); Text("  Price intelligence", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                EngineBadge("Rules + recorded prices")
            }
            if (!deal.hasHistory) Text(deal.verdict + ". " + deal.bestTimeAdvice, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
            else {
                LineChart(history.map { it.price }, Modifier.fillMaxWidth().height(110.dp).padding(vertical = 10.dp))
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Stat("Lowest", deal.lowest.rupees()); Stat("Average", deal.average.rupees()); Stat("Highest", deal.highest.rupees()); Stat("Now", p.price.rupees())
                }
                Text(deal.bestTimeAdvice, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                if (deal.isDemoData) Text("Note: price records in this build are seeded demo data (${history.size} records over 90 days).", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleSmall)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ReviewInsightsCard(i: com.meeshox.app.ai.ReviewInsights, onWrite: () -> Unit) {
    Card(Modifier.padding(16.dp).fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Ratings & reviews", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = onWrite) { Text("Write a review") }
            }
            Text("Actual statistics", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(90.dp)) {
                    Text("${i.average}", style = MaterialTheme.typography.headlineSmall); Icon(Icons.Filled.Star, null, tint = Mint)
                    Text("${i.count} reviews", style = MaterialTheme.typography.labelSmall)
                    Text("${i.verifiedShare}% verified", style = MaterialTheme.typography.labelSmall)
                }
                Column(Modifier.weight(1f)) {
                    (5 downTo 1).forEach { s ->
                        val c = i.distribution[s] ?: 0
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("$s★", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(24.dp))
                            LinearProgressIndicator(progress = { if (i.count == 0) 0f else c.toFloat() / i.count }, modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)),
                                color = if (s >= 4) Mint else if (s == 3) Color(0xFFF59E0B) else DealRed)
                            Text(" $c", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(28.dp))
                        }
                    }
                }
            }
            if (i.fit.isNotEmpty()) Text("Fit feedback: " + i.fit.entries.joinToString(" · ") { "${it.key.lowercase().replace("true", "true to size")} ${it.value}" },
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
            HorizontalDivider(Modifier.padding(vertical = 10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Review summary", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.weight(1f))
                EngineBadge("Keyword rules · generated")
            }
            Text("Overall: ${i.sentiment}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 6.dp))
            Text(i.summary, style = MaterialTheme.typography.bodySmall)
            if (i.positives.isNotEmpty()) Text("👍 " + i.positives.take(3).joinToString(" · ") { "${it.first} (${it.second})" }, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            if (i.complaints.isNotEmpty()) Text("👎 " + i.complaints.take(3).joinToString(" · ") { "${it.first} (${it.second})" }, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ReviewRow(r: ReviewEntity) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RatingChip(r.rating.toDouble())
            Text("  ${r.userName}", style = MaterialTheme.typography.titleSmall)
            if (r.verified) Text("  ✓ Verified purchase", style = MaterialTheme.typography.labelSmall, color = Mint)
        }
        Text(r.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
        Text(fmtDate(r.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider(Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun SizeDialog(p: ProductEntity, fit: Map<String, Int>, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    var measure by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<Pair<String, String>?>(null) }
    val isJeans = p.subType == "jeans"
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Find my size") }, text = {
        Column {
            Text(if (isJeans) "Enter your waist in inches" else "Enter your chest/bust in inches", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(measure, { measure = it.filter { c -> c.isDigit() || c == '.' } }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            result?.let { (s, why) -> Text("Recommended: $s", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)); Text(why, style = MaterialTheme.typography.bodySmall) }
            Spacer(Modifier.height(6.dp)); EngineBadge("Size chart + review fit stats")
        }
    }, confirmButton = {
        if (result == null) TextButton(onClick = { measure.toDoubleOrNull()?.let { result = SizeAdvisor.recommend(p, it, fit, null) } }) { Text("Recommend") }
        else TextButton(onClick = { onPick(result!!.first) }) { Text("Select ${result!!.first}") }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } })
}

@Composable
private fun WriteReviewDialog(onDismiss: () -> Unit, onSubmit: (Int, String) -> Unit) {
    var stars by remember { mutableIntStateOf(5) }
    var text by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Rate this product") }, text = {
        Column {
            Row { (1..5).forEach { s -> IconButton(onClick = { stars = s }) { Icon(Icons.Filled.Star, null, tint = if (s <= stars) Color(0xFFF59E0B) else Color.LightGray) } } }
            OutlinedTextField(text, { if (it.length <= 500) text = it }, label = { Text("Your review") }, minLines = 3)
        }
    }, confirmButton = { TextButton(onClick = { onSubmit(stars, text.trim()) }, enabled = text.trim().length >= 5) { Text("Submit") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}
