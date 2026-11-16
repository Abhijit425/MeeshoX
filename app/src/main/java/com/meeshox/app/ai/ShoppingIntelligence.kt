package com.meeshox.app.ai

import com.meeshox.app.data.CategoryEntity
import com.meeshox.app.data.OrderEntity
import com.meeshox.app.data.OrderItemEntity
import com.meeshox.app.data.PriceHistoryEntity
import com.meeshox.app.data.ProductEntity
import com.meeshox.app.data.ReviewEntity
import com.meeshox.app.domain.rupees
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * MEESHO X Shopping Intelligence — ON-DEVICE RULE ENGINE.
 *
 * Everything here runs offline with deterministic rules, keyword parsing and simple statistics.
 * It is NOT a large-language-model inference and the UI labels it as "On-device rules".
 * A cloud AI provider can be plugged in later behind [AiProvider] without changing callers.
 */
interface AiProvider {
    val label: String
    val isGenerative: Boolean
}
object RuleEngineProvider : AiProvider {
    override val label = "On-device rules (offline)"
    override val isGenerative = false
}

// ============================ Query parsing ============================

data class ParsedQuery(
    val raw: String,
    val keywords: List<String>,
    val maxPrice: Double?,
    val minPrice: Double?,
    val categoryIds: Set<Long>,
    val colours: Set<String>,
    val occasion: String?,
    val recipient: String?,
    val gender: String?,
    val priorities: List<String>, // camera, battery, display, ram, storage, rating, price
    val intent: Intent
)

enum class Intent { SEARCH, TRACK_ORDER, DEALS, GIFT, OUTFIT, BUDGET_COMBO, COMPARE, RETURN_HELP, GREETING }

object QueryParser {
    private val stop = setOf("a", "an", "the", "for", "me", "my", "find", "show", "with", "and", "under", "below", "best", "good", "i", "want",
        "need", "of", "in", "to", "some", "please", "buy", "suggest", "less", "than", "within", "around", "rs", "inr", "upto", "up", "is", "on", "what", "which")

    val categoryWords: Map<String, Long> = mapOf(
        "fashion" to 1, "kurta" to 1, "saree" to 1, "sari" to 1, "dress" to 1, "shirt" to 1, "tshirt" to 1, "t-shirt" to 1, "jeans" to 1, "sherwani" to 1, "outfit" to 1, "clothes" to 1, "clothing" to 1, "ethnic" to 1,
        "electronics" to 2, "earbuds" to 2, "headphones" to 2, "watch" to 2, "smartwatch" to 2, "laptop" to 2, "speaker" to 2, "gadget" to 2,
        "phone" to 3, "mobile" to 3, "smartphone" to 3, "5g" to 3,
        "mixer" to 4, "fryer" to 4, "fan" to 4, "appliance" to 4, "kitchen" to 4,
        "decor" to 5, "vase" to 5, "lights" to 5, "cushion" to 5, "diya" to 5, "wall" to 5,
        "beauty" to 6, "serum" to 6, "lipstick" to 6, "sunscreen" to 6, "makeup" to 6, "skincare" to 6, "kajal" to 6,
        "grocery" to 7, "rice" to 7, "dal" to 7, "oil" to 7, "tea" to 7, "atta" to 7, "detergent" to 7,
        "shoes" to 8, "shoe" to 8, "sneakers" to 8, "footwear" to 8, "heels" to 8, "sandals" to 8, "juttis" to 8, "mojaris" to 8,
        "jewellery" to 9, "jewelry" to 9, "necklace" to 9, "earrings" to 9, "jhumkas" to 9, "bangles" to 9, "pendant" to 9,
        "baby" to 10, "kids" to 10, "toy" to 10, "teddy" to 10, "stroller" to 10, "diaper" to 10,
        "sports" to 11, "cricket" to 11, "yoga" to 11, "dumbbells" to 11, "football" to 11, "badminton" to 11, "fitness" to 11,
        "book" to 12, "books" to 12, "novel" to 12,
        "car" to 13, "bike" to 13, "helmet" to 13, "automotive" to 13
    )
    val colourWords = listOf("red", "maroon", "pink", "orange", "yellow", "green", "olive", "teal", "blue", "navy", "purple", "black", "white", "grey", "gray", "beige", "brown", "gold", "silver")
    private val occasions = listOf("wedding", "party", "festive", "diwali", "office", "casual", "birthday", "anniversary", "date", "gym", "college", "housewarming")
    private val recipients = mapOf(
        "mother" to "mother", "mom" to "mother", "maa" to "mother", "father" to "father", "dad" to "father", "papa" to "father",
        "wife" to "wife", "husband" to "husband", "sister" to "sister", "brother" to "brother", "friend" to "friend",
        "girlfriend" to "partner", "boyfriend" to "partner", "baby" to "baby", "kid" to "kid", "child" to "kid", "son" to "kid", "daughter" to "kid",
        "boss" to "colleague", "colleague" to "colleague", "teacher" to "colleague", "grandmother" to "mother", "grandfather" to "father"
    )

