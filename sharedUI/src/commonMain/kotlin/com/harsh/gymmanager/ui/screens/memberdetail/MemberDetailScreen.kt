package com.harsh.gymmanager.ui.screens.memberdetail

import com.harsh.gymmanager.ui.isOwnerOrNoAccount
import androidx.compose.ui.text.input.ImeAction
import com.harsh.gymmanager.domain.suggestions.amountSuggestions
import com.harsh.gymmanager.ui.components.LoadingMark
import com.harsh.gymmanager.ui.components.rememberUsageStats
import com.harsh.gymmanager.ui.components.AmountChips
import kotlinx.datetime.LocalDate
import com.harsh.gymmanager.ui.components.DateField
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
import com.harsh.gymmanager.domain.messaging.whatsAppNumber
import com.harsh.gymmanager.ui.LocalWhatsApp
import com.harsh.gymmanager.presentation.messaging.MessageRequest
import com.harsh.gymmanager.ui.components.MyGymBottomSheet
import com.harsh.gymmanager.ui.components.MyGymButton
import com.harsh.gymmanager.ui.components.MyGymCard
import com.harsh.gymmanager.ui.components.MyGymIconButton
import com.harsh.gymmanager.ui.components.MyGymTextField
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
import com.harsh.gymmanager.ui.format.formatTime
import com.harsh.gymmanager.ui.components.GhostButton
import com.harsh.gymmanager.domain.usecase.MemberAttendance
import kotlinx.datetime.daysUntil
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.harsh.gymmanager.domain.model.CheckIn
import com.harsh.gymmanager.domain.usecase.MEMBER_HEATMAP_WEEKS
import com.harsh.gymmanager.ui.format.shortMonthName
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import com.harsh.gymmanager.ui.format.resolve
import com.harsh.gymmanager.ui.format.formatPhone
import com.harsh.gymmanager.ui.format.label
import com.harsh.gymmanager.ui.format.rememberToday
import com.harsh.gymmanager.ui.format.statusLabel
import com.harsh.gymmanager.ui.icons.MyGymIcons
import com.harsh.gymmanager.ui.motion.SharedKeys
import com.harsh.gymmanager.ui.motion.sharedElement
import com.harsh.gymmanager.ui.theme.MyGymSpace
import com.harsh.gymmanager.ui.theme.MyGymTheme
import com.harsh.gymmanager.ui.theme.screenGutter

private sealed interface Confirm {
    data object DeleteMember : Confirm
    data class DeleteMembership(val membership: Membership) : Confirm
    data class DeletePayment(val payment: Payment) : Confirm
}

