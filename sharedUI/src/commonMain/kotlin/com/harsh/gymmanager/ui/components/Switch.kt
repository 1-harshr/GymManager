package com.harsh.gymmanager.ui.components

import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.harsh.gymmanager.ui.theme.AkhadaTheme

/** The app's on/off switch: ink when on, sunken when off. */
@Composable
fun AkhadaSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val colors = AkhadaTheme.colors
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        colors = SwitchDefaults.colors(
            checkedThumbColor = colors.onInk, checkedTrackColor = colors.ink,
            uncheckedThumbColor = colors.inkMuted, uncheckedTrackColor = colors.surfaceSunken, uncheckedBorderColor = colors.surfaceSunken,
        ),
    )
}
