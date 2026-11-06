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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus

data class SellMembershipUiState(
    val isLoading: Boolean = true,
    val plans: List<Plan> = emptyList(),
    val selectedPlan: Plan? = null,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    /** Length in days, both ends counted; kept in step with [endDate]. */
    val daysText: String = "",
    val priceText: String = "",
    val discountText: String = "",
    val firstPaymentText: String = "",
    /** Until the owner edits it, the amount paid follows the price: most members pay in full at the counter. */
    val firstPaymentEdited: Boolean = false,
    val paymentMode: PaymentMode = PaymentMode.CASH,
    val notes: String = "",
    val errorMessage: UiMessage? = null,
    val isSaving: Boolean = false,
    /** True when the member has had a membership before, so this one is a renewal. */
    val isRenewal: Boolean = false,
    /** True once the owner has edited anything, so leaving asks before throwing it away. */
    val hasChanges: Boolean = false,
) {
    /** Keeps the amount paid equal to the charge while the owner hasn't typed their own. */
    internal fun withPaidInFull(): SellMembershipUiState =
        if (firstPaymentEdited) this else copy(firstPaymentText = amountCharged?.toString().orEmpty())

    private val price: Long? get() = parseRupees(priceText)
    private val discount: Long? get() = if (discountText.isBlank()) 0 else parseRupees(discountText)
    private val firstPayment: Long? get() = if (firstPaymentText.isBlank()) 0 else parseRupees(firstPaymentText)

    /** True when this sale's length or price differs from the chosen plan. A discount is normal, not a change. */
    val isCustomised: Boolean
        get() {
            val plan = selectedPlan ?: return false
            val start = startDate ?: return false
            return endDate != plan.endDateFor(start) || price != plan.defaultPrice
        }

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
        startDate == null || endDate == null || daysText.toIntOrNull()?.takeIf { it >= 1 } == null -> UiMessage.ChooseDates
        price == null -> UiMessage.EnterPrice
        discount == null -> UiMessage.EnterDiscount
        amountCharged == null -> UiMessage.Invalid(ValidationError.DISCOUNT_TOO_LARGE)
        firstPayment == null -> UiMessage.EnterAmountPaid
        amountDueAfterPayment == null -> UiMessage.AmountPaidTooHigh(amountCharged!!)
        else -> null
    }
}

sealed interface SellMembershipEvent {
    data class Sold(val membershipId: String) : SellMembershipEvent
}

/** Sells a new membership or renews one; the form is pre-filled from the chosen plan, a renewal's from the last one. */
class SellMembershipViewModel(
    private val memberId: String,
    private val planUseCases: PlanUseCases,
    private val membershipUseCases: MembershipUseCases,
) : ViewModel() {
    private val _state = MutableStateFlow(SellMembershipUiState())
    val state: StateFlow<SellMembershipUiState> = _state.asStateFlow()

    private val eventChannel = Channel<SellMembershipEvent>(Channel.BUFFERED)
    val events: Flow<SellMembershipEvent> = eventChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            // Newest first, so a renewal starts on the plan the member is on now.
            val history = membershipUseCases.observeMemberships(memberId).first()
            val currentPlanId = history.firstOrNull()?.planId
            _state.update { it.copy(isRenewal = history.isNotEmpty()) }
            var known: Set<String>? = null
            planUseCases.observePlans().collect { plans ->
                _state.update { it.copy(isLoading = false, plans = plans) }
                if (known == null) {
                    plans.firstOrNull { it.id == currentPlanId }?.let { selectPlan(it, byOwner = false) }
                } else {
                    // A plan added from this screen comes back selected, ready to sell.
                    val added = plans.filter { it.id !in known.orEmpty() }
                    if (added.size == 1) selectPlan(added.single(), byOwner = true)
                }
                known = plans.map { it.id }.toSet()
            }
        }
    }

    fun onPlanSelect(plan: Plan) = selectPlan(plan, byOwner = true)

    /** [byOwner] is false when the form picks the plan itself, which isn't a change worth asking about. */
    private fun selectPlan(plan: Plan, byOwner: Boolean) {
        viewModelScope.launch {
            val quote = membershipUseCases.quoteMembership(memberId, plan)
            _state.update {
                it.copy(
                    selectedPlan = plan,
                    startDate = quote.startDate,
                    endDate = quote.endDate,
                    daysText = lengthInDays(quote.startDate, quote.endDate).toString(),
                    priceText = quote.price.toString(),
                    discountText = "",
                    errorMessage = null,
                    hasChanges = it.hasChanges || byOwner,
                ).withPaidInFull()
            }
        }
    }

    /**
     * Moving the start keeps the length: the plan's own length (so months stay calendar months)
     * unless the days were changed for this sale, in which case that many days.
     */
    fun onStartDateChange(date: LocalDate) = _state.update {
        val plan = it.selectedPlan
        val start = it.startDate
        val keptPlanLength = plan != null && start != null && it.endDate == plan.endDateFor(start)
        val days = it.daysText.toIntOrNull()?.takeIf { d -> d >= 1 }
        val end = when {
            keptPlanLength -> plan!!.endDateFor(date)
            days != null -> date.plus(days - 1, DateTimeUnit.DAY)
            else -> it.endDate
        }
        it.copy(startDate = date, endDate = end, daysText = end?.let { e -> lengthInDays(date, e).toString() } ?: it.daysText, errorMessage = null, hasChanges = true)
    }

    fun onEndDateChange(date: LocalDate) = _state.update {
        val start = it.startDate
        it.copy(endDate = date, daysText = start?.let { s -> lengthInDays(s, date).toString() } ?: it.daysText, errorMessage = null, hasChanges = true)
    }

    /** Typing a length moves the end date; an unusable number waits until it is fixed. */
    fun onDaysChange(text: String) = _state.update {
        val digits = text.filter(Char::isDigit).take(4)
        val days = digits.toIntOrNull()?.takeIf { d -> d >= 1 }
        val start = it.startDate
        it.copy(
            daysText = digits,
            endDate = if (days != null && start != null) start.plus(days - 1, DateTimeUnit.DAY) else it.endDate,
            errorMessage = null,
            hasChanges = true,
        )
    }

    /** Puts length and price back to the plan's, keeping the start date and any discount. */
    fun resetToPlan() {
        val plan = _state.value.selectedPlan ?: return
        _state.update {
            val start = it.startDate ?: return@update it
            val end = plan.endDateFor(start)
            it.copy(endDate = end, daysText = lengthInDays(start, end).toString(), priceText = plan.defaultPrice.toString(), errorMessage = null, hasChanges = true)
                .withPaidInFull()
        }
    }
    fun onPriceChange(text: String) = _state.update { it.copy(priceText = text, errorMessage = null, hasChanges = true).withPaidInFull() }
    fun onDiscountChange(text: String) = _state.update { it.copy(discountText = text, errorMessage = null, hasChanges = true).withPaidInFull() }
    fun onFirstPaymentChange(text: String) =
        _state.update { it.copy(firstPaymentText = text, firstPaymentEdited = true, errorMessage = null, hasChanges = true) }
    fun onPaymentModeChange(mode: PaymentMode) = _state.update { it.copy(paymentMode = mode, hasChanges = true) }
    fun onNotesChange(text: String) = _state.update { it.copy(notes = text, hasChanges = true) }

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

/** Days covered from [start] to [end], both included. */
private fun lengthInDays(start: LocalDate, end: LocalDate): Int = start.daysUntil(end) + 1
