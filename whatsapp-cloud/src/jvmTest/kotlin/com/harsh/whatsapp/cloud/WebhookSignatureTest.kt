package com.harsh.whatsapp.cloud

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WebhookSignatureTest {
    private val body = """{"object":"whatsapp_business_account"}""".encodeToByteArray()

    // echo -n '{"object":"whatsapp_business_account"}' | openssl dgst -sha256 -hmac secret
    private val signature = "sha256=03f6dd944ecab4ddac0e3dbc3923b2da4e12b03df679ae23a52efd78ca6056e0"

    @Test
    fun acceptsMetasSignature() {
        assertTrue(verifyWebhookSignature("secret", body, signature))
    }

    @Test
    fun rejectsWrongSecretMissingHeaderAndBareHex() {
        assertFalse(verifyWebhookSignature("other", body, signature))
        assertFalse(verifyWebhookSignature("secret", body, null))
        assertFalse(verifyWebhookSignature("secret", body, signature.removePrefix("sha256=")))
        assertFalse(verifyWebhookSignature("secret", body + 1.toByte(), signature))
    }
}
