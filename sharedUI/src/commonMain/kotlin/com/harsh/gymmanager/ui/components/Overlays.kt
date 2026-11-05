package com.harsh.gymmanager.ui.components

import gymmanager.sharedui.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.harsh.gymmanager.ui.icons.AkhadaIcons
import com.harsh.gymmanager.ui.theme.AkhadaElevation
import com.harsh.gymmanager.ui.theme.AkhadaRadius
import com.harsh.gymmanager.ui.theme.AkhadaSpace
import com.harsh.gymmanager.ui.theme.AkhadaTheme

/** A sheet that rises from the bottom for quick tasks; swipe down or tap outside to dismiss. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AkhadaBottomSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val colors = AkhadaTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = AkhadaRadius.sheetShape,
        containerColor = colors.surface,
        contentColor = colors.ink,
        scrimColor = colors.scrim,
        dragHandle = {
            Box(Modifier.padding(top = AkhadaSpace.md).size(36.dp, 4.dp).background(colors.line, AkhadaRadius.pillShape))
        },
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = AkhadaSpace.gutter).padding(top = AkhadaSpace.lg, bottom = AkhadaSpace.xl).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(AkhadaSpace.lg),
            content = content,
        )
    }
}

/** Asks before something that can't be undone. */
@Composable
fun ConfirmSheet(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = true,
) {
    AkhadaBottomSheet(onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(AkhadaSpace.xs)) {
            Text(title, style = AkhadaTheme.typography.title)
            Text(body, style = AkhadaTheme.typography.body, color = AkhadaTheme.colors.inkMuted)
        }
        AkhadaButton(
            confirmLabel,
            onClick = onConfirm,
            modifier = Modifier.fillMaxWidth(),
            kind = if (destructive) ButtonKind.Danger else ButtonKind.Primary,
        )
        AkhadaButton(stringResource(Res.string.cancel), onClick = onDismiss, modifier = Modifier.fillMaxWidth(), kind = ButtonKind.Secondary)
    }
}

data class MenuAction(val label: String, val destructive: Boolean = false, val onClick: () -> Unit)

/** A round "more" button that opens a short menu of actions. */
@Composable
fun MoreMenu(actions: List<MenuAction>, contentDescription: String, modifier: Modifier = Modifier, container: androidx.compose.ui.graphics.Color = AkhadaTheme.colors.surfaceSunken) {
    val colors = AkhadaTheme.colors
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        AkhadaIconButton(AkhadaIcons.MoreVertical, contentDescription, onClick = { open = true }, iconSize = 20.dp, container = container)
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            shape = AkhadaRadius.fieldShape,
            containerColor = colors.surface,
            shadowElevation = AkhadaElevation.float,
        ) {
            actions.forEach { action ->
                DropdownMenuItem(
                    text = {
                        Text(action.label, style = AkhadaTheme.typography.body, color = if (action.destructive) colors.statusDue else colors.ink)
                    },
                    onClick = { open = false; action.onClick() },
                )
            }
        }
    }
}

/** Snackbars float above the tab bar in `ink`, one past-tense sentence each. */
@Composable
fun AkhadaSnackbarHost(state: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(state, modifier) { data: SnackbarData ->
        val colors = AkhadaTheme.colors
        Snackbar(
            modifier = Modifier.padding(horizontal = AkhadaSpace.gutter),
            shape = AkhadaRadius.fieldShape,
            containerColor = colors.ink,
            contentColor = colors.onInk,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(data.visuals.message, style = AkhadaTheme.typography.body)
            }
        }
    }
}