    fun parseAmount(s: String): Double? {
        val m = Regex("(\\d+(?:[.,]\\d+)*)\\s*(k|thousand|lakh|l)?\\b").find(s.lowercase()) ?: return null
        var v = m.groupValues[1].replace(",", "").toDoubleOrNull() ?: return null
        when (m.groupValues[2]) { "k", "thousand" -> v *= 1000; "lakh", "l" -> v *= 100000 }
        return v
    }

    fun parse(input: String): ParsedQuery {
        val t = input.lowercase().replace("₹", " ₹").replace("rs.", "rs ")
        var maxP: Double? = null; var minP: Double? = null
        Regex("(under|below|less than|within|upto|up to|max|budget(?: of)?|for)\\s*(?:rs|₹|inr)?\\s*(\\d[\\d,]*(?:\\.\\d+)?(?:\\s*(?:k|thousand|lakh)\\b)?)").find(t)?.let { maxP = parseAmount(it.groupValues[2]) }
        Regex("(above|over|more than|min)\\s*(?:rs|₹|inr)?\\s*(\\d[\\d,]*(?:\\s*(?:k|thousand)\\b)?)").find(t)?.let { minP = parseAmount(it.groupValues[2]) }
        Regex("between\\s*(?:rs|₹)?\\s*(\\d[\\d,]*(?:k\\b)?)\\s*(?:and|-|to)\\s*(?:rs|₹)?\\s*(\\d[\\d,]*(?:k\\b)?)").find(t)?.let {
            minP = parseAmount(it.groupValues[1]); maxP = parseAmount(it.groupValues[2])
        }
        if (maxP == null) Regex("₹\\s*(\\d[\\d,]*(?:k\\b)?)").find(t)?.let { maxP = parseAmount(it.groupValues[1]) }
        if (maxP != null && maxP!! < 50) maxP = null // e.g. "5g" misread

        val words = t.split(Regex("[^a-z0-9\\-]+")).filter { it.isNotBlank() }
        val cats = words.mapNotNull { categoryWords[it] }.toMutableSet()
        val colours = words.filter { it in colourWords }.map { if (it == "gray") "grey" else it }.toSet()
        val occasion = occasions.firstOrNull { t.contains(it) }
        val recipient = recipients.entries.firstOrNull { Regex("\\b${it.key}\\b").containsMatchIn(t) }?.value
        val gender = when {
            Regex("\\b(women|woman|ladies|girl|female|her)\\b").containsMatchIn(t) || recipient in listOf("mother", "wife", "sister") -> "women"
            Regex("\\b(men|man|gents|boy|male|him)\\b").containsMatchIn(t) || recipient in listOf("father", "husband", "brother") -> "men"
            else -> null
        }
        val priorities = buildList {
            if (t.contains("camera") || t.contains("photo")) add("camera")
            if (t.contains("battery") || t.contains("backup")) add("battery")
            if (t.contains("display") || t.contains("screen") || t.contains("amoled")) add("display")
            if (t.contains("ram") || t.contains("gaming") || t.contains("performance") || t.contains("fast")) add("ram")
            if (t.contains("storage") || t.contains("memory")) add("storage")
            if (t.contains("rated") || t.contains("rating") || t.contains("quality")) add("rating")
            if (t.contains("cheap") || t.contains("cheapest") || t.contains("lowest")) add("price")
        }
        val intent = when {
            Regex("\\b(track|where is|status of)\\b.*\\border").containsMatchIn(t) || t.contains("track my") -> Intent.TRACK_ORDER
            Regex("\\b(return|refund|exchange)\\b").containsMatchIn(t) -> Intent.RETURN_HELP
            t.contains("compare") || t.contains(" vs ") -> Intent.COMPARE
            Regex("\\b(deal|deals|offer|offers|discount|sale)\\b").containsMatchIn(t) && cats.isEmpty() -> Intent.DEALS
            t.contains("gift") || t.contains("present") -> Intent.GIFT
            t.contains("outfit") || t.contains("wear") || t.contains("look") || t.contains("stylist") || (occasion == "wedding" && cats.isEmpty()) -> Intent.OUTFIT
            Regex("\\b(combo|combination|budget shopping|spend|basket)\\b").containsMatchIn(t) -> Intent.BUDGET_COMBO
            Regex("^(hi|hello|hey|namaste)\\b").containsMatchIn(t.trim()) && words.size <= 3 -> Intent.GREETING
            else -> Intent.SEARCH
        }
        if (intent == Intent.OUTFIT && cats.isEmpty()) cats.add(1)
        val keywords = words.filter { it !in stop && it.toDoubleOrNull() == null && it.length > 1 }
        return ParsedQuery(input, keywords, maxP, minP, cats, colours, occasion, recipient, gender, priorities, intent)
    }
}

// ============================ Ranking / search ============================

object Ranker {
    fun genderOk(p: ProductEntity, gender: String?): Boolean {
        if (gender == null) return true
        val t = p.tags.split(",").map { it.trim() }
        return ("men" !in t && "women" !in t) || gender in t
    }

    fun specNumber(p: ProductEntity, key: String): Double =
        p.specMap()[key]?.let { Regex("(\\d+(?:\\.\\d+)?)").find(it)?.groupValues?.get(1)?.toDoubleOrNull() } ?: 0.0

