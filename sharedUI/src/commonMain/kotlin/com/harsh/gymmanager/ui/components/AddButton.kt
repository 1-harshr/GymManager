package com.harsh.gymmanager.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.harsh.gymmanager.ui.icons.MyGymIcons
import com.harsh.gymmanager.ui.theme.MyGymElevation
import com.harsh.gymmanager.ui.theme.MyGymRadius
import com.harsh.gymmanager.ui.theme.MyGymSize
import com.harsh.gymmanager.ui.theme.MyGymTheme

/** The round accent button that floats beside the tab bar on Members; accent glass on iOS 26. */
@Composable
fun AddButton(onClick: () -> Unit, contentDescription: String, modifier: Modifier = Modifier, size: Dp = MyGymSize.addButton) {
    if (usesLiquidGlass) {
        GlassIconButton(MyGymIcons.Plus, contentDescription, onClick, size, modifier, prominent = true)
        return
    }
    val colors = MyGymTheme.colors
    Surface(
        onClick = onClick,
        modifier = modifier.size(size),
        shape = MyGymRadius.pillShape,
        color = colors.accent,
        contentColor = colors.onAccent,
        shadowElevation = MyGymElevation.float,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(MyGymIcons.Plus, contentDescription, Modifier.size(28.dp))
        }
    }
}
