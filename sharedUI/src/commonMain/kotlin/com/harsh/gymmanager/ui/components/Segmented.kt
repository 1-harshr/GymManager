package com.harsh.gymmanager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.harsh.gymmanager.ui.theme.AkhadaRadius
import com.harsh.gymmanager.ui.theme.AkhadaTheme

/**
 * Two or three mutually exclusive options in one pill. With [allowNone], tapping the
 * selected option clears it (used for the optional gender field).
 */
@Composable
fun <T> SegmentedControl(
    options: List<T>,
    selected: T?,
    onSelect: (T?) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    allowNone: Boolean = false,
) {
    val colors = AkhadaTheme.colors
    Row(
        modifier.fillMaxWidth().background(colors.surfaceSunken, AkhadaRadius.pillShape).padding(4.dp).selectableGroup(),
    ) {
        options.forEach { option ->
            val on = option == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(40.dp)
                    .then(if (on) Modifier.shadow(1.dp, AkhadaRadius.pillShape) else Modifier)
                    .clip(AkhadaRadius.pillShape)
                    .background(if (on) colors.surface else colors.surfaceSunken)
                    .selectable(selected = on, role = Role.RadioButton) {
                        onSelect(if (on && allowNone) null else option)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(label(option), style = AkhadaTheme.typography.bodyStrong, color = if (on) colors.ink else colors.inkMuted)
            }
        }
    }
}
