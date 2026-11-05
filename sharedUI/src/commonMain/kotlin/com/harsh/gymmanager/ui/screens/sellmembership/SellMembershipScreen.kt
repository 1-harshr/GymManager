package com.harsh.gymmanager.ui.screens.sellmembership

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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.harsh.gymmanager.LocalAppDependencies
import com.harsh.gymmanager.domain.model.PaymentMode
import com.harsh.gymmanager.presentation.formatRupees
import com.harsh.gymmanager.presentation.sellmembership.SellMembershipEvent
import com.harsh.gymmanager.presentation.sellmembership.SellMembershipViewModel
import com.harsh.gymmanager.ui.components.AkhadaButton
import com.harsh.gymmanager.ui.components.AkhadaCard
import com.harsh.gymmanager.ui.components.AkhadaTextField
import com.harsh.gymmanager.ui.components.DateField
import com.harsh.gymmanager.ui.components.EmptyState
import com.harsh.gymmanager.ui.components.PlanCard
import com.harsh.gymmanager.ui.components.PushedHeader
import com.harsh.gymmanager.ui.components.SectionHeader
import com.harsh.gymmanager.ui.components.SegmentedControl
import com.harsh.gymmanager.ui.components.label
import com.harsh.gymmanager.ui.icons.AkhadaIcons
import com.harsh.gymmanager.ui.theme.AkhadaSpace
import com.harsh.gymmanager.ui.theme.AkhadaTheme
import com.harsh.gymmanager.ui.theme.screenGutter

@Composable
fun SellMembershipScreen(memberId: Long, onBack: () -> Unit, onSold: () -> Unit, onAddPlan: () -> Unit) {
    val deps = LocalAppDependencies.current
    val vm = viewModel(key = "sell-$memberId") { SellMembershipViewModel(memberId, deps.planUseCases, deps.membershipUseCases) }
    val state by vm.state.collectAsStateWithLifecycle()
    val memberFlow = remember(memberId) { deps.memberUseCases.observeMember(memberId) }
    val member by memberFlow.collectAsState(null)

    LaunchedEffect(vm) {
        vm.events.collect { if (it is SellMembershipEvent.Sold) onSold() }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        PushedHeader("New membership", onBack, subtitle = member?.name)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = AkhadaSpace.xl),
            verticalArrangement = Arrangement.spacedBy(AkhadaSpace.md),
        ) {
            SectionHeader("Plan", Modifier.screenGutter())
            if (!state.isLoading && state.plans.isEmpty()) {
                EmptyState(AkhadaIcons.Tag, "No plans yet", "Add a plan first, then come back to sell it.", actionLabel = "Add plan", onAction = onAddPlan)
                return@Column
            }
            state.plans.forEach { plan ->
                PlanCard(plan, onClick = { vm.onPlanSelect(plan) }, modifier = Modifier.screenGutter(), selected = plan.id == state.selectedPlan?.id)
            }
            if (state.selectedPlan == null) return@Column

            SectionHeader("Dates and price", Modifier.screenGutter().padding(top = AkhadaSpace.md))
            Column(Modifier.screenGutter(), verticalArrangement = Arrangement.spacedBy(AkhadaSpace.lg)) {
                Row(horizontalArrangement = Arrangement.spacedBy(AkhadaSpace.md)) {
                    DateField(state.startDate, { it?.let(vm::onStartDateChange) }, "Starts", Modifier.weight(1f))
                    DateField(state.endDate, { it?.let(vm::onEndDateChange) }, "Ends", Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(AkhadaSpace.md)) {
                    AkhadaTextField(state.priceText, vm::onPriceChange, "Price", Modifier.weight(1f), prefix = "₹", keyboardType = KeyboardType.Number)
                    AkhadaTextField(state.discountText, vm::onDiscountChange, "Discount", Modifier.weight(1f), optional = true, prefix = "₹", keyboardType = KeyboardType.Number, placeholder = "0")
                }
            }

            SectionHeader("Paid now", Modifier.screenGutter().padding(top = AkhadaSpace.md))
            Column(Modifier.screenGutter(), verticalArrangement = Arrangement.spacedBy(AkhadaSpace.lg)) {
                AkhadaTextField(
                    state.firstPaymentText, vm::onFirstPaymentChange, "Amount paid",
                    optional = true, prefix = "₹", keyboardType = KeyboardType.Number, placeholder = "Leave empty if paying later",
                )
                SegmentedControl(PaymentMode.entries, state.paymentMode, { it?.let(vm::onPaymentModeChange) }, { it.label() })
                AkhadaTextField(
                    state.notes, vm::onNotesChange, "Notes", optional = true,
                    singleLine = false, capitalization = KeyboardCapitalization.Sentences,
                )
                AkhadaCard {
                    SummaryLine("Charged", state.amountCharged?.let(::formatRupees) ?: "–")
                    SummaryLine(
                        "Due after this",
                        state.amountDueAfterPayment?.let { if (it == 0L) "Nothing" else formatRupees(it) } ?: "–",
                        due = (state.amountDueAfterPayment ?: 0) > 0,
                    )
                }
            }
        }
        Column(Modifier.screenGutter().navigationBarsPadding().padding(vertical = AkhadaSpace.md), verticalArrangement = Arrangement.spacedBy(AkhadaSpace.sm)) {
            state.errorMessage?.let { Text(it, style = AkhadaTheme.typography.caption, color = AkhadaTheme.colors.statusDue) }
            AkhadaButton(
                if (state.isSaving) "Saving…" else "Save membership",
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
        Text(label, Modifier.weight(1f), style = AkhadaTheme.typography.body, color = AkhadaTheme.colors.inkMuted)
        Text(
            value,
            style = AkhadaTheme.typography.bodyStrong.copy(fontFeatureSettings = "tnum"),
            color = if (due) AkhadaTheme.colors.statusDue else AkhadaTheme.colors.ink,
        )
    }
}
