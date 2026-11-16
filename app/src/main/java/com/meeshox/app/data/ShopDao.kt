package com.meeshox.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ShopDao {
    // ---------- users ----------
    @Insert suspend fun insertUser(u: UserEntity): Long
    @Update suspend fun updateUser(u: UserEntity)
    @Query("SELECT * FROM users WHERE phone = :phone LIMIT 1") suspend fun userByPhone(phone: String): UserEntity?
    @Query("SELECT * FROM users WHERE id = :id") fun userFlow(id: Long): Flow<UserEntity?>
    @Query("SELECT * FROM users WHERE id = :id") suspend fun user(id: Long): UserEntity?
    @Query("DELETE FROM users WHERE id = :id") suspend fun deleteUser(id: Long)

    // ---------- addresses ----------
    @Insert suspend fun insertAddress(a: AddressEntity): Long
    @Query("SELECT * FROM user_addresses WHERE userId = :uid ORDER BY isDefault DESC, id DESC") fun addresses(uid: Long): Flow<List<AddressEntity>>
    @Query("DELETE FROM user_addresses WHERE id = :id") suspend fun deleteAddress(id: Long)

    // ---------- categories ----------
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertCategories(c: List<CategoryEntity>)
    @Query("SELECT * FROM categories ORDER BY id") fun categories(): Flow<List<CategoryEntity>>
    @Query("SELECT * FROM categories ORDER BY id") suspend fun categoriesOnce(): List<CategoryEntity>
    @Query("SELECT COUNT(*) FROM categories") suspend fun categoryCount(): Int

    // ---------- sellers ----------
    @Insert suspend fun insertSeller(s: SellerEntity): Long
    @Query("SELECT * FROM sellers WHERE id = :id") suspend fun seller(id: Long): SellerEntity?
    @Query("SELECT * FROM sellers WHERE userId = :uid LIMIT 1") fun sellerForUser(uid: Long): Flow<SellerEntity?>

    // ---------- products ----------
    @Insert suspend fun insertProduct(p: ProductEntity): Long
    @Insert suspend fun insertProducts(p: List<ProductEntity>): List<Long>
    @Update suspend fun updateProduct(p: ProductEntity)
    @Query("SELECT * FROM products WHERE id = :id") suspend fun product(id: Long): ProductEntity?
    @Query("SELECT * FROM products WHERE id = :id") fun productFlow(id: Long): Flow<ProductEntity?>
    @Query("SELECT * FROM products WHERE id IN (:ids)") suspend fun products(ids: List<Long>): List<ProductEntity>
    @Query("SELECT * FROM products WHERE active = 1") suspend fun allProducts(): List<ProductEntity>
    @Query("SELECT * FROM products WHERE active = 1 AND categoryId = :cid") suspend fun productsInCategory(cid: Long): List<ProductEntity>
    @Query("SELECT * FROM products WHERE active = 1 ORDER BY popularity DESC LIMIT :n") fun trending(n: Int): Flow<List<ProductEntity>>
    @Query("SELECT * FROM products WHERE active = 1 ORDER BY ratingCount DESC LIMIT :n") fun bestSellers(n: Int): Flow<List<ProductEntity>>
    @Query("SELECT * FROM products WHERE active = 1 AND mrp > 0 ORDER BY (mrp - price) * 1.0 / mrp DESC LIMIT :n") fun bigDiscounts(n: Int): Flow<List<ProductEntity>>
    @Query("SELECT * FROM products WHERE sellerId = :sid ORDER BY id DESC") fun productsBySeller(sid: Long): Flow<List<ProductEntity>>
    @Query("UPDATE products SET stock = stock - :qty, popularity = popularity + :qty WHERE id = :id AND stock >= :qty")
    suspend fun decrementStock(id: Long, qty: Int): Int
    @Query("UPDATE products SET stock = stock + :qty WHERE id = :id") suspend fun incrementStock(id: Long, qty: Int)

    // ---------- reviews ----------
    @Insert suspend fun insertReviews(r: List<ReviewEntity>)
    @Insert suspend fun insertReview(r: ReviewEntity)
    @Query("SELECT * FROM reviews WHERE productId = :pid ORDER BY createdAt DESC") fun reviews(pid: Long): Flow<List<ReviewEntity>>
    @Query("SELECT * FROM reviews WHERE productId = :pid") suspend fun reviewsOnce(pid: Long): List<ReviewEntity>
    @Query("SELECT r.* FROM reviews r JOIN products p ON p.id = r.productId WHERE p.sellerId = :sid ORDER BY r.createdAt DESC LIMIT 50")
    fun reviewsForSeller(sid: Long): Flow<List<ReviewEntity>>

    // ---------- price history ----------
    @Insert suspend fun insertPriceHistory(h: List<PriceHistoryEntity>)
    @Query("SELECT * FROM price_history WHERE productId = :pid ORDER BY recordedAt") suspend fun priceHistory(pid: Long): List<PriceHistoryEntity>

    // ---------- cart ----------
    @Insert suspend fun insertCartItem(c: CartItemEntity): Long
    @Update suspend fun updateCartItem(c: CartItemEntity)
    @Query("SELECT * FROM cart_items WHERE userId = :uid ORDER BY id") fun cart(uid: Long): Flow<List<CartItemEntity>>
    @Query("SELECT * FROM cart_items WHERE userId = :uid AND savedForLater = 0") suspend fun activeCartOnce(uid: Long): List<CartItemEntity>
    @Query("SELECT * FROM cart_items WHERE userId = :uid AND productId = :pid AND size = :size AND color = :color AND savedForLater = 0 LIMIT 1")
    suspend fun findCartItem(uid: Long, pid: Long, size: String, color: String): CartItemEntity?
    @Query("DELETE FROM cart_items WHERE id = :id") suspend fun deleteCartItem(id: Long)
    @Query("DELETE FROM cart_items WHERE userId = :uid AND savedForLater = 0") suspend fun clearActiveCart(uid: Long)
    @Query("SELECT COALESCE(SUM(quantity),0) FROM cart_items WHERE userId = :uid AND savedForLater = 0") fun cartCount(uid: Long): Flow<Int>

    // ---------- wishlist ----------
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun addWishlist(w: WishlistEntity)
    @Query("DELETE FROM wishlist_items WHERE userId = :uid AND productId = :pid") suspend fun removeWishlist(uid: Long, pid: Long)
    @Query("SELECT productId FROM wishlist_items WHERE userId = :uid ORDER BY addedAt DESC") fun wishlistIds(uid: Long): Flow<List<Long>>
    @Query("SELECT productId FROM wishlist_items WHERE userId = :uid") suspend fun wishlistIdsOnce(uid: Long): List<Long>

    // ---------- coupons ----------
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertCoupons(c: List<CouponEntity>)
    @Query("SELECT * FROM coupons WHERE active = 1") fun coupons(): Flow<List<CouponEntity>>
    @Query("SELECT * FROM coupons WHERE code = :code AND active = 1") suspend fun coupon(code: String): CouponEntity?

    // ---------- orders ----------
    @Insert suspend fun insertOrder(o: OrderEntity): Long
    @Update suspend fun updateOrder(o: OrderEntity)
    @Insert suspend fun insertOrderItems(i: List<OrderItemEntity>)
    @Query("SELECT * FROM orders WHERE userId = :uid ORDER BY createdAt DESC") fun orders(uid: Long): Flow<List<OrderEntity>>
    @Query("SELECT * FROM orders WHERE userId = :uid ORDER BY createdAt DESC") suspend fun ordersOnce(uid: Long): List<OrderEntity>
    @Query("SELECT * FROM orders WHERE id = :id") fun orderFlow(id: Long): Flow<OrderEntity?>
    @Query("SELECT * FROM orders WHERE id = :id") suspend fun order(id: Long): OrderEntity?
    @Query("SELECT * FROM order_items WHERE orderId = :oid") suspend fun orderItems(oid: Long): List<OrderItemEntity>
    @Query("SELECT oi.* FROM order_items oi JOIN orders o ON o.id = oi.orderId WHERE o.userId = :uid") suspend fun allOrderItemsForUser(uid: Long): List<OrderItemEntity>
    @Query("SELECT * FROM order_items WHERE sellerId = :sid ORDER BY id DESC") fun orderItemsForSeller(sid: Long): Flow<List<OrderItemEntity>>
    @Query("SELECT * FROM orders WHERE id IN (:ids)") suspend fun ordersByIds(ids: List<Long>): List<OrderEntity>
    @Query("SELECT COUNT(*) FROM orders WHERE userId = :uid AND createdAt > :since") suspend fun ordersSince(uid: Long, since: Long): Int
    @Query("SELECT COUNT(*) FROM orders WHERE userId = :uid AND status IN ('RETURN_REQUESTED','RETURNED')") suspend fun returnCount(uid: Long): Int

    // ---------- search / recently viewed ----------
    @Insert suspend fun insertSearch(s: SearchHistoryEntity)
    @Query("SELECT * FROM search_history WHERE userId = :uid ORDER BY at DESC LIMIT 20") suspend fun searchHistory(uid: Long): List<SearchHistoryEntity>
    @Query("DELETE FROM search_history WHERE userId = :uid") suspend fun clearSearch(uid: Long)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertViewed(v: RecentlyViewedEntity)
    @Query("SELECT productId FROM recently_viewed WHERE userId = :uid ORDER BY at DESC LIMIT 15") fun recentlyViewed(uid: Long): Flow<List<Long>>
    @Query("SELECT productId FROM recently_viewed WHERE userId = :uid ORDER BY at DESC LIMIT 30") suspend fun recentlyViewedOnce(uid: Long): List<Long>
    @Query("DELETE FROM recently_viewed WHERE userId = :uid") suspend fun clearViewed(uid: Long)

    // ---------- support / chat ----------
    @Insert suspend fun insertTicket(t: SupportTicketEntity): Long
    @Update suspend fun updateTicket(t: SupportTicketEntity)
    @Query("SELECT * FROM support_tickets WHERE userId = :uid ORDER BY createdAt DESC") fun tickets(uid: Long): Flow<List<SupportTicketEntity>>
    @Insert suspend fun insertMessage(m: ChatMessageEntity)
    @Query("SELECT * FROM ai_conversations WHERE userId = :uid AND channel = :ch ORDER BY at") fun messages(uid: Long, ch: String): Flow<List<ChatMessageEntity>>
    @Query("DELETE FROM ai_conversations WHERE userId = :uid AND channel = :ch") suspend fun clearMessages(uid: Long, ch: String)

    // ---------- notifications ----------
    @Insert suspend fun insertNotification(n: NotificationEntity)
    @Query("SELECT * FROM notifications WHERE userId = :uid ORDER BY at DESC") fun notifications(uid: Long): Flow<List<NotificationEntity>>
    @Query("UPDATE notifications SET read = 1 WHERE userId = :uid") suspend fun markNotificationsRead(uid: Long)
    @Query("SELECT COUNT(*) FROM notifications WHERE userId = :uid AND read = 0") fun unreadCount(uid: Long): Flow<Int>

    // ---------- shared lists ----------
    @Insert suspend fun insertList(l: SharedListEntity): Long
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertListItem(i: SharedListItemEntity)
    @Query("DELETE FROM shopping_list_items WHERE listId = :lid AND productId = :pid") suspend fun removeListItem(lid: Long, pid: Long)
    @Query("SELECT * FROM shopping_lists WHERE ownerId = :uid ORDER BY createdAt DESC") fun lists(uid: Long): Flow<List<SharedListEntity>>
    @Query("SELECT * FROM shopping_list_items WHERE listId = :lid") fun listItems(lid: Long): Flow<List<SharedListItemEntity>>
    @Query("DELETE FROM shopping_lists WHERE id = :id") suspend fun deleteList(id: Long)

    // ---------- audit ----------
    @Insert suspend fun audit(a: AuditLogEntity)
}
