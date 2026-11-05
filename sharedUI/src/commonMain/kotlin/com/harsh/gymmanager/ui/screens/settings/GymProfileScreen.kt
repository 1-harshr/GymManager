package com.harsh.gymmanager.ui.screens.settings

import com.harsh.gymmanager.ui.components.rememberDiscardGuard
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.harsh.gymmanager.LocalAppDependencies
import com.harsh.gymmanager.domain.model.GymProfile
import com.harsh.gymmanager.presentation.settings.GymProfileEvent
import com.harsh.gymmanager.presentation.settings.GymProfileViewModel
import com.harsh.gymmanager.ui.components.AkhadaButton
import com.harsh.gymmanager.ui.components.AkhadaTextField
import com.harsh.gymmanager.ui.components.ButtonKind
import com.harsh.gymmanager.ui.components.GhostButton
import com.harsh.gymmanager.ui.components.GymLogo
import com.harsh.gymmanager.ui.components.PushedHeader
import com.harsh.gymmanager.ui.format.asString
import com.harsh.gymmanager.ui.icons.AkhadaIcons
import com.harsh.gymmanager.ui.platform.rememberLogoPicker
import com.harsh.gymmanager.ui.theme.AkhadaSpace
import com.harsh.gymmanager.ui.theme.AkhadaTheme
import com.harsh.gymmanager.ui.theme.screenGutter
import gymmanager.sharedui.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/** First-run setup when no gym is saved yet; afterwards, editing the gym from Settings. */
@Composable
fun GymProfileScreen(onBack: () -> Unit, onSaved: (firstRun: Boolean) -> Unit) {
    val deps = LocalAppDependencies.current
    val vm = viewModel { GymProfileViewModel(deps.settingsUseCases) }
    val state by vm.state.collectAsStateWithLifecycle()
    val back = rememberDiscardGuard(state.hasChanges, onBack)
    var logoFailed by remember { mutableStateOf(false) }
    val pickLogo = rememberLogoPicker { path ->
        logoFailed = path == null
        if (path != null) vm.onLogoChange(path)
    }
    LaunchedEffect(vm) { vm.events.collect { if (it is GymProfileEvent.Saved) onSaved(state.isFirstRun) } }

    Column(Modifier.fillMaxSize().imePadding()) {
        if (state.isFirstRun) {
            Column(Modifier.statusBarsPadding().screenGutter().padding(top = AkhadaSpace.xl, bottom = AkhadaSpace.lg)) {
                Text(stringResource(Res.string.setup_title), style = AkhadaTheme.typography.screenTitle)
                Text(stringResource(Res.string.setup_subtitle), style = AkhadaTheme.typography.caption, color = AkhadaTheme.colors.inkMuted)
            }
        } else {
            PushedHeader(stringResource(Res.string.gym_details), back)
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).screenGutter().padding(bottom = AkhadaSpace.xl),
            verticalArrangement = Arrangement.spacedBy(AkhadaSpace.lg),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AkhadaSpace.lg)) {
                GymLogo(profile = GymProfile(state.name.ifBlank { "G" }), size = 88.dp, logoPath = state.logoPath)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AkhadaSpace.sm)) {
                    AkhadaButton(
                        stringResource(if (state.logoPath == null) Res.string.choose_logo else Res.string.change_logo),
                        pickLogo,
                        Modifier.fillMaxWidth(),
                        kind = ButtonKind.Secondary,
                        icon = AkhadaIcons.Camera,
                    )
                    if (state.logoPath != null) GhostButton(stringResource(Res.string.remove_logo), { vm.onLogoChange(null) })
                }
            }
            Text(
                stringResource(if (logoFailed) Res.string.logo_failed else Res.string.logo_hint),
                style = AkhadaTheme.typography.caption,
                color = if (logoFailed) AkhadaTheme.colors.statusDue else AkhadaTheme.colors.inkMuted,
            )
            AkhadaTextField(
                state.name, vm::onNameChange, stringResource(Res.string.gym_name),
                error = state.nameError?.asString(), placeholder = stringResource(Res.string.gym_name_hint),
                capitalization = KeyboardCapitalization.Words,
            )
            AkhadaTextField(
                state.address, vm::onAddressChange, stringResource(Res.string.gym_address),
                optional = true, placeholder = stringResource(Res.string.gym_address_hint),
                singleLine = false, capitalization = KeyboardCapitalization.Words,
            )
            state.errorMessage?.let { Text(it.asString(), style = AkhadaTheme.typography.caption, color = AkhadaTheme.colors.statusDue) }
        }
        AkhadaButton(
            stringResource(
                when {
                    state.isSaving -> Res.string.saving
                    state.isFirstRun -> Res.string.`continue`
                    else -> Res.string.save_changes
                },
            ),
            vm::save,
            Modifier.fillMaxWidth().screenGutter().navigationBarsPadding().padding(vertical = AkhadaSpace.md),
            enabled = !state.isSaving && !state.isLoading,
        )
    }
}
