package com.harsh.gymmanager.presentation.bulkrenew

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.harsh.gymmanager.domain.model.PaymentMode
import com.harsh.gymmanager.domain.model.Plan
import com.harsh.gymmanager.domain.usecase.BulkRenewItem
import com.harsh.gymmanager.domain.usecase.BulkRenewUseCase
import com.harsh.gymmanager.domain.usecase.PlanUseCases
import com.harsh.gymmanager.presentation.toUserMessage
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BulkRenewUiState(
    val isLoading: Boolean = true,
    val plans: List<Plan> = emptyList(),
    val selectedPlan: Plan? = null,
    /** Each member's renewal dates for the chosen plan. */
    val items: List<BulkRenewItem> = emptyList(),
    val markPaid: Boolean = false,
    val paymentMode: PaymentMode = PaymentMode.CASH,
    val errorMessage: String? = null,
    val isSaving: Boolean = false,
) {
    /** Whole rupees the renewals add up to at the plan price. */
    val total: Long get() = (selectedPlan?.defaultPrice ?: 0) * items.size
}

sealed interface BulkRenewEvent {
    data class Renewed(val count: Int) : BulkRenewEvent
}

/** Renews the members picked in select mode on one plan. */
class BulkRenewViewModel(
    private val memberIds: Set<Long>,
    private val planUseCases: PlanUseCases,
    private val bulkRenew: BulkRenewUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(BulkRenewUiState())
    val state: StateFlow<BulkRenewUiState> = _state.asStateFlow()

    private val eventChannel = Channel<BulkRenewEvent>(Channel.BUFFERED)
    val events: Flow<BulkRenewEvent> = eventChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            planUseCases.observePlans().collect { plans -> _state.update { it.copy(isLoading = false, plans = plans) } }
        }
    }

    fun onPlanSelect(plan: Plan) {
        _state.update { it.copy(selectedPlan = plan, errorMessage = null) }
        viewModelScope.launch {
            val items = bulkRenew.preview(memberIds, plan)
            _state.update { if (it.selectedPlan?.id == plan.id) it.copy(items = items) else it }
        }
    }

    fun onMarkPaidChange(value: Boolean) = _state.update { it.copy(markPaid = value) }

    fun onPaymentModeChange(mode: PaymentMode) = _state.update { it.copy(paymentMode = mode) }

    fun save() {
        val current = _state.value
        val plan = current.selectedPlan
        if (current.isSaving) return
        if (plan == null) {
            _state.update { it.copy(errorMessage = "Choose a plan") }
            return
        }
        _state.update { it.copy(isSaving = true, errorMessage = null) }
        viewModelScope.launch {
            bulkRenew(memberIds, plan, current.markPaid, current.paymentMode)
                .onSuccess { eventChannel.send(BulkRenewEvent.Renewed(it)) }
                .onFailure { e -> _state.update { it.copy(isSaving = false, errorMessage = e.toUserMessage()) } }
        }
    }
}
