package com.meeshox.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meeshox.app.data.ProductEntity
import com.meeshox.app.domain.rupees
import com.meeshox.app.ui.theme.DealRed
import com.meeshox.app.ui.theme.Mint

fun categoryIcon(name: String): ImageVector = when (name) {
    "checkroom" -> Icons.Outlined.Checkroom
    "headphones" -> Icons.Outlined.Headphones
    "smartphone" -> Icons.Outlined.Smartphone
    "kitchen" -> Icons.Outlined.Kitchen
    "chair" -> Icons.Outlined.Chair
    "face" -> Icons.Outlined.Face
    "local_grocery_store" -> Icons.Outlined.LocalGroceryStore
    "ice_skating" -> Icons.Outlined.IceSkating
    "diamond" -> Icons.Outlined.Diamond
    "child_care" -> Icons.Outlined.ChildCare
    "sports_cricket" -> Icons.Outlined.SportsCricket
    "menu_book" -> Icons.Outlined.MenuBook
    else -> Icons.Outlined.DirectionsCar
}

fun subTypeIcon(subType: String, categoryId: Long): ImageVector = when (subType) {
    "smartphone" -> Icons.Outlined.Smartphone
    "earbuds" -> Icons.Outlined.Headphones
    "smartwatch", "watch-accessory" -> Icons.Outlined.Watch
    "laptop" -> Icons.Outlined.Laptop
    "speaker" -> Icons.Outlined.Speaker
    "sneakers", "ethnic-footwear", "heels" -> Icons.Outlined.IceSkating
    "jewellery" -> Icons.Outlined.Diamond
    "book" -> Icons.Outlined.MenuBook
    "fan" -> Icons.Outlined.Air
    "mixer", "air-fryer" -> Icons.Outlined.Blender
    "decor" -> Icons.Outlined.Lightbulb
    "skincare", "makeup" -> Icons.Outlined.Spa
    "grocery" -> Icons.Outlined.ShoppingBasket
    "baby" -> Icons.Outlined.ChildCare
    "sports" -> Icons.Outlined.SportsCricket
    "auto" -> Icons.Outlined.DirectionsCar
    else -> if (categoryId == 1L) Icons.Outlined.Checkroom else Icons.Outlined.ShoppingBag
}

/** Original product artwork: colour-true gradient tile with the product's type glyph (no copyrighted imagery). */
@Composable
fun ProductArt(p: ProductEntity, modifier: Modifier = Modifier, variant: Int = 0, iconSize: Int = 56) {
    val base = Color(p.colorHex)
    val light = base.copy(alpha = 0.18f)
    val angle = variant % 3
    Box(
        modifier.background(
            Brush.linearGradient(
                when (angle) { 0 -> listOf(light, base.copy(alpha = 0.55f)); 1 -> listOf(base.copy(alpha = 0.4f), light); else -> listOf(light, base.copy(alpha = 0.3f), light) }
            )
        ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Color.White.copy(alpha = 0.25f), radius = size.minDimension * (0.32f + variant * 0.04f), center = Offset(size.width * 0.5f, size.height * 0.52f))
        }
        Icon(subTypeIcon(p.subType, p.categoryId), null, tint = if (p.colorHex == 0xFFF9FAFB) Color(0xFF6B7280) else base, modifier = Modifier.size(iconSize.dp))
    }
}

