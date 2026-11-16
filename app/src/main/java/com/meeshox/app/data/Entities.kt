package com.meeshox.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "users", indices = [Index(value = ["phone"], unique = true)])
data class UserEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String,
    val email: String,
    val passwordHash: String,
    val salt: String,
    val role: String = "CUSTOMER", // CUSTOMER, SELLER, ADMIN
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "user_addresses", indices = [Index("userId")])
data class AddressEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long,
    val fullName: String,
    val phone: String,
    val line1: String,
    val city: String,
    val state: String,
    val pincode: String,
    val isDefault: Boolean = false
)

@Entity(tableName = "sellers", indices = [Index(value = ["userId"])])
data class SellerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long?,
    val businessName: String,
    val gstin: String,
    val city: String,
    val rating: Double,
    val verified: Boolean,
    val bankLast4: String = ""
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: Long,
    val name: String,
    val icon: String,
    val colorHex: Long
)

@Entity(
    tableName = "products",
    indices = [Index("categoryId"), Index("sellerId"), Index("price"), Index("popularity")]
)
data class ProductEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val brand: String,
    val categoryId: Long,
    val subType: String,
    val description: String,
    val price: Double,
    val mrp: Double,
    val rating: Double,
    val ratingCount: Int,
    val stock: Int,
    val sellerId: Long,
    val colors: String,      // CSV of colour names
    val sizes: String,       // CSV, empty if not applicable
    val colorHex: Long,      // primary colour, used for visual (colour) search & card art
    val popularity: Int,
    val deliveryDays: Int,
    val tags: String,        // CSV keywords, incl. specs like "camera:50mp"
    val specs: String,       // "key=value;key=value"
    val returnDays: Int = 7,
    val active: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
) {
    val discountPercent: Int get() = if (mrp > price) (((mrp - price) / mrp) * 100).toInt() else 0
    fun specMap(): Map<String, String> = specs.split(";").mapNotNull {
        val p = it.split("="); if (p.size == 2) p[0].trim() to p[1].trim() else null
    }.toMap()
    fun colorList() = colors.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    fun sizeList() = sizes.split(",").map { it.trim() }.filter { it.isNotEmpty() }
}

@Entity(tableName = "reviews", indices = [Index("productId")])
data class ReviewEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Long,
    val userName: String,
    val rating: Int,
    val text: String,
    val verified: Boolean,
    val fit: String = "", // SMALL, TRUE, LARGE or empty
    val createdAt: Long
)

@Entity(tableName = "price_history", indices = [Index("productId")])
data class PriceHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Long,
    val price: Double,
    val recordedAt: Long,
    val isDemoSeed: Boolean
)

@Entity(tableName = "cart_items", indices = [Index("userId"), Index("productId")])
data class CartItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long,
    val productId: Long,
    val quantity: Int,
    val size: String,
    val color: String,
    val savedForLater: Boolean = false
)

@Entity(tableName = "wishlist_items", primaryKeys = ["userId", "productId"])
data class WishlistEntity(
    val userId: Long,
    val productId: Long,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "coupons")
data class CouponEntity(
    @PrimaryKey val code: String,
    val description: String,
    val percentOff: Int,
    val flatOff: Double,
    val minOrder: Double,
    val maxDiscount: Double,
    val active: Boolean = true
)

@Entity(tableName = "orders", indices = [Index("userId")])
data class OrderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long,
    val addressText: String,
    val pincode: String,
    val subtotal: Double,
    val discount: Double,
    val deliveryCharge: Double,
    val tax: Double,
    val total: Double,
    val couponCode: String?,
    val paymentMethod: String,
    val paymentStatus: String,  // PENDING, PAID, FAILED, REFUND_INITIATED, REFUNDED, COD_PENDING
    val paymentRef: String,
    val status: String,         // PLACED, CONFIRMED, SHIPPED, OUT_FOR_DELIVERY, DELIVERED, CANCELLED, RETURN_REQUESTED, RETURNED
    val deliveryOption: String,
    val expectedDelivery: Long,
    val riskScore: Int = 0,
    val riskFlags: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "order_items", indices = [Index("orderId"), Index("productId")])
data class OrderItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val orderId: Long,
    val productId: Long,
    val sellerId: Long,
    val name: String,
    val price: Double,
    val quantity: Int,
    val size: String,
    val color: String
)

@Entity(tableName = "search_history", indices = [Index("userId")])
data class SearchHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long,
    val query: String,
    val at: Long = System.currentTimeMillis()
)

@Entity(tableName = "recently_viewed", primaryKeys = ["userId", "productId"])
data class RecentlyViewedEntity(
    val userId: Long,
    val productId: Long,
    val at: Long = System.currentTimeMillis()
)

@Entity(tableName = "support_tickets", indices = [Index("userId")])
data class SupportTicketEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long,
    val orderId: Long?,
    val subject: String,
    val message: String,
    val status: String = "OPEN", // OPEN, ESCALATED, RESOLVED
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "ai_conversations", indices = [Index("userId")])
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long,
    val channel: String, // ASSISTANT, SUPPORT
    val fromUser: Boolean,
    val text: String,
    val productIds: String = "",
    val at: Long = System.currentTimeMillis()
)

@Entity(tableName = "notifications", indices = [Index("userId")])
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long,
    val title: String,
    val body: String,
    val read: Boolean = false,
    val at: Long = System.currentTimeMillis()
)

@Entity(tableName = "shopping_lists")
data class SharedListEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ownerId: Long,
    val name: String,
    val shareCode: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "shopping_list_items", primaryKeys = ["listId", "productId"])
data class SharedListItemEntity(
    val listId: Long,
    val productId: Long,
    val addedBy: String
)

@Entity(tableName = "audit_logs")
data class AuditLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long?,
    val action: String,
    val detail: String,
    val at: Long = System.currentTimeMillis()
)

/** Joined view for cart rendering. */
data class CartLine(
    val item: CartItemEntity,
    val product: ProductEntity
)
