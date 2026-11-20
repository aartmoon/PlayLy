package com.playly.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.playly.app.ui.PlayLyApp
import com.playly.app.ui.PlayLyViewModel

class MainActivity : ComponentActivity() {
    private val model by viewModels<PlayLyViewModel> {
        (application as PlayLyApplication).component.viewModelFactory()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { PlayLyApp(model) }
    }
}
