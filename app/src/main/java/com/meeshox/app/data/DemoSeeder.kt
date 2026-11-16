package com.meeshox.app.data

import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Seeds the local database with DEMO catalogue data for development builds.
 * All brands, sellers and products are fictional. Price-history rows are flagged isDemoSeed = true
 * so the UI can label them honestly.
 */
object DemoSeeder {

    private const val DAY = 24L * 60 * 60 * 1000

    val categories = listOf(
        CategoryEntity(1, "Fashion", "checkroom", 0xFFEC4899),
        CategoryEntity(2, "Electronics", "headphones", 0xFF3B82F6),
        CategoryEntity(3, "Mobiles", "smartphone", 0xFF6366F1),
        CategoryEntity(4, "Appliances", "kitchen", 0xFF0EA5E9),
        CategoryEntity(5, "Home Decor", "chair", 0xFFF59E0B),
        CategoryEntity(6, "Beauty", "face", 0xFFF43F5E),
        CategoryEntity(7, "Grocery", "local_grocery_store", 0xFF22C55E),
        CategoryEntity(8, "Footwear", "ice_skating", 0xFF8B5CF6),
        CategoryEntity(9, "Jewellery", "diamond", 0xFFEAB308),
        CategoryEntity(10, "Baby", "child_care", 0xFF14B8A6),
        CategoryEntity(11, "Sports", "sports_cricket", 0xFFEF4444),
        CategoryEntity(12, "Books", "menu_book", 0xFF78716C),
        CategoryEntity(13, "Automotive", "directions_car", 0xFF64748B)
    )

    val colourPalette = linkedMapOf(
        "Red" to 0xFFDC2626, "Maroon" to 0xFF7F1D1D, "Pink" to 0xFFEC4899, "Orange" to 0xFFF97316,
        "Yellow" to 0xFFEAB308, "Green" to 0xFF16A34A, "Olive" to 0xFF65A30D, "Teal" to 0xFF0D9488,
        "Blue" to 0xFF2563EB, "Navy" to 0xFF1E3A8A, "Purple" to 0xFF7C3AED, "Black" to 0xFF111827,
        "White" to 0xFFF9FAFB, "Grey" to 0xFF6B7280, "Beige" to 0xFFD6C6A8, "Brown" to 0xFF78350F,
        "Gold" to 0xFFCA8A04, "Silver" to 0xFF9CA3AF
    )

    val coupons = listOf(
        CouponEntity("WELCOME10", "10% off your first order (max ₹200)", 10, 0.0, 299.0, 200.0),
        CouponEntity("FLAT100", "₹100 off on orders above ₹999", 0, 100.0, 999.0, 100.0),
        CouponEntity("FESTIVE20", "20% off above ₹2,499 (max ₹750)", 20, 0.0, 2499.0, 750.0),
        CouponEntity("FREESHIP", "₹49 off — covers standard delivery", 0, 49.0, 199.0, 49.0)
    )

    private data class Template(
        val cat: Long, val subType: String, val names: List<String>, val brands: List<String>,
        val priceMin: Int, val priceMax: Int, val colours: List<String>, val sizes: String,
        val tags: String, val specs: (Random) -> String = { "" }, val returnDays: Int = 7
    )

    private val clothingSizes = "S,M,L,XL,XXL"
    private val shoeSizes = "6,7,8,9,10,11"