@Composable
fun MemberDetailScreen(
    memberId: String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onSellMembership: () -> Unit,
) {
    val deps = LocalAppDependencies.current
    val snackbar = LocalSnackbar.current
    val vm = viewModel(key = "member-$memberId") {
        MemberDetailViewModel(
            memberId, deps.observeMemberDetail, deps.memberUseCases, deps.membershipUseCases, deps.paymentUseCases,
            attendance = deps.attendance,
        )
    }
    val state by vm.state.collectAsStateWithLifecycle()
    // Staff don't archive or delete; that's the owner's call.
    val isOwner = isOwnerOrNoAccount
    var confirm by remember { mutableStateOf<Confirm?>(null) }
    val uriHandler = LocalUriHandler.current
    val offerWhatsApp = LocalWhatsApp.current
    val today = rememberToday()

    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                is MemberDetailEvent.ShowMessage -> snackbar.showSnackbar(event.message.resolve())
                is MemberDetailEvent.PaymentRecorded -> offerWhatsApp(MessageRequest.Receipt(memberId, event.membershipId, event.paymentId))
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
            EmptyState(MyGymIcons.Users, stringResource(Res.string.member_gone_title), stringResource(Res.string.member_gone_body))
        }
        return
    }
    if (detail == null) {
        Column(Modifier.fillMaxSize()) {
            PushedHeader("", onBack)
            LoadingMark(Modifier.weight(1f))
        }
        return
    }
    val member = detail.member
    // Newest first, so the last one owing is the oldest due; payments settle that first.
    val owingMemberships = detail.memberships.filter { it.amountDue > 0 }
    val oldestOwing = owingMemberships.lastOrNull()
    val renewalDue = detail.status == MembershipStatus.EXPIRING_SOON || detail.status == MembershipStatus.EXPIRED

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = MyGymSpace.xxl),
        verticalArrangement = Arrangement.spacedBy(MyGymSpace.md),
    ) {
        item {
            PushedHeader(
                member.name,
                onBack,
                subtitle = stringResource(Res.string.joined_on, member.joinedOn.formatFull()),
                titleModifier = Modifier.sharedElement(SharedKeys.memberName(memberId)),
            ) {
                if (whatsAppNumber(member.phone) != null) {
                    MyGymIconButton(MyGymIcons.MessageCircle, stringResource(Res.string.wa_message_member, member.name), onClick = { offerWhatsApp(MessageRequest.AnyFor(memberId)) }, iconSize = 20.dp)
                }
                member.phone?.let { phone ->
                    MyGymIconButton(MyGymIcons.Phone, stringResource(Res.string.call_person, member.name), onClick = { uriHandler.openUri("tel:$phone") }, iconSize = 20.dp)
                }
                MoreMenu(
                    actions = listOfNotNull(
                        MenuAction(stringResource(Res.string.edit_details), onClick = onEdit),
                        when {
                            !isOwner -> null
                            member.isArchived -> MenuAction(stringResource(Res.string.restore_member), onClick = vm::restoreMember)
                            else -> MenuAction(stringResource(Res.string.archive_member), onClick = vm::archiveMember)
                        },
                        if (isOwner) MenuAction(stringResource(Res.string.delete_permanently), destructive = true) { confirm = Confirm.DeleteMember } else null,
                    ),
                    contentDescription = stringResource(Res.string.more_for, member.name),
                )
            }
        }
        item {
            MyGymCard(Modifier.screenGutter()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MyGymSpace.lg)) {
                    Avatar(member.name, member.photoPath, Modifier.sharedElement(SharedKeys.memberPhoto(memberId)), size = 72.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MyGymSpace.xs)) {
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
                            style = MyGymTheme.typography.body,
                        )
                        if (detail.memberships.isNotEmpty()) {
                            Text(
                                if (detail.totalDue > 0) stringResource(Res.string.amount_due, formatRupees(detail.totalDue)) else stringResource(Res.string.nothing_due),
                                style = MyGymTheme.typography.bodyStrong,
                                color = if (detail.totalDue > 0) MyGymTheme.colors.statusDue else MyGymTheme.colors.statusActive,
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
                    Text(facts.joinToString(" · "), style = MyGymTheme.typography.caption, color = MyGymTheme.colors.inkMuted)
                }
                member.notes?.let { Text(it, style = MyGymTheme.typography.body) }
            }
        }
        item {
            // The main button is what this member needs next: a first membership, money owed, or a renewal.
            Column(Modifier.screenGutter(), verticalArrangement = Arrangement.spacedBy(MyGymSpace.md)) {
                val sellLabel = if (detail.memberships.isEmpty()) stringResource(Res.string.sell_membership) else stringResource(Res.string.renew_membership)
                when {
                    oldestOwing != null -> {
                        MyGymButton(stringResource(Res.string.record_payment), { vm.openPaymentForm(oldestOwing.id) }, Modifier.fillMaxWidth())
                        MyGymButton(sellLabel, onSellMembership, Modifier.fillMaxWidth(), kind = ButtonKind.Secondary)
                    }
                    detail.memberships.isEmpty() || renewalDue -> MyGymButton(sellLabel, onSellMembership, Modifier.fillMaxWidth())
                    else -> MyGymButton(sellLabel, onSellMembership, Modifier.fillMaxWidth(), kind = ButtonKind.Secondary)
                }
            }
        }
        val attendance = state.attendance
        if (attendance != null && !member.isArchived) {
            item { AttendanceCard(attendance, today, onCheckIn = vm::checkIn, onUndo = vm::undoCheckIn, modifier = Modifier.screenGutter()) }
        }
        if (detail.memberships.isNotEmpty()) {
            item { SectionHeader(stringResource(Res.string.history), Modifier.screenGutter().padding(top = MyGymSpace.md)) }
            items(detail.memberships, key = { it.id }) { membership ->
                MembershipCard(
                    membership,
                    today,
                    // Only needed to pick which one when more than one membership is owed on.
                    onRecordPayment = if (owingMemberships.size > 1) ({ vm.openPaymentForm(membership.id) }) else null,
                    onDelete = if (isOwner) ({ confirm = Confirm.DeleteMembership(membership) }) else null,
                    onPaymentClick = if (isOwner) ({ payment -> confirm = Confirm.DeletePayment(payment) }) else null,
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
            onDateChange = vm::onPaymentDateChange,
            today = today,
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

/** When the member last came and how often this month, with today's check-in or the button to make it. */
@Composable
private fun AttendanceCard(
    attendance: MemberAttendance,
    today: LocalDate,
    onCheckIn: () -> Unit,
    onUndo: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MyGymTheme.colors
    val lastVisit = attendance.lastVisit
    MyGymCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MyGymSpace.md)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MyGymSpace.xs)) {
                Text(stringResource(Res.string.attendance), style = MyGymTheme.typography.label, color = colors.inkMuted)
                Text(
                    when {
                        lastVisit == null -> stringResource(Res.string.no_visits_yet)
                        lastVisit == today -> stringResource(Res.string.came_in_today)
                        lastVisit.daysUntil(today) == 1 -> stringResource(Res.string.last_came_yesterday)
                        else -> lastVisit.daysUntil(today).let { pluralStringResource(Res.plurals.last_came_days_ago, it, it) }
                    },
                    style = MyGymTheme.typography.bodyStrong,
                )
                if (lastVisit != null) {
                    Text(
                        pluralStringResource(Res.plurals.visits_this_month, attendance.visitsThisMonth, attendance.visitsThisMonth),
                        style = MyGymTheme.typography.caption,
                        color = colors.inkMuted,
                    )
                }
            }
            val checkIn = attendance.today
            if (checkIn == null) {
                MyGymButton(stringResource(Res.string.check_in), onCheckIn, icon = MyGymIcons.UserCheck)
            } else {
                Column(horizontalAlignment = Alignment.End) {
                    Text(stringResource(Res.string.checked_in_at, checkIn.at.formatTime()), style = MyGymTheme.typography.caption, color = colors.statusActive)
                    GhostButton(stringResource(Res.string.undo), { onUndo(checkIn.id) })
                }
            }
        }
        if (lastVisit != null) VisitHeatmap(attendance.recent, today)
    }
}

