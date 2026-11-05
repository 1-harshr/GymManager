package com.harsh.gymmanager.ui.screens.memberdetail

import gymmanager.sharedui.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.getString
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.harsh.gymmanager.LocalAppDependencies
import com.harsh.gymmanager.domain.MembershipStatus
import com.harsh.gymmanager.domain.model.Membership
import com.harsh.gymmanager.domain.model.Payment
import com.harsh.gymmanager.domain.model.PaymentMode
import com.harsh.gymmanager.presentation.formatRupees
import com.harsh.gymmanager.presentation.memberdetail.MemberDetailEvent
import com.harsh.gymmanager.presentation.memberdetail.MemberDetailViewModel
import com.harsh.gymmanager.presentation.memberdetail.PaymentForm
import com.harsh.gymmanager.ui.LocalSnackbar
import com.harsh.gymmanager.ui.components.AkhadaBottomSheet
import com.harsh.gymmanager.ui.components.AkhadaButton
import com.harsh.gymmanager.ui.components.AkhadaCard
import com.harsh.gymmanager.ui.components.AkhadaIconButton
import com.harsh.gymmanager.ui.components.AkhadaTextField
import com.harsh.gymmanager.ui.components.Avatar
import com.harsh.gymmanager.ui.components.Badge
import com.harsh.gymmanager.ui.components.BadgeTone
import com.harsh.gymmanager.ui.components.ButtonKind
import com.harsh.gymmanager.ui.components.ConfirmSheet
import com.harsh.gymmanager.ui.components.EmptyState
import com.harsh.gymmanager.ui.components.MembershipCard
import com.harsh.gymmanager.ui.components.MenuAction
import com.harsh.gymmanager.ui.components.MoreMenu
import com.harsh.gymmanager.ui.components.PushedHeader
import com.harsh.gymmanager.ui.components.SectionHeader
import com.harsh.gymmanager.ui.components.SegmentedControl
import com.harsh.gymmanager.ui.components.tone
import com.harsh.gymmanager.ui.format.asString
import com.harsh.gymmanager.ui.format.formatFull
import com.harsh.gymmanager.ui.format.resolve
import com.harsh.gymmanager.ui.format.formatPhone
import com.harsh.gymmanager.ui.format.label
import com.harsh.gymmanager.ui.format.rememberToday
import com.harsh.gymmanager.ui.format.statusLabel
import com.harsh.gymmanager.ui.icons.AkhadaIcons
import com.harsh.gymmanager.ui.theme.AkhadaSpace
import com.harsh.gymmanager.ui.theme.AkhadaTheme
import com.harsh.gymmanager.ui.theme.screenGutter

private sealed interface Confirm {
    data object DeleteMember : Confirm
    data class DeleteMembership(val membership: Membership) : Confirm
    data class DeletePayment(val payment: Payment) : Confirm
}

