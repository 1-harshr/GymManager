package com.harsh.gymmanager.domain.messaging

/**
 * Gets a [MemberMessage] to the member. [LinkSender] opens WhatsApp for the owner to send;
 * once Make.com is set up, [com.harsh.gymmanager.domain.whatsapp.WhatsAppMessaging] sends through it.
 */
interface MessageSender {
    /** [text] is the message as the owner previewed it; template-based senders don't use it. */
    suspend fun send(message: MemberMessage, text: String): SendOutcome
}

sealed interface SendOutcome {
    /** WhatsApp is open with the message typed in; the owner taps send there. */
    data object Opened : SendOutcome

    /** Queued for Make, which sends it with no tap; delivery isn't confirmed. */
    data object Queued : SendOutcome

    data class Failed(val reason: SendFailure, val detail: String? = null) : SendOutcome

    /** True when the message is on its way (or typed in), so it belongs in the log. */
    val isSent: Boolean get() = this !is Failed
}

enum class SendFailure {
    /** Nothing on the phone can open a WhatsApp link. */
    NO_WHATSAPP_APP,

    /** Make.com isn't set up. */
    NOT_SET_UP,
}

/** Opens a wa.me link with the text typed in. [openUri] throws IllegalArgumentException when nothing can open it. */
class LinkSender(private val openUri: (String) -> Unit) : MessageSender {
    override suspend fun send(message: MemberMessage, text: String): SendOutcome = try {
        openUri(whatsAppLink(message.whatsAppNumber, text))
        SendOutcome.Opened
    } catch (e: IllegalArgumentException) {
        // Android's handler throws this when no app can open the link.
        SendOutcome.Failed(SendFailure.NO_WHATSAPP_APP)
    }
}
