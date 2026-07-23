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
        // Recover if a focus/lifecycle blip paused playback while returning to the app
        viewModel.reassertPlaybackIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        viewModel.onAppForegrounded()
        // Second chance after the activity is fully interactive
        viewModel.reassertPlaybackIfNeeded()
    }
}
