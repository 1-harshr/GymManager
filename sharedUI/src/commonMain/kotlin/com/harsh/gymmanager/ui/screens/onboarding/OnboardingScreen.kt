package com.harsh.gymmanager.ui.screens.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.harsh.gymmanager.ui.platform.SystemBackHandler
import com.harsh.gymmanager.ui.screens.account.DeleteAccountScreen
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.harsh.gymmanager.domain.repository.GymRepository
import com.harsh.gymmanager.presentation.onboarding.OnboardingStep
import com.harsh.gymmanager.presentation.onboarding.OnboardingUiState
import com.harsh.gymmanager.presentation.onboarding.OnboardingViewModel
import com.harsh.gymmanager.ui.components.GhostButton
import com.harsh.gymmanager.ui.components.MyGymButton
import com.harsh.gymmanager.ui.components.MyGymCard
import com.harsh.gymmanager.ui.components.MyGymIconButton
import com.harsh.gymmanager.ui.components.MyGymTextField
import com.harsh.gymmanager.ui.format.asString
import com.harsh.gymmanager.ui.icons.MyGymIcons
import com.harsh.gymmanager.ui.motion.MyGymMotion
import com.harsh.gymmanager.ui.screens.signin.BrandMark
import com.harsh.gymmanager.ui.screens.signin.headline
import com.harsh.gymmanager.ui.theme.MyGymSpace
import com.harsh.gymmanager.ui.theme.MyGymTheme
import com.harsh.gymmanager.ui.theme.screenGutter
import gymmanager.sharedui.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/**
 * Right after signing in to no gym: the owner sets one up, staff join with their owner's code.
 * [signedInAs] is the number they signed in with, so someone on the wrong account can sign out,
 * and anyone can delete the account they made.
 */
@Composable
fun OnboardingScreen(gyms: GymRepository, signedInAs: String, onSignOut: () -> Unit) {
    val vm = viewModel { OnboardingViewModel(gyms) }
    val state by vm.state.collectAsStateWithLifecycle()
    var deletingAccount by remember { mutableStateOf(false) }
    if (deletingAccount) {
        SystemBackHandler(enabled = true) { deletingAccount = false }
        // Without a gym there's no one to hand over to, so this never needs Staff.
        DeleteAccountScreen(gyms, onBack = { deletingAccount = false }, onOpenStaff = {})
        return
    }

    AnimatedContent(
        targetState = state.step,
        modifier = Modifier.fillMaxSize().imePadding(),
        transitionSpec = {
            val forward = targetState != OnboardingStep.CHOOSE
            (slideInHorizontally(MyGymMotion.push()) { if (forward) it else -it / 3 } + fadeIn(MyGymMotion.push())) togetherWith
                (slideOutHorizontally(MyGymMotion.push()) { if (forward) -it / 3 else it } + fadeOut(MyGymMotion.push()))
        },
    ) { step ->
        when (step) {
            OnboardingStep.CHOOSE -> ChooseStep(vm, signedInAs, onSignOut, onDeleteAccount = { deletingAccount = true })
            OnboardingStep.CREATE -> CreateStep(state, vm)
            OnboardingStep.JOIN -> JoinStep(state, vm)
        }
    }
}

@Composable
private fun ChooseStep(vm: OnboardingViewModel, signedInAs: String, onSignOut: () -> Unit, onDeleteAccount: () -> Unit) {
    val colors = MyGymTheme.colors
    val typography = MyGymTheme.typography
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).statusBarsPadding().screenGutter()
                .padding(top = MyGymSpace.xl, bottom = MyGymSpace.lg),
        ) {
            BrandMark()
            Spacer(Modifier.height(MyGymSpace.xxl + MyGymSpace.lg))
            Text(stringResource(Res.string.onboarding_title), style = typography.headline)
            Spacer(Modifier.height(MyGymSpace.sm))
            Text(stringResource(Res.string.onboarding_subtitle), style = typography.body, color = colors.inkMuted)
            Spacer(Modifier.height(MyGymSpace.xxl))
            Column(verticalArrangement = Arrangement.spacedBy(MyGymSpace.md)) {
                ChoiceCard(
                    MyGymIcons.Dumbbell,
                    stringResource(Res.string.onboarding_owner_title),
                    stringResource(Res.string.onboarding_owner_body),
                ) { vm.choose(OnboardingStep.CREATE) }
                ChoiceCard(
                    MyGymIcons.Users,
                    stringResource(Res.string.onboarding_staff_title),
                    stringResource(Res.string.onboarding_staff_body),
                ) { vm.choose(OnboardingStep.JOIN) }
            }
        }
        Column(
            Modifier.fillMaxWidth().screenGutter().navigationBarsPadding().padding(vertical = MyGymSpace.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(Res.string.account_signed_in_as, signedInAs), style = typography.caption, color = colors.inkMuted)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(Res.string.onboarding_not_you), style = typography.body, color = colors.inkMuted)
                GhostButton(stringResource(Res.string.sign_out), onSignOut)
            }
            GhostButton(stringResource(Res.string.delete_account), onDeleteAccount, color = colors.inkMuted)
        }
    }
}

