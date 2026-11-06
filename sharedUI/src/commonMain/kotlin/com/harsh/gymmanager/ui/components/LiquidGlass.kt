package com.harsh.gymmanager.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * True where the phone draws Liquid Glass (iOS 26 and later). There the controls that float over
 * content (tab bar, header buttons, the add button, the select-mode bar and confirm prompts) are
 * the system's own views, since Compose can't draw the glass. Everywhere else the app draws them.
 */
expect val usesLiquidGlass: Boolean

/** True inside screen headers, whose buttons belong to the floating layer that turns to glass. */
val LocalInHeader = compositionLocalOf { false }

/** The system tab bar along the bottom edge. Only called where [usesLiquidGlass]. */
@Composable
expect fun SystemTabBar(tabs: List<TabItem>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier)

/** The height [SystemTabBar] draws its bar at, without the home indicator area below it. */
expect val systemTabBarHeight: Dp

/**
 * iOS 26 draws the tab bar as a 60pt capsule whose middle sits 10pt above the middle of the bar's
 * frame (measured on an iPhone 17 Pro). Controls beside it or in its place use these to line up.
 */
val GlassCapsuleHeight = 60.dp
val GlassCapsuleLift = 10.dp

/** How far the capsule's bottom edge sits above the bottom of the screen. */
val glassCapsuleBottom: Dp get() = systemTabBarHeight / 2 + GlassCapsuleLift - GlassCapsuleHeight / 2

/**
 * A round system glass button. [prominent] fills it with the accent colour; [badge] puts a small
 * count on its corner; a [menu] opens the system menu on tap instead of calling [onClick].
 * Only called where [usesLiquidGlass].
 */
@Composable
expect fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    size: Dp,
    modifier: Modifier = Modifier,
    prominent: Boolean = false,
    badge: String? = null,
    menu: List<MenuAction>? = null,
)

/** The select-mode actions as one glass capsule. Only called where [usesLiquidGlass]. */
@Composable
expect fun GlassActionBar(actions: List<BulkAction>, modifier: Modifier = Modifier)

/** Asks before something that can't be undone, as the system's action sheet. Only called where [usesLiquidGlass]. */
@Composable
expect fun SystemConfirmPrompt(
    title: String,
    body: String,
    confirmLabel: String,
    cancelLabel: String,
    destructive: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
)
