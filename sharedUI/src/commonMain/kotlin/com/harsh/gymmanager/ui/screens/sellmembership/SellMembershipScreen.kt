package com.harsh.gymmanager.ui.screens.sellmembership

import com.harsh.gymmanager.ui.isOwnerOrNoAccount
import androidx.compose.ui.text.style.TextDecoration
import com.harsh.gymmanager.presentation.parseRupees
import com.harsh.gymmanager.domain.suggestions.mostUsedFirst
import com.harsh.gymmanager.domain.suggestions.discountSuggestions
import com.harsh.gymmanager.domain.suggestions.amountSuggestions
import com.harsh.gymmanager.ui.components.rememberUsageStats
import com.harsh.gymmanager.ui.components.SuggestionChips
import com.harsh.gymmanager.ui.components.AmountChips
import com.harsh.gymmanager.ui.components.rememberDiscardGuard
import gymmanager.sharedui.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.harsh.gymmanager.ui.components.GhostButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.harsh.gymmanager.LocalAppDependencies
import com.harsh.gymmanager.domain.model.PaymentMode
import com.harsh.gymmanager.presentation.formatRupees
import com.harsh.gymmanager.presentation.sellmembership.SellMembershipEvent
import com.harsh.gymmanager.presentation.sellmembership.SellMembershipViewModel
import com.harsh.gymmanager.ui.components.MyGymButton
import com.harsh.gymmanager.ui.components.MyGymCard
import com.harsh.gymmanager.ui.components.MyGymTextField
import com.harsh.gymmanager.ui.components.DateField
import com.harsh.gymmanager.ui.components.EmptyState
import com.harsh.gymmanager.ui.components.PlanCard
import com.harsh.gymmanager.ui.components.PushedHeader
import com.harsh.gymmanager.ui.components.SectionHeader
import com.harsh.gymmanager.ui.components.SegmentedControl
import com.harsh.gymmanager.ui.format.asString
import com.harsh.gymmanager.ui.format.label
import com.harsh.gymmanager.ui.icons.MyGymIcons
import com.harsh.gymmanager.ui.theme.MyGymSpace
import com.harsh.gymmanager.ui.theme.MyGymTheme
import com.harsh.gymmanager.ui.theme.screenGutter

