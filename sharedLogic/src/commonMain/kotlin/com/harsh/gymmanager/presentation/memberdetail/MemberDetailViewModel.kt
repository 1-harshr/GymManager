package com.harsh.gymmanager.presentation.memberdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.harsh.gymmanager.domain.model.PaymentMode
import com.harsh.gymmanager.domain.usecase.MemberDetail
import com.harsh.gymmanager.domain.usecase.MemberUseCases
import com.harsh.gymmanager.domain.usecase.MembershipUseCases
import com.harsh.gymmanager.domain.usecase.ObserveMemberDetailUseCase
import com.harsh.gymmanager.domain.usecase.PaymentUseCases
import com.harsh.gymmanager.presentation.parseRupees
import com.harsh.gymmanager.presentation.UiMessage
import com.harsh.gymmanager.presentation.toUiMessage
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

/** The "record payment" sheet for one membership. */
data class PaymentForm(
    val membershipId: String,
    val amountDue: Long,
    val amountText: String,
    val mode: PaymentMode = PaymentMode.CASH,
    /** Today unless the owner is entering a payment taken earlier. */
    val paidOn: LocalDate,
    val note: String = "",
    val error: UiMessage? = null,
    val isSaving: Boolean = false,
)

data class MemberDetailUiState(
    val isLoading: Boolean = true,
    /** Null once loaded means the member no longer exists. */
    val detail: MemberDetail? = null,
    val paymentForm: PaymentForm? = null,
)

sealed interface MemberDetailEvent {
    data class ShowMessage(val message: UiMessage) : MemberDetailEvent
    data object MemberDeleted : MemberDetailEvent

    /** Sent just before the payment's snackbar message, so the screen can offer a receipt. */
    data class PaymentRecorded(val membershipId: String, val paymentId: String) : MemberDetailEvent
}

class MemberDetailViewModel(
    private val memberId: String,
    observeMemberDetail: ObserveMemberDetailUseCase,
    private val memberUseCases: MemberUseCases,
    private val membershipUseCases: MembershipUseCases,
    private val paymentUseCases: PaymentUseCases,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : ViewModel() {
    private val paymentForm = MutableStateFlow<PaymentForm?>(null)
    private val eventChannel = Channel<MemberDetailEvent>(Channel.BUFFERED)
    val events: Flow<MemberDetailEvent> = eventChannel.receiveAsFlow()

    val state: StateFlow<MemberDetailUiState> =
        combine(observeMemberDetail(memberId), paymentForm) { detail, form ->
            MemberDetailUiState(isLoading = false, detail = detail, paymentForm = form)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MemberDetailUiState())

    /** Opens the payment sheet pre-filled with what is still owed. */
    fun openPaymentForm(membershipId: String) {
        val membership = state.value.detail?.memberships?.find { it.id == membershipId } ?: return
        paymentForm.value = PaymentForm(
            membershipId = membershipId,
            amountDue = membership.amountDue,
            amountText = membership.amountDue.takeIf { it > 0 }?.toString().orEmpty(),
            paidOn = clock.todayIn(timeZone),
        )
    }

    fun onPaymentAmountChange(text: String) = paymentForm.update { it?.copy(amountText = text, error = null) }

    fun onPaymentModeChange(mode: PaymentMode) = paymentForm.update { it?.copy(mode = mode) }

    fun onPaymentDateChange(date: LocalDate) = paymentForm.update { it?.copy(paidOn = date) }

    fun onPaymentNoteChange(note: String) = paymentForm.update { it?.copy(note = note) }

    fun dismissPaymentForm() {
        paymentForm.value = null
    }

    fun submitPayment() {
        val form = paymentForm.value ?: return
        if (form.isSaving) return
        val amount = parseRupees(form.amountText)
        if (amount == null || amount == 0L) {
            paymentForm.update { it?.copy(error = UiMessage.EnterAmount) }
            return
        }
        paymentForm.update { it?.copy(isSaving = true) }
        viewModelScope.launch {
            paymentUseCases.recordPayment(form.membershipId, amount, form.mode, paidOn = form.paidOn, note = form.note)
                .onSuccess { paymentId ->
                    paymentForm.value = null
                    eventChannel.send(MemberDetailEvent.PaymentRecorded(form.membershipId, paymentId))
                    eventChannel.send(MemberDetailEvent.ShowMessage(UiMessage.PaymentRecorded(amount)))
                }
                .onFailure { e -> paymentForm.update { it?.copy(isSaving = false, error = e.toUiMessage()) } }
        }
    }

    fun deletePayment(paymentId: String) = run(UiMessage.PaymentDeleted) { paymentUseCases.deletePayment(paymentId) }

    fun deleteMembership(membershipId: String) =
        run(UiMessage.MembershipDeleted) { membershipUseCases.deleteMembership(membershipId) }

    fun archiveMember() = run(UiMessage.MemberArchived) { memberUseCases.archiveMember(memberId) }

    fun restoreMember() = run(UiMessage.MemberRestored) { memberUseCases.restoreMember(memberId) }

    fun deleteMemberPermanently() {
        viewModelScope.launch {
            memberUseCases.deleteMemberPermanently(memberId)
                .onSuccess { eventChannel.send(MemberDetailEvent.MemberDeleted) }
                .onFailure { eventChannel.send(MemberDetailEvent.ShowMessage(it.toUiMessage())) }
        }
    }

    private fun run(successMessage: UiMessage, action: suspend () -> Result<Unit>) {
        viewModelScope.launch {
            val message = action().fold({ successMessage }, { it.toUiMessage() })
            eventChannel.send(MemberDetailEvent.ShowMessage(message))
        }
    }
}
