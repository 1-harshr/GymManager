package com.harsh.gymmanager.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.harsh.gymmanager.ui.components.AkhadaCard
import com.harsh.gymmanager.ui.components.GymLogo
import com.harsh.gymmanager.ui.components.PushedHeader
import com.harsh.gymmanager.ui.components.SectionHeader
import com.harsh.gymmanager.ui.theme.AkhadaSpace
import com.harsh.gymmanager.ui.theme.AkhadaTheme
import com.harsh.gymmanager.ui.theme.screenGutter

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val colors = AkhadaTheme.colors
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        PushedHeader("Settings", onBack)
        Column(Modifier.screenGutter().padding(bottom = AkhadaSpace.xxl), verticalArrangement = Arrangement.spacedBy(AkhadaSpace.md)) {
            AkhadaCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AkhadaSpace.md)) {
                    GymLogo()
                    Column {
                        Text("Akhada Gym & MMA", style = AkhadaTheme.typography.title)
                        Text("Vishnu Mandir Road, Bihar Electronic Building, Daltonganj", style = AkhadaTheme.typography.caption, color = colors.inkMuted)
                    }
                }
            }
            SectionHeader("Data", Modifier.padding(top = AkhadaSpace.md))
            AkhadaCard {
                Text("Stored on this phone only", style = AkhadaTheme.typography.bodyStrong)
                Text(
                    "Members, plans and payments never leave this phone. Keep Google backup turned on in the phone's settings until export and restore arrive in the app.",
                    style = AkhadaTheme.typography.body,
                    color = colors.inkMuted,
                )
            }
            SectionHeader("Appearance", Modifier.padding(top = AkhadaSpace.md))
            AkhadaCard {
                Text("Light or dark follows your phone's display setting.", style = AkhadaTheme.typography.body, color = colors.inkMuted)
            }
            SectionHeader("About", Modifier.padding(top = AkhadaSpace.md))
            AkhadaCard {
                Text("Version 1.0", style = AkhadaTheme.typography.body)
                Text("Fonts: Barlow and Barlow Condensed (SIL Open Font License). Icons: Lucide (ISC License).", style = AkhadaTheme.typography.caption, color = colors.inkMuted)
            }
        }
    }
}
