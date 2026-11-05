package com.harsh.gymmanager.ui.screens.bulkrenew

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.harsh.gymmanager.LocalAppDependencies
import com.harsh.gymmanager.domain.model.PaymentMode
import com.harsh.gymmanager.presentation.bulkrenew.BulkRenewEvent
import com.harsh.gymmanager.presentation.bulkrenew.BulkRenewViewModel
import com.harsh.gymmanager.presentation.formatRupees
import com.harsh.gymmanager.ui.components.AkhadaButton
import com.harsh.gymmanager.ui.components.AkhadaCard
import com.harsh.gymmanager.ui.components.Avatar
import com.harsh.gymmanager.ui.components.EmptyState
import com.harsh.gymmanager.ui.components.PlanCard
import com.harsh.gymmanager.ui.components.PushedHeader
import com.harsh.gymmanager.ui.components.SectionHeader
import com.harsh.gymmanager.ui.components.SegmentedControl
import com.harsh.gymmanager.ui.components.label
import com.harsh.gymmanager.ui.format.formatDateRange
import com.harsh.gymmanager.ui.icons.AkhadaIcons
import com.harsh.gymmanager.ui.theme.AkhadaSpace
import com.harsh.gymmanager.ui.theme.AkhadaTheme
import com.harsh.gymmanager.ui.theme.screenGutter

@Composable
fun BulkRenewScreen(memberIds: Set<Long>, onBack: () -> Unit, onDone: (Int) -> Unit, onAddPlan: () -> Unit) {
    val deps = LocalAppDependencies.current
    val colors = AkhadaTheme.colors
    val vm = viewModel(key = "bulk-renew-${memberIds.sorted().joinToString(",")}") {
        BulkRenewViewModel(memberIds, deps.planUseCases, deps.bulkRenew)
    }
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(vm) { vm.events.collect { if (it is BulkRenewEvent.Renewed) onDone(it.count) } }

    val who = if (memberIds.size == 1) "1 member" else "${memberIds.size} members"
    Column(Modifier.fillMaxSize()) {
        PushedHeader("Renew $who", onBack, subtitle = "Each member continues from their own end date")
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = AkhadaSpace.xl),
            verticalArrangement = Arrangement.spacedBy(AkhadaSpace.md),
        ) {
            SectionHeader("Plan", Modifier.screenGutter())
            if (!state.isLoading && state.plans.isEmpty()) {
                EmptyState(AkhadaIcons.Tag, "No plans yet", "Add a plan first, then come back to renew.", actionLabel = "Add plan", onAction = onAddPlan)
                return@Column
            }
            state.plans.forEach { plan ->
                PlanCard(plan, onClick = { vm.onPlanSelect(plan) }, modifier = Modifier.screenGutter(), selected = plan.id == state.selectedPlan?.id)
            }
            if (state.selectedPlan == null) return@Column

            SectionHeader("Payment", Modifier.screenGutter().padding(top = AkhadaSpace.md))
            AkhadaCard(Modifier.screenGutter()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Mark all as paid in full", style = AkhadaTheme.typography.bodyStrong)
                        Text(
                            if (state.markPaid) "${formatRupees(state.total)} recorded today" else "Each member will owe ${formatRupees(state.selectedPlan!!.defaultPrice)}",
                            style = AkhadaTheme.typography.caption,
                            color = colors.inkMuted,
                        )
                    }
                    Switch(
                        checked = state.markPaid,
                        onCheckedChange = vm::onMarkPaidChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = colors.onInk, checkedTrackColor = colors.ink,
                            uncheckedThumbColor = colors.inkMuted, uncheckedTrackColor = colors.surfaceSunken, uncheckedBorderColor = colors.surfaceSunken,
                        ),
                    )
                }
                if (state.markPaid) {
                    SegmentedControl(PaymentMode.entries, state.paymentMode, { it?.let(vm::onPaymentModeChange) }, { it.label() })
                }
            }

            SectionHeader("New dates", Modifier.screenGutter().padding(top = AkhadaSpace.md))
            if (state.items.size < memberIds.size && state.items.isNotEmpty()) {
                Text(
                    "Archived members are left out.",
                    Modifier.screenGutter(),
                    style = AkhadaTheme.typography.caption,
                    color = colors.inkMuted,
                )
            }
            state.items.forEach { item ->
                AkhadaCard(Modifier.screenGutter()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AkhadaSpace.md)) {
                        Avatar(item.member.name, item.member.photoPath)
                        Column(Modifier.weight(1f)) {
                            Text(item.member.name, style = AkhadaTheme.typography.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(formatDateRange(item.startDate, item.endDate), style = AkhadaTheme.typography.caption, color = colors.inkMuted)
                        }
                    }
                }
            }
        }
        Column(Modifier.screenGutter().navigationBarsPadding().padding(vertical = AkhadaSpace.md), verticalArrangement = Arrangement.spacedBy(AkhadaSpace.sm)) {
            state.errorMessage?.let { Text(it, style = AkhadaTheme.typography.caption, color = colors.statusDue) }
            val count = state.items.size
            AkhadaButton(
                when {
                    state.isSaving -> "Saving…"
                    state.selectedPlan == null -> "Choose a plan"
                    count == 1 -> "Renew 1 member"
                    else -> "Renew $count members"
                },
                vm::save,
                Modifier.fillMaxWidth(),
                enabled = !state.isSaving && state.selectedPlan != null && count > 0,
            )
        }
    }
}
