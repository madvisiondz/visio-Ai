package com.oasismall.oasisai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.oasismall.oasisai.ui.OasisViewModelFactory
import com.oasismall.oasisai.ui.navigation.OasisNavHost
import com.oasismall.oasisai.ui.theme.OasisAITheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val factory = OasisViewModelFactory(applicationContext as OasisApp)
        setContent {
            OasisAITheme {
                OasisNavHost(factory = factory)
            }
        }
    }
}
