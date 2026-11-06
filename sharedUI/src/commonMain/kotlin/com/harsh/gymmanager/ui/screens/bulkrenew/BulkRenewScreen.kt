package com.harsh.gymmanager.ui.screens.bulkrenew

import com.harsh.gymmanager.ui.isOwnerOrNoAccount
import com.harsh.gymmanager.domain.suggestions.mostUsedFirst
import com.harsh.gymmanager.ui.components.rememberUsageStats
import gymmanager.sharedui.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.pluralStringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.harsh.gymmanager.ui.components.MyGymButton
import com.harsh.gymmanager.ui.components.MyGymCard
import com.harsh.gymmanager.ui.components.MyGymSwitch
import com.harsh.gymmanager.ui.components.Avatar
import com.harsh.gymmanager.ui.components.EmptyState
import com.harsh.gymmanager.ui.components.PlanCard
import com.harsh.gymmanager.ui.components.PushedHeader
import com.harsh.gymmanager.ui.components.SectionHeader
import com.harsh.gymmanager.ui.components.SegmentedControl
import com.harsh.gymmanager.ui.format.asString
import com.harsh.gymmanager.ui.format.label
import com.harsh.gymmanager.ui.format.formatDateRange
import com.harsh.gymmanager.ui.icons.MyGymIcons
import com.harsh.gymmanager.ui.theme.MyGymSpace
import com.harsh.gymmanager.ui.theme.MyGymTheme
import com.harsh.gymmanager.ui.theme.screenGutter

@Composable
fun BulkRenewScreen(memberIds: Set<Long>, onBack: () -> Unit, onDone: (Int) -> Unit, onAddPlan: () -> Unit) {
    val deps = LocalAppDependencies.current
    val colors = MyGymTheme.colors
    val vm = viewModel(key = "bulk-renew-${memberIds.sorted().joinToString(",")}") {
        BulkRenewViewModel(memberIds, deps.planUseCases, deps.bulkRenew)
    }
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(vm) { vm.events.collect { if (it is BulkRenewEvent.Renewed) onDone(it.count) } }

    Column(Modifier.fillMaxSize()) {
        PushedHeader(pluralStringResource(Res.plurals.renew_members_title, memberIds.size, memberIds.size), onBack, subtitle = stringResource(Res.string.bulk_renew_subtitle))
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = MyGymSpace.xl),
            verticalArrangement = Arrangement.spacedBy(MyGymSpace.md),
        ) {
            SectionHeader(stringResource(Res.string.section_plan), Modifier.screenGutter())
            if (!state.isLoading && state.plans.isEmpty()) {
                EmptyState(MyGymIcons.Tag, stringResource(Res.string.no_plans_title), stringResource(Res.string.no_plans_renew_body), actionLabel = if (isOwnerOrNoAccount) stringResource(Res.string.add_plan) else null, onAction = onAddPlan)
                return@Column
            }
            state.plans.mostUsedFirst(rememberUsageStats().value).forEach { plan ->
                PlanCard(plan, onClick = { vm.onPlanSelect(plan) }, modifier = Modifier.screenGutter(), selected = plan.id == state.selectedPlan?.id)
            }
            if (state.selectedPlan == null) return@Column

            SectionHeader(stringResource(Res.string.section_payment), Modifier.screenGutter().padding(top = MyGymSpace.md))
            MyGymCard(Modifier.screenGutter()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(Res.string.mark_all_paid), style = MyGymTheme.typography.bodyStrong)
                        Text(
                            if (state.markPaid) stringResource(Res.string.recorded_today, formatRupees(state.total)) else stringResource(Res.string.each_will_owe, formatRupees(state.selectedPlan!!.defaultPrice)),
                            style = MyGymTheme.typography.caption,
                            color = colors.inkMuted,
                        )
                    }
                    MyGymSwitch(
                        checked = state.markPaid,
                        onCheckedChange = vm::onMarkPaidChange,
                    )
                }
                if (state.markPaid) {
                    SegmentedControl(PaymentMode.entries, state.paymentMode, { it?.let(vm::onPaymentModeChange) }, { it.label() })
                }
            }

            SectionHeader(stringResource(Res.string.section_new_dates), Modifier.screenGutter().padding(top = MyGymSpace.md))
            if (state.items.size < memberIds.size && state.items.isNotEmpty()) {
                Text(
                    stringResource(Res.string.archived_left_out),
                    Modifier.screenGutter(),
                    style = MyGymTheme.typography.caption,
                    color = colors.inkMuted,
                )
            }
            state.items.forEach { item ->
                MyGymCard(Modifier.screenGutter()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MyGymSpace.md)) {
                        Avatar(item.member.name, item.member.photoPath)
                        Column(Modifier.weight(1f)) {
                            Text(item.member.name, style = MyGymTheme.typography.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(formatDateRange(item.startDate, item.endDate), style = MyGymTheme.typography.caption, color = colors.inkMuted)
                        }
                    }
                }
            }
        }
        Column(Modifier.screenGutter().navigationBarsPadding().padding(vertical = MyGymSpace.md), verticalArrangement = Arrangement.spacedBy(MyGymSpace.sm)) {
            state.errorMessage?.let { Text(it.asString(), style = MyGymTheme.typography.caption, color = colors.statusDue) }
            val count = state.items.size
            MyGymButton(
                when {
                    state.isSaving -> stringResource(Res.string.saving)
                    state.selectedPlan == null -> stringResource(Res.string.choose_a_plan)
                    else -> pluralStringResource(Res.plurals.renew_members_button, count, count)
                },
                vm::save,
                Modifier.fillMaxWidth(),
                enabled = !state.isSaving && state.selectedPlan != null && count > 0,
            )
        }
    }
}
