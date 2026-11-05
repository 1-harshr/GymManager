package com.harsh.gymmanager

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.harsh.gymmanager.ui.components.AddButton
import com.harsh.gymmanager.ui.components.FloatingTabBar
import com.harsh.gymmanager.ui.components.TabItem
import com.harsh.gymmanager.ui.icons.AkhadaIcons
import com.harsh.gymmanager.ui.theme.AkhadaRadius
import com.harsh.gymmanager.ui.theme.AkhadaSize
import com.harsh.gymmanager.ui.theme.AkhadaSpace
import com.harsh.gymmanager.ui.theme.AkhadaTheme
import gymmanager.sharedui.generated.resources.Res
import gymmanager.sharedui.generated.resources.akhada_logo
import org.jetbrains.compose.resources.painterResource

private val MainTabs = listOf(
    TabItem("Home", AkhadaIcons.Home),
    TabItem("Members", AkhadaIcons.Users),
    TabItem("Plans", AkhadaIcons.Tag),
)
private const val MEMBERS_TAB = 1

@Composable
@Preview
fun App() {
    AkhadaTheme {
        var tab by rememberSaveable { mutableIntStateOf(0) }
        Box(Modifier.fillMaxSize().background(AkhadaTheme.colors.bg)) {
            // Placeholder content until the real screens are built.
            Column(
                Modifier.statusBarsPadding().padding(horizontal = AkhadaSpace.gutter, vertical = AkhadaSpace.md),
                verticalArrangement = Arrangement.spacedBy(AkhadaSpace.xl),
            ) {
                ScreenHeader(
                    title = MainTabs[tab].label,
                    subtitle = if (tab == 0) "Akhada Gym & MMA" else "Coming next",
                )
                Surface(shape = AkhadaRadius.cardShape, color = AkhadaTheme.colors.surface) {
                    Text(
                        "This screen is next on the list. Drag across the tab bar to switch tabs.",
                        Modifier.padding(AkhadaSpace.lg),
                        style = AkhadaTheme.typography.body,
                        color = AkhadaTheme.colors.inkMuted,
                    )
                }
            }
            Row(
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = AkhadaSpace.md),
                horizontalArrangement = Arrangement.spacedBy(AkhadaSpace.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FloatingTabBar(tabs = MainTabs, selected = tab, onSelect = { tab = it })
                AnimatedVisibility(tab == MEMBERS_TAB, enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
                    AddButton(onClick = {}, contentDescription = "Add member")
                }
            }
        }
    }
}

@Composable
private fun ScreenHeader(title: String, subtitle: String) {
    val colors = AkhadaTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AkhadaSpace.md)) {
        Image(
            painterResource(Res.drawable.akhada_logo),
            contentDescription = "Akhada Gym & MMA logo",
            modifier = Modifier.size(AkhadaSize.logo).clip(CircleShape).background(colors.logoGround),
            contentScale = ContentScale.Crop,
        )
        Column(Modifier.weight(1f)) {
            Text(title, style = AkhadaTheme.typography.screenTitle, color = colors.ink)
            Text(subtitle, style = AkhadaTheme.typography.caption, color = colors.inkMuted)
        }
        Surface(shape = CircleShape, color = colors.surfaceSunken, modifier = Modifier.size(AkhadaSize.iconButton)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(AkhadaIcons.Settings, contentDescription = "Settings", Modifier.size(22.dp), tint = colors.ink)
            }
        }
    }
}
