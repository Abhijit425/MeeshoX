package com.meeshox.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.meeshox.app.ui.MeeshoXRoot
import com.meeshox.app.ui.ShopViewModel
import com.meeshox.app.payments.RazorpayBridge
import com.razorpay.PaymentData
import com.razorpay.PaymentResultWithDataListener

class MainActivity : ComponentActivity(), PaymentResultWithDataListener {
    private val vm: ShopViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        RazorpayBridge.attach(this)
        setContent { MeeshoXRoot(vm) }
    }
    override fun onResume() { super.onResume(); RazorpayBridge.attach(this) }
    override fun onDestroy() { RazorpayBridge.detach(this); super.onDestroy() }
    override fun onPaymentSuccess(razorpayPaymentId: String?, paymentData: PaymentData?) = RazorpayBridge.onSuccess(razorpayPaymentId, paymentData)
    override fun onPaymentError(code: Int, response: String?, paymentData: PaymentData?) = RazorpayBridge.onError(code, response)
}
