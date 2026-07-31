package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    private val viewModel: AudioViewModel by viewModels {
        val context = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            applicationContext.createAttributionContext("audio_player")
        } else {
            applicationContext
        }
        AudioViewModelFactory(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme(dynamicColor = false) {
                GlassPlayerApp(viewModel = viewModel)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Single reassert when returning — recovers OEM/focus blips without triple-firing
        viewModel.reassertPlaybackIfNeeded()
    }

    override fun onStop() {
        // Flush position / pause state before process may be killed
        viewModel.persistPlaybackSession()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        viewModel.onAppForegrounded()
    }
}
