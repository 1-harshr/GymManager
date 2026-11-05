package com.harsh.gymmanager.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import com.harsh.gymmanager.ui.icons.AkhadaIcons
import com.harsh.gymmanager.ui.theme.AkhadaSize
import com.harsh.gymmanager.ui.theme.AkhadaSpace
import com.harsh.gymmanager.ui.theme.AkhadaTheme
import gymmanager.sharedui.generated.resources.Res
import gymmanager.sharedui.generated.resources.akhada_logo
import org.jetbrains.compose.resources.painterResource

/** The gym's logo in its white disc; never recoloured. */
@Composable
fun GymLogo(modifier: Modifier = Modifier) {
    Image(
        painterResource(Res.drawable.akhada_logo),
        contentDescription = "Akhada Gym & MMA logo",
        modifier = modifier.size(AkhadaSize.logo).clip(CircleShape).background(AkhadaTheme.colors.logoGround),
        contentScale = ContentScale.Crop,
    )
}

/** Header for the main tabs: logo, title, one line of context and actions on the right. */
@Composable
fun TabHeader(
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    titleStyle: TextStyle = AkhadaTheme.typography.screenTitle,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = AkhadaSpace.gutter, vertical = AkhadaSpace.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AkhadaSpace.md),
    ) {
        GymLogo()
        HeaderText(title, subtitle, titleStyle, Modifier.weight(1f))
        actions()
    }
}

/** Header for pushed screens: back button, title and actions. */
@Composable
fun PushedHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = AkhadaSpace.gutter, vertical = AkhadaSpace.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AkhadaSpace.md),
    ) {
        AkhadaIconButton(AkhadaIcons.ChevronLeft, "Back", onBack)
        HeaderText(title, subtitle, AkhadaTheme.typography.screenTitle, Modifier.weight(1f))
        actions()
    }
}

@Composable
private fun HeaderText(title: String, subtitle: String?, style: TextStyle, modifier: Modifier) {
    Column(modifier) {
        Text(title, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (subtitle != null) {
            Text(subtitle, style = AkhadaTheme.typography.caption, color = AkhadaTheme.colors.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
