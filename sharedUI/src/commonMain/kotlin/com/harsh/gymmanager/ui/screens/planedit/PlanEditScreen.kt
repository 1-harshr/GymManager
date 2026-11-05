package com.harsh.gymmanager.ui.screens.planedit

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
import com.harsh.gymmanager.ui.theme.AkhadaSpace
import com.harsh.gymmanager.ui.theme.AkhadaTheme
import com.harsh.gymmanager.ui.theme.screenGutter

/** Adds a plan when [planId] is null. */
@Composable
fun PlanEditScreen(planId: Long?, onBack: () -> Unit, onSaved: () -> Unit) {
    val deps = LocalAppDependencies.current
    val vm = viewModel(key = "plan-edit-$planId") { PlanEditViewModel(planId, deps.planUseCases) }
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                PlanEditEvent.Saved -> onSaved()
                PlanEditEvent.NotFound -> onBack()
            }
        }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        PushedHeader(if (state.isEditing) "Edit plan" else "New plan", onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).screenGutter(),
            verticalArrangement = Arrangement.spacedBy(AkhadaSpace.lg),
        ) {
            AkhadaTextField(
                state.name, vm::onNameChange, "Plan name",
                error = state.nameError, placeholder = "e.g. Gym · Monthly", capitalization = KeyboardCapitalization.Words,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(AkhadaSpace.md), verticalAlignment = Alignment.Bottom) {
                AkhadaTextField(
                    state.durationText, vm::onDurationChange, "Length",
                    Modifier.weight(1f), error = state.durationError, keyboardType = KeyboardType.Number,
                )
                SegmentedControl(
                    listOf(DurationUnit.DAYS, DurationUnit.MONTHS), state.durationUnit,
                    { it?.let(vm::onDurationUnitChange) },
                    { if (it == DurationUnit.DAYS) "Days" else "Months" },
                    Modifier.weight(1.2f).padding(bottom = if (state.durationError != null) 24.dp else 6.dp),
                )
            }
            AkhadaTextField(
                state.priceText, vm::onPriceChange, "Price",
                error = state.priceError, prefix = "₹", keyboardType = KeyboardType.Number,
            )
            if (state.isEditing) {
                Text(
                    "Changes apply to memberships you sell from now on. Ones already sold keep their price and dates.",
                    style = AkhadaTheme.typography.caption,
                    color = AkhadaTheme.colors.inkMuted,
                )
            }
            state.errorMessage?.let { Text(it, style = AkhadaTheme.typography.caption, color = AkhadaTheme.colors.statusDue) }
        }
        AkhadaButton(
            if (state.isSaving) "Saving…" else if (state.isEditing) "Save changes" else "Add plan",
            vm::save,
            Modifier.fillMaxWidth().screenGutter().navigationBarsPadding().padding(vertical = AkhadaSpace.md),
            enabled = !state.isSaving && !state.isLoading,
        )
    }
}