@Composable
fun SellMembershipScreen(memberId: String, onBack: () -> Unit, onSold: (membershipId: String) -> Unit, onAddPlan: () -> Unit) {
    val deps = LocalAppDependencies.current
    val vm = viewModel(key = "sell-$memberId") { SellMembershipViewModel(memberId, deps.planUseCases, deps.membershipUseCases) }
    val state by vm.state.collectAsStateWithLifecycle()
    val back = rememberDiscardGuard(state.hasChanges, onBack)
    val usage by rememberUsageStats()
    val memberFlow = remember(memberId) { deps.memberUseCases.observeMember(memberId) }
    val member by memberFlow.collectAsState(null)

    LaunchedEffect(vm) {
        vm.events.collect { if (it is SellMembershipEvent.Sold) onSold(it.membershipId) }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        PushedHeader(
            stringResource(if (state.isRenewal) Res.string.renew_membership else Res.string.new_membership),
            back,
            subtitle = member?.name,
        )
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = MyGymSpace.xl),
            verticalArrangement = Arrangement.spacedBy(MyGymSpace.md),
        ) {
            SectionHeader(
                stringResource(Res.string.section_plan),
                Modifier.screenGutter(),
                action = if (state.plans.isNotEmpty()) stringResource(Res.string.new_plan) else null,
                onAction = onAddPlan,
            )
            if (!state.isLoading && state.plans.isEmpty()) {
                EmptyState(MyGymIcons.Tag, stringResource(Res.string.no_plans_title), stringResource(Res.string.no_plans_sell_body), actionLabel = if (isOwnerOrNoAccount) stringResource(Res.string.add_plan) else null, onAction = onAddPlan)
                return@Column
            }
            // The plans this gym sells most come first.
            state.plans.mostUsedFirst(usage).forEach { plan ->
                PlanCard(plan, onClick = { vm.onPlanSelect(plan) }, modifier = Modifier.screenGutter(), selected = plan.id == state.selectedPlan?.id)
            }
            if (state.selectedPlan == null) return@Column

            SectionHeader(stringResource(Res.string.dates_and_price), Modifier.screenGutter().padding(top = MyGymSpace.md))
            Column(Modifier.screenGutter(), verticalArrangement = Arrangement.spacedBy(MyGymSpace.lg)) {
                Row(horizontalArrangement = Arrangement.spacedBy(MyGymSpace.md)) {
                    DateField(state.startDate, { it?.let(vm::onStartDateChange) }, stringResource(Res.string.starts), Modifier.weight(1f))
                    DateField(state.endDate, { it?.let(vm::onEndDateChange) }, stringResource(Res.string.ends), Modifier.weight(1f))
                }
                MyGymTextField(state.daysText, vm::onDaysChange, stringResource(Res.string.length_days), keyboardType = KeyboardType.Number)
                MyGymTextField(state.priceText, vm::onPriceChange, stringResource(Res.string.price), prefix = "₹", keyboardType = KeyboardType.Number)
                // Price, then the discount with its quick picks, then what that leaves to pay.
                Column(verticalArrangement = Arrangement.spacedBy(MyGymSpace.sm)) {
                    MyGymTextField(state.discountText, vm::onDiscountChange, stringResource(Res.string.discount), optional = true, prefix = "₹", keyboardType = KeyboardType.Number, placeholder = "0")
                    val discounts = discountSuggestions(parseRupees(state.priceText) ?: 0, usage).filter { it != parseRupees(state.discountText) }
                    SuggestionChips(discounts.map { stringResource(Res.string.chip_discount, formatRupees(it)) }, onPick = { vm.onDiscountChange(discounts[it].toString()) })
                    DiscountedCharge(parseRupees(state.priceText), state.amountCharged)
                }
                if (state.isCustomised) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MyGymSpace.sm)) {
                        Text(
                            stringResource(Res.string.sale_customised, state.selectedPlan?.name.orEmpty()),
                            Modifier.weight(1f),
                            style = MyGymTheme.typography.caption,
                            color = MyGymTheme.colors.inkMuted,
                        )
                        GhostButton(stringResource(Res.string.reset_to_plan), vm::resetToPlan)
                    }
                }
            }

            SectionHeader(stringResource(Res.string.paid_now), Modifier.screenGutter().padding(top = MyGymSpace.md))
            Column(Modifier.screenGutter(), verticalArrangement = Arrangement.spacedBy(MyGymSpace.lg)) {
                Column(verticalArrangement = Arrangement.spacedBy(MyGymSpace.sm)) {
                    MyGymTextField(
                        state.firstPaymentText, vm::onFirstPaymentChange, stringResource(Res.string.amount_paid),
                        optional = true, prefix = "₹", keyboardType = KeyboardType.Number, placeholder = stringResource(Res.string.amount_paid_hint),
                    )
                    AmountChips(amountSuggestions(state.amountCharged ?: 0, usage, payLater = true), state.firstPaymentText, vm::onFirstPaymentChange)
                }
                SegmentedControl(PaymentMode.entries, state.paymentMode, { it?.let(vm::onPaymentModeChange) }, { it.label() })
                MyGymTextField(
                    state.notes, vm::onNotesChange, stringResource(Res.string.notes), optional = true,
                    singleLine = false, capitalization = KeyboardCapitalization.Sentences,
                )
                MyGymCard {
                    SummaryLine(stringResource(Res.string.charged), state.amountCharged?.let(::formatRupees) ?: "–")
                    SummaryLine(
                        stringResource(Res.string.due_after_this),
                        when (val due = state.amountDueAfterPayment) {
                            null -> "–"
                            0L -> stringResource(Res.string.nothing)
                            else -> formatRupees(due)
                        },
                        due = (state.amountDueAfterPayment ?: 0) > 0,
                    )
                }
            }
        }
        Column(Modifier.screenGutter().navigationBarsPadding().padding(vertical = MyGymSpace.md), verticalArrangement = Arrangement.spacedBy(MyGymSpace.sm)) {
            state.errorMessage?.let { Text(it.asString(), style = MyGymTheme.typography.caption, color = MyGymTheme.colors.statusDue) }
            MyGymButton(
                if (state.isSaving) stringResource(Res.string.saving) else stringResource(Res.string.save_membership),
                vm::save,
                Modifier.fillMaxWidth(),
                enabled = !state.isSaving && state.selectedPlan != null,
            )
        }
    }
}

@Composable
private fun SummaryLine(label: String, value: String, due: Boolean = false) {
    Row {
        Text(label, Modifier.weight(1f), style = MyGymTheme.typography.body, color = MyGymTheme.colors.inkMuted)
        Text(
            value,
            style = MyGymTheme.typography.bodyStrong.copy(fontFeatureSettings = "tnum"),
            color = if (due) MyGymTheme.colors.statusDue else MyGymTheme.colors.ink,
        )
    }
}

/** "~~₹1,500~~ ₹1,300 to pay": shown once a discount brings the charge below the price. */
@Composable
private fun DiscountedCharge(price: Long?, charged: Long?) {
    if (price == null || charged == null || charged >= price) return
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MyGymSpace.sm)) {
        Text(
            formatRupees(price),
            style = MyGymTheme.typography.body.copy(textDecoration = TextDecoration.LineThrough),
            color = MyGymTheme.colors.inkMuted,
        )
        Text(stringResource(Res.string.discounted_to_pay, formatRupees(charged)), style = MyGymTheme.typography.bodyStrong)
    }
}
