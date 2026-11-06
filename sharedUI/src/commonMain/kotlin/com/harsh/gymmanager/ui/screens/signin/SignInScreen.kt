package com.harsh.gymmanager.ui.screens.signin

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.harsh.gymmanager.LocalDemoMode
import com.harsh.gymmanager.domain.repository.AuthRepository
import com.harsh.gymmanager.presentation.signin.PHONE_COUNTRY_CODE
import com.harsh.gymmanager.presentation.signin.SignInUiState
import com.harsh.gymmanager.presentation.signin.SignInViewModel
import com.harsh.gymmanager.ui.components.GhostButton
import com.harsh.gymmanager.ui.components.MyGymButton
import com.harsh.gymmanager.ui.components.MyGymTextField
import com.harsh.gymmanager.ui.format.asString
import com.harsh.gymmanager.ui.screens.settings.DemoPasswordSheet
import com.harsh.gymmanager.ui.theme.MyGymSpace
import com.harsh.gymmanager.ui.theme.MyGymTheme
import com.harsh.gymmanager.ui.theme.screenGutter
import gymmanager.sharedui.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/** The first screen until someone signs in: their mobile number, then the code texted to it. */
@Composable
fun SignInScreen(auth: AuthRepository) {
    val vm = viewModel { SignInViewModel(auth) }
    val state by vm.state.collectAsStateWithLifecycle()
    val demoMode = LocalDemoMode.current
    var askingDemoPassword by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().imePadding()) {
        Column(Modifier.statusBarsPadding().screenGutter().padding(top = MyGymSpace.xl, bottom = MyGymSpace.lg)) {
            Text(
                stringResource(if (state.awaitingCode) Res.string.signin_code_title else Res.string.signin_title),
                style = MyGymTheme.typography.screenTitle,
            )
            Text(
                if (state.awaitingCode) {
                    stringResource(Res.string.signin_code_body, displayPhone(state.phone))
                } else {
                    stringResource(Res.string.signin_subtitle)
                },
                style = MyGymTheme.typography.caption,
                color = MyGymTheme.colors.inkMuted,
            )
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).screenGutter().padding(bottom = MyGymSpace.xl),
            verticalArrangement = Arrangement.spacedBy(MyGymSpace.lg),
        ) {
            if (state.awaitingCode) CodeStep(state, vm) else PhoneStep(state, vm)
        }
        Column(
            Modifier.fillMaxWidth().screenGutter().navigationBarsPadding().padding(vertical = MyGymSpace.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (state.awaitingCode) {
                MyGymButton(
                    stringResource(if (state.isBusy) Res.string.signin_verifying else Res.string.signin_verify),
                    vm::verifyCode,
                    Modifier.fillMaxWidth(),
                    enabled = !state.isBusy,
                )
            } else {
                MyGymButton(
                    stringResource(if (state.isBusy) Res.string.signin_sending else Res.string.signin_send_code),
                    vm::sendCode,
                    Modifier.fillMaxWidth(),
                    enabled = !state.isBusy,
                )
                if (demoMode != null) {
                    GhostButton(stringResource(Res.string.signin_try_demo), { askingDemoPassword = true }, Modifier.padding(top = MyGymSpace.sm))
                }
            }
        }
    }
    if (askingDemoPassword && demoMode != null) DemoPasswordSheet(demoMode, onDismiss = { askingDemoPassword = false })
}

@Composable
private fun PhoneStep(state: SignInUiState, vm: SignInViewModel) {
    MyGymTextField(
        state.phone,
        vm::onPhoneChange,
        stringResource(Res.string.signin_phone),
        prefix = PHONE_COUNTRY_CODE,
        keyboardType = KeyboardType.Phone,
        error = state.phoneError?.asString(),
        imeAction = ImeAction.Done,
        onDone = vm::sendCode,
    )
}

@Composable
private fun CodeStep(state: SignInUiState, vm: SignInViewModel) {
    MyGymTextField(
        state.code,
        vm::onCodeChange,
        stringResource(Res.string.signin_code),
        keyboardType = KeyboardType.NumberPassword,
        error = state.codeError?.asString(),
        imeAction = ImeAction.Done,
        onDone = vm::verifyCode,
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        GhostButton(stringResource(Res.string.signin_change_number), vm::changeNumber)
        if (state.resendInSeconds > 0) {
            Text(
                stringResource(Res.string.signin_resend_in, state.resendInSeconds),
                Modifier.padding(horizontal = MyGymSpace.sm),
                style = MyGymTheme.typography.caption,
                color = MyGymTheme.colors.inkMuted,
            )
        } else {
            GhostButton(stringResource(Res.string.signin_resend), vm::resendCode)
        }
    }
}

/** "+91 90000 00001" for "9000000001". */
private fun displayPhone(digits: String): String =
    "$PHONE_COUNTRY_CODE ${digits.take(5)} ${digits.drop(5)}".trimEnd()