/**
 * The last [MEMBER_HEATMAP_WEEKS] weeks as a grid, one column a week running Monday to Sunday,
 * with each day they came filled in.
 */
@Composable
private fun VisitHeatmap(visits: List<CheckIn>, today: LocalDate, modifier: Modifier = Modifier) {
    val colors = MyGymTheme.colors
    val firstMonday = today.minus(today.dayOfWeek.ordinal, DateTimeUnit.DAY).minus(MEMBER_HEATMAP_WEEKS - 1, DateTimeUnit.WEEK)
    val days = remember(visits) { visits.mapTo(HashSet()) { it.date } }
    val shown = days.count { it >= firstMonday }
    val summary = pluralStringResource(Res.plurals.visits_last_weeks, shown, shown, MEMBER_HEATMAP_WEEKS)
    Column(modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = summary }, verticalArrangement = Arrangement.spacedBy(MyGymSpace.xs)) {
        Row(horizontalArrangement = Arrangement.spacedBy(HEATMAP_GAP)) {
            repeat(MEMBER_HEATMAP_WEEKS) { week ->
                val monday = firstMonday.plus(week, DateTimeUnit.WEEK)
                // Each month is named over the week holding its first Monday.
                Box(Modifier.weight(1f)) {
                    if (monday.day <= 7) {
                        Text(monday.shortMonthName(), style = MyGymTheme.typography.caption, color = colors.inkMuted, maxLines = 1, softWrap = false)
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(HEATMAP_GAP)) {
            repeat(MEMBER_HEATMAP_WEEKS) { week ->
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(HEATMAP_GAP)) {
                    repeat(7) { weekday ->
                        val day = firstMonday.plus(week * 7 + weekday, DateTimeUnit.DAY)
                        val cell = Modifier.fillMaxWidth().aspectRatio(1f)
                        if (day > today) {
                            Spacer(cell)
                        } else {
                            Box(cell.clip(HEATMAP_CELL).background(if (day in days) colors.statusActive else colors.surfaceSunken))
                        }
                    }
                }
            }
        }
        Text(summary, style = MyGymTheme.typography.caption, color = colors.inkMuted)
    }
}

private val HEATMAP_GAP = 3.dp
private val HEATMAP_CELL = RoundedCornerShape(3.dp)

@Composable
private fun PaymentSheet(
    form: PaymentForm,
    planName: String,
    onAmountChange: (String) -> Unit,
    onModeChange: (PaymentMode) -> Unit,
    onDateChange: (LocalDate) -> Unit,
    today: LocalDate,
    onNoteChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
) {
    MyGymBottomSheet(onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(MyGymSpace.xs)) {
            Text(stringResource(Res.string.record_payment), style = MyGymTheme.typography.title)
            Text(stringResource(Res.string.due_on_plan, formatRupees(form.amountDue), planName), style = MyGymTheme.typography.caption, color = MyGymTheme.colors.inkMuted)
        }
        Column(verticalArrangement = Arrangement.spacedBy(MyGymSpace.sm)) {
            MyGymTextField(
                form.amountText, onAmountChange, stringResource(Res.string.amount),
                prefix = "₹", keyboardType = KeyboardType.Number, error = form.error?.asString(),
            )
            val usage by rememberUsageStats()
            AmountChips(amountSuggestions(form.amountDue, usage), form.amountText, onAmountChange)
        }
        SegmentedControl(PaymentMode.entries, form.mode, { it?.let(onModeChange) }, { it.label() })
        DateField(form.paidOn, { it?.let(onDateChange) }, stringResource(Res.string.paid_on_label), latest = today)
        MyGymTextField(form.note, onNoteChange, stringResource(Res.string.note), optional = true, placeholder = stringResource(Res.string.payment_note_hint), imeAction = ImeAction.Done)
        val amount = com.harsh.gymmanager.presentation.parseRupees(form.amountText)
        MyGymButton(
            if (form.isSaving) stringResource(Res.string.saving) else if (amount != null && amount > 0) stringResource(Res.string.record_amount, formatRupees(amount)) else stringResource(Res.string.record_payment),
            onSubmit,
            Modifier.fillMaxWidth(),
            enabled = !form.isSaving,
        )
    }
}