    fun textScore(p: ProductEntity, words: List<String>): Double {
        if (words.isEmpty()) return 0.0
        val hay = (p.name + " " + p.brand + " " + p.tags + " " + p.subType + " " + p.specs).lowercase()
        return words.count { hay.contains(it) || (it.endsWith("s") && hay.contains(it.dropLast(1))) }.toDouble() / words.size
    }

    /** Hard filters then a transparent weighted score. */
    fun search(all: List<ProductEntity>, q: ParsedQuery, limit: Int = 30): List<ProductEntity> {
        var c = all.filter { it.active }
        if (q.categoryIds.isNotEmpty()) c = c.filter { it.categoryId in q.categoryIds }
        q.maxPrice?.let { m -> c = c.filter { it.price <= m } }
        q.minPrice?.let { m -> c = c.filter { it.price >= m } }
        if (q.colours.isNotEmpty()) c = c.filter { p -> q.colours.any { col -> p.colors.lowercase().contains(col) } }.ifEmpty { c }
        if (q.gender != null) c = c.filter { p -> genderOk(p, q.gender) }.ifEmpty { c }
        if (q.occasion != null) c = c.sortedByDescending { if (it.tags.contains(q.occasion)) 1 else 0 }
        val words = q.keywords.filter { QueryParser.categoryWords[it] == null && it !in QueryParser.colourWords }
        if (q.categoryIds.isEmpty() && words.isNotEmpty()) c = c.filter { textScore(it, words) > 0 }
        val maxOf = q.priorities.associateWith { k -> c.maxOfOrNull { specNumber(it, k) }?.takeIf { it > 0 } ?: 1.0 }
        return c.sortedByDescending { p ->
            var s = textScore(p, words) * 2 + p.rating / 5.0 + ln(1.0 + p.ratingCount) / 10.0 + p.discountPercent / 200.0
            if (q.occasion != null && p.tags.contains(q.occasion)) s += 1.0
            for ((i, k) in q.priorities.withIndex()) {
                val w = 2.0 / (i + 1)
                s += when (k) {
                    "rating" -> w * p.rating / 5.0
                    "price" -> w * (1 - p.price / (c.maxOfOrNull { it.price } ?: p.price))
                    else -> w * specNumber(p, k) / (maxOf[k] ?: 1.0)
                }
            }
            if (p.stock == 0) s -= 2
            s
        }.take(limit)
    }

    fun prosCons(p: ProductEntity, peers: List<ProductEntity>): Pair<List<String>, List<String>> {
        val pros = mutableListOf<String>(); val cons = mutableListOf<String>()
        fun cmp(key: String, label: String, unit: String) {
            val v = specNumber(p, key); if (v == 0.0) return
            val avg = peers.map { specNumber(it, key) }.filter { it > 0 }.average()
            if (avg.isNaN()) return
            if (v > avg * 1.08) pros += "$label ${v.roundToInt()} $unit (above average)"
            else if (v < avg * 0.92) cons += "$label ${v.roundToInt()} $unit (below average)"
        }
        cmp("camera", "Camera", "MP"); cmp("battery", "Battery", if (p.categoryId == 3L) "mAh" else "h"); cmp("ram", "RAM", "GB"); cmp("storage", "Storage", "GB")
        if (p.rating >= 4.2) pros += "Highly rated (${p.rating}★ from ${p.ratingCount} ratings)" else if (p.rating < 3.6) cons += "Lower rating (${p.rating}★)"
        if (p.discountPercent >= 30) pros += "${p.discountPercent}% off MRP"
        if (p.stock in 1..5) cons += "Only ${p.stock} left in stock"
        if (p.stock == 0) cons += "Out of stock"
        if (p.specMap()["display"]?.contains("AMOLED") == true) pros += "AMOLED display"
        return pros to cons
    }
}

// ============================ Assistant ============================

data class AssistantReply(val text: String, val productIds: List<Long>, val action: String? = null)

class ShoppingAssistant(private val provider: AiProvider = RuleEngineProvider) {

