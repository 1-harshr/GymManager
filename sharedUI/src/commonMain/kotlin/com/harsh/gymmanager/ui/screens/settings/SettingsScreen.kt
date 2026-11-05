package com.harsh.gymmanager.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.harsh.gymmanager.domain.model.AppLanguage
import com.harsh.gymmanager.domain.model.AppSettings
import com.harsh.gymmanager.domain.model.ThemeMode
import com.harsh.gymmanager.ui.components.AkhadaButton
import com.harsh.gymmanager.ui.components.AkhadaCard
import com.harsh.gymmanager.ui.components.ButtonKind
import com.harsh.gymmanager.ui.components.GymLogo
import com.harsh.gymmanager.ui.components.PushedHeader
import com.harsh.gymmanager.ui.components.SectionHeader
import com.harsh.gymmanager.ui.components.SegmentedControl
import com.harsh.gymmanager.ui.theme.AkhadaSize
import com.harsh.gymmanager.ui.theme.AkhadaSpace
import com.harsh.gymmanager.ui.theme.AkhadaTheme
import com.harsh.gymmanager.ui.theme.screenGutter
import gymmanager.sharedui.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onEditGym: () -> Unit,
    onThemeChange: (ThemeMode) -> Unit,
    onLanguageChange: (AppLanguage) -> Unit,
) {
    val colors = AkhadaTheme.colors
    val typography = AkhadaTheme.typography
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        PushedHeader(stringResource(Res.string.settings), onBack)
        Column(Modifier.screenGutter().padding(bottom = AkhadaSpace.xxl), verticalArrangement = Arrangement.spacedBy(AkhadaSpace.md)) {
            SectionHeader(stringResource(Res.string.section_gym))
            AkhadaCard(onClick = onEditGym) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AkhadaSpace.md)) {
                    settings.gym?.let { GymLogo(profile = it, size = AkhadaSize.avatar + AkhadaSpace.lg) }
                    Column(Modifier.weight(1f)) {
                        Text(settings.gym?.name.orEmpty(), style = typography.title)
                        settings.gym?.address?.let { Text(it, style = typography.caption, color = colors.inkMuted) }
                    }
                }
                AkhadaButton(stringResource(Res.string.edit_gym), onEditGym, Modifier.fillMaxWidth(), kind = ButtonKind.Secondary)
            }

            SectionHeader(stringResource(Res.string.section_appearance), Modifier.padding(top = AkhadaSpace.md))
            AkhadaCard {
                val themeLabels = mapOf(
                    ThemeMode.SYSTEM to stringResource(Res.string.theme_system),
                    ThemeMode.LIGHT to stringResource(Res.string.theme_light),
                    ThemeMode.DARK to stringResource(Res.string.theme_dark),
                )
                SegmentedControl(ThemeMode.entries, settings.theme, { it?.let(onThemeChange) }, { themeLabels.getValue(it) })
                Text(stringResource(Res.string.theme_hint), style = typography.caption, color = colors.inkMuted)
            }

            SectionHeader(stringResource(Res.string.section_language), Modifier.padding(top = AkhadaSpace.md))
            AkhadaCard {
                val languageLabels = mapOf(
                    AppLanguage.SYSTEM to stringResource(Res.string.language_system),
                    AppLanguage.ENGLISH to stringResource(Res.string.language_english),
                    AppLanguage.HINDI to stringResource(Res.string.language_hindi),
                )
                SegmentedControl(AppLanguage.entries, settings.language, { it?.let(onLanguageChange) }, { languageLabels.getValue(it) })
            }

            SectionHeader(stringResource(Res.string.section_data), Modifier.padding(top = AkhadaSpace.md))
            AkhadaCard {
                Text(stringResource(Res.string.data_title), style = typography.bodyStrong)
                Text(stringResource(Res.string.data_body), style = typography.body, color = colors.inkMuted)
            }

            SectionHeader(stringResource(Res.string.section_about), Modifier.padding(top = AkhadaSpace.md))
            AkhadaCard {
                Text(stringResource(Res.string.version, "1.0"), style = typography.body)
                Text(stringResource(Res.string.licences), style = typography.caption, color = colors.inkMuted)
            }
        }
    }
}
