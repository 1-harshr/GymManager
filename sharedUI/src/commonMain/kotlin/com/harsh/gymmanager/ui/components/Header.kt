package com.harsh.gymmanager.ui.components

import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.harsh.gymmanager.domain.model.GymProfile
import com.harsh.gymmanager.ui.LocalGym
import com.harsh.gymmanager.ui.format.initials
import com.harsh.gymmanager.ui.photo.MemberPhoto
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import com.harsh.gymmanager.ui.icons.AkhadaIcons
import com.harsh.gymmanager.ui.theme.AkhadaSize
import com.harsh.gymmanager.ui.theme.AkhadaSpace
import com.harsh.gymmanager.ui.theme.AkhadaTheme
import gymmanager.sharedui.generated.resources.Res
import gymmanager.sharedui.generated.resources.back
import gymmanager.sharedui.generated.resources.gym_logo
import org.jetbrains.compose.resources.stringResource

/**
 * The gym's logo in a disc: the owner's image on `logo-ground`, or the gym's initials on `ink`
 * when no logo has been chosen. Never recoloured.
 */
@Composable
fun GymLogo(modifier: Modifier = Modifier, profile: GymProfile = LocalGym.current, size: Dp = AkhadaSize.logo, logoPath: String? = profile.logoPath) {
    val colors = AkhadaTheme.colors
    val description = stringResource(Res.string.gym_logo, profile.name)
    if (logoPath != null) {
        Box(modifier.size(size).clip(CircleShape).background(colors.logoGround)) {
            MemberPhoto(logoPath, contentDescription = description, modifier = Modifier.size(size))
        }
    } else {
        Box(
            modifier.size(size).clip(CircleShape).background(colors.ink).semantics { contentDescription = description },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                initials(profile.name).ifEmpty { "G" },
                style = AkhadaTheme.typography.stat.copy(fontSize = (size.value * 0.4f).sp, lineHeight = (size.value * 0.45f).sp),
                color = colors.onInk,
            )
        }
    }
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
        CompositionLocalProvider(LocalInHeader provides true) { actions() }
    }
}

/** Header for pushed screens: back button, title and actions. */
@Composable
fun PushedHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    titleModifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = AkhadaSpace.gutter, vertical = AkhadaSpace.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AkhadaSpace.md),
    ) {
        CompositionLocalProvider(LocalInHeader provides true) {
            AkhadaIconButton(AkhadaIcons.ChevronLeft, stringResource(Res.string.back), onBack)
            HeaderText(title, subtitle, AkhadaTheme.typography.screenTitle, Modifier.weight(1f), titleModifier)
            actions()
        }
    }
}

@Composable
private fun HeaderText(title: String, subtitle: String?, style: TextStyle, modifier: Modifier, titleModifier: Modifier = Modifier) {
    Column(modifier) {
        Text(title, titleModifier, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (subtitle != null) {
            Text(subtitle, style = AkhadaTheme.typography.caption, color = AkhadaTheme.colors.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
