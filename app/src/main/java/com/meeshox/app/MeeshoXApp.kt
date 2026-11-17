package com.meeshox.app

import android.app.Application
import com.meeshox.app.data.AppDatabase
import com.meeshox.app.data.DemoSeeder
import com.meeshox.app.data.Prefs
import com.meeshox.app.data.ShopRepository
import com.meeshox.app.domain.SandboxPaymentProvider
import com.meeshox.app.payments.GatewayConfig
import com.meeshox.app.payments.RazorpayProvider
import com.razorpay.Checkout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** Manual dependency container (keeps the build simple; swap for Hilt modules later). */
class MeeshoXApp : Application() {
    lateinit var repository: ShopRepository
        private set
    val seeded = MutableStateFlow(false)
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        val db = AppDatabase.build(this)
        val prefs = Prefs(this)
        val razorpay = RazorpayProvider {
            val p = prefs.current()
            GatewayConfig(p.razorpayKeyId.ifBlank { BuildConfig.RAZORPAY_KEY_ID }, p.paymentServerUrl.ifBlank { BuildConfig.PAYMENT_SERVER_URL })
        }
        repository = ShopRepository(db.dao(), prefs, SandboxPaymentProvider(), razorpay)
        try { Checkout.preload(applicationContext) } catch (e: Exception) { }
        appScope.launch {
            if (BuildConfig.DEMO_SEED) DemoSeeder.seed(db.dao())
            seeded.value = true
        }
    }
}
