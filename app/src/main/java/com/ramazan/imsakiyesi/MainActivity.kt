package com.ramazan.imsakiyesi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ramazan.imsakiyesi.ui.theme.RamazanImsakiyesiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RamazanImsakiyesiTheme {
                RamadanApp()
            }
        }
    }
}
