package com.harsh.whatsapp.cloud

import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Checks Meta's `X-Hub-Signature-256` header (`sha256=<hex>`) against the raw request [body].
 * Reject the request when this is false: anyone can post to a webhook URL.
 */
public fun verifyWebhookSignature(appSecret: String, body: ByteArray, signatureHeader: String?): Boolean {
    val expected = signatureHeader?.removePrefix("sha256=")?.takeIf { it != signatureHeader } ?: return false
    val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(appSecret.encodeToByteArray(), "HmacSHA256")) }
    val actual = mac.doFinal(body).joinToString("") { "%02x".format(it) }
    return MessageDigest.isEqual(actual.encodeToByteArray(), expected.lowercase().encodeToByteArray())
}
