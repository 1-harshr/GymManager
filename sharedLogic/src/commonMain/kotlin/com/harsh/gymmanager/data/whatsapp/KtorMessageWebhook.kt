package com.harsh.gymmanager.data.whatsapp

import com.harsh.gymmanager.domain.whatsapp.MessageWebhook
import com.harsh.gymmanager.domain.whatsapp.OutboxMessage
import com.harsh.gymmanager.domain.whatsapp.WebhookResult
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.content.TextContent
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Posts each message as JSON to the Make.com scenario's custom webhook. Besides the `params`
 * list, the values are repeated as `p1`, `p2`, … so they're easy to map in Make's editor, and
 * `graph_body` is the whole WhatsApp Cloud API request, ready for Make's "Make an API Call".
 */
class KtorMessageWebhook(private val http: HttpClient) : MessageWebhook {
    override suspend fun post(url: String, message: OutboxMessage): WebhookResult {
        val body = buildJsonObject {
            put("id", message.clientId)
            put("to", message.recipient)
            put("template", message.templateName)
            put("language", message.languageCode)
            put("params", buildJsonArray { message.parameters.forEach { add(JsonPrimitive(it)) } })
            message.parameters.forEachIndexed { i, value -> put("p${i + 1}", value) }
            put("graph_body", graphBody(message).toString())
            put("kind", message.kind.name.lowercase())
            put("member", message.memberName)
        }
        val response = try {
            http.post(url) { setBody(TextContent(body.toString(), ContentType.Application.Json)) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return WebhookResult.TryLater(e.message ?: "No connection")
        }
        if (response.status.isSuccess()) return WebhookResult.Accepted
        val text = runCatching { response.bodyAsText() }.getOrDefault("").take(300)
        val reason = "HTTP ${response.status.value}${if (text.isNotBlank()) ": $text" else ""}"
        return when (response.status.value) {
            // Timed out, too many requests, or Make itself having trouble.
            408, 429, in 500..599 -> WebhookResult.TryLater(reason)
            else -> WebhookResult.Refused(reason)
        }
    }

    /** The Cloud API's send-template request, so the scenario needn't build it. */
    private fun graphBody(message: OutboxMessage) = buildJsonObject {
        put("messaging_product", "whatsapp")
        put("to", message.recipient)
        put("type", "template")
        put("template", buildJsonObject {
            put("name", message.templateName)
            put("language", buildJsonObject { put("code", message.languageCode) })
            put("components", buildJsonArray {
                add(buildJsonObject {
                    put("type", "body")
                    put("parameters", buildJsonArray {
                        message.parameters.forEach { value -> add(buildJsonObject { put("type", "text"); put("text", value) }) }
                    })
                })
            })
        })
    }
}
