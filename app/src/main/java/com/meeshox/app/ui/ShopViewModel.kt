package com.meeshox.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.meeshox.app.MeeshoXApp
import com.meeshox.app.ai.Recommender
import com.meeshox.app.ai.UserSignals
import com.meeshox.app.data.CategoryEntity
import com.meeshox.app.data.Outcome
import com.meeshox.app.data.ProductEntity
import com.meeshox.app.data.UserEntity
import com.meeshox.app.data.UserPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class ShopViewModel(app: Application) : AndroidViewModel(app) {
    private val shopApp = app as MeeshoXApp
    val repo = shopApp.repository
    val dao = repo.dao

    val seeded: StateFlow<Boolean> = shopApp.seeded
    val prefs: StateFlow<UserPrefs?> = repo.prefs.flow.map<UserPrefs, UserPrefs?> { it }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val uid: StateFlow<Long> = repo.prefs.flow.map { it.userId }.stateIn(viewModelScope, SharingStarted.Eagerly, -1L)

    val user: StateFlow<UserEntity?> = uid.flatMapLatest { if (it > 0) dao.userFlow(it) else flowOf(null) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val categories: StateFlow<List<CategoryEntity>> = dao.categories().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val cartCount: StateFlow<Int> = uid.flatMapLatest { dao.cartCount(it) }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val wishlist: StateFlow<List<Long>> = uid.flatMapLatest { dao.wishlistIds(it) }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val recentlyViewed: StateFlow<List<Long>> = uid.flatMapLatest { dao.recentlyViewed(it) }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val unread: StateFlow<Int> = uid.flatMapLatest { dao.unreadCount(it) }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    private val _catalogue = MutableStateFlow<List<ProductEntity>>(emptyList())
    val catalogue: StateFlow<List<ProductEntity>> = _catalogue

    private val _recommended = MutableStateFlow<List<ProductEntity>>(emptyList())
    val recommended: StateFlow<List<ProductEntity>> = _recommended

    val compare = MutableStateFlow<List<Long>>(emptyList())
    var pendingCoupon: String? = null

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages = _messages.asSharedFlow()

    init {
        viewModelScope.launch {
            seeded.filter { it }.first()
            refreshCatalogue()
        }
    }

    fun toast(msg: String) { _messages.tryEmit(msg) }

    fun io(block: suspend CoroutineScope.() -> Unit) = viewModelScope.launch(Dispatchers.IO, block = block)

    fun refreshCatalogue() = io {
        _catalogue.value = dao.allProducts()
        refreshRecommendations()
    }

    fun refreshRecommendations() = io {
        val u = uid.value
        val p = repo.prefs.current()
        val signals = if (u > 0 && p.personalization) UserSignals(
            viewed = dao.recentlyViewedOnce(u), wishlist = dao.wishlistIdsOnce(u),
            purchased = dao.allOrderItemsForUser(u).map { it.productId }, searches = dao.searchHistory(u).map { it.query },
            favouriteBrands = p.favouriteBrands.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        ) else null
        _recommended.value = Recommender.recommend(_catalogue.value.ifEmpty { dao.allProducts() }, signals)
    }

    fun product(id: Long): ProductEntity? = _catalogue.value.firstOrNull { it.id == id }

    fun toggleWishlist(pid: Long) = io {
        val inList = pid in wishlist.value
        repo.toggleWishlist(uid.value, pid, inList)
        toast(if (inList) "Removed from wishlist" else "Added to wishlist")
    }

    fun addToCart(pid: Long, size: String, color: String, qty: Int = 1, onDone: () -> Unit = {}) = io {
        when (val r = repo.addToCart(uid.value, pid, qty, size, color)) {
            is Outcome.Ok -> { toast("Added to cart"); launch(Dispatchers.Main) { onDone() } }
            is Outcome.Err -> toast(r.message)
        }
    }

    fun toggleCompare(pid: Long) {
        val cur = compare.value
        compare.value = when {
            pid in cur -> cur - pid
            cur.size >= 5 -> { toast("You can compare up to 5 products"); cur }
            else -> { toast("Added to compare (${cur.size + 1}/5)"); cur + pid }
        }
    }

    fun <T> handle(r: Outcome<T>, success: (T) -> Unit) {
        when (r) {
            is Outcome.Ok -> viewModelScope.launch(Dispatchers.Main) { success(r.value) }
            is Outcome.Err -> toast(r.message)
        }
    }
}
