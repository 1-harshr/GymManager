package com.harsh.gymmanager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.harsh.gymmanager.ui.theme.MyGymRadius
import com.harsh.gymmanager.ui.theme.MyGymSize
import com.harsh.gymmanager.ui.theme.MyGymSpace
import com.harsh.gymmanager.ui.theme.MyGymTheme

enum class ButtonKind { Primary, Secondary, Danger }

/** The pill button: one Primary per screen, Secondary for the alternative, Danger for deletes. */
@Composable
fun MyGymButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.Primary,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val colors = MyGymTheme.colors
    val (container, content) = when (kind) {
        ButtonKind.Primary -> colors.accent to colors.onAccent
        ButtonKind.Secondary -> colors.surfaceSunken to colors.ink
        ButtonKind.Danger -> colors.statusDueSoft to colors.statusDue
    }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .height(52.dp)
            .graphicsLayer { val s = if (pressed) 0.98f else 1f; scaleX = s; scaleY = s }
            .alpha(if (enabled) 1f else 0.4f),
        shape = MyGymRadius.pillShape,
        color = container,
        contentColor = content,
        interactionSource = interaction,
    ) {
        Row(
            Modifier.padding(horizontal = MyGymSpace.xl),
            horizontalArrangement = Arrangement.spacedBy(MyGymSpace.sm, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) Icon(icon, contentDescription = null, Modifier.size(20.dp))
            Text(text, style = MyGymTheme.typography.bodyStrong, maxLines = 1)
        }
    }
}

/** A low-weight underlined text action inside cards: "See all", "Record payment". */
@Composable
fun GhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = MyGymTheme.colors.ink) {
    Surface(onClick = onClick, modifier = modifier, shape = MyGymRadius.pillShape, color = Color.Transparent, contentColor = color) {
        Text(
            text,
            Modifier.padding(horizontal = MyGymSpace.sm, vertical = MyGymSpace.sm),
            style = MyGymTheme.typography.bodyStrong.copy(textDecoration = TextDecoration.Underline),
        )
    }
}

/**
 * A round button holding one icon; every use needs a [contentDescription] naming the action.
 * [badge] puts a small count on its corner. In a header on iOS 26 it is a system glass button.
 */
@Composable
fun MyGymIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = MyGymSize.iconButton,
    iconSize: Dp = 22.dp,
    container: Color = MyGymTheme.colors.surfaceSunken,
    content: Color = MyGymTheme.colors.ink,
    badge: String? = null,
) {
    if (usesLiquidGlass && LocalInHeader.current) {
        GlassIconButton(icon, contentDescription, onClick, size, modifier, badge = badge)
        return
    }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(modifier) {
        Surface(
            onClick = onClick,
            modifier = Modifier.size(size).graphicsLayer { val s = if (pressed) 0.94f else 1f; scaleX = s; scaleY = s },
            shape = CircleShape,
            color = container,
            contentColor = content,
            interactionSource = interaction,
        ) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription, Modifier.size(iconSize)) }
        }
        if (badge != null) {
            Text(
                badge,
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 4.dp, y = (-2).dp)
                    .defaultMinSize(minWidth = 18.dp)
                    .background(MyGymTheme.colors.accent, MyGymRadius.pillShape)
                    .padding(horizontal = 5.dp),
                style = MyGymTheme.typography.tab,
                color = MyGymTheme.colors.onAccent,
                textAlign = TextAlign.Center,
            )
        }
    }
}
