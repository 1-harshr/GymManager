package com.harsh.gymmanager.data.whatsapp

import com.harsh.gymmanager.domain.messaging.MessageKind
import com.harsh.gymmanager.domain.whatsapp.OutboxMessage
import com.harsh.gymmanager.domain.whatsapp.WebhookResult
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Instant

class KtorMessageWebhookTest {
    private val message = OutboxMessage(
        clientId = "c1",
        memberId = 5,
        memberName = "Ravi Kumar",
        kind = MessageKind.RECEIPT,
        recipient = "919876543210",
        templateName = "gym_payment_due",
        languageCode = "en",
        parameters = listOf("Ravi", "₹200", "My Gym"),
        createdAt = Instant.fromEpochMilliseconds(0),
        updatedAt = Instant.fromEpochMilliseconds(0),
    )

    private fun webhook(status: HttpStatusCode, body: String = "Accepted", seen: MutableList<String> = mutableListOf()) =
        KtorMessageWebhook(HttpClient(MockEngine { request ->
            seen += request.url.toString()
            seen += request.body.toByteArray().decodeToString()
            respond(body, status)
        }))

    @Test
    fun postsTheMessageWithFlatValuesForMake() = runTest {
        val seen = mutableListOf<String>()

        assertEquals(WebhookResult.Accepted, webhook(HttpStatusCode.OK, seen = seen).post("https://hook.eu2.make.com/abc", message))
        assertEquals("https://hook.eu2.make.com/abc", seen[0])
        val sent = Json.parseToJsonElement(seen[1]).jsonObject
        assertEquals(
            Json.parseToJsonElement(
                """{"id":"c1","to":"919876543210","template":"gym_payment_due","language":"en",
                "params":["Ravi","₹200","My Gym"],"p1":"Ravi","p2":"₹200","p3":"My Gym","kind":"receipt","member":"Ravi Kumar"}"""
            ),
            JsonObject(sent - "graph_body"),
        )
        assertEquals(
            Json.parseToJsonElement(
                """{"messaging_product":"whatsapp","to":"919876543210","type":"template","template":{"name":"gym_payment_due",
                "language":{"code":"en"},"components":[{"type":"body","parameters":[
                {"type":"text","text":"Ravi"},{"type":"text","text":"₹200"},{"type":"text","text":"My Gym"}]}]}}"""
            ),
            Json.parseToJsonElement(sent.getValue("graph_body").jsonPrimitive.content),
        )
    }

    @Test
    fun makeTroubleIsRetriedButRefusalIsNot() = runTest {
        assertIs<WebhookResult.TryLater>(webhook(HttpStatusCode.InternalServerError).post("u", message))
        assertIs<WebhookResult.TryLater>(webhook(HttpStatusCode.TooManyRequests).post("u", message))
        val refused = assertIs<WebhookResult.Refused>(webhook(HttpStatusCode.BadRequest, "Template not found").post("u", message))
        assertEquals("HTTP 400: Template not found", refused.reason)
    }

    @Test
    fun noConnectionIsRetried() = runTest {
        val offline = KtorMessageWebhook(HttpClient(MockEngine { throw IllegalStateException("offline") }))

        assertEquals(WebhookResult.TryLater("offline"), offline.post("u", message))
    }
}
