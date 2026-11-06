package com.harsh.whatsapp.cloud

import io.ktor.client.HttpClient
import io.ktor.http.HttpMethod

/**
 * Sends messages through the WhatsApp Business Cloud API from one business phone number, and
 * manages that number.
 *
 * Messages a business starts must use an approved [Template]; [sendText] only works within 24
 * hours of the recipient's last message to you.
 *
 * Don't ship a long-lived token inside an app you distribute: anyone with the app can extract
 * it. Keep the token on a server, or on a device only its owner uses.
 *
 * @param phoneNumberId The sender's id from WhatsApp Manager (not the phone number itself).
 * @param accessToken Called before each request, so a token can be rotated without a new client.
 * @param httpClient Bring your own to control the engine, timeouts and logging. The default uses
 *   whichever Ktor engine is on the classpath.
 */
public class WhatsAppCloudClient(
    private val phoneNumberId: String,
    private val accessToken: suspend () -> String,
    httpClient: HttpClient = HttpClient(),
    apiVersion: String = DEFAULT_API_VERSION,
    baseUrl: String = DEFAULT_BASE_URL,
) {
    private val graph = Graph(httpClient, apiVersion, baseUrl)

    /** Sends [template] to [to], a number in international form without `+`, e.g. `919876543210`. */
    public suspend fun sendTemplate(to: String, template: Template): SendResult =
        send(MessageRequest(to = to, type = "template", template = template.toBody()))

    /** Sends free-form text; fails with [WhatsAppError.Kind.OUTSIDE_REPLY_WINDOW] outside the 24-hour window. */
    public suspend fun sendText(to: String, body: String, previewUrl: Boolean = false): SendResult =
        send(MessageRequest(to = to, type = "text", text = TextBody(body, previewUrl)))

    /** The number as WhatsApp shows it, and the business name members see. */
    public suspend fun phoneNumber(): WhatsAppResult<PhoneNumberInfo> = graph.call(
        HttpMethod.Get,
        phoneNumberId,
        accessToken(),
        PhoneNumberInfo.serializer(),
        query = mapOf("fields" to "display_phone_number,verified_name,quality_rating"),
    )

    /**
     * Registers the number for the Cloud API, setting its two-step verification [pin] (6 digits).
     * Skip this for a number that stays on the WhatsApp Business app (coexistence).
     */
    public suspend fun register(pin: String): WhatsAppResult<Unit> =
        graph.call(HttpMethod.Post, "$phoneNumberId/register", accessToken(), Success.serializer(), graph.encode(RegisterRequest.serializer(), RegisterRequest(pin = pin)))
            .ignoreValue()

    /**
     * For a coexistence number: asks WhatsApp to send the Business app's contacts or chat history
     * as webhooks. Each kind can be requested once per onboarding, within 24 hours of it.
     */
    public suspend fun requestAppDataSync(type: AppDataSync): WhatsAppResult<Unit> =
        graph.call(HttpMethod.Post, "$phoneNumberId/smb_app_data", accessToken(), Success.serializer(), graph.encode(SyncRequest.serializer(), SyncRequest(syncType = type.wireName)))
            .ignoreValue()

    private suspend fun send(request: MessageRequest): SendResult =
        when (val result = graph.call(HttpMethod.Post, "$phoneNumberId/messages", accessToken(), MessageResponse.serializer(), graph.encode(MessageRequest.serializer(), request))) {
            is WhatsAppResult.Failure -> SendResult.Failed(result.error)
            is WhatsAppResult.Success -> {
                val id = result.value.messages.firstOrNull()?.id
                if (id == null) {
                    SendResult.Failed(WhatsAppError(WhatsAppError.Kind.UNKNOWN, "No message id in the response"))
                } else {
                    SendResult.Accepted(id, result.value.contacts.firstOrNull()?.waId)
                }
            }
        }

    public companion object {
        /** The Graph API version requests go to unless the constructor says otherwise. */
        public const val DEFAULT_API_VERSION: String = "v27.0"
        public const val DEFAULT_BASE_URL: String = "https://graph.facebook.com"
    }
}

/** What a coexistence number can sync from the WhatsApp Business app. */
public enum class AppDataSync(internal val wireName: String) {
    CONTACTS("smb_app_state_sync"),
    HISTORY("history"),
}

internal fun WhatsAppResult<*>.ignoreValue(): WhatsAppResult<Unit> = when (this) {
    is WhatsAppResult.Success -> WhatsAppResult.Success(Unit)
    is WhatsAppResult.Failure -> this
}
