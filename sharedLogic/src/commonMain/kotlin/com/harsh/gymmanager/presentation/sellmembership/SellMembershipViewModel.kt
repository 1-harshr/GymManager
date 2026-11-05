package com.harsh.gymmanager.presentation.sellmembership

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.harsh.gymmanager.domain.model.PaymentMode
import com.harsh.gymmanager.domain.model.Plan
import com.harsh.gymmanager.domain.usecase.MembershipUseCases
import com.harsh.gymmanager.domain.usecase.PlanUseCases
import com.harsh.gymmanager.domain.ValidationError
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
import kotlinx.datetime.LocalDate

data class SellMembershipUiState(
    val isLoading: Boolean = true,
    val plans: List<Plan> = emptyList(),
    val selectedPlan: Plan? = null,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val priceText: String = "",
    val discountText: String = "",
    val firstPaymentText: String = "",
    val paymentMode: PaymentMode = PaymentMode.CASH,
    val notes: String = "",
    val errorMessage: UiMessage? = null,
    val isSaving: Boolean = false,
) {
    private val price: Long? get() = parseRupees(priceText)
    private val discount: Long? get() = if (discountText.isBlank()) 0 else parseRupees(discountText)
    private val firstPayment: Long? get() = if (firstPaymentText.isBlank()) 0 else parseRupees(firstPaymentText)

    /** Price minus discount, or null while the inputs aren't valid numbers. */
    val amountCharged: Long?
        get() {
            val price = price ?: return null
            val discount = discount ?: return null
            return (price - discount).takeIf { it >= 0 }
        }

    /** What will still be owed after the first payment. */
    val amountDueAfterPayment: Long?
        get() {
            val charged = amountCharged ?: return null
            val paid = firstPayment ?: return null
            return (charged - paid).takeIf { it >= 0 }
        }

    internal fun validationError(): UiMessage? = when {
        selectedPlan == null -> UiMessage.ChoosePlan
        startDate == null || endDate == null -> UiMessage.ChooseDates
        price == null -> UiMessage.EnterPrice
        discount == null -> UiMessage.EnterDiscount
        amountCharged == null -> UiMessage.Invalid(ValidationError.DISCOUNT_TOO_LARGE)
        firstPayment == null -> UiMessage.EnterAmountPaid
        amountDueAfterPayment == null -> UiMessage.AmountPaidTooHigh(amountCharged!!)
        else -> null
    }
}

sealed interface SellMembershipEvent {
    data class Sold(val membershipId: Long) : SellMembershipEvent
}

/** Sells a new membership or renews one; the form is pre-filled from the chosen plan. */
class SellMembershipViewModel(
    private val memberId: Long,
    private val planUseCases: PlanUseCases,
    private val membershipUseCases: MembershipUseCases,
) : ViewModel() {
    private val _state = MutableStateFlow(SellMembershipUiState())
    val state: StateFlow<SellMembershipUiState> = _state.asStateFlow()

    private val eventChannel = Channel<SellMembershipEvent>(Channel.BUFFERED)
    val events: Flow<SellMembershipEvent> = eventChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            planUseCases.observePlans().collect { plans ->
                _state.update { it.copy(isLoading = false, plans = plans) }
            }
        }
    }

    fun onPlanSelect(plan: Plan) {
        viewModelScope.launch {
            val quote = membershipUseCases.quoteMembership(memberId, plan)
            _state.update {
                it.copy(
                    selectedPlan = plan,
                    startDate = quote.startDate,
                    endDate = quote.endDate,
                    priceText = quote.price.toString(),
                    errorMessage = null,
                )
            }
        }
    }

    /** Moving the start date moves the end date with it, based on the plan length. */
    fun onStartDateChange(date: LocalDate) = _state.update {
        it.copy(startDate = date, endDate = it.selectedPlan?.endDateFor(date) ?: it.endDate, errorMessage = null)
    }

    fun onEndDateChange(date: LocalDate) = _state.update { it.copy(endDate = date, errorMessage = null) }
    fun onPriceChange(text: String) = _state.update { it.copy(priceText = text, errorMessage = null) }
    fun onDiscountChange(text: String) = _state.update { it.copy(discountText = text, errorMessage = null) }
    fun onFirstPaymentChange(text: String) = _state.update { it.copy(firstPaymentText = text, errorMessage = null) }
    fun onPaymentModeChange(mode: PaymentMode) = _state.update { it.copy(paymentMode = mode) }
    fun onNotesChange(text: String) = _state.update { it.copy(notes = text) }

    fun save() {
        val current = _state.value
        if (current.isSaving) return
        current.validationError()?.let { error ->
            _state.update { it.copy(errorMessage = error) }
            return
        }
        _state.update { it.copy(isSaving = true, errorMessage = null) }
        viewModelScope.launch {
            membershipUseCases.sellMembership(
                memberId = memberId,
                plan = current.selectedPlan!!,
                startDate = current.startDate,
                endDate = current.endDate,
                price = parseRupees(current.priceText),
                discount = parseRupees(current.discountText) ?: 0,
                notes = current.notes,
                firstPaymentAmount = parseRupees(current.firstPaymentText),
                firstPaymentMode = current.paymentMode,
            )
                .onSuccess { eventChannel.send(SellMembershipEvent.Sold(it)) }
                .onFailure { e -> _state.update { it.copy(isSaving = false, errorMessage = e.toUiMessage()) } }
        }
    }
}
