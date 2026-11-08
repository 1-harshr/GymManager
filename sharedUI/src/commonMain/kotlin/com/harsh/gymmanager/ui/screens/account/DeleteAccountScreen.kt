package com.harsh.gymmanager.ui.screens.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.harsh.gymmanager.domain.repository.AccountDeletion
import com.harsh.gymmanager.domain.repository.GymRepository
import com.harsh.gymmanager.presentation.account.DeleteAccountViewModel
import com.harsh.gymmanager.ui.LocalSnackbar
import com.harsh.gymmanager.ui.components.ButtonKind
import com.harsh.gymmanager.ui.components.LoadingMark
import com.harsh.gymmanager.ui.components.MyGymButton
import com.harsh.gymmanager.ui.components.MyGymCard
import com.harsh.gymmanager.ui.components.MyGymTextField
import com.harsh.gymmanager.ui.components.PushedHeader
import com.harsh.gymmanager.ui.format.asString
import com.harsh.gymmanager.ui.format.resolve
import com.harsh.gymmanager.ui.theme.MyGymSpace
import com.harsh.gymmanager.ui.theme.MyGymTheme
import com.harsh.gymmanager.ui.theme.screenGutter
import gymmanager.sharedui.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/**
 * Deleting your own account for good. Says what goes with it first: only the account, the whole gym
 * when they're its only owner and alone there, or nothing yet when someone else must be made owner.
 * Success signs out, which takes the app back to sign-in.
 */
@Composable
fun DeleteAccountScreen(gyms: GymRepository, onBack: () -> Unit, onOpenStaff: () -> Unit) {
    val vm = viewModel { DeleteAccountViewModel(gyms) }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbar.current
    val colors = MyGymTheme.colors
    val typography = MyGymTheme.typography
    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it.resolve()) } }

    val gymName = state.gymName.orEmpty()
    Column(Modifier.fillMaxSize().imePadding()) {
        PushedHeader(stringResource(Res.string.delete_account_title), onBack)
        val deletion = state.deletion
        val loadError = state.loadError
        if (deletion == null && loadError == null) {
            LoadingMark(Modifier.weight(1f))
            return@Column
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).screenGutter().padding(bottom = MyGymSpace.xl),
            verticalArrangement = Arrangement.spacedBy(MyGymSpace.lg),
        ) {
            if (loadError != null) {
                MyGymCard {
                    Text(stringResource(Res.string.delete_account_check_failed), style = typography.bodyStrong)
                    Text(loadError.asString(), style = typography.body, color = colors.inkMuted)
                    MyGymButton(stringResource(Res.string.try_again), vm::load, Modifier.fillMaxWidth(), kind = ButtonKind.Secondary)
                }
                return@Column
            }
            when (deletion) {
                AccountDeletion.ACCOUNT -> Text(
                    if (state.gymName == null) stringResource(Res.string.delete_account_body_no_gym) else stringResource(Res.string.delete_account_body, gymName),
                    style = typography.body,
                )
                AccountDeletion.ACCOUNT_AND_GYM -> {
                    Text(stringResource(Res.string.delete_gym_body, gymName), style = typography.body)
                    MyGymTextField(
                        state.typedName,
                        vm::onTypedNameChange,
                        stringResource(Res.string.delete_gym_type_name, gymName),
                        placeholder = gymName,
                        imeAction = ImeAction.Done,
                    )
                }
                AccountDeletion.HAND_OVER_FIRST -> {
                    Text(stringResource(Res.string.delete_hand_over_body, gymName), style = typography.body)
                    MyGymButton(stringResource(Res.string.delete_open_staff), onOpenStaff, Modifier.fillMaxWidth(), kind = ButtonKind.Secondary)
                }
                null -> Unit
            }
        }
        if (deletion == AccountDeletion.ACCOUNT || deletion == AccountDeletion.ACCOUNT_AND_GYM) {
            MyGymButton(
                when {
                    state.isDeleting -> stringResource(Res.string.deleting_account)
                    deletion == AccountDeletion.ACCOUNT_AND_GYM -> stringResource(Res.string.delete_account_and_gym_confirm)
                    else -> stringResource(Res.string.delete_account_confirm)
                },
                vm::delete,
                Modifier.fillMaxWidth().screenGutter().navigationBarsPadding().padding(vertical = MyGymSpace.md),
                kind = ButtonKind.Danger,
                enabled = state.canDelete,
            )
        }
    }
}
