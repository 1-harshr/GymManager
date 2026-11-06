package com.harsh.gymmanager.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Android has no glass to match, so the app draws all of its own controls. */
actual val usesLiquidGlass: Boolean = false

@Composable
actual fun SystemTabBar(tabs: List<TabItem>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier) = Unit

actual val systemTabBarHeight: Dp = 0.dp

@Composable
actual fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    size: Dp,
    modifier: Modifier,
    prominent: Boolean,
    badge: String?,
    menu: List<MenuAction>?,
) = Unit

@Composable
actual fun GlassActionBar(actions: List<BulkAction>, modifier: Modifier) = Unit

@Composable
actual fun SystemConfirmPrompt(
    title: String,
    body: String,
    confirmLabel: String,
    cancelLabel: String,
    destructive: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) = Unit