    fun reply(input: String, catalogue: List<ProductEntity>, categories: List<CategoryEntity>, orders: List<OrderEntity>): AssistantReply {
        val q = QueryParser.parse(input)
        return when (q.intent) {
            Intent.GREETING -> AssistantReply("Namaste! Tell me what you're looking for — e.g. \"smartphone under ₹20,000 with best camera and battery\", \"wedding outfit under ₹3,000\", or \"gift for my mother under ₹1,500\".", emptyList())
            Intent.TRACK_ORDER -> {
                val o = orders.maxByOrNull { it.createdAt }
                if (o == null) AssistantReply("You have no orders yet.", emptyList())
                else AssistantReply("Your latest order #${o.id} (${o.total.rupees()}) is currently **${o.status.replace('_', ' ').lowercase()}**. Tap below to open tracking.", emptyList(), "order:${o.id}")
            }
            Intent.RETURN_HELP -> AssistantReply("Returns are accepted within each product's return window after delivery (shown on the product page). Open Orders → select the order → \"Return\". I'll check eligibility automatically. Refunds go to the original payment method (sandbox in this build).", emptyList(), "orders")
            Intent.DEALS -> {
                val deals = catalogue.filter { it.stock > 0 && it.discountPercent >= 30 && it.rating >= 4.0 }.sortedByDescending { it.discountPercent + it.rating * 5 }.take(8)
                AssistantReply("Top deals right now — at least 30% off MRP and rated 4★+. Open a product to see its deal score from recorded price history.", deals.map { it.id })
            }
            Intent.GIFT -> giftReply(q, catalogue)
            Intent.OUTFIT -> outfitReply(q, catalogue)
            Intent.BUDGET_COMBO -> {
                val budget = q.maxPrice ?: 5000.0
                val combo = BudgetPlanner.plan(catalogue, budget, q.categoryIds)
                AssistantReply("Best combination I found within ${budget.rupees()}: ${combo.size} items totalling ${combo.sumOf { it.price }.rupees()}. Picked for rating and value, one per category.", combo.map { it.id })
            }
            Intent.COMPARE, Intent.SEARCH -> searchReply(q, catalogue, categories)
        }
    }

    private fun searchReply(q: ParsedQuery, catalogue: List<ProductEntity>, categories: List<CategoryEntity>): AssistantReply {
        val results = Ranker.search(catalogue, q, 6)
        if (results.isEmpty()) {
            val hint = if (q.maxPrice != null) " Try raising the budget above ${q.maxPrice.rupees()}." else ""
            return AssistantReply("I couldn't find a match in the catalogue.$hint", emptyList())
        }
        val catName = q.categoryIds.firstOrNull()?.let { id -> categories.firstOrNull { it.id == id }?.name } ?: "products"
        val sb = StringBuilder()
        sb.append("Understood: $catName")
        q.maxPrice?.let { sb.append(" under ${it.rupees()}") }
        if (q.priorities.isNotEmpty()) sb.append(", prioritising ${q.priorities.joinToString(" & ")}")
        if (q.colours.isNotEmpty()) sb.append(", colour ${q.colours.joinToString("/")}")
        sb.append(".\n\n")
        val top = results.first()
        val (pros, cons) = Ranker.prosCons(top, results)
        sb.append("Top pick: ${top.name} — ${top.price.rupees()}\n")
        if (pros.isNotEmpty()) sb.append("✓ ").append(pros.joinToString("\n✓ ")).append("\n")
        if (cons.isNotEmpty()) sb.append("✗ ").append(cons.joinToString("\n✗ ")).append("\n")
        if (results.size > 1) {
            sb.append("\nHow the others differ:\n")
            results.drop(1).take(3).forEach { p ->
                val diff = mutableListOf<String>()
                val dp = p.price - top.price
                diff += if (dp < 0) "${(-dp).rupees()} cheaper" else "${dp.rupees()} costlier"
                for (k in q.priorities.filter { it != "price" && it != "rating" }) {
                    val a = Ranker.specNumber(p, k); val b = Ranker.specNumber(top, k)
                    if (a > 0 && b > 0 && a != b) diff += "$k ${a.roundToInt()} vs ${b.roundToInt()}"
                }
                diff += "${p.rating}★"
                sb.append("• ${p.name}: ${diff.joinToString(", ")}\n")
            }
        }
        sb.append("\n(${provider.label}) Tap \"Compare\" on any product for a side-by-side table.")
        return AssistantReply(sb.toString().trim(), results.map { it.id })
    }

    private fun giftReply(q: ParsedQuery, catalogue: List<ProductEntity>): AssistantReply {
        val picks = GiftFinder.find(catalogue, q.recipient, q.occasion, q.maxPrice, q.gender)
        val who = q.recipient ?: "them"
        return AssistantReply(
            "Gift ideas for $who" + (q.occasion?.let { " ($it)" } ?: "") + (q.maxPrice?.let { " under ${it.rupees()}" } ?: "") +
                ": chosen from well-rated, giftable products across different categories.", picks.map { it.id })
    }

    private fun outfitReply(q: ParsedQuery, catalogue: List<ProductEntity>): AssistantReply {
        val outfit = OutfitBuilder.build(catalogue, q.occasion ?: "casual", q.maxPrice ?: 3000.0, q.gender)
        if (outfit.isEmpty()) return AssistantReply("I couldn't build a complete outfit within that budget. Try a slightly higher budget.", emptyList())
        val total = outfit.sumOf { it.second.price }
        val text = buildString {
            append("Complete ${q.occasion ?: "casual"} look for ${total.rupees()}")
            q.maxPrice?.let { append(" (budget ${it.rupees()})") }
            append(":\n")
            outfit.forEach { (slot, p) -> append("• $slot: ${p.name} — ${p.price.rupees()}\n") }
            val colours = outfit.map { it.second.colorList().firstOrNull() ?: "" }.filter { it.isNotEmpty() }
            if (colours.isNotEmpty()) append("\nColour story: ${colours.joinToString(" + ")}.")
        }
        return AssistantReply(text, outfit.map { it.second.id })
    }
}

