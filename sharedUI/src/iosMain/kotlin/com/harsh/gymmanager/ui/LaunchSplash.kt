package com.harsh.gymmanager.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.harsh.gymmanager.ui.icons.MyGymIcons
import com.harsh.gymmanager.ui.motion.MyGymMotion
import kotlinx.coroutines.delay

/**
 * Draws the same picture as the native launch screen (LaunchBackground and LaunchIcon in the asset
 * catalog), holds it for [MyGymMotion.SPLASH_HOLD_MS] and until the app is ready, then fades it away.
 * It follows the system appearance, as the launch screen does, rather than the app's theme setting.
 * [onGone] runs once it has faded out.
 */
@Composable
fun LaunchSplash(appReady: Boolean, onGone: () -> Unit = {}) {
    var held by remember { mutableStateOf(false) }
    var gone by remember { mutableStateOf(false) }
    val exit = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(MyGymMotion.SPLASH_HOLD_MS)
        held = true
    }
    LaunchedEffect(held, appReady) {
        if (held && appReady) {
            exit.animateTo(1f, tween(MyGymMotion.SPLASH_EXIT_MS, easing = MyGymMotion.Settle))
            gone = true
            onGone()
        }
    }
    if (gone) return

    val background = if (isSystemInDarkTheme()) Color(0xFF0F0F10) else Color(0xFFF3F3F0)
    Box(
        Modifier.fillMaxSize().graphicsLayer { alpha = 1f - exit.value }.background(background),
        contentAlignment = Alignment.Center,
    ) {
        // 120pt tile with a 600/1024 glyph, matching LaunchIcon.imageset.
        Box(
            Modifier
                .graphicsLayer { scaleX = 1f + 0.08f * exit.value; scaleY = scaleX }
                .size(120.dp)
                .background(Color(0xFFB0441B), RoundedCornerShape(27.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(MyGymIcons.Dumbbell, contentDescription = null, tint = Color.White, modifier = Modifier.size(70.dp))
        }
    }
}
