package com.harsh.gymmanager

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.harsh.gymmanager.ui.motion.AkhadaMotion

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Hold the splash for a short beat and until stored settings load, so the first frame is the real screen.
        val shownAt = SystemClock.uptimeMillis()
        var ready = false
        splash.setKeepOnScreenCondition { !ready || SystemClock.uptimeMillis() - shownAt < AkhadaMotion.SPLASH_HOLD_MS }
        splash.setOnExitAnimationListener { provider ->
            val exit = AkhadaMotion.SPLASH_EXIT_MS.toLong()
            provider.iconView.animate().scaleX(1.08f).scaleY(1.08f).setDuration(exit).start()
            provider.view.animate().alpha(0f).setDuration(exit).withEndAction { provider.remove() }.start()
        }
        val app = application as GymManagerApplication
        setContent {
            GymManagerApp(app.container, app.demoMode, onReady = { ready = true })
        }
    }
}