object GiftFinder {
    private val recipientTags = mapOf(
        "mother" to listOf("kitchen", "saree", "jewellery", "decor", "skincare", "mother"),
        "father" to listOf("watch", "smartwatch", "earbuds", "books", "car"),
        "wife" to listOf("jewellery", "saree", "makeup", "skincare", "dress"),
        "husband" to listOf("smartwatch", "earbuds", "watch", "sneakers", "speaker"),
        "sister" to listOf("makeup", "jewellery", "dress", "earbuds"),
        "brother" to listOf("earbuds", "sneakers", "sports", "tshirt"),
        "partner" to listOf("jewellery", "smartwatch", "watch", "decor"),
        "friend" to listOf("speaker", "books", "decor", "earbuds"),
        "baby" to listOf("baby"), "kid" to listOf("baby", "books", "sports"),
        "colleague" to listOf("decor", "books", "skincare")
    )
    fun find(all: List<ProductEntity>, recipient: String?, occasion: String?, max: Double?, gender: String?): List<ProductEntity> {
        val tags = recipientTags[recipient] ?: listOf("gift")
        val pool = all.filter { p -> p.stock > 0 && (max == null || p.price <= max) && p.rating >= 3.8 && p.categoryId != 7L }
        val scored = pool.map { p ->
            val hay = p.tags + "," + p.subType
            var s = tags.count { hay.contains(it) } * 2.0 + p.rating
            if (hay.contains("gift")) s += 1
            if (occasion != null && hay.contains(occasion)) s += 1
            if (!Ranker.genderOk(p, gender)) s -= 3
            p to s
        }.sortedByDescending { it.second }
        // diversify by sub-type
        return scored.map { it.first }.distinctBy { it.subType }.take(6)
    }
}

object OutfitBuilder {
    fun build(all: List<ProductEntity>, occasion: String, budget: Double, gender: String?): List<Pair<String, ProductEntity>> {
        val ethnic = occasion in listOf("wedding", "festive", "diwali")
        fun fits(p: ProductEntity) = p.stock > 0 && Ranker.genderOk(p, gender)
        val mainTypes = when {
            ethnic && gender == "men" -> listOf("sherwani")
            ethnic -> listOf("saree", "kurta", "sherwani")
            occasion == "party" || occasion == "date" -> listOf("dress", "tshirt", "jeans")
            else -> listOf("tshirt", "jeans", "kurta", "dress")
        }
        val footTypes = if (ethnic) listOf("ethnic-footwear", "heels") else listOf("sneakers", "heels")
        val accTypes = if (ethnic) listOf("jewellery", "watch-accessory") else listOf("watch-accessory", "jewellery")
        val shares = listOf(0.6, 0.25, 0.15)
        val slots = listOf("Outfit" to mainTypes, "Footwear" to footTypes, "Accessory" to accTypes)
        val chosen = mutableListOf<Pair<String, ProductEntity>>()
        var remaining = budget
        for ((i, slot) in slots.withIndex()) {
            val cap = if (i == slots.lastIndex) remaining else max(remaining * shares[i] / shares.drop(i).sum(), 0.0) + (budget * 0.1)
            val pick = all.filter { it.subType in slot.second && fits(it) && it.price <= min(cap, remaining) }
                .sortedByDescending { it.rating * 2 + (if (it.tags.contains(occasion)) 2 else 0) + it.price / budget }
                .firstOrNull()
            if (pick != null) { chosen += slot.first to pick; remaining -= pick.price }
        }
        return chosen
    }
}

object BudgetPlanner {
    /** Greedy value-per-rupee across categories (one item each), then fills the leftover budget. */
    fun plan(all: List<ProductEntity>, budget: Double, preferredCategories: Set<Long> = emptySet()): List<ProductEntity> {
        val pool = all.filter { it.stock > 0 && it.price <= budget && it.rating >= 3.8 && (preferredCategories.isEmpty() || it.categoryId in preferredCategories) }
        val bestPerCategory = pool.groupBy { it.categoryId }.mapValues { (_, list) -> list.maxByOrNull { value(it) }!! }.values
            .sortedByDescending { value(it) }
        val chosen = mutableListOf<ProductEntity>(); var left = budget
        for (p in bestPerCategory) if (p.price <= left) { chosen += p; left -= p.price }
        // try to upgrade items using leftover money
        for (i in chosen.indices) {
            val cur = chosen[i]
            val better = pool.filter { it.categoryId == cur.categoryId && it.id != cur.id && it.price - cur.price <= left && it.rating > cur.rating }
                .maxByOrNull { it.rating }
            if (better != null) { left -= (better.price - cur.price); chosen[i] = better }
        }
        return chosen
    }
    private fun value(p: ProductEntity) = (p.rating * p.rating) * (1 + p.discountPercent / 100.0) * ln(2.0 + p.ratingCount) / sqrt(p.price)
}

// ============================ Recommendations ============================

data class UserSignals(
    val viewed: List<Long>, val wishlist: List<Long>, val purchased: List<Long>, val searches: List<String>, val favouriteBrands: List<String>
)

