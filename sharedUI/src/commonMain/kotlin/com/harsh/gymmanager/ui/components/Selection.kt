package com.harsh.gymmanager.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.harsh.gymmanager.ui.icons.AkhadaIcons
import com.harsh.gymmanager.ui.theme.AkhadaElevation
import com.harsh.gymmanager.ui.theme.AkhadaRadius
import com.harsh.gymmanager.ui.theme.AkhadaSize
import com.harsh.gymmanager.ui.theme.AkhadaSpace
import com.harsh.gymmanager.ui.theme.AkhadaTheme

/** The header while selecting: cancel, how many are ticked, and Select all. */
@Composable
fun SelectionHeader(count: Int, onCancel: () -> Unit, onSelectAll: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = AkhadaSpace.gutter, vertical = AkhadaSpace.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AkhadaSpace.md),
    ) {
        AkhadaIconButton(AkhadaIcons.X, "Cancel selection", onCancel)
        Column(Modifier.weight(1f)) {
            Text("$count selected", style = AkhadaTheme.typography.screenTitle)
            Text("Long-press or tap to change", style = AkhadaTheme.typography.caption, color = AkhadaTheme.colors.inkMuted)
        }
        GhostButton("Select all", onSelectAll)
    }
}

data class BulkAction(val label: String, val icon: ImageVector, val destructive: Boolean = false, val onClick: () -> Unit)

/** Floats where the tab bar sits while selecting, with the actions for the ticked items. */
@Composable
fun BulkActionBar(actions: List<BulkAction>, modifier: Modifier = Modifier) {
    val colors = AkhadaTheme.colors
    Surface(modifier = modifier, shape = AkhadaRadius.pillShape, color = colors.surface, shadowElevation = AkhadaElevation.float) {
        Row(Modifier.padding(AkhadaSize.tabBarPadding)) {
            actions.forEach { action ->
                val tint = if (action.destructive) colors.statusDue else colors.ink
                Surface(
                    onClick = action.onClick,
                    shape = AkhadaRadius.pillShape,
                    color = colors.surface,
                    contentColor = tint,
                    modifier = Modifier.width(AkhadaSize.tabItemWidth + 8.dp).height(AkhadaSize.tabBarHeight - AkhadaSize.tabBarPadding * 2),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Icon(action.icon, contentDescription = null, Modifier.size(AkhadaSize.tabIcon))
                        Text(action.label, style = AkhadaTheme.typography.tab, maxLines = 1)
                    }
                }
            }
        }
    }
}
