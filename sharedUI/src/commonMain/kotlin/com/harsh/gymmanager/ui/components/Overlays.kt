package com.harsh.gymmanager.ui.components

import com.harsh.gymmanager.ui.platform.SystemBackHandler
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
import com.harsh.gymmanager.ui.icons.MyGymIcons
import com.harsh.gymmanager.ui.theme.MyGymSize
import com.harsh.gymmanager.ui.theme.MyGymElevation
import com.harsh.gymmanager.ui.theme.MyGymRadius
import com.harsh.gymmanager.ui.theme.MyGymSpace
import com.harsh.gymmanager.ui.theme.MyGymTheme

/** A sheet that rises from the bottom for quick tasks; swipe down or tap outside to dismiss. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyGymBottomSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val colors = MyGymTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = MyGymRadius.sheetShape,
        containerColor = colors.surface,
        contentColor = colors.ink,
        scrimColor = colors.scrim,
        dragHandle = {
            Box(Modifier.padding(top = MyGymSpace.md).size(36.dp, 4.dp).background(colors.line, MyGymRadius.pillShape))
        },
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = MyGymSpace.gutter).padding(top = MyGymSpace.lg, bottom = MyGymSpace.xl).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(MyGymSpace.lg),
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
    if (usesLiquidGlass) {
        SystemConfirmPrompt(title, body, confirmLabel, stringResource(Res.string.cancel), destructive, onConfirm, onDismiss)
        return
    }
    MyGymBottomSheet(onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(MyGymSpace.xs)) {
            Text(title, style = MyGymTheme.typography.title)
            Text(body, style = MyGymTheme.typography.body, color = MyGymTheme.colors.inkMuted)
        }
        MyGymButton(
            confirmLabel,
            onClick = onConfirm,
            modifier = Modifier.fillMaxWidth(),
            kind = if (destructive) ButtonKind.Danger else ButtonKind.Primary,
        )
        MyGymButton(stringResource(Res.string.cancel), onClick = onDismiss, modifier = Modifier.fillMaxWidth(), kind = ButtonKind.Secondary)
    }
}

data class MenuAction(val label: String, val destructive: Boolean = false, val onClick: () -> Unit)

/** A round "more" button that opens a short menu of actions; in a header on iOS 26, a glass button with the system menu. */
@Composable
fun MoreMenu(actions: List<MenuAction>, contentDescription: String, modifier: Modifier = Modifier, container: androidx.compose.ui.graphics.Color = MyGymTheme.colors.surfaceSunken) {
    val colors = MyGymTheme.colors
    if (usesLiquidGlass && LocalInHeader.current) {
        GlassIconButton(MyGymIcons.MoreVertical, contentDescription, onClick = {}, MyGymSize.iconButton, modifier, menu = actions)
        return
    }
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        MyGymIconButton(MyGymIcons.MoreVertical, contentDescription, onClick = { open = true }, iconSize = 20.dp, container = container)
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            shape = MyGymRadius.fieldShape,
            containerColor = colors.surface,
            shadowElevation = MyGymElevation.float,
        ) {
            actions.forEach { action ->
                DropdownMenuItem(
                    text = {
                        Text(action.label, style = MyGymTheme.typography.body, color = if (action.destructive) colors.statusDue else colors.ink)
                    },
                    onClick = { open = false; action.onClick() },
                )
            }
        }
    }
}

/** Snackbars float above the tab bar in `ink`, one past-tense sentence each. */
@Composable
fun MyGymSnackbarHost(state: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(state, modifier) { data: SnackbarData ->
        val colors = MyGymTheme.colors
        Snackbar(
            modifier = Modifier.padding(horizontal = MyGymSpace.gutter),
            shape = MyGymRadius.fieldShape,
            containerColor = colors.ink,
            contentColor = colors.onInk,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(data.visuals.message, style = MyGymTheme.typography.body)
            }
        }
    }
}

/**
 * Back for a form: leaves straight away, or first asks whether to discard when [hasChanges]. Also
 * catches the system back gesture. Returns what the screen's own back button should call.
 */
@Composable
fun rememberDiscardGuard(hasChanges: Boolean, onBack: () -> Unit): () -> Unit {
    var asking by remember { mutableStateOf(false) }
    SystemBackHandler(enabled = hasChanges) { asking = true }
    if (asking) {
        ConfirmSheet(
            title = stringResource(Res.string.discard_title),
            body = stringResource(Res.string.discard_body),
            confirmLabel = stringResource(Res.string.discard_confirm),
            onConfirm = {
                asking = false
                onBack()
            },
            onDismiss = { asking = false },
        )
    }
    return { if (hasChanges) asking = true else onBack() }
}