@Composable
fun MemberDetailScreen(
    memberId: Long,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onSellMembership: () -> Unit,
) {
    val deps = LocalAppDependencies.current
    val snackbar = LocalSnackbar.current
    val vm = viewModel(key = "member-$memberId") {
        MemberDetailViewModel(memberId, deps.observeMemberDetail, deps.memberUseCases, deps.membershipUseCases, deps.paymentUseCases)
    }
    val state by vm.state.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf<Confirm?>(null) }
    val uriHandler = LocalUriHandler.current
    val today = rememberToday()

    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                is MemberDetailEvent.ShowMessage -> snackbar.showSnackbar(event.message.resolve())
                MemberDetailEvent.MemberDeleted -> {
                    onBack()
                    snackbar.showSnackbar(getString(Res.string.member_deleted))
                }
            }
        }
    }

    val detail = state.detail
    if (!state.isLoading && detail == null) {
        Column(Modifier.fillMaxSize()) {
            PushedHeader(stringResource(Res.string.member), onBack)
            EmptyState(AkhadaIcons.Users, stringResource(Res.string.member_gone_title), stringResource(Res.string.member_gone_body))
        }
        return
    }
    if (detail == null) {
        PushedHeader("", onBack)
        return
    }
    val member = detail.member
    val owing = detail.memberships.firstOrNull { it.amountDue > 0 }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = AkhadaSpace.xxl),
        verticalArrangement = Arrangement.spacedBy(AkhadaSpace.md),
    ) {
        item {
            PushedHeader(member.name, onBack, subtitle = stringResource(Res.string.joined_on, member.joinedOn.formatFull())) {
                member.phone?.let { phone ->
                    AkhadaIconButton(AkhadaIcons.Phone, stringResource(Res.string.call_person, member.name), onClick = { uriHandler.openUri("tel:$phone") }, iconSize = 20.dp)
                }
                MoreMenu(
                    actions = listOf(
                        MenuAction(stringResource(Res.string.edit_details), onClick = onEdit),
                        if (member.isArchived) MenuAction(stringResource(Res.string.restore_member), onClick = vm::restoreMember)
                        else MenuAction(stringResource(Res.string.archive_member), onClick = vm::archiveMember),
                        MenuAction(stringResource(Res.string.delete_permanently), destructive = true) { confirm = Confirm.DeleteMember },
                    ),
                    contentDescription = stringResource(Res.string.more_for, member.name),
                )
            }
        }
        item {
            AkhadaCard(Modifier.screenGutter()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AkhadaSpace.lg)) {
                    Avatar(member.name, member.photoPath, size = 72.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AkhadaSpace.xs)) {
                        if (member.isArchived) {
                            Badge(stringResource(Res.string.archived), BadgeTone.Expired)
                        } else {
                            Badge(statusLabel(detail.status, detail.latestEndDate, today), detail.status.tone())
                        }
                        val endDate = detail.latestEndDate
                        Text(
                            when {
                                endDate == null -> stringResource(Res.string.no_membership_yet)
                                detail.status == MembershipStatus.EXPIRED -> stringResource(Res.string.ended_on_full, endDate.formatFull())
                                else -> stringResource(Res.string.ends_on_full, endDate.formatFull())
                            },
                            style = AkhadaTheme.typography.body,
                        )
                        if (detail.memberships.isNotEmpty()) {
                            Text(
                                if (detail.totalDue > 0) stringResource(Res.string.amount_due, formatRupees(detail.totalDue)) else stringResource(Res.string.nothing_due),
                                style = AkhadaTheme.typography.bodyStrong,
                                color = if (detail.totalDue > 0) AkhadaTheme.colors.statusDue else AkhadaTheme.colors.statusActive,
                            )
                        }
                    }
                }
                val facts = listOfNotNull(
                    member.phone?.let(::formatPhone),
                    member.gender?.label(),
                    member.dateOfBirth?.let { stringResource(Res.string.born_on, it.formatFull()) },
                )
                if (facts.isNotEmpty()) {
                    Text(facts.joinToString(" · "), style = AkhadaTheme.typography.caption, color = AkhadaTheme.colors.inkMuted)
                }
                member.notes?.let { Text(it, style = AkhadaTheme.typography.body) }
            }
        }
        item {
            Column(Modifier.screenGutter(), verticalArrangement = Arrangement.spacedBy(AkhadaSpace.md)) {
                AkhadaButton(
                    if (detail.memberships.isEmpty()) stringResource(Res.string.sell_membership) else stringResource(Res.string.renew_membership),
                    onSellMembership,
                    Modifier.fillMaxWidth(),
                )
                if (owing != null) {
                    AkhadaButton(stringResource(Res.string.record_payment), { vm.openPaymentForm(owing.id) }, Modifier.fillMaxWidth(), kind = ButtonKind.Secondary)
                }
            }
        }
        if (detail.memberships.isNotEmpty()) {
            item { SectionHeader(stringResource(Res.string.history), Modifier.screenGutter().padding(top = AkhadaSpace.md)) }
            items(detail.memberships, key = { it.id }) { membership ->
                MembershipCard(
                    membership,
                    today,
                    onRecordPayment = { vm.openPaymentForm(membership.id) },
                    onDelete = { confirm = Confirm.DeleteMembership(membership) },
                    onPaymentClick = { confirm = Confirm.DeletePayment(it) },
                    modifier = Modifier.screenGutter(),
                )
            }
        }
    }

    state.paymentForm?.let { form ->
        PaymentSheet(
            form = form,
            planName = detail.memberships.firstOrNull { it.id == form.membershipId }?.planName.orEmpty(),
            onAmountChange = vm::onPaymentAmountChange,
            onModeChange = vm::onPaymentModeChange,
            onNoteChange = vm::onPaymentNoteChange,
            onSubmit = vm::submitPayment,
            onDismiss = vm::dismissPaymentForm,
        )
    }

    when (val c = confirm) {
        Confirm.DeleteMember -> ConfirmSheet(
            title = stringResource(Res.string.delete_member_title, member.name),
            body = stringResource(Res.string.delete_member_body),
            confirmLabel = stringResource(Res.string.delete_member),
            onConfirm = { confirm = null; vm.deleteMemberPermanently() },
            onDismiss = { confirm = null },
        )
        is Confirm.DeleteMembership -> ConfirmSheet(
            title = stringResource(Res.string.delete_membership_title, c.membership.planName),
            body = pluralStringResource(Res.plurals.delete_membership_body, c.membership.payments.size, c.membership.payments.size),
            confirmLabel = stringResource(Res.string.delete_membership),
            onConfirm = { confirm = null; vm.deleteMembership(c.membership.id) },
            onDismiss = { confirm = null },
        )
        is Confirm.DeletePayment -> ConfirmSheet(
            title = stringResource(Res.string.delete_payment_title, formatRupees(c.payment.amount)),
            body = stringResource(Res.string.delete_payment_body, c.payment.paidOn.formatFull(), c.payment.mode.label()),
            confirmLabel = stringResource(Res.string.delete_payment),
            onConfirm = { confirm = null; vm.deletePayment(c.payment.id) },
            onDismiss = { confirm = null },
        )
        null -> Unit
    }
}

@Composable
private fun PaymentSheet(
    form: PaymentForm,
    planName: String,
    onAmountChange: (String) -> Unit,
    onModeChange: (PaymentMode) -> Unit,
    onNoteChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
) {
    AkhadaBottomSheet(onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(AkhadaSpace.xs)) {
            Text(stringResource(Res.string.record_payment), style = AkhadaTheme.typography.title)
            Text(stringResource(Res.string.due_on_plan, formatRupees(form.amountDue), planName), style = AkhadaTheme.typography.caption, color = AkhadaTheme.colors.inkMuted)
        }
        AkhadaTextField(
            form.amountText, onAmountChange, stringResource(Res.string.amount),
            prefix = "₹", keyboardType = KeyboardType.Number, error = form.error?.asString(),
        )
        SegmentedControl(PaymentMode.entries, form.mode, { it?.let(onModeChange) }, { it.label() })
        AkhadaTextField(form.note, onNoteChange, stringResource(Res.string.note), optional = true, placeholder = stringResource(Res.string.payment_note_hint))
        val amount = com.harsh.gymmanager.presentation.parseRupees(form.amountText)
        AkhadaButton(
            if (form.isSaving) stringResource(Res.string.saving) else if (amount != null && amount > 0) stringResource(Res.string.record_amount, formatRupees(amount)) else stringResource(Res.string.record_payment),
            onSubmit,
            Modifier.fillMaxWidth(),
            enabled = !form.isSaving,
        )
    }
}
