package com.harsh.gymmanager.ui.motion

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.IntOffset
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.RectangleShape
import androidx.navigation.NavBackStackEntry
import com.harsh.gymmanager.ui.theme.AkhadaElevation
import com.harsh.gymmanager.ui.theme.AkhadaTheme

/**
 * Navigation motion. Pushed screens slide in like a card over the one below, which drifts
 * back and dims; opening a member grows the page out of the list while their photo and
 * name travel into place.
 */
object AkhadaMotion {
    /** Starts quickly and settles gently, so movement feels thrown rather than timed. */
    val Settle = CubicBezierEasing(0.2f, 0.85f, 0.25f, 1f)

    const val PUSH_MS = 420
    const val OPEN_MS = 400
    const val TAB_MS = 320

    /** The splash stays at least this long from launch, then fades out, so it reads as a beat rather than a flicker. */
    const val SPLASH_HOLD_MS = 900L
    const val SPLASH_EXIT_MS = 350

    fun <T> push(): FiniteAnimationSpec<T> = tween(PUSH_MS, easing = Settle)
    /**
     * Going back slides on a critically damped spring: it eases in, then settles without the
     * lurch-then-crawl of [Settle], so the page reads as being pulled away rather than snapped off.
     */
    private const val BACK_STIFFNESS = 400f
    private val backSlide = spring(Spring.DampingRatioNoBouncy, BACK_STIFFNESS, IntOffset.VisibilityThreshold)
    private val backFade = spring(Spring.DampingRatioNoBouncy, BACK_STIFFNESS, 0.01f)
    fun <T> open(): FiniteAnimationSpec<T> = tween(OPEN_MS, easing = Settle)

    /** How far the screen underneath drifts, as a share of the width, and how much it dims. */
    private const val UNDERLAY_SHIFT = 4
    private const val UNDERLAY_ALPHA = 0.55f

    val pushEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        slideInHorizontally(push()) { it }
    }
    val pushExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        slideOutHorizontally(push()) { -it / UNDERLAY_SHIFT } + fadeOut(push(), targetAlpha = UNDERLAY_ALPHA)
    }
    val popEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        slideInHorizontally(backSlide) { -it / UNDERLAY_SHIFT } + fadeIn(backFade, initialAlpha = UNDERLAY_ALPHA)
    }
    val popExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        slideOutHorizontally(backSlide) { it }
    }

    /** A page that grows out of the list behind it. */
    val openEnter: EnterTransition =
        fadeIn(tween(OPEN_MS / 2, delayMillis = 40)) + scaleIn(open(), initialScale = 0.92f)
    val openExit: ExitTransition =
        fadeOut(tween(OPEN_MS / 2)) + scaleOut(open(), targetScale = 1.03f)
    val closeEnter: EnterTransition =
        fadeIn(tween(OPEN_MS / 2, delayMillis = 60)) + scaleIn(open(), initialScale = 1.03f)
    val closeExit: ExitTransition =
        fadeOut(tween(OPEN_MS / 2)) + scaleOut(open(), targetScale = 0.92f)
}

/** Set once around the NavHost; shared elements need it. */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

/** The current destination's enter/exit animation, so shared elements know when to fly. */
val LocalDestinationScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * Wraps every destination: an opaque page with an edge shadow, so a sliding screen reads as a
 * card moving over the one below instead of two screens overlapping.
 */
@Composable
fun AnimatedVisibilityScope.Destination(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalDestinationScope provides this) {
        Box(
            Modifier
                .fillMaxSize()
                .shadow(AkhadaElevation.float, RectangleShape, clip = false)
                .background(AkhadaTheme.colors.bg),
        ) { content() }
    }
}

private val SharedBounds = BoundsTransform { _, _ -> AkhadaMotion.open() }

/**
 * Marks something that should travel between screens, such as a member's photo and name moving
 * from a list row into the detail header. Both ends use the same [key]; it scales to fit while moving.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedElement(key: String): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    val destination = LocalDestinationScope.current ?: return this
    return with(shared) {
        this@sharedElement.sharedBounds(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = destination,
            boundsTransform = SharedBounds,
            resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(),
        )
    }
}

object SharedKeys {
    fun memberPhoto(id: Long) = "member-photo-$id"
    fun memberName(id: Long) = "member-name-$id"
}
