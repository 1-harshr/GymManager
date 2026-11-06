package com.harsh.whatsapp.cloud

import io.ktor.client.HttpClient
import io.ktor.http.HttpMethod

/**
 * Manages one WhatsApp Business Account (WABA): its message templates and your app's webhook
 * subscription to it.
 *
 * @param wabaId The account id, e.g. `waba_id` from Embedded Signup.
 */
public class WhatsAppBusinessAccount(
    private val wabaId: String,
    private val accessToken: suspend () -> String,
    httpClient: HttpClient = HttpClient(),
    apiVersion: String = WhatsAppCloudClient.DEFAULT_API_VERSION,
    baseUrl: String = WhatsAppCloudClient.DEFAULT_BASE_URL,
) {
    private val graph = Graph(httpClient, apiVersion, baseUrl)

    /** Sends this account's webhooks to your app, as configured in the app dashboard. */
    public suspend fun subscribeApp(): WhatsAppResult<Unit> =
        graph.call(HttpMethod.Post, "$wabaId/subscribed_apps", accessToken(), Success.serializer()).ignoreValue()

    /**
     * Submits [template] for review. Meta usually decides within 24 hours and says so in a
     * `message_template_status_update` webhook. A template with the same name and language
     * fails with [WhatsAppError.isDuplicateTemplate].
     */
    public suspend fun createTemplate(template: TemplateDefinition): WhatsAppResult<TemplateInfo> =
        graph.call(HttpMethod.Post, "$wabaId/message_templates", accessToken(), CreatedTemplate.serializer(), graph.encode(CreateTemplateRequest.serializer(), template.toRequest()))
            .map { TemplateInfo(it.id, template.name, template.languageCode, it.status, it.category ?: template.category.name) }

    /** The account's templates with their review status (first page, up to [limit]). */
    public suspend fun templates(limit: Int = 100): WhatsAppResult<List<TemplateInfo>> =
        graph.call(
            HttpMethod.Get,
            "$wabaId/message_templates",
            accessToken(),
            TemplatePage.serializer(),
            query = mapOf("fields" to "id,name,language,status,category", "limit" to limit.toString()),
        ).map { page -> page.data.map { TemplateInfo(it.id, it.name, it.language, it.status, it.category) } }
}

/**
 * A template to submit for review. Write variables as `{{1}}`, `{{2}}`, …; WhatsApp rejects a
 * body that starts or ends with one.
 *
 * @property bodyExample A sample value for each variable, in order; review needs them.
 */
public data class TemplateDefinition(
    val name: String,
    val languageCode: String,
    val category: TemplateCategory,
    val body: String,
    val bodyExample: List<String>,
)

public enum class TemplateCategory { UTILITY, MARKETING, AUTHENTICATION }

/** @property status Meta's review status, e.g. `APPROVED`, `PENDING`, `REJECTED`, `PAUSED`. */
public data class TemplateInfo(val id: String, val name: String, val languageCode: String, val status: String, val category: String)

internal fun <T, R> WhatsAppResult<T>.map(transform: (T) -> R): WhatsAppResult<R> = when (this) {
    is WhatsAppResult.Success -> WhatsAppResult.Success(transform(value))
    is WhatsAppResult.Failure -> this
}
