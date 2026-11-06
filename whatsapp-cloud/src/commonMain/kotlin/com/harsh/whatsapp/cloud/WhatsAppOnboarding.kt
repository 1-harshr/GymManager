package com.harsh.whatsapp.cloud

import io.ktor.client.HttpClient
import io.ktor.http.HttpMethod

/**
 * The server side of Embedded Signup for a Tech Provider. It needs your app secret, so it must
 * only ever run on your server.
 *
 * After a customer finishes the flow: [exchangeCode] within 30 seconds, then
 * [WhatsAppBusinessAccount.subscribeApp], then [WhatsAppCloudClient.register] unless the number
 * stays on the WhatsApp Business app.
 */
public class WhatsAppOnboarding(
    private val appId: String,
    private val appSecret: String,
    httpClient: HttpClient = HttpClient(),
    apiVersion: String = WhatsAppCloudClient.DEFAULT_API_VERSION,
    baseUrl: String = WhatsAppCloudClient.DEFAULT_BASE_URL,
) {
    private val graph = Graph(httpClient, apiVersion, baseUrl)

    /** Swaps the code from `FB.login` for a business token that acts on the customer's assets. */
    public suspend fun exchangeCode(code: String): WhatsAppResult<String> =
        graph.call(
            HttpMethod.Get,
            "oauth/access_token",
            token = null,
            response = AccessToken.serializer(),
            query = mapOf("client_id" to appId, "client_secret" to appSecret, "code" to code),
        ).map { it.accessToken }
}
