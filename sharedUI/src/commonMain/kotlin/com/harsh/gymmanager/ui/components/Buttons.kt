package com.harsh.gymmanager.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.harsh.gymmanager.ui.theme.AkhadaRadius
import com.harsh.gymmanager.ui.theme.AkhadaSize
import com.harsh.gymmanager.ui.theme.AkhadaSpace
import com.harsh.gymmanager.ui.theme.AkhadaTheme

enum class ButtonKind { Primary, Secondary, Danger }

/** The pill button: one Primary per screen, Secondary for the alternative, Danger for deletes. */
@Composable
fun AkhadaButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.Primary,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val colors = AkhadaTheme.colors
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
        shape = AkhadaRadius.pillShape,
        color = container,
        contentColor = content,
        interactionSource = interaction,
    ) {
        Row(
            Modifier.padding(horizontal = AkhadaSpace.xl),
            horizontalArrangement = Arrangement.spacedBy(AkhadaSpace.sm, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) Icon(icon, contentDescription = null, Modifier.size(20.dp))
            Text(text, style = AkhadaTheme.typography.bodyStrong, maxLines = 1)
        }
    }
}

/** A low-weight underlined text action inside cards: "See all", "Record payment". */
@Composable
fun GhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = AkhadaTheme.colors.ink) {
    Surface(onClick = onClick, modifier = modifier, shape = AkhadaRadius.pillShape, color = Color.Transparent, contentColor = color) {
        Text(
            text,
            Modifier.padding(horizontal = AkhadaSpace.sm, vertical = AkhadaSpace.sm),
            style = AkhadaTheme.typography.bodyStrong.copy(textDecoration = TextDecoration.Underline),
        )
    }
}

/** A round button holding one icon; every use needs a [contentDescription] naming the action. */
@Composable
fun AkhadaIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = AkhadaSize.iconButton,
    iconSize: Dp = 22.dp,
    container: Color = AkhadaTheme.colors.surfaceSunken,
    content: Color = AkhadaTheme.colors.ink,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Surface(
        onClick = onClick,
        modifier = modifier.size(size).graphicsLayer { val s = if (pressed) 0.94f else 1f; scaleX = s; scaleY = s },
        shape = CircleShape,
        color = container,
        contentColor = content,
        interactionSource = interaction,
    ) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription, Modifier.size(iconSize)) }
    }
}
