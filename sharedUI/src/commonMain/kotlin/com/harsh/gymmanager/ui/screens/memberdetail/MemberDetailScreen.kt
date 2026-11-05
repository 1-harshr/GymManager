package com.harsh.gymmanager.ui.screens.memberdetail

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
import com.harsh.gymmanager.ui.components.label
import com.harsh.gymmanager.ui.components.tone
import com.harsh.gymmanager.ui.format.formatFull
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
                is MemberDetailEvent.ShowMessage -> snackbar.showSnackbar(event.message)
                MemberDetailEvent.MemberDeleted -> {
                    onBack()
                    snackbar.showSnackbar("Member deleted")
                }
            }
        }
    }

    val detail = state.detail
    if (!state.isLoading && detail == null) {
        Column(Modifier.fillMaxSize()) {
            PushedHeader("Member", onBack)
            EmptyState(AkhadaIcons.Users, "This member no longer exists", "They may have been deleted.")
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
            PushedHeader(member.name, onBack, subtitle = "Joined ${member.joinedOn.formatFull()}") {
                member.phone?.let { phone ->
                    AkhadaIconButton(AkhadaIcons.Phone, "Call ${member.name}", onClick = { uriHandler.openUri("tel:$phone") }, iconSize = 20.dp)
                }
                MoreMenu(
                    actions = listOf(
                        MenuAction("Edit details", onClick = onEdit),
                        if (member.isArchived) MenuAction("Restore member", onClick = vm::restoreMember)
                        else MenuAction("Archive member", onClick = vm::archiveMember),
                        MenuAction("Delete permanently", destructive = true) { confirm = Confirm.DeleteMember },
                    ),
                    contentDescription = "More for ${member.name}",
                )
            }
        }
        item {
            AkhadaCard(Modifier.screenGutter()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AkhadaSpace.lg)) {
                    Avatar(member.name, member.photoPath, size = 72.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AkhadaSpace.xs)) {
                        if (member.isArchived) {
                            Badge("Archived", BadgeTone.Expired)
                        } else {
                            Badge(statusLabel(detail.status, detail.latestEndDate, today), detail.status.tone())
                        }
                        val endDate = detail.latestEndDate
                        Text(
                            when {
                                endDate == null -> "No membership yet"
                                detail.status == MembershipStatus.EXPIRED -> "Ended ${endDate.formatFull()}"
                                else -> "Ends ${endDate.formatFull()}"
                            },
                            style = AkhadaTheme.typography.body,
                        )
                        if (detail.memberships.isNotEmpty()) {
                            Text(
                                if (detail.totalDue > 0) "${formatRupees(detail.totalDue)} due" else "Nothing due",
                                style = AkhadaTheme.typography.bodyStrong,
                                color = if (detail.totalDue > 0) AkhadaTheme.colors.statusDue else AkhadaTheme.colors.statusActive,
                            )
                        }
                    }
                }
                val facts = listOfNotNull(
                    member.phone?.let(::formatPhone),
                    member.gender?.label(),
                    member.dateOfBirth?.let { "Born ${it.formatFull()}" },
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
                    if (detail.memberships.isEmpty()) "Sell membership" else "Renew membership",
                    onSellMembership,
                    Modifier.fillMaxWidth(),
                )
                if (owing != null) {
                    AkhadaButton("Record payment", { vm.openPaymentForm(owing.id) }, Modifier.fillMaxWidth(), kind = ButtonKind.Secondary)
                }
            }
        }
        if (detail.memberships.isNotEmpty()) {
            item { SectionHeader("History", Modifier.screenGutter().padding(top = AkhadaSpace.md)) }
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
            title = "Delete ${member.name}?",
            body = "This removes their memberships and payments too, and can't be undone. Archive them instead to keep their history.",
            confirmLabel = "Delete member",
            onConfirm = { confirm = null; vm.deleteMemberPermanently() },
            onDismiss = { confirm = null },
        )
        is Confirm.DeleteMembership -> ConfirmSheet(
            title = "Delete ${c.membership.planName}?",
            body = "Its ${c.membership.payments.size} payment(s) are deleted with it. Use this only to fix a mistake.",
            confirmLabel = "Delete membership",
            onConfirm = { confirm = null; vm.deleteMembership(c.membership.id) },
            onDismiss = { confirm = null },
        )
        is Confirm.DeletePayment -> ConfirmSheet(
            title = "Delete payment of ${formatRupees(c.payment.amount)}?",
            body = "Paid on ${c.payment.paidOn.formatFull()} by ${c.payment.mode.label()}. The amount goes back to due.",
            confirmLabel = "Delete payment",
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
            Text("Record payment", style = AkhadaTheme.typography.title)
            Text("${formatRupees(form.amountDue)} due on $planName", style = AkhadaTheme.typography.caption, color = AkhadaTheme.colors.inkMuted)
        }
        AkhadaTextField(
            form.amountText, onAmountChange, "Amount",
            prefix = "₹", keyboardType = KeyboardType.Number, error = form.error,
        )
        SegmentedControl(PaymentMode.entries, form.mode, { it?.let(onModeChange) }, { it.label() })
        AkhadaTextField(form.note, onNoteChange, "Note", optional = true, placeholder = "e.g. paid by brother")
        val amount = com.harsh.gymmanager.presentation.parseRupees(form.amountText)
        AkhadaButton(
            if (form.isSaving) "Saving…" else if (amount != null && amount > 0) "Record ${formatRupees(amount)}" else "Record payment",
            onSubmit,
            Modifier.fillMaxWidth(),
            enabled = !form.isSaving,
        )
    }
}
