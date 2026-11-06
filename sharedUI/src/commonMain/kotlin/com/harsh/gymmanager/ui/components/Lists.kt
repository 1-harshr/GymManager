package com.harsh.gymmanager.ui.components

import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.text.KeyboardActions
import gymmanager.sharedui.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.harsh.gymmanager.ui.icons.MyGymIcons
import com.harsh.gymmanager.ui.theme.MyGymRadius
import com.harsh.gymmanager.ui.theme.MyGymSpace
import com.harsh.gymmanager.ui.theme.MyGymTheme

/** The pill search field at the top of Members. */
@Composable
fun SearchBar(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier, placeholder: String? = null) {
    val colors = MyGymTheme.colors
    val focusManager = LocalFocusManager.current
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        textStyle = MyGymTheme.typography.body.copy(color = colors.ink),
        cursorBrush = SolidColor(colors.ink),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        // Results filter as you type, so the search key just puts the keyboard away.
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        modifier = modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Row(
                Modifier.height(48.dp).background(colors.surface, MyGymRadius.pillShape).padding(start = MyGymSpace.lg, end = MyGymSpace.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MyGymSpace.sm),
            ) {
                Icon(MyGymIcons.Search, contentDescription = null, Modifier.size(20.dp), tint = colors.inkMuted)
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text(placeholder ?: stringResource(Res.string.search_hint), style = MyGymTheme.typography.body, color = colors.inkMuted)
                    inner()
                }
                if (query.isNotEmpty()) {
                    MyGymIconButton(
                        MyGymIcons.X, stringResource(Res.string.clear_search), onClick = { onQueryChange("") },
                        size = 40.dp, iconSize = 18.dp, container = colors.surface, content = colors.inkMuted,
                    )
                }
            }
        },
    )
}

/** One row of mutually exclusive filters that scrolls edge to edge. */
@Composable
fun <T> FilterChips(options: List<T>, selected: T, onSelect: (T) -> Unit, label: @Composable (T) -> String, modifier: Modifier = Modifier) {
    val colors = MyGymTheme.colors
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = MyGymSpace.gutter),
        horizontalArrangement = Arrangement.spacedBy(MyGymSpace.sm),
    ) {
        items(options) { option ->
            val on = option == selected
            Surface(
                onClick = { onSelect(option) },
                shape = MyGymRadius.pillShape,
                color = if (on) colors.ink else colors.surface,
                contentColor = if (on) colors.onInk else colors.ink,
            ) {
                Text(
                    label(option),
                    Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                    style = MyGymTheme.typography.caption.copy(fontWeight = MyGymTheme.typography.bodyStrong.fontWeight),
                )
            }
        }
    }
}

/** The capitalised label above a group of rows, with an optional link. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, action: String? = null, onAction: () -> Unit = {}) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text.uppercase(), Modifier.weight(1f), style = MyGymTheme.typography.label, color = MyGymTheme.colors.inkMuted)
        if (action != null) GhostButton(action, onAction)
    }
}

/** What a list shows when it has nothing yet: icon, heading, one sentence and the action that fills it. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    val colors = MyGymTheme.colors
    Column(
        modifier.fillMaxWidth().padding(horizontal = MyGymSpace.lg, vertical = MyGymSpace.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MyGymSpace.sm),
    ) {
        Box(Modifier.size(64.dp).background(colors.surfaceSunken, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, Modifier.size(28.dp), tint = colors.ink)
        }
        Text(title, style = MyGymTheme.typography.title, textAlign = TextAlign.Center)
        Text(body, style = MyGymTheme.typography.body, color = colors.inkMuted, textAlign = TextAlign.Center)
        if (actionLabel != null) {
            MyGymButton(actionLabel, onAction, Modifier.padding(top = MyGymSpace.sm))
        }
    }
}