object Recommender {
    /** Content + popularity hybrid. Returns popularity-only when personalisation is disabled. */
    fun recommend(all: List<ProductEntity>, s: UserSignals?, limit: Int = 12): List<ProductEntity> {
        if (s == null) return all.sortedByDescending { it.popularity + it.rating * 50 }.take(limit)
        val byId = all.associateBy { it.id }
        val catAffinity = HashMap<Long, Double>(); val brandAffinity = HashMap<String, Double>(); val typeAffinity = HashMap<String, Double>()
        fun bump(id: Long, w: Double) = byId[id]?.let {
            catAffinity.merge(it.categoryId, w, { a, b -> a + b }); brandAffinity.merge(it.brand, w, { a, b -> a + b }); typeAffinity.merge(it.subType, w, { a, b -> a + b })
        }
        s.viewed.forEachIndexed { i, id -> bump(id, 1.0 / (1 + i * 0.2)) }
        s.wishlist.forEach { bump(it, 2.0) }
        s.purchased.forEach { bump(it, 1.5) }
        s.favouriteBrands.forEach { brandAffinity.merge(it, 2.0, { a, b -> a + b }) }
        val searchWords = s.searches.flatMap { QueryParser.parse(it).keywords }.toSet()
        val seen = (s.purchased).toSet()
        return all.filter { it.id !in seen && it.stock > 0 }.sortedByDescending { p ->
            (catAffinity[p.categoryId] ?: 0.0) * 1.5 + (typeAffinity[p.subType] ?: 0.0) * 2 + (brandAffinity[p.brand] ?: 0.0) +
                Ranker.textScore(p, searchWords.toList()) * 2 + p.rating / 5 + ln(1.0 + p.popularity) / 10
        }.take(limit)
    }

    fun similar(p: ProductEntity, all: List<ProductEntity>, limit: Int = 10): List<ProductEntity> =
        all.filter { it.id != p.id && it.categoryId == p.categoryId }.sortedByDescending {
            (if (it.subType == p.subType) 3.0 else 0.0) + (if (it.brand == p.brand) 0.5 else 0.0) -
                abs(it.price - p.price) / max(p.price, 1.0) + Ranker.textScore(it, p.tags.split(",")) + it.rating / 5
        }.take(limit)

    /** Rule-based complementary pairs (until enough co-purchase data exists). */
    private val complements = mapOf(
        "smartphone" to listOf("earbuds", "smartwatch", "auto"), "laptop" to listOf("earbuds", "speaker"),
        "saree" to listOf("jewellery", "ethnic-footwear", "makeup"), "kurta" to listOf("jewellery", "ethnic-footwear"),
        "sherwani" to listOf("ethnic-footwear", "watch-accessory"), "dress" to listOf("heels", "jewellery", "makeup"),
        "jeans" to listOf("tshirt", "sneakers"), "tshirt" to listOf("jeans", "sneakers"), "sneakers" to listOf("tshirt", "sports"),
        "skincare" to listOf("makeup"), "makeup" to listOf("skincare"), "baby" to listOf("baby"), "grocery" to listOf("grocery")
    )
    fun boughtTogether(p: ProductEntity, all: List<ProductEntity>, coPurchased: List<Long>): List<ProductEntity> {
        val real = all.filter { it.id in coPurchased && it.id != p.id }
        val types = complements[p.subType] ?: emptyList()
        val rule = types.mapNotNull { t -> all.filter { it.subType == t && it.stock > 0 && it.id != p.id }.maxByOrNull { it.rating * ln(2.0 + it.ratingCount) } }
        return (real + rule).distinctBy { it.id }.take(3)
    }
}

// ============================ Reviews ============================

data class ReviewInsights(
    val count: Int, val average: Double, val distribution: Map<Int, Int>, val verifiedShare: Int,
    val positives: List<Pair<String, Int>>, val complaints: List<Pair<String, Int>>,
    val fit: Map<String, Int>, val sentiment: String, val summary: String
)

object ReviewAnalyzer {
    private val positiveThemes = mapOf(
        "Quality" to listOf("quality is excellent", "premium", "durable", "works perfectly"),
        "Value for money" to listOf("value for money", "worth"),
        "Matches photos" to listOf("exactly like", "vibrant"),
        "Comfort" to listOf("comfortable"),
        "Fast delivery" to listOf("delivery was quick", "packaging was good"),
        "Battery life" to listOf("battery life is great")
    )
    private val negativeThemes = mapOf(
        "Quality concerns" to listOf("quality could be better", "not worth"),
        "Colour mismatch" to listOf("colour slightly different"),
        "Durability" to listOf("stopped working", "stitching came off"),
        "Packaging/delivery" to listOf("packaging was damaged", "delivery was late")
    )

