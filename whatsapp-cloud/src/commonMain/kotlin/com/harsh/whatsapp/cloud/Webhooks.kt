package com.harsh.whatsapp.cloud

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * One thing a WhatsApp webhook reported. Verify the request's signature before trusting any of
 * these (`verifyWebhookSignature` on the JVM).
 */
public sealed interface WebhookEvent {
    /** The account the event belongs to (the entry id). */
    public val wabaId: String

    /**
     * A sent message moved on: `sent`, `delivered`, `read` or `failed`. [error] explains a failure.
     *
     * @property timestamp Seconds since the epoch.
     */
    public data class MessageStatus(
        override val wabaId: String,
        val phoneNumberId: String,
        val messageId: String,
        val status: String,
        val recipient: String?,
        val timestamp: Long?,
        val error: WhatsAppError?,
    ) : WebhookEvent

    /** Someone messaged the business. Only the parts a reply-window or log needs are kept. */
    public data class IncomingMessage(
        override val wabaId: String,
        val phoneNumberId: String,
        val messageId: String,
        val from: String,
        val type: String,
        val text: String?,
        val timestamp: Long?,
    ) : WebhookEvent

    /** Meta reviewed a template: [event] is e.g. `APPROVED`, `REJECTED`, `PAUSED`, `DISABLED`. */
    public data class TemplateStatus(
        override val wabaId: String,
        val templateId: String?,
        val name: String,
        val languageCode: String,
        val event: String,
        val reason: String?,
    ) : WebhookEvent

    /** Any other field (`account_update`, `history`, …), left as raw JSON. */
    public data class Other(override val wabaId: String, val field: String, val value: JsonObject) : WebhookEvent
}

/** Reads a webhook request body into events. Unreadable bodies give an empty list. */
public fun parseWebhook(body: String): List<WebhookEvent> {
    val payload = runCatching { Graph.json.decodeFromString(WebhookPayload.serializer(), body) }.getOrNull() ?: return emptyList()
    return payload.entry.flatMap { entry ->
        entry.changes.flatMap { change -> change.events(entry.id) }
    }
}

private fun WebhookChange.events(wabaId: String): List<WebhookEvent> = when (field) {
    "messages" -> {
        val value = runCatching { Graph.json.decodeFromJsonElement(MessagesValue.serializer(), value) }.getOrNull()
        val phoneNumberId = value?.metadata?.phoneNumberId.orEmpty()
        value?.statuses.orEmpty().map { status ->
            WebhookEvent.MessageStatus(
                wabaId = wabaId,
                phoneNumberId = phoneNumberId,
                messageId = status.id,
                status = status.status,
                recipient = status.recipientId,
                timestamp = status.timestamp?.toLongOrNull(),
                error = status.errors.firstOrNull()?.let { error ->
                    WhatsAppError(
                        kind = error.code?.let(WhatsAppError::kindOf) ?: WhatsAppError.Kind.UNKNOWN,
                        message = error.message ?: error.title.orEmpty(),
                        code = error.code,
                        details = error.errorData?.details,
                    )
                },
            )
        } + value?.messages.orEmpty().map { message ->
            WebhookEvent.IncomingMessage(wabaId, phoneNumberId, message.id, message.from, message.type, message.text?.body, message.timestamp?.toLongOrNull())
        }
    }
    "message_template_status_update" -> listOfNotNull(
        runCatching { Graph.json.decodeFromJsonElement(TemplateStatusValue.serializer(), value) }.getOrNull()?.let {
            WebhookEvent.TemplateStatus(wabaId, it.templateId?.toString(), it.name, it.language, it.event, it.reason)
        }
    )
    else -> listOf(WebhookEvent.Other(wabaId, field, value))
}

@Serializable
private data class WebhookPayload(val entry: List<WebhookEntry> = emptyList())

@Serializable
private data class WebhookEntry(val id: String, val changes: List<WebhookChange> = emptyList())

@Serializable
private data class WebhookChange(val field: String, val value: JsonObject)

@Serializable
private data class MessagesValue(
    val metadata: Metadata? = null,
    val statuses: List<StatusValue> = emptyList(),
    val messages: List<InboundValue> = emptyList(),
)

@Serializable
private data class Metadata(@SerialName("phone_number_id") val phoneNumberId: String? = null)

@Serializable
private data class StatusValue(
    val id: String,
    val status: String,
    val timestamp: String? = null,
    @SerialName("recipient_id") val recipientId: String? = null,
    val errors: List<StatusError> = emptyList(),
)

@Serializable
private data class StatusError(
    val code: Int? = null,
    val title: String? = null,
    val message: String? = null,
    @SerialName("error_data") val errorData: ErrorData? = null,
)

@Serializable
private data class InboundValue(
    val id: String,
    val from: String,
    val type: String,
    val timestamp: String? = null,
    val text: InboundText? = null,
)

@Serializable
private data class InboundText(val body: String)

@Serializable
private data class TemplateStatusValue(
    val event: String,
    @SerialName("message_template_id") val templateId: Long? = null,
    @SerialName("message_template_name") val name: String,
    @SerialName("message_template_language") val language: String,
    val reason: String? = null,
)