@Composable
fun PriceRow(p: ProductEntity, big: Boolean = false) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(p.price.rupees(), style = if (big) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        if (p.discountPercent > 0) {
            Text(p.mrp.rupees(), style = MaterialTheme.typography.bodySmall, textDecoration = TextDecoration.LineThrough, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${p.discountPercent}% off", style = MaterialTheme.typography.bodySmall, color = Mint, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun RatingChip(rating: Double, count: Int? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(color = if (rating >= 4) Mint else if (rating >= 3) Color(0xFFF59E0B) else DealRed, shape = RoundedCornerShape(6.dp)) {
            Row(Modifier.padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(String.format("%.1f", rating), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Icon(Icons.Filled.Star, null, tint = Color.White, modifier = Modifier.size(11.dp))
            }
        }
        if (count != null) Text("  ($count)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ProductCard(p: ProductEntity, wished: Boolean, onClick: () -> Unit, onWish: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Box {
            ProductArt(p, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)))
            if (p.discountPercent >= 40) Surface(color = DealRed, shape = RoundedCornerShape(bottomEnd = 10.dp), modifier = Modifier.align(Alignment.TopStart)) {
                Text("DEAL", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
            }
            IconButton(onClick = onWish, modifier = Modifier.align(Alignment.TopEnd)) {
                Icon(if (wished) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, "Wishlist", tint = if (wished) DealRed else Color(0xFF6B7280))
            }
            if (p.stock == 0) Surface(color = Color.Black.copy(alpha = 0.6f), modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                Text("Out of stock", color = Color.White, fontSize = 11.sp, modifier = Modifier.padding(4.dp), maxLines = 1)
            }
        }
        Column(Modifier.padding(10.dp)) {
            Text(p.name, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.height(32.dp))
            Spacer(Modifier.height(4.dp))
            PriceRow(p)
            Spacer(Modifier.height(4.dp))
            RatingChip(p.rating, p.ratingCount)
            if (p.stock in 1..5) Text("Only ${p.stock} left", color = DealRed, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun ProductListRow(p: ProductEntity, wished: Boolean, onClick: () -> Unit, onWish: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), shape = MaterialTheme.shapes.medium) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            ProductArt(p, Modifier.size(96.dp).clip(RoundedCornerShape(12.dp)), iconSize = 40)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(p.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp)); PriceRow(p); Spacer(Modifier.height(4.dp)); RatingChip(p.rating, p.ratingCount)
                Text("Delivery in ${p.deliveryDays} days", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onWish) { Icon(if (wished) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, null, tint = if (wished) DealRed else Color.Gray) }
        }
    }
}

@Composable
fun SectionHeader(title: String, subtitle: String? = null, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 18.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (action != null && onAction != null) TextButton(onClick = onAction) { Text(action) }
    }
}

@Composable
fun ProductRow(products: List<ProductEntity>, wishlist: List<Long>, onOpen: (Long) -> Unit, onWish: (Long) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(products, key = { it.id }) { p ->
            ProductCard(p, p.id in wishlist, { onOpen(p.id) }, { onWish(p.id) }, Modifier.width(150.dp))
        }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, message: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(88.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(44.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        if (action != null && onAction != null) Button(onClick = onAction, modifier = Modifier.padding(top = 16.dp)) { Text(action) }
    }
}

@Composable
fun SkeletonBox(modifier: Modifier) {
    val t = rememberInfiniteTransition(label = "sk")
    val a by t.animateFloat(0.35f, 0.8f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "a")
    Box(modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).alpha(a))
}

@Composable
fun SkeletonGrid() {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(3) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SkeletonBox(Modifier.weight(1f).height(200.dp)); SkeletonBox(Modifier.weight(1f).height(200.dp))
            }
        }
    }
}

@Composable
fun LabelPill(text: String, color: Color = MaterialTheme.colorScheme.primaryContainer, textColor: Color = MaterialTheme.colorScheme.onPrimaryContainer, modifier: Modifier = Modifier) {
    Surface(color = color, shape = RoundedCornerShape(50), modifier = modifier) {
        Text(text, color = textColor, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
    }
}

/** Honest provenance label for AI/rule outputs. */
@Composable
fun EngineBadge(text: String = "On-device rules · not generative AI") {
    LabelPill("⚙ $text", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun LineChart(values: List<Double>, modifier: Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    if (values.size < 2) return
    val min = values.min(); val max = values.max(); val range = (max - min).takeIf { it > 0 } ?: 1.0
    Canvas(modifier) {
        val stepX = size.width / (values.size - 1)
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = i * stepX; val y = size.height - ((v - min) / range * size.height * 0.85f + size.height * 0.075f).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            drawCircle(color, 4.dp.toPx(), Offset(x, y))
        }
        drawPath(path, color, style = Stroke(width = 2.5.dp.toPx()))
    }
}

@Composable
fun BarChart(values: List<Int>, labels: List<String>, modifier: Modifier, color: Color = MaterialTheme.colorScheme.secondary) {
    val max = (values.maxOrNull() ?: 0).coerceAtLeast(1)
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
        values.forEachIndexed { i, v ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$v", style = MaterialTheme.typography.labelSmall)
                Box(Modifier.fillMaxWidth().height((100f * v / max).coerceAtLeast(2f).dp).clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)).background(color))
                Text(labels.getOrElse(i) { "" }, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
        }
    }
}
