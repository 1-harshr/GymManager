package com.harsh.gymmanager.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.harsh.gymmanager.ui.icons.MyGymIcons
import com.harsh.gymmanager.ui.motion.MyGymMotion
import com.harsh.gymmanager.ui.theme.BrandClay
import com.harsh.gymmanager.ui.theme.MyGymSpace
import com.harsh.gymmanager.ui.theme.MyGymTheme
import com.harsh.gymmanager.ui.theme.screenGutter
import gymmanager.sharedui.generated.resources.Res
import gymmanager.sharedui.generated.resources.load_failed_body
import gymmanager.sharedui.generated.resources.load_failed_title
import gymmanager.sharedui.generated.resources.loading
import gymmanager.sharedui.generated.resources.try_again
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

/** A load quicker than this never shows the mark, so fast screens don't flash it. */
private const val LOADING_DELAY_MS = 250L

/** One breath, in or out. */
private const val BREATH_MS = 850

/**
 * Stands in for a screen's content while it's on its way: the launcher icon's clay tile and
 * dumbbell, breathing gently. Give it the space the content would take; it centres itself there.
 */
@Composable
fun LoadingMark(modifier: Modifier = Modifier) {
    val trouble = LocalLoadTrouble.current
    if (trouble.unreachable) {
        LoadFailed(trouble.retry, modifier)
        return
    }
    val label = stringResource(Res.string.loading)
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(LOADING_DELAY_MS)
        appear.animateTo(1f, tween(300, easing = MyGymMotion.Settle))
    }
    val breath by rememberInfiniteTransition(label = "loading").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(BREATH_MS, easing = MyGymMotion.Settle), RepeatMode.Reverse),
        label = "breath",
    )
    Box(modifier.fillMaxWidth().semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .graphicsLayer {
                    alpha = appear.value * (0.55f + 0.45f * breath)
                    scaleX = 0.9f + 0.1f * breath
                    scaleY = scaleX
                }
                .size(56.dp)
                .background(BrandClay, RoundedCornerShape(13.dp)),
            contentAlignment = Alignment.Center,
        ) {
            BrandGlyph()
        }
    }
}

/**
 * Whether the gym online can't be read right now, and how to try again, so a screen still waiting
 * for its records says so rather than loading forever.
 */
class LoadTrouble(val unreachable: Boolean, val retry: () -> Unit)

val LocalLoadTrouble = compositionLocalOf { LoadTrouble(unreachable = false, retry = {}) }

/** The mark at rest, with what went wrong and a way to try again. */
@Composable
private fun LoadFailed(onRetry: () -> Unit, modifier: Modifier) {
    val colors = MyGymTheme.colors
    Column(
        modifier.fillMaxWidth().screenGutter(),
        verticalArrangement = Arrangement.spacedBy(MyGymSpace.sm, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.alpha(0.55f).size(56.dp).background(BrandClay, RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
            BrandGlyph()
        }
        Text(stringResource(Res.string.load_failed_title), Modifier.padding(top = MyGymSpace.sm), style = MyGymTheme.typography.bodyStrong, textAlign = TextAlign.Center)
        Text(stringResource(Res.string.load_failed_body), style = MyGymTheme.typography.caption, color = colors.inkMuted, textAlign = TextAlign.Center)
        MyGymButton(stringResource(Res.string.try_again), onRetry, Modifier.padding(top = MyGymSpace.sm), kind = ButtonKind.Secondary)
    }
}

@Composable
private fun BrandGlyph() {
    Icon(MyGymIcons.Dumbbell, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
}