    fun analyse(reviews: List<ReviewEntity>): ReviewInsights {
        val n = reviews.size
        val avg = if (n == 0) 0.0 else reviews.map { it.rating }.average()
        val dist = (1..5).associateWith { s -> reviews.count { it.rating == s } }
        fun themes(map: Map<String, List<String>>) = map.map { (k, kws) -> k to reviews.count { r -> kws.any { r.text.lowercase().contains(it) } } }
            .filter { it.second > 0 }.sortedByDescending { it.second }
        val pos = themes(positiveThemes); val neg = themes(negativeThemes)
        val fit = reviews.filter { it.fit.isNotEmpty() }.groupingBy { it.fit }.eachCount()
        val sentiment = when { avg >= 4.2 -> "Very positive"; avg >= 3.6 -> "Mostly positive"; avg >= 2.8 -> "Mixed"; n == 0 -> "No reviews"; else -> "Negative" }
        val summary = buildString {
            if (n == 0) { append("No reviews yet."); return@buildString }
            append("Buyers mostly mention ")
            append(pos.take(2).joinToString(" and ") { it.first.lowercase() }.ifEmpty { "general satisfaction" })
            if (neg.isNotEmpty()) append("; the most common complaint is ${neg.first().first.lowercase()} (${neg.first().second} of $n reviews)")
            append(".")
            val total = fit.values.sum()
            if (total > 0) {
                val small = fit["SMALL"] ?: 0; val large = fit["LARGE"] ?: 0
                append(when {
                    small * 3 > total -> " Several buyers say it runs small — consider one size up."
                    large * 3 > total -> " Several buyers say it runs large."
                    else -> " Most buyers say it fits true to size."
                })
            }
        }
        return ReviewInsights(n, (avg * 10).roundToInt() / 10.0, dist, if (n == 0) 0 else reviews.count { it.verified } * 100 / n, pos, neg, fit, sentiment, summary)
    }
}

// ============================ Price intelligence ============================

data class DealInsight(
    val hasHistory: Boolean, val isDemoData: Boolean, val lowest: Double, val highest: Double, val average: Double,
    val score: Int, val verdict: String, val bestTimeAdvice: String
)

object PriceIntelligence {
    fun analyse(p: ProductEntity, history: List<PriceHistoryEntity>, sellerRating: Double): DealInsight {
        if (history.size < 3) return DealInsight(false, false, p.price, p.price, p.price, 0, "Not enough price history yet", "We'll start tracking this product's price from today.")
        val prices = history.map { it.price }
        val lo = prices.min(); val hi = prices.max(); val avg = prices.average()
        val vsAvg = (avg - p.price) / avg // positive = cheaper than usual
        val pos = if (hi > lo) (hi - p.price) / (hi - lo) else 0.5 // 1 = at historic low
        var score = (50 + vsAvg * 200 + pos * 25 + (p.rating - 3.5) * 10 + (sellerRating - 3.5) * 5).roundToInt().coerceIn(0, 100)
        if (p.stock == 0) score = min(score, 30)
        val verdict = when { score >= 80 -> "Great deal"; score >= 60 -> "Good price"; score >= 40 -> "Fair price"; else -> "Higher than usual" }
        val advice = when {
            p.price <= lo * 1.01 -> "Lowest recorded price — a good time to buy."
            p.price > avg * 1.05 -> "Price is above its recorded average (${avg.rupees()}). Consider setting a price-drop alert."
            else -> "Price is close to its recorded average; drops of ${((hi - lo) / 2).rupees()} have occurred in the tracked period."
        }
        return DealInsight(true, history.any { it.isDemoSeed }, lo, hi, avg, score, verdict, advice)
    }
}

// ============================ Size advice ============================

object SizeAdvisor {
    /** Chest (inches) → size chart used across demo apparel brands. */
    private val chart = linkedMapOf("S" to 36.0, "M" to 38.0, "L" to 40.0, "XL" to 42.0, "XXL" to 44.0)
    private val waist = linkedMapOf("28" to 28.0, "30" to 30.0, "32" to 32.0, "34" to 34.0, "36" to 36.0)

    fun recommend(p: ProductEntity, chestOrWaist: Double, fitStats: Map<String, Int>, previousSize: String?): Pair<String, String> {
        val sizes = p.sizeList()
        if (sizes.isEmpty()) return "" to "This product has no size options."
        val table = if (sizes.first().toIntOrNull() != null && p.subType == "jeans") waist else chart
        var best = table.entries.filter { it.key in sizes }.minByOrNull { abs(it.value - chestOrWaist) }?.key ?: sizes[sizes.size / 2]
        val total = fitStats.values.sum()
        val reason = StringBuilder("Based on your ${if (table === waist) "waist" else "chest"} of ${chestOrWaist.roundToInt()}\" and the size chart")
        if (total >= 3) {
            val small = (fitStats["SMALL"] ?: 0).toDouble() / total; val large = (fitStats["LARGE"] ?: 0).toDouble() / total
            val idx = sizes.indexOf(best)
            if (small > 0.35 && idx < sizes.lastIndex) { best = sizes[idx + 1]; reason.append(", sized up because ${(small * 100).roundToInt()}% of reviewers say it runs small") }
            else if (large > 0.35 && idx > 0) { best = sizes[idx - 1]; reason.append(", sized down because ${(large * 100).roundToInt()}% of reviewers say it runs large") }
        }
        if (!previousSize.isNullOrBlank()) reason.append(". You previously bought size $previousSize")
        reason.append(".")
        return best to reason.toString()
    }
}

