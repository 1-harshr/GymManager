package com.harsh.whatsapp.cloud

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class OnboardingAndWebhooksTest {
    private val requests = mutableListOf<Pair<HttpRequestData, String>>()

    private fun http(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) = HttpClient(MockEngine { request ->
        requests += request to request.body.toByteArray().decodeToString()
        handler(request)
    })

    private fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

    @Test
    fun codeExchangeSendsAppCredentials() = runTest {
        val onboarding = WhatsAppOnboarding("app1", "secret1", http { json("""{"access_token":"biz-token","token_type":"bearer"}""") })

        assertEquals(WhatsAppResult.Success("biz-token"), onboarding.exchangeCode("code1"))
        val url = requests.single().first.url
        assertEquals("/v27.0/oauth/access_token", url.encodedPath)
        assertEquals("app1", url.parameters["client_id"])
        assertEquals("secret1", url.parameters["client_secret"])
        assertEquals("code1", url.parameters["code"])
    }

    @Test
    fun subscribeRegisterAndSync() = runTest {
        val client = http { json("""{"success":true}""") }
        assertEquals(WhatsAppResult.Success(Unit), WhatsAppBusinessAccount("waba1", { "t" }, client).subscribeApp())
        val phone = WhatsAppCloudClient("phone1", { "t" }, client)
        assertEquals(WhatsAppResult.Success(Unit), phone.register("123456"))
        assertEquals(WhatsAppResult.Success(Unit), phone.requestAppDataSync(AppDataSync.HISTORY))

        assertEquals(listOf("/v27.0/waba1/subscribed_apps", "/v27.0/phone1/register", "/v27.0/phone1/smb_app_data"), requests.map { it.first.url.encodedPath })
        assertEquals(Json.parseToJsonElement("""{"messaging_product":"whatsapp","pin":"123456"}"""), Json.parseToJsonElement(requests[1].second))
        assertEquals(Json.parseToJsonElement("""{"messaging_product":"whatsapp","sync_type":"history"}"""), Json.parseToJsonElement(requests[2].second))
    }

    @Test
    fun createTemplateSendsBodyWithExample() = runTest {
        val account = WhatsAppBusinessAccount("waba1", { "t" }, http { json("""{"id":"77","status":"PENDING","category":"UTILITY"}""") })

        val result = account.createTemplate(TemplateDefinition("gym_welcome", "en", TemplateCategory.UTILITY, "Hi {{1}}, welcome to {{2}}!", listOf("Ravi", "My Gym")))

        assertEquals(WhatsAppResult.Success(TemplateInfo("77", "gym_welcome", "en", "PENDING", "UTILITY")), result)
        assertEquals(HttpMethod.Post, requests.single().first.method)
        assertEquals(
            Json.parseToJsonElement(
                """{"name":"gym_welcome","language":"en","category":"UTILITY",
                "components":[{"type":"BODY","text":"Hi {{1}}, welcome to {{2}}!","example":{"body_text":[["Ravi","My Gym"]]}}]}"""
            ),
            Json.parseToJsonElement(requests.single().second),
        )
    }

    @Test
    fun duplicateTemplateIsRecognised() = runTest {
        val account = WhatsAppBusinessAccount("waba1", { "t" }, http {
            json("""{"error":{"message":"Content in This Language Already Exists","code":100,"error_subcode":2388024}}""", HttpStatusCode.BadRequest)
        })

        val error = assertIs<WhatsAppResult.Failure>(account.createTemplate(TemplateDefinition("x", "en", TemplateCategory.UTILITY, "Hi {{1}}.", listOf("a")))).error
        assertTrue(error.isDuplicateTemplate)
    }

    @Test
    fun listsTemplates() = runTest {
        val account = WhatsAppBusinessAccount("waba1", { "t" }, http {
            json("""{"data":[{"id":"1","name":"gym_welcome","language":"en","status":"APPROVED","category":"UTILITY"}],"paging":{}}""")
        })

        assertEquals(WhatsAppResult.Success(listOf(TemplateInfo("1", "gym_welcome", "en", "APPROVED", "UTILITY"))), account.templates())
    }

    @Test
    fun parsesStatusesIncomingAndTemplateUpdates() {
        val events = parseWebhook(
            """{"object":"whatsapp_business_account","entry":[{"id":"waba1","changes":[
              {"field":"messages","value":{"messaging_product":"whatsapp","metadata":{"display_phone_number":"9190","phone_number_id":"phone1"},
                "statuses":[
                  {"id":"wamid.1","status":"delivered","timestamp":"1762400000","recipient_id":"919876543210"},
                  {"id":"wamid.2","status":"failed","timestamp":"1762400001","recipient_id":"910000000000",
                   "errors":[{"code":131026,"title":"Message undeliverable","message":"Message undeliverable","error_data":{"details":"Not on WhatsApp"}}]}],
                "messages":[{"from":"919876543210","id":"wamid.in","timestamp":"1762400002","type":"text","text":{"body":"Thanks!"}}]}},
              {"field":"message_template_status_update","value":{"event":"APPROVED","message_template_id":77,"message_template_name":"gym_welcome","message_template_language":"en","reason":"NONE"}},
              {"field":"account_update","value":{"event":"PARTNER_ADDED"}}
            ]}]}"""
        )

        assertEquals(5, events.size)
        assertEquals(WebhookEvent.MessageStatus("waba1", "phone1", "wamid.1", "delivered", "919876543210", 1762400000, null), events[0])
        val failed = assertIs<WebhookEvent.MessageStatus>(events[1]).error!!
        assertEquals(WhatsAppError.Kind.RECIPIENT_UNAVAILABLE, failed.kind)
        assertEquals("Not on WhatsApp", failed.details)
        assertEquals(WebhookEvent.IncomingMessage("waba1", "phone1", "wamid.in", "919876543210", "text", "Thanks!", 1762400002), events[2])
        assertEquals(WebhookEvent.TemplateStatus("waba1", "77", "gym_welcome", "en", "APPROVED", "NONE"), events[3])
        assertEquals("account_update", assertIs<WebhookEvent.Other>(events[4]).field)
    }

    @Test
    fun garbageWebhookIsEmpty() {
        assertEquals(emptyList(), parseWebhook("not json"))
    }
}