/** One of the two ways in: a big tappable card with an icon, what it is and who it's for. */
@Composable
private fun ChoiceCard(icon: ImageVector, title: String, body: String, onClick: () -> Unit) {
    val colors = MyGymTheme.colors
    MyGymCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MyGymSpace.lg)) {
            Box(Modifier.size(48.dp).background(colors.accentSoft, CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = colors.onAccentSoft, modifier = Modifier.size(24.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MyGymSpace.xs)) {
                Text(title, style = MyGymTheme.typography.title)
                Text(body, style = MyGymTheme.typography.body, color = colors.inkMuted)
            }
            Icon(MyGymIcons.ChevronRight, contentDescription = null, tint = colors.inkMuted, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun CreateStep(state: OnboardingUiState, vm: OnboardingViewModel) {
    FormStep(
        title = stringResource(Res.string.create_gym_title),
        subtitle = stringResource(Res.string.create_gym_subtitle),
        error = state.errorMessage?.asString(),
        action = stringResource(if (state.isBusy) Res.string.creating_gym else Res.string.create_gym),
        busy = state.isBusy,
        onAction = vm::create,
        onBack = vm::back,
    ) {
        MyGymTextField(
            state.gymName, vm::onGymNameChange, stringResource(Res.string.gym_name),
            error = state.gymNameError?.asString(), placeholder = stringResource(Res.string.gym_name_hint),
            capitalization = KeyboardCapitalization.Words,
        )
        MyGymTextField(
            state.gymAddress, vm::onGymAddressChange, stringResource(Res.string.gym_address),
            optional = true, placeholder = stringResource(Res.string.gym_address_hint),
            capitalization = KeyboardCapitalization.Words,
        )
        MyGymTextField(
            state.yourName, vm::onYourNameChange, stringResource(Res.string.your_name),
            error = state.yourNameError?.asString(), placeholder = stringResource(Res.string.your_name_hint),
            capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done, onDone = vm::create,
        )
    }
}

@Composable
private fun JoinStep(state: OnboardingUiState, vm: OnboardingViewModel) {
    FormStep(
        title = stringResource(Res.string.join_gym_title),
        subtitle = stringResource(Res.string.join_gym_subtitle),
        error = state.errorMessage?.asString(),
        action = stringResource(if (state.isBusy) Res.string.joining_gym else Res.string.join_gym),
        busy = state.isBusy,
        onAction = vm::join,
        onBack = vm::back,
    ) {
        MyGymTextField(
            state.code, vm::onCodeChange, stringResource(Res.string.invite_code),
            error = state.codeError?.asString(), placeholder = "K7P4-QXM2",
            capitalization = KeyboardCapitalization.Characters, visualTransformation = InviteCodeGrouping,
        )
        MyGymTextField(
            state.yourName, vm::onYourNameChange, stringResource(Res.string.your_name),
            error = state.yourNameError?.asString(), placeholder = stringResource(Res.string.your_name_staff_hint),
            capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done, onDone = vm::join,
        )
    }
}

/** A step with a back arrow, a headline, its fields, and one action docked at the bottom. */
@Composable
private fun FormStep(
    title: String,
    subtitle: String,
    error: String?,
    action: String,
    busy: Boolean,
    onAction: () -> Unit,
    onBack: () -> Unit,
    fields: @Composable () -> Unit,
) {
    val colors = MyGymTheme.colors
    val typography = MyGymTheme.typography
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.statusBarsPadding().padding(horizontal = MyGymSpace.gutter, vertical = MyGymSpace.md)) {
            MyGymIconButton(MyGymIcons.ChevronLeft, stringResource(Res.string.back), onBack)
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).screenGutter()
                .padding(top = MyGymSpace.lg, bottom = MyGymSpace.lg),
            verticalArrangement = Arrangement.spacedBy(MyGymSpace.lg),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(MyGymSpace.sm)) {
                Text(title, style = typography.headline)
                Text(subtitle, style = typography.body, color = colors.inkMuted)
            }
            Spacer(Modifier.height(MyGymSpace.sm))
            fields()
            error?.let { Text(it, style = typography.caption, color = colors.statusDue) }
        }
        MyGymButton(
            action,
            onAction,
            Modifier.fillMaxWidth().screenGutter().navigationBarsPadding().padding(vertical = MyGymSpace.md),
            enabled = !busy,
        )
    }
}

/** Shows "K7P4-QXM2" while the field holds "K7P4QXM2". */
private object InviteCodeGrouping : VisualTransformation {
    private const val GROUP = 4

    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val grouped = if (raw.length > GROUP) raw.take(GROUP) + "-" + raw.drop(GROUP) else raw
        return TransformedText(
            AnnotatedString(grouped),
            object : OffsetMapping {
                override fun originalToTransformed(offset: Int) = if (offset > GROUP) offset + 1 else offset
                override fun transformedToOriginal(offset: Int) = if (offset > GROUP) offset - 1 else offset
            },
        )
    }
}
