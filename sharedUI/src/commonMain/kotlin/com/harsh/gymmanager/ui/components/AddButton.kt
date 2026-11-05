package com.harsh.gymmanager.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.harsh.gymmanager.ui.icons.AkhadaIcons
import com.harsh.gymmanager.ui.theme.AkhadaElevation
import com.harsh.gymmanager.ui.theme.AkhadaRadius
import com.harsh.gymmanager.ui.theme.AkhadaSize
import com.harsh.gymmanager.ui.theme.AkhadaTheme

/** The round accent button that floats beside the tab bar on Members. */
@Composable
fun AddButton(onClick: () -> Unit, contentDescription: String, modifier: Modifier = Modifier) {
    val colors = AkhadaTheme.colors
    Surface(
        onClick = onClick,
        modifier = modifier.size(AkhadaSize.addButton),
        shape = AkhadaRadius.pillShape,
        color = colors.accent,
        contentColor = colors.onAccent,
        shadowElevation = AkhadaElevation.float,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(AkhadaIcons.Plus, contentDescription, Modifier.size(28.dp))
        }
    }
}
