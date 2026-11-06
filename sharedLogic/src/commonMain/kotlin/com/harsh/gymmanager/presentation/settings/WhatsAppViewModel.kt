package com.harsh.gymmanager.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.harsh.gymmanager.domain.messaging.whatsAppNumber
import com.harsh.gymmanager.domain.whatsapp.AutoSend
import com.harsh.gymmanager.domain.whatsapp.OutboxMessage
import com.harsh.gymmanager.domain.whatsapp.WebhookResult
import com.harsh.gymmanager.domain.whatsapp.WhatsAppMessaging
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class WhatsAppUiState(
    val isLoading: Boolean = true,
    /** False in demo mode. */
    val available: Boolean = true,
    val webhookUrl: String? = null,
    val autoSend: AutoSend = AutoSend(),
    val recent: List<OutboxMessage> = emptyList(),
    val testing: Boolean = false,
)

sealed interface WhatsAppEvent {
    data object Saved : WhatsAppEvent
    data object InvalidWebhook : WhatsAppEvent
    data object InvalidNumber : WhatsAppEvent
    data object Removed : WhatsAppEvent
    data object TestSent : WhatsAppEvent
    data class TestFailed(val reason: String, val tryLater: Boolean) : WhatsAppEvent
}

/** Settings → WhatsApp: the Make.com webhook, what goes out automatically, and recent messages. */
class WhatsAppViewModel(private val whatsApp: WhatsAppMessaging, private val gymName: () -> String) : ViewModel() {
    private val testing = MutableStateFlow(false)

    val state: StateFlow<WhatsAppUiState> =
        combine(whatsApp.observeSettings(), whatsApp.observeOutbox(), testing) { settings, recent, testing ->
            WhatsAppUiState(
                isLoading = false,
                available = whatsApp.isAvailable,
                webhookUrl = settings.webhookUrl,
                autoSend = settings.autoSend,
                recent = recent,
                testing = testing,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WhatsAppUiState(available = whatsApp.isAvailable))

    private val eventChannel = Channel<WhatsAppEvent>(Channel.BUFFERED)
    val events: Flow<WhatsAppEvent> = eventChannel.receiveAsFlow()

    fun saveWebhook(url: String) {
        viewModelScope.launch {
            if (whatsApp.setWebhookUrl(url)) {
                eventChannel.send(WhatsAppEvent.Saved)
                // Anything queued while it wasn't set up can go now.
                whatsApp.flush()
            } else {
                eventChannel.send(WhatsAppEvent.InvalidWebhook)
            }
        }
    }

    fun removeWebhook() {
        viewModelScope.launch {
            whatsApp.removeWebhook()
            eventChannel.send(WhatsAppEvent.Removed)
        }
    }

    fun setAutoSend(autoSend: AutoSend) {
        viewModelScope.launch { whatsApp.setAutoSend(autoSend) }
    }

    /** Sends a test welcome to [phone], usually the owner's own mobile. */
    fun sendTest(phone: String) {
        val number = whatsAppNumber(phone)
        if (number == null) {
            eventChannel.trySend(WhatsAppEvent.InvalidNumber)
            return
        }
        if (testing.value) return
        testing.value = true
        viewModelScope.launch {
            try {
                eventChannel.send(
                    when (val result = whatsApp.sendTest(number, gymName())) {
                        WebhookResult.Accepted -> WhatsAppEvent.TestSent
                        is WebhookResult.TryLater -> WhatsAppEvent.TestFailed(result.reason, tryLater = true)
                        is WebhookResult.Refused -> WhatsAppEvent.TestFailed(result.reason, tryLater = false)
                    }
                )
            } finally {
                testing.value = false
            }
        }
    }
}
