package com.harsh.whatsapp.cloud

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class WhatsAppCloudClientTest {
    private val requests = mutableListOf<HttpRequestData>()
    private val bodies = mutableListOf<String>()

    private fun client(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) = WhatsAppCloudClient(
        phoneNumberId = "1234",
        accessToken = { "token-abc" },
        httpClient = HttpClient(MockEngine { request ->
            requests += request
            bodies += request.body.toByteArray().decodeToString()
            handler(request)
        }),
    )

    private fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

    @Test
    fun templateRequestMatchesTheGraphApi() = runTest {
        val result = client { json("""{"messaging_product":"whatsapp","contacts":[{"input":"919876543210","wa_id":"919876543210"}],"messages":[{"id":"wamid.X","message_status":"accepted"}]}""") }
            .sendTemplate("919876543210", Template("membership_receipt", "en", bodyParameters = listOf("Ravi", "₹1,500")))

        assertEquals(SendResult.Accepted("wamid.X", "919876543210"), result)
        val request = requests.single()
        assertEquals("https://graph.facebook.com/v27.0/1234/messages", request.url.toString())
        assertEquals("Bearer token-abc", request.headers[HttpHeaders.Authorization])
        assertEquals(
            Json.parseToJsonElement(
                """{"messaging_product":"whatsapp","recipient_type":"individual","to":"919876543210","type":"template",
                "template":{"name":"membership_receipt","language":{"code":"en"},
                "components":[{"type":"body","parameters":[{"type":"text","text":"Ravi"},{"type":"text","text":"₹1,500"}]}]}}"""
            ),
            Json.parseToJsonElement(bodies.single()),
        )
    }

    @Test
    fun templateWithoutParametersSendsNoComponents() = runTest {
        client { json("""{"messages":[{"id":"wamid.Y"}]}""") }.sendTemplate("91", Template("hello_world", "en_US"))

        val template = (Json.parseToJsonElement(bodies.single()) as JsonObject)["template"] as JsonObject
        assertEquals(JsonArray(emptyList()), template["components"])
    }

    @Test
    fun textRequestCarriesBodyAndPreviewFlag() = runTest {
        client { json("""{"messages":[{"id":"wamid.Z"}]}""") }.sendText("919876543210", "Hi", previewUrl = true)

        assertEquals(
            Json.parseToJsonElement("""{"messaging_product":"whatsapp","recipient_type":"individual","to":"919876543210","type":"text","text":{"body":"Hi","preview_url":true}}"""),
            Json.parseToJsonElement(bodies.single()),
        )
    }

    @Test
    fun metaErrorsAreMappedToKinds() = runTest {
        val result = client {
            json(
                """{"error":{"message":"(#131026) Message Undeliverable.","type":"OAuthException","code":131026,
                "error_data":{"messaging_product":"whatsapp","details":"Not a WhatsApp user"},"fbtrace_id":"trace1"}}""",
                HttpStatusCode.BadRequest,
            )
        }.sendTemplate("910000000000", Template("x", "en"))

        val error = assertIs<SendResult.Failed>(result).error
        assertEquals(WhatsAppError.Kind.RECIPIENT_UNAVAILABLE, error.kind)
        assertEquals(131026, error.code)
        assertEquals(400, error.httpStatus)
        assertEquals("Not a WhatsApp user", error.details)
        assertEquals("trace1", error.fbTraceId)
    }

    @Test
    fun expiredTokenIsAnAuthError() = runTest {
        val result = client { json("""{"error":{"message":"Session has expired","code":190}}""", HttpStatusCode.Unauthorized) }
            .sendText("91", "Hi")

        assertEquals(WhatsAppError.Kind.AUTH, assertIs<SendResult.Failed>(result).error.kind)
    }

    @Test
    fun unreadableServerErrorIsRetryable() = runTest {
        val result = client { respond("<html>Bad gateway</html>", HttpStatusCode.BadGateway) }.sendText("91", "Hi")

        val error = assertIs<SendResult.Failed>(result).error
        assertEquals(WhatsAppError.Kind.SERVER, error.kind)
        assertTrue(error.isRetryable)
    }

    @Test
    fun connectionFailureIsANetworkError() = runTest {
        val result = client { throw IllegalStateException("offline") }.sendText("91", "Hi")

        val error = assertIs<SendResult.Failed>(result).error
        assertEquals(WhatsAppError.Kind.NETWORK, error.kind)
        assertEquals("offline", error.message)
    }

    @Test
    fun errorCodesGroupByWhatToDo() {
        assertEquals(WhatsAppError.Kind.OUTSIDE_REPLY_WINDOW, WhatsAppError.kindOf(131047))
        assertEquals(WhatsAppError.Kind.TEMPLATE, WhatsAppError.kindOf(132001))
        assertEquals(WhatsAppError.Kind.RATE_LIMITED, WhatsAppError.kindOf(130429))
        assertEquals(WhatsAppError.Kind.PERMISSION, WhatsAppError.kindOf(250))
        assertEquals(WhatsAppError.Kind.PAYMENT, WhatsAppError.kindOf(131042))
        assertEquals(WhatsAppError.Kind.UNKNOWN, WhatsAppError.kindOf(999999))
    }
}
