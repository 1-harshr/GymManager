package com.harsh.gymmanager.presentation.messaging

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.harsh.gymmanager.domain.messaging.MemberMessage
import com.harsh.gymmanager.domain.messaging.MessagingUseCases
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What to offer sending on WhatsApp. */
sealed interface MessageRequest {
    /** Right after adding a member. */
    data class Welcome(val memberId: Long) : MessageRequest

    /** Right after a sale ([paymentId] null) or a later payment. */
    data class Receipt(val memberId: Long, val membershipId: Long, val paymentId: Long? = null) : MessageRequest

    /** From the member's page: every message that makes sense for them now. */
    data class AnyFor(val memberId: Long) : MessageRequest
}

/** The sheet's choices; [selected] indexes [options]. */
data class WhatsAppPrompt(val options: List<MemberMessage>, val selected: Int = 0) {
    val message: MemberMessage get() = options[selected]
}

/**
 * Drives the "send on WhatsApp?" sheet. Requests for members without a usable number are
 * dropped quietly, since the owner chose not to give one.
 */
class WhatsAppPromptViewModel(private val messaging: MessagingUseCases) : ViewModel() {
    private val _prompt = MutableStateFlow<WhatsAppPrompt?>(null)
    val prompt: StateFlow<WhatsAppPrompt?> = _prompt.asStateFlow()

    private var loading: Job? = null

    fun offer(request: MessageRequest) {
        loading?.cancel()
        loading = viewModelScope.launch {
            val options = when (request) {
                is MessageRequest.Welcome -> listOfNotNull(messaging.welcome(request.memberId))
                is MessageRequest.Receipt -> listOfNotNull(messaging.receipt(request.memberId, request.membershipId, request.paymentId))
                is MessageRequest.AnyFor -> listOfNotNull(
                    messaging.reminder(request.memberId),
                    messaging.latestReceipt(request.memberId),
                    messaging.welcome(request.memberId),
                )
            }
            _prompt.value = options.takeIf { it.isNotEmpty() }?.let(::WhatsAppPrompt)
        }
    }

    fun select(index: Int) = _prompt.update { it?.copy(selected = index.coerceIn(0, it.options.lastIndex)) }

    /** Call after WhatsApp opened with the selected message. */
    fun sent() {
        val message = _prompt.value?.message ?: return
        _prompt.value = null
        viewModelScope.launch { messaging.markSent(message) }
    }

    fun dismiss() {
        loading?.cancel()
        _prompt.value = null
    }
}
