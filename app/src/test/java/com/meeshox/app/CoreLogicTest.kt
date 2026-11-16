package com.meeshox.app

import com.meeshox.app.ai.BudgetPlanner
import com.meeshox.app.ai.Intent
import com.meeshox.app.ai.PriceIntelligence
import com.meeshox.app.ai.QueryParser
import com.meeshox.app.ai.Ranker
import com.meeshox.app.ai.ReviewAnalyzer
import com.meeshox.app.ai.RiskScorer
import com.meeshox.app.ai.SizeAdvisor
import com.meeshox.app.data.CouponEntity
import com.meeshox.app.data.PriceHistoryEntity
import com.meeshox.app.data.ProductEntity
import com.meeshox.app.data.ReviewEntity
import com.meeshox.app.domain.OrderLifecycle
import com.meeshox.app.domain.PasswordHasher
import com.meeshox.app.domain.PaymentMethod
import com.meeshox.app.domain.Pricing
import com.meeshox.app.domain.SandboxPaymentProvider
import com.meeshox.app.domain.Serviceability
import com.meeshox.app.domain.Validators
import com.meeshox.app.domain.rupees
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class CoreLogicTest {
    private fun product(id: Long, cat: Long, price: Double, rating: Double = 4.2, specs: String = "", sub: String = "x", stock: Int = 10, sizes: String = "") = ProductEntity(
        id = id, name = "P$id", brand = "B", categoryId = cat, subType = sub, description = "", price = price, mrp = price * 1.5, rating = rating,
        ratingCount = 100, stock = stock, sellerId = 1, colors = "Red", sizes = sizes, colorHex = 0xFFDC2626, popularity = 10, deliveryDays = 3,
        tags = "phone,mobile", specs = specs)

    @Test fun rupeeFormattingUsesIndianGrouping() {
        assertEquals("₹1,23,456", 123456.0.rupees())
        assertEquals("₹999", 999.0.rupees())
        assertEquals("₹20,000", 20000.0.rupees())
    }

    @Test fun parsesBudgetCategoryAndPriorities() {
        val q = QueryParser.parse("Find me a smartphone under ₹20,000 with the best camera and battery")
        assertEquals(20000.0, q.maxPrice!!, 0.1)
        assertTrue(3L in q.categoryIds)
        assertEquals(listOf("camera", "battery"), q.priorities)
        assertEquals(Intent.SEARCH, q.intent)
    }

    @Test fun parsesShorthandAndIntents() {
        assertEquals(20000.0, QueryParser.parse("phone under 20k").maxPrice!!, 0.1)
        assertEquals(Intent.TRACK_ORDER, QueryParser.parse("Track my last order").intent)
        assertEquals(Intent.GIFT, QueryParser.parse("Find a birthday gift for my mother").intent)
        assertEquals("mother", QueryParser.parse("Find a birthday gift for my mother").recipient)
        assertEquals(Intent.DEALS, QueryParser.parse("Show today's best deals").intent)
        assertEquals(Intent.OUTFIT, QueryParser.parse("Suggest a wedding outfit under ₹3,000").intent)
        assertTrue("red" in QueryParser.parse("red shoes under 1000").colours)
    }

    @Test fun rankerRespectsBudgetAndPrioritisesCamera() {
        val all = listOf(
            product(1, 3, 18000.0, specs = "camera=50 MP;battery=5000 mAh"),
            product(2, 3, 19000.0, specs = "camera=200 MP;battery=4500 mAh"),
            product(3, 3, 25000.0, specs = "camera=200 MP;battery=6000 mAh")
        )
        val r = Ranker.search(all, QueryParser.parse("phone under 20000 best camera"))
        assertEquals(listOf(2L, 1L), r.map { it.id })
    }

    @Test fun couponAndDeliveryRules() {
        val c = CouponEntity("FESTIVE20", "", 20, 0.0, 2499.0, 750.0)
        val small = Pricing.breakdown(listOf(Triple(300.0, 400.0, 1)), c, false)
        assertEquals(0.0, small.couponDiscount, 0.0)
        assertEquals(Pricing.STANDARD_DELIVERY, small.deliveryCharge, 0.0)
        val big = Pricing.breakdown(listOf(Triple(5000.0, 6000.0, 1)), c, false)
        assertEquals(750.0, big.couponDiscount, 0.0) // capped
        assertEquals(0.0, big.deliveryCharge, 0.0)
        assertEquals(4250.0, big.total, 0.01)
        assertTrue(big.taxIncluded > 0)
    }

    @Test fun validators() {
        assertTrue(Validators.phone("9876543210")); assertFalse(Validators.phone("1234567890"))
        assertTrue(Validators.pincode("700001")); assertFalse(Validators.pincode("012345"))
        assertTrue(Validators.gstin("19AABCK1234F1Z5")); assertFalse(Validators.gstin("19AABCK1234F1X5"))
        assertTrue(Validators.password("secret123")); assertFalse(Validators.password("short"))
        assertFalse(Serviceability.check("194101").serviceable)
        assertEquals("Metro", Serviceability.check("700091").zone)
    }

    @Test fun passwordHashing() {
        val salt = PasswordHasher.newSalt()
        val h = PasswordHasher.hash("secret123", salt)
        assertTrue(PasswordHasher.verify("secret123", salt, h))
        assertFalse(PasswordHasher.verify("secret124", salt, h))
    }

    @Test fun sandboxPaymentOutcomes() = runTest {
        val p = SandboxPaymentProvider()
        assertEquals("PAID", p.pay(100.0, PaymentMethod.UPI, "ok@upi").status)
        assertFalse(p.pay(100.0, PaymentMethod.UPI, "fail@upi").success)
        assertEquals("COD_PENDING", p.pay(100.0, PaymentMethod.COD, "").status)
    }

    @Test fun orderLifecycle() {
        val h = 3_600_000L
        val now = 100 * h
        assertEquals("PLACED", OrderLifecycle.simulatedStatus(now, "PLACED", now))
        assertEquals("SHIPPED", OrderLifecycle.simulatedStatus(now - 5 * h, "PLACED", now))
        assertEquals("DELIVERED", OrderLifecycle.simulatedStatus(now - 50 * h, "PLACED", now))
        assertEquals("CANCELLED", OrderLifecycle.simulatedStatus(now - 50 * h, "CANCELLED", now))
        assertTrue(OrderLifecycle.canCancel("CONFIRMED")); assertFalse(OrderLifecycle.canCancel("SHIPPED"))
        assertTrue(OrderLifecycle.canReturn("DELIVERED", now - h, 7, now))
        assertFalse(OrderLifecycle.canReturn("DELIVERED", now - 8 * 24 * h, 7, now))
    }

    @Test fun budgetPlannerStaysWithinBudget() {
        val all = (1..30).map { product(it.toLong(), (it % 6 + 1).toLong(), 200.0 + it * 97) }
        val plan = BudgetPlanner.plan(all, 5000.0)
        assertTrue(plan.isNotEmpty())
        assertTrue(plan.sumOf { it.price } <= 5000.0)
        assertEquals(plan.size, plan.map { it.categoryId }.distinct().size)
    }

    @Test fun riskScoringFlagsButExplains() {
        val r = RiskScorer.score(30000.0, 6, 0, 0, true, 0, 1)
        assertTrue(r.score >= 50)
        assertTrue(r.flags.isNotEmpty())
        assertEquals(0, RiskScorer.score(800.0, 1, 0, 3, false, 100, 1).score)
    }

    @Test fun priceIntelligenceNeedsHistory() {
        val p = product(1, 3, 1000.0)
        assertFalse(PriceIntelligence.analyse(p, emptyList(), 4.0).hasHistory)
        val hist = listOf(1200.0, 1300.0, 1250.0, 1000.0).mapIndexed { i, v -> PriceHistoryEntity(productId = 1, price = v, recordedAt = i.toLong(), isDemoSeed = false) }
        val d = PriceIntelligence.analyse(p, hist, 4.0)
        assertTrue(d.hasHistory); assertTrue(d.score >= 60); assertEquals(1000.0, d.lowest, 0.0)
    }

    @Test fun reviewStatsAndSizeAdvice() {
        val reviews = (1..6).map { ReviewEntity(productId = 1, userName = "u", rating = if (it < 5) 5 else 2, text = "Value for money product. Runs small", verified = true, fit = if (it <= 3) "SMALL" else "TRUE", createdAt = 0) }
        val i = ReviewAnalyzer.analyse(reviews)
        assertEquals(6, i.count); assertEquals(4, i.distribution[5]); assertEquals(3, i.fit["SMALL"])
        val shirt = product(9, 1, 500.0, sub = "tshirt", sizes = "S,M,L,XL,XXL")
        val (size, _) = SizeAdvisor.recommend(shirt, 38.0, i.fit, null)
        assertEquals("L", size) // M by chart, sized up because half the reviewers say it runs small
    }
}
