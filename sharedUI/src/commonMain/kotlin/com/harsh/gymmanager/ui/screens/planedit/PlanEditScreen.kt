package com.harsh.gymmanager.ui.screens.planedit

import com.harsh.gymmanager.ui.format.durationLabel
import com.harsh.gymmanager.domain.suggestions.PLAN_LENGTHS
import com.harsh.gymmanager.ui.components.SuggestionChips
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.harsh.gymmanager.LocalAppDependencies
import com.harsh.gymmanager.domain.model.DurationUnit
import com.harsh.gymmanager.presentation.planedit.PlanEditEvent
import com.harsh.gymmanager.presentation.planedit.PlanEditViewModel
import com.harsh.gymmanager.ui.components.AkhadaButton
import com.harsh.gymmanager.ui.components.AkhadaTextField
import com.harsh.gymmanager.ui.components.PushedHeader
import com.harsh.gymmanager.ui.components.SegmentedControl
import com.harsh.gymmanager.ui.format.asString
import com.harsh.gymmanager.ui.theme.AkhadaSpace
import com.harsh.gymmanager.ui.theme.AkhadaTheme
import com.harsh.gymmanager.ui.theme.screenGutter

/** Adds a plan when [planId] is null. */
@Composable
fun PlanEditScreen(planId: Long?, onBack: () -> Unit, onSaved: () -> Unit) {
    val deps = LocalAppDependencies.current
    val vm = viewModel(key = "plan-edit-$planId") { PlanEditViewModel(planId, deps.planUseCases) }
    val state by vm.state.collectAsStateWithLifecycle()
    val back = rememberDiscardGuard(state.hasChanges, onBack)

    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                PlanEditEvent.Saved -> onSaved()
                PlanEditEvent.NotFound -> onBack()
            }
        }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        PushedHeader(if (state.isEditing) stringResource(Res.string.edit_plan) else stringResource(Res.string.new_plan), back)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).screenGutter(),
            verticalArrangement = Arrangement.spacedBy(AkhadaSpace.lg),
        ) {
            AkhadaTextField(
                state.name, vm::onNameChange, stringResource(Res.string.plan_name),
                error = state.nameError?.asString(), placeholder = stringResource(Res.string.plan_name_hint), capitalization = KeyboardCapitalization.Words,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(AkhadaSpace.md), verticalAlignment = Alignment.Bottom) {
                AkhadaTextField(
                    state.durationText, vm::onDurationChange, stringResource(Res.string.plan_length),
                    Modifier.weight(1f), error = state.durationError?.asString(), keyboardType = KeyboardType.Number,
                )
                SegmentedControl(
                    listOf(DurationUnit.DAYS, DurationUnit.MONTHS), state.durationUnit,
                    { it?.let(vm::onDurationUnitChange) },
                    { if (it == DurationUnit.DAYS) stringResource(Res.string.unit_days) else stringResource(Res.string.unit_months) },
                    Modifier.weight(1.2f).padding(bottom = if (state.durationError != null) 24.dp else 6.dp),
                )
            }
            SuggestionChips(
                PLAN_LENGTHS.map { (value, unit) -> durationLabel(value, unit) },
                selected = PLAN_LENGTHS.indexOfFirst { (value, unit) -> state.durationText == value.toString() && state.durationUnit == unit }.takeIf { it >= 0 },
                onPick = { i ->
                    val (value, unit) = PLAN_LENGTHS[i]
                    vm.onDurationChange(value.toString())
                    vm.onDurationUnitChange(unit)
                },
            )
            AkhadaTextField(
                state.priceText, vm::onPriceChange, stringResource(Res.string.price),
                error = state.priceError?.asString(), prefix = "₹", keyboardType = KeyboardType.Number,
            )
            if (state.isEditing) {
                Text(
                    stringResource(Res.string.plan_edit_note),
                    style = AkhadaTheme.typography.caption,
                    color = AkhadaTheme.colors.inkMuted,
                )
            }
            state.errorMessage?.let { Text(it.asString(), style = AkhadaTheme.typography.caption, color = AkhadaTheme.colors.statusDue) }
        }
        AkhadaButton(
            if (state.isSaving) stringResource(Res.string.saving) else if (state.isEditing) stringResource(Res.string.save_changes) else stringResource(Res.string.add_plan),
            vm::save,
            Modifier.fillMaxWidth().screenGutter().navigationBarsPadding().padding(vertical = AkhadaSpace.md),
            enabled = !state.isSaving && !state.isLoading,
        )
    }
}
