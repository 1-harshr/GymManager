package com.harsh.whatsapp.cloud

import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.parameter
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.content.TextContent
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/** The result of a Graph API call other than sending a message. */
public sealed interface WhatsAppResult<out T> {
    public data class Success<out T>(val value: T) : WhatsAppResult<T>
    public data class Failure(val error: WhatsAppError) : WhatsAppResult<Nothing>
}

public fun <T> WhatsAppResult<T>.getOrNull(): T? = (this as? WhatsAppResult.Success)?.value

/** Makes Graph API requests and turns Meta's error bodies into [WhatsAppError]. */
internal class Graph(private val httpClient: HttpClient, private val apiVersion: String, private val baseUrl: String) {
    suspend fun <T> call(
        method: HttpMethod,
        path: String,
        token: String?,
        response: KSerializer<T>,
        body: String? = null,
        query: Map<String, String> = emptyMap(),
    ): WhatsAppResult<T> {
        val reply = try {
            httpClient.request("$baseUrl/$apiVersion/$path") {
                this.method = method
                token?.let { bearerAuth(it) }
                query.forEach { (name, value) -> parameter(name, value) }
                body?.let { setBody(TextContent(it, ContentType.Application.Json)) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return WhatsAppResult.Failure(WhatsAppError(WhatsAppError.Kind.NETWORK, e.message ?: "Request failed", cause = e))
        }
        val status = reply.status.value
        val text = try {
            reply.bodyAsText()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return WhatsAppResult.Failure(WhatsAppError(WhatsAppError.Kind.NETWORK, "Couldn't read the response", httpStatus = status, cause = e))
        }
        if (!reply.status.isSuccess()) return WhatsAppResult.Failure(errorOf(text, status))
        return runCatching { json.decodeFromString(response, text) }.fold(
            onSuccess = { WhatsAppResult.Success(it) },
            onFailure = { WhatsAppResult.Failure(WhatsAppError(WhatsAppError.Kind.UNKNOWN, "Unexpected response: $text", httpStatus = status, cause = it)) },
        )
    }

    fun <T> encode(serializer: KSerializer<T>, value: T): String = json.encodeToString(serializer, value)

    private fun errorOf(text: String, status: Int): WhatsAppError {
        val error = runCatching { json.decodeFromString(ErrorResponse.serializer(), text).error }.getOrNull()
            ?: return WhatsAppError(if (status >= 500) WhatsAppError.Kind.SERVER else WhatsAppError.Kind.UNKNOWN, "HTTP $status: $text", httpStatus = status)
        return WhatsAppError(
            kind = error.code?.let(WhatsAppError::kindOf) ?: WhatsAppError.Kind.UNKNOWN,
            message = error.message,
            code = error.code,
            subcode = error.subcode,
            httpStatus = status,
            details = error.errorData?.details,
            fbTraceId = error.fbTraceId,
        )
    }

    companion object {
        val json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            encodeDefaults = true
        }
    }
}