    private val templates = listOf(
        // Fashion
        Template(1, "kurta", listOf("Block Print Cotton Kurta", "Chikankari Straight Kurta", "Festive Silk Blend Kurta", "Anarkali Flared Kurta"),
            listOf("Vastra", "Rangreza", "Taana"), 499, 2499, listOf("Maroon", "Yellow", "Teal", "Pink", "Navy", "White"), clothingSizes,
            "ethnic,women,festive,wedding,traditional", { "fabric=${listOf("Cotton", "Rayon", "Silk blend").random(it)};fit=Regular" }),
        Template(1, "saree", listOf("Banarasi Art Silk Saree", "Printed Georgette Saree", "Kanjeevaram Style Saree"),
            listOf("Rangreza", "Kalaa"), 699, 2999, listOf("Red", "Maroon", "Green", "Pink", "Gold", "Purple"), "",
            "ethnic,women,wedding,festive,party,traditional", { "fabric=${listOf("Art silk", "Georgette", "Chiffon").random(it)};length=6.3 m with blouse" }),
        Template(1, "sherwani", listOf("Jacquard Sherwani Set", "Nehru Jacket Kurta Set", "Embroidered Kurta Pyjama"),
            listOf("Taana", "Raja Saab"), 999, 2999, listOf("Beige", "Maroon", "Navy", "Gold", "White"), clothingSizes,
            "ethnic,men,wedding,festive,traditional", { "fabric=Jacquard;fit=Regular" }),
        Template(1, "tshirt", listOf("Oversized Graphic Tee", "Classic Crew Neck T-Shirt", "Polo T-Shirt", "Dry-Fit Sports Tee"),
            listOf("UrbanLoop", "Basics.co", "StreetHaus"), 249, 899, listOf("Black", "White", "Navy", "Olive", "Grey", "Red"), clothingSizes,
            "casual,men,women,western,daily", { "fabric=${listOf("100% Cotton", "Poly-cotton").random(it)};fit=${listOf("Regular", "Oversized", "Slim").random(it)}" }),
        Template(1, "jeans", listOf("Slim Fit Stretch Jeans", "Straight Fit Denim", "High-Rise Mom Jeans"),
            listOf("DenimDesh", "UrbanLoop"), 599, 1799, listOf("Blue", "Navy", "Black", "Grey"), "28,30,32,34,36",
            "casual,western,men,women,denim", { "fabric=Denim;fit=${listOf("Slim", "Straight", "Relaxed").random(it)}" }),
        Template(1, "dress", listOf("Floral A-Line Dress", "Bodycon Party Dress", "Tiered Maxi Dress"),
            listOf("Bloom&Co", "StreetHaus"), 499, 1999, listOf("Red", "Black", "Pink", "Blue", "Yellow"), clothingSizes,
            "western,women,party,date,casual", { "fabric=${listOf("Crepe", "Rayon", "Georgette").random(it)}" }),
        // Electronics
        Template(2, "earbuds", listOf("Pulse Buds ANC", "AirTone TWS Earbuds", "BassPods Lite"),
            listOf("Sonik", "Boomz", "Auralis"), 799, 4999, listOf("Black", "White", "Blue"), "",
            "audio,music,gadget,gift,wireless", { "battery=${(20..50).random(it)} h;anc=${listOf("Yes", "No").random(it)};bluetooth=5.3" }),
        Template(2, "smartwatch", listOf("FitPulse Smartwatch", "Orbit Watch 2", "ActiveBand Pro"),
            listOf("Auralis", "Orbit"), 1299, 6999, listOf("Black", "Silver", "Pink", "Blue"), "",
            "wearable,fitness,gadget,gift,men,women", { "display=${listOf("1.8\" AMOLED", "1.96\" AMOLED", "1.7\" LCD").random(it)};battery=${(5..14).random(it)} days" }),
        Template(2, "laptop", listOf("Nova Book 14", "Zenith Pro 15", "Swift Air 13"),
            listOf("Nova", "Zenith"), 32999, 79999, listOf("Silver", "Grey", "Black"), "",
            "computer,work,student,laptop", { "ram=${listOf(8, 16).random(it)} GB;storage=${listOf(512, 1024).random(it)} GB SSD;battery=${(6..14).random(it)} h" }),
        Template(2, "speaker", listOf("Thunder Bluetooth Speaker", "Mini Boom Speaker", "Party Tower Speaker"),
            listOf("Boomz", "Sonik"), 999, 7999, listOf("Black", "Blue", "Red"), "",
            "audio,music,party,gift", { "output=${listOf(10, 20, 40, 80).random(it)} W;battery=${(8..20).random(it)} h" }),
        // Mobiles
        Template(3, "smartphone", listOf("Nimbus Z5", "Nimbus Z5 Pro", "Orbit X2", "Orbit X2 Max", "Volt 12", "Volt 12 Lite", "Pixelon A9", "Pixelon A9s"),
            listOf("Nimbus", "Orbit", "Volt", "Pixelon"), 8999, 44999, listOf("Black", "Blue", "Green", "Silver", "Purple"), "",
            "mobile,phone,smartphone,5g,gadget", {
                "camera=${listOf(48, 50, 64, 108, 200).random(it)} MP;battery=${listOf(4500, 5000, 5500, 6000, 6500).random(it)} mAh;" +
                    "ram=${listOf(4, 6, 8, 12).random(it)} GB;storage=${listOf(64, 128, 256).random(it)} GB;display=${listOf("6.5\" LCD", "6.67\" AMOLED", "6.78\" AMOLED 120Hz").random(it)};" +
                    "processor=${listOf("Dimensity 7050", "Snapdragon 6 Gen 1", "Snapdragon 7s Gen 2", "Helio G99").random(it)}"
            }),
        // Appliances
        Template(4, "mixer", listOf("PowerGrind Mixer Grinder 750W", "Turbo Mixer 500W"),
            listOf("KitchenKing", "Homely"), 1799, 4999, listOf("White", "Red", "Black"), "", "kitchen,home,appliance,mother,gift",
            { "power=${listOf(500, 750, 1000).random(it)} W;jars=${(2..4).random(it)}" }, 10),
        Template(4, "air-fryer", listOf("CrispAir Fryer 4.2L", "HealthyFry Digital Air Fryer"),
            listOf("Homely", "KitchenKing"), 2999, 7999, listOf("Black", "White"), "", "kitchen,home,appliance,healthy,gift",
            { "capacity=${listOf("3.5", "4.2", "5.5").random(it)} L" }, 10),
        Template(4, "fan", listOf("BreezeMax BLDC Ceiling Fan", "Silent Table Fan"),
            listOf("Breeze", "Homely"), 1499, 4499, listOf("White", "Brown", "Grey"), "", "home,appliance,summer", { "power=${(28..75).random(it)} W" }, 10),
        // Home decor
        Template(5, "decor", listOf("Macramé Wall Hanging", "Ceramic Flower Vase Set", "Brass Diya Set", "LED Fairy Lights 10m", "Abstract Canvas Wall Art", "Cotton Cushion Covers (Set of 5)"),
            listOf("Ghar Sajao", "NestCraft"), 249, 1999, listOf("Beige", "Gold", "White", "Teal", "Orange", "Brown"), "",
            "home,decor,festive,diwali,gift,housewarming"),
        // Beauty
        Template(6, "skincare", listOf("Vitamin C Face Serum", "Niacinamide 10% Serum", "SPF 50 Sunscreen Gel", "Aloe Hydrating Face Wash"),
            listOf("GlowLab", "Prakriti"), 199, 899, listOf("White", "Orange", "Green"), "", "beauty,skincare,women,men,gift,self-care"),
        Template(6, "makeup", listOf("Matte Liquid Lipstick", "Kajal Duo Pack", "Glow Highlighter Palette"),
            listOf("GlowLab", "Rouge India"), 149, 799, listOf("Red", "Maroon", "Pink", "Brown", "Black"), "", "beauty,makeup,women,party,wedding,gift"),
        // Grocery
        Template(7, "grocery", listOf("Basmati Rice 5kg", "Cold Pressed Mustard Oil 1L", "Masala Tea 500g", "Toor Dal 1kg", "Dry Fruits Gift Box 500g", "Detergent Powder 4kg", "Dishwash Gel 1L", "Atta Whole Wheat 10kg"),
            listOf("Annapurna Farms", "DesiKart", "CleanHome"), 99, 999, listOf("Yellow", "Green", "Brown"), "", "grocery,daily,household,essentials", { "" }, 0),
        // Footwear
        Template(8, "sneakers", listOf("Cloud Run Sneakers", "Street Court Sneakers", "Knit Walking Shoes"),
            listOf("Stride", "Kickz"), 699, 2999, listOf("White", "Black", "Red", "Grey", "Blue"), shoeSizes,
            "footwear,shoes,casual,sports,men,women"),
        Template(8, "ethnic-footwear", listOf("Embroidered Mojaris", "Kolhapuri Flats", "Wedding Juttis"),
            listOf("Juttiwala", "Stride"), 399, 1499, listOf("Gold", "Maroon", "Brown", "Beige"), shoeSizes,
            "footwear,ethnic,wedding,festive,traditional,men,women"),
        Template(8, "heels", listOf("Block Heel Sandals", "Party Stilettos"),
            listOf("Kickz", "Bloom&Co"), 499, 1799, listOf("Black", "Gold", "Pink", "Red"), "5,6,7,8", "footwear,women,party,wedding"),
        // Jewellery
        Template(9, "jewellery", listOf("Kundan Necklace Set", "Oxidised Jhumkas", "American Diamond Earrings", "Temple Bangles (Set of 4)", "Sterling Silver Pendant"),
            listOf("Aabhushan", "Zevar"), 299, 2499, listOf("Gold", "Silver", "Maroon", "Green"), "",
            "jewellery,women,wedding,festive,gift,accessories,traditional"),
        Template(9, "watch-accessory", listOf("Analog Leather Watch", "Steel Chain Watch"), listOf("Samay", "Zevar"), 599, 2499,
            listOf("Brown", "Black", "Silver", "Gold"), "", "accessories,men,women,gift,watch"),
        // Baby
        Template(10, "baby", listOf("Organic Cotton Onesies (3 pack)", "Soft Plush Teddy", "Baby Care Gift Hamper", "Foldable Baby Stroller", "Diaper Pants (M, 64 pcs)"),
            listOf("LittleNest", "Tinytots"), 299, 5999, listOf("Yellow", "Blue", "Pink", "White"), "", "baby,kids,gift,newborn,essentials"),
        // Sports
        Template(11, "sports", listOf("English Willow Cricket Bat", "Yoga Mat 6mm", "Adjustable Dumbbells 10kg", "Badminton Racket Pair", "Football Size 5"),
            listOf("PlayOn", "FitNation"), 299, 3999, listOf("Blue", "Black", "Green", "Red", "Purple"), "", "sports,fitness,gift,outdoor,men,women"),
        // Books
        Template(12, "book", listOf("The Quiet Monsoon (Novel)", "Coding Interviews Decoded", "Indian History in 100 Objects", "Mindful Money (Personal Finance)", "Bedtime Stories for Little Ones"),
            listOf("Pustak House", "Inkwell"), 149, 699, listOf("White", "Blue", "Orange"), "", "books,reading,gift,student"),
        // Automotive
        Template(13, "auto", listOf("Car Phone Holder", "Microfibre Cleaning Kit", "Tyre Inflator Portable", "Bike Helmet ISI", "Car Seat Cushion"),
            listOf("AutoGenie", "MotoPro"), 199, 2999, listOf("Black", "Grey", "Red"), "", "automotive,car,bike,accessories,men")
    )

    private val positive = listOf(
        "Quality is excellent for the price.", "Looks exactly like the pictures.", "Very comfortable, would buy again.",
        "Delivery was quick and packaging was good.", "Value for money product.", "Material feels premium and durable.",
        "Battery life is great.", "Colour is vibrant and did not fade after washing.", "Works perfectly, no issues so far."
    )
    private val negative = listOf(
        "Quality could be better.", "Colour slightly different from the photo.", "Stopped working after a few weeks.",
        "Packaging was damaged.", "Stitching came off after two washes.", "Not worth the price.", "Delivery was late."
    )
    private val fitText = mapOf("SMALL" to "Runs small, order one size up.", "LARGE" to "Fits a bit large.", "TRUE" to "Fits true to size.")
    private val names = listOf("Priya S.", "Rahul K.", "Ananya D.", "Vikram M.", "Sneha P.", "Arjun R.", "Meera N.", "Karan T.", "Ishita B.", "Rohit G.", "Pooja J.", "Sourav C.")

    suspend fun seed(dao: ShopDao) {
        if (dao.categoryCount() > 0) return
        val rnd = Random(2026)
        dao.insertCategories(categories)
        dao.insertCoupons(coupons)
        val sellerIds = listOf(
            SellerEntity(userId = null, businessName = "Kolkata Handloom House", gstin = "19AABCK1234F1Z5", city = "Kolkata", rating = 4.4, verified = true),
            SellerEntity(userId = null, businessName = "Surat Trends", gstin = "24AAACS9876K1Z2", city = "Surat", rating = 4.1, verified = true),
            SellerEntity(userId = null, businessName = "TechBazaar India", gstin = "29AAFCT4567L1Z8", city = "Bengaluru", rating = 4.3, verified = true),
            SellerEntity(userId = null, businessName = "Ghar Essentials", gstin = "27AADCG7654M1Z1", city = "Mumbai", rating = 3.9, verified = true),
            SellerEntity(userId = null, businessName = "Jaipur Crafts Co.", gstin = "08AAJCJ3456N1Z4", city = "Jaipur", rating = 4.5, verified = true)
        ).map { dao.insertSeller(it) }

        val now = System.currentTimeMillis()
        for (t in templates) {
            for (name in t.names) {
                val brand = t.brands.random(rnd)
                val price = roundPrice(rnd.nextInt(t.priceMin, t.priceMax + 1))
                val discount = listOf(0, 10, 15, 20, 30, 40, 50, 60).random(rnd)
                val mrp = if (discount == 0) price.toDouble() else roundPrice((price / (1 - discount / 100.0)).roundToInt()).toDouble()
                val colours = t.colours.shuffled(rnd).take(rnd.nextInt(1, minOf(4, t.colours.size) + 1))
                val primaryHex = colourPalette[colours.first()] ?: 0xFF7C3AED
                val ratingCount = rnd.nextInt(12, 9000)
                val product = ProductEntity(
                    name = "$brand $name", brand = brand, categoryId = t.cat, subType = t.subType,
                    description = describe(name, brand, t), price = price.toDouble(), mrp = mrp,
                    rating = 0.0, ratingCount = ratingCount, stock = if (rnd.nextInt(10) == 0) rnd.nextInt(0, 4) else rnd.nextInt(5, 200),
                    sellerId = sellerIds[(t.cat.toInt() + rnd.nextInt(3)) % sellerIds.size], colors = colours.joinToString(","),
                    sizes = t.sizes, colorHex = primaryHex, popularity = rnd.nextInt(10, 1000), deliveryDays = rnd.nextInt(2, 8),
                    tags = t.tags + "," + t.subType + "," + colours.joinToString(",") { it.lowercase() }, specs = t.specs(rnd),
                    returnDays = t.returnDays, createdAt = now - rnd.nextLong(1, 120) * DAY
                )
                val pid = dao.insertProduct(product)
                // reviews
                val reviews = (0 until rnd.nextInt(6, 16)).map {
                    val stars = weightedStars(rnd)
                    val fit = if (t.sizes.isNotEmpty()) listOf("SMALL", "TRUE", "TRUE", "TRUE", "LARGE").random(rnd) else ""
                    val text = buildString {
                        append(if (stars >= 4) positive.random(rnd) else if (stars <= 2) negative.random(rnd) else (positive.random(rnd) + " " + negative.random(rnd)))
                        if (fit.isNotEmpty()) append(" ").append(fitText[fit])
                    }
                    ReviewEntity(productId = pid, userName = names.random(rnd), rating = stars, text = text,
                        verified = rnd.nextInt(10) < 8, fit = fit, createdAt = now - rnd.nextLong(1, 90) * DAY)
                }
                dao.insertReviews(reviews)
                val avg = reviews.map { it.rating }.average()
                dao.updateProduct(product.copy(id = pid, rating = (avg * 10).roundToInt() / 10.0))
                // price history: one record roughly every 10 days over the last 90 days
                val history = (9 downTo 1).map { k ->
                    val factor = 1 + rnd.nextInt(-8, 18) / 100.0
                    PriceHistoryEntity(productId = pid, price = roundPrice((price * factor).roundToInt()).toDouble(), recordedAt = now - k * 10 * DAY, isDemoSeed = true)
                } + PriceHistoryEntity(productId = pid, price = price.toDouble(), recordedAt = now, isDemoSeed = true)
                dao.insertPriceHistory(history)
            }
        }
    }

    private fun weightedStars(r: Random): Int = when (r.nextInt(100)) {
        in 0..44 -> 5; in 45..74 -> 4; in 75..86 -> 3; in 87..93 -> 2; else -> 1
    }

    /** Retail-style price ending in 9, e.g. 1234 -> 1229. */
    fun roundPrice(p: Int): Int = if (p < 100) p else (p / 10) * 10 - 1

    private fun describe(name: String, brand: String, t: Template): String =
        "$name by $brand. Designed for everyday use with quality checks at every step. " +
            when (t.cat) {
                1L -> "Soft, breathable fabric and a flattering cut that works for ${t.tags.split(",").take(2).joinToString(" and ")} occasions."
                2L, 3L -> "Reliable performance, solid build and a 1-year seller warranty."
                4L -> "Energy-efficient appliance with 1-year warranty and easy cleaning."
                5L -> "Adds warmth and character to any room — perfect for festive décor and gifting."
                6L -> "Dermatologically tested, suitable for Indian skin and climate."
                7L -> "Sourced from trusted farms and packed hygienically."
                8L -> "Cushioned footbed and durable sole for all-day comfort."
                9L -> "Skin-friendly, anti-tarnish finish with elegant detailing."
                10L -> "Gentle, safe materials made for little ones."
                11L -> "Built for performance and practice sessions."
                12L -> "A well-reviewed read, printed on quality paper."
                else -> "Durable accessory with universal fit."
            }
}
