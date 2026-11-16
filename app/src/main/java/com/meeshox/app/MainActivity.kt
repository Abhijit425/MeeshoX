package com.meeshox.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.meeshox.app.ui.MeeshoXRoot
import com.meeshox.app.ui.ShopViewModel

class MainActivity : ComponentActivity() {
    private val vm: ShopViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { MeeshoXRoot(vm) }
    }
}
