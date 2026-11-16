package com.meeshox.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        UserEntity::class, AddressEntity::class, SellerEntity::class, CategoryEntity::class,
        ProductEntity::class, ReviewEntity::class, PriceHistoryEntity::class, CartItemEntity::class,
        WishlistEntity::class, CouponEntity::class, OrderEntity::class, OrderItemEntity::class,
        SearchHistoryEntity::class, RecentlyViewedEntity::class, SupportTicketEntity::class,
        ChatMessageEntity::class, NotificationEntity::class, SharedListEntity::class,
        SharedListItemEntity::class, AuditLogEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): ShopDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "meeshox.db").build()
    }
}
