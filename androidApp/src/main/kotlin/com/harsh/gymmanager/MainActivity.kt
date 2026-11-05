package com.harsh.gymmanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Hold the splash until stored settings load, so the first frame is the real screen.
        var ready = false
        splash.setKeepOnScreenCondition { !ready }
        val container = (application as GymManagerApplication).container
        setContent {
            App(container, onReady = { ready = true })
        }
    }
}