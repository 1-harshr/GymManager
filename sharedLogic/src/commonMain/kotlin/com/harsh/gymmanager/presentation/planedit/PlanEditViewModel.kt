package com.harsh.gymmanager.presentation.planedit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.harsh.gymmanager.domain.ValidationError
import com.harsh.gymmanager.domain.model.DurationUnit
import com.harsh.gymmanager.domain.model.Plan
import com.harsh.gymmanager.domain.usecase.PlanUseCases
import com.harsh.gymmanager.presentation.parseRupees
import com.harsh.gymmanager.presentation.UiMessage
import com.harsh.gymmanager.presentation.toUiMessage
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlanEditUiState(
    val isEditing: Boolean,
    val isLoading: Boolean = isEditing,
    val name: String = "",
    val durationText: String = "1",
    val durationUnit: DurationUnit = DurationUnit.MONTHS,
    val priceText: String = "",
    val nameError: UiMessage? = null,
    val durationError: UiMessage? = null,
    val priceError: UiMessage? = null,
    val errorMessage: UiMessage? = null,
    val isSaving: Boolean = false,
    /** True once the owner has edited anything, so leaving asks before throwing it away. */
    val hasChanges: Boolean = false,
)

sealed interface PlanEditEvent {
    data object Saved : PlanEditEvent
    data object NotFound : PlanEditEvent
}

/** Adds a plan when [planId] is null, otherwise edits that plan. */
class PlanEditViewModel(
    private val planId: Long?,
    private val planUseCases: PlanUseCases,
) : ViewModel() {
    private val _state = MutableStateFlow(PlanEditUiState(isEditing = planId != null))
    val state: StateFlow<PlanEditUiState> = _state.asStateFlow()

    private val eventChannel = Channel<PlanEditEvent>(Channel.BUFFERED)
    val events: Flow<PlanEditEvent> = eventChannel.receiveAsFlow()

    private var existing: Plan? = null

    init {
        if (planId != null) {
            viewModelScope.launch {
                val plan = planUseCases.getPlan(planId)
                if (plan == null) {
                    eventChannel.send(PlanEditEvent.NotFound)
                    return@launch
                }
                existing = plan
                _state.update {
                    it.copy(
                        isLoading = false,
                        name = plan.name,
                        durationText = plan.durationValue.toString(),
                        durationUnit = plan.durationUnit,
                        priceText = plan.defaultPrice.toString(),
                    )
                }
            }
        }
    }

    fun onNameChange(value: String) = _state.update { it.copy(name = value, nameError = null, errorMessage = null, hasChanges = true) }

    fun onDurationChange(value: String) =
        _state.update { it.copy(durationText = value, durationError = null, errorMessage = null, hasChanges = true) }

    fun onDurationUnitChange(value: DurationUnit) = _state.update { it.copy(durationUnit = value, hasChanges = true) }

    fun onPriceChange(value: String) = _state.update { it.copy(priceText = value, priceError = null, errorMessage = null, hasChanges = true) }

    fun save() {
        val current = _state.value
        if (current.isSaving || current.isLoading) return
        val duration = current.durationText.trim().toIntOrNull()?.takeIf { it > 0 }
        val price = parseRupees(current.priceText)
        val nameError = if (current.name.isBlank()) UiMessage.Invalid(ValidationError.PLAN_NAME_REQUIRED) else null
        val durationError = if (duration == null) UiMessage.EnterDuration else null
        val priceError = if (price == null) UiMessage.EnterPrice else null
        if (nameError != null || durationError != null || priceError != null) {
            _state.update { it.copy(nameError = nameError, durationError = durationError, priceError = priceError) }
            return
        }
        _state.update { it.copy(isSaving = true, errorMessage = null) }
        viewModelScope.launch {
            val before = existing
            val result = if (before == null) {
                planUseCases.addPlan(current.name, duration!!, current.durationUnit, price!!).map { }
            } else {
                planUseCases.updatePlan(
                    before.copy(
                        name = current.name,
                        durationValue = duration!!,
                        durationUnit = current.durationUnit,
                        defaultPrice = price!!,
                    )
                )
            }
            result
                .onSuccess { eventChannel.send(PlanEditEvent.Saved) }
                .onFailure { e -> _state.update { it.copy(isSaving = false, errorMessage = e.toUiMessage()) } }
        }
    }
}