// ============================ Visual (colour) search ============================

object ColourMatcher {
    fun distance(a: Long, b: Long): Double {
        val ar = (a shr 16 and 0xFF).toInt(); val ag = (a shr 8 and 0xFF).toInt(); val ab = (a and 0xFF).toInt()
        val br = (b shr 16 and 0xFF).toInt(); val bg = (b shr 8 and 0xFF).toInt(); val bb = (b and 0xFF).toInt()
        val rMean = (ar + br) / 2.0
        val dr = ar - br; val dg = ag - bg; val db = ab - bb
        return sqrt((2 + rMean / 256) * dr * dr + 4.0 * dg * dg + (2 + (255 - rMean) / 256) * db * db)
    }
    fun nearestName(argb: Long, palette: Map<String, Long>): String = palette.minByOrNull { distance(argb, it.value) }!!.key
    fun match(all: List<ProductEntity>, argb: Long, categoryId: Long?, limit: Int = 20): List<ProductEntity> =
        all.filter { categoryId == null || it.categoryId == categoryId }.sortedBy { distance(it.colorHex, argb) }.take(limit)
}

// ============================ Seller tools ============================

object SellerTools {
    data class Listing(val title: String, val description: String, val keywords: List<String>, val tags: List<String>)

    fun generateListing(name: String, brand: String, category: String, material: String, colours: String, features: String): Listing {
        val feats = features.split(",", "\n").map { it.trim() }.filter { it.isNotEmpty() }
        val colourList = colours.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val title = listOf(brand, name, material.takeIf { it.isNotBlank() }, colourList.firstOrNull()?.let { "($it)" })
            .filterNotNull().joinToString(" ").replace(Regex("\\s+"), " ").trim().take(90)
        val desc = buildString {
            append("$brand $name — a $category essential")
            if (material.isNotBlank()) append(" crafted in $material")
            append(".\n\n")
            if (feats.isNotEmpty()) { append("Key features:\n"); feats.forEach { append("• ${it.replaceFirstChar { c -> c.uppercase() }}\n") }; append("\n") }
            if (colourList.isNotEmpty()) append("Available in ${colourList.joinToString(", ")}.\n")
            append("Quality-checked and packed with care. Easy returns as per policy.")
        }
        val kw = (name.lowercase().split(" ") + category.lowercase() + material.lowercase().split(" ") + colourList.map { it.lowercase() } + feats.flatMap { it.lowercase().split(" ") })
            .map { it.trim(',', '.', ' ') }.filter { it.length > 2 }.distinct().take(15)
        val tags = listOf(category.lowercase()) + colourList.map { it.lowercase() } + feats.take(3).map { it.lowercase().replace(" ", "-") }
        return Listing(title, desc, kw, tags.distinct())
    }

    /** Suggests a price band from comparable catalogue products (median ± IQR). */
    fun priceRecommendation(categoryId: Long, subType: String?, all: List<ProductEntity>): Triple<Double, Double, Double>? {
        val peers = all.filter { it.categoryId == categoryId && (subType == null || it.subType == subType) }.map { it.price }.sorted()
        if (peers.size < 3) return null
        fun q(f: Double) = peers[((peers.size - 1) * f).roundToInt()]
        return Triple(q(0.25), q(0.5), q(0.75))
    }

    /** Simple moving-average forecast from real order history (needs ≥ 2 weeks of data). */
    fun forecastWeekly(items: List<OrderItemEntity>, orders: Map<Long, OrderEntity>): Pair<List<Int>, Int?> {
        val week = 7L * 24 * 60 * 60 * 1000
        val now = System.currentTimeMillis()
        val buckets = IntArray(8)
        items.forEach { i -> orders[i.orderId]?.let { o -> val w = ((now - o.createdAt) / week).toInt(); if (w in 0..7) buckets[7 - w] += i.quantity } }
        val list = buckets.toList()
        val nonZeroWeeks = list.count { it > 0 }
        return list to (if (nonZeroWeeks >= 2) list.takeLast(4).average().roundToInt() else null)
    }
}

// ============================ Smart reorder ============================

object ReorderPredictor {
    data class Suggestion(val productId: Long, val name: String, val timesBought: Int, val avgIntervalDays: Int?, val dueInDays: Int?)

    fun suggest(items: List<OrderItemEntity>, orders: Map<Long, OrderEntity>, products: Map<Long, ProductEntity>): List<Suggestion> {
        val day = 24L * 60 * 60 * 1000
        return items.filter { products[it.productId]?.categoryId in listOf(7L, 6L, 10L) }.groupBy { it.productId }.map { (pid, list) ->
            val times = list.mapNotNull { orders[it.orderId]?.createdAt }.sorted()
            val interval = if (times.size >= 2) ((times.last() - times.first()) / (times.size - 1) / day).toInt().coerceAtLeast(1) else null
            val due = interval?.let { (it - (System.currentTimeMillis() - times.last()) / day).toInt() }
            Suggestion(pid, list.first().name, list.size, interval, due)
        }.sortedWith(compareBy({ it.dueInDays ?: Int.MAX_VALUE }, { -it.timesBought }))
    }
}
