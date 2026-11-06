package com.harsh.whatsapp.cloud

/** What came of one send. */
public sealed interface SendResult {
    /**
     * WhatsApp accepted the message. It isn't delivered yet: delivery and read receipts only
     * arrive through webhooks.
     *
     * @property messageId The `wamid.…` id that later webhook statuses refer to.
     * @property waId The recipient's WhatsApp id, when the API returns one.
     */
    public data class Accepted(val messageId: String, val waId: String?) : SendResult

    public data class Failed(val error: WhatsAppError) : SendResult
}

/**
 * Why a send failed.
 *
 * @property code Meta's error code, or null when no response came back.
 * @property subcode Meta's finer-grained code, when given.
 * @property httpStatus The HTTP status, or null when no response came back.
 * @property details Meta's longer explanation (`error_data.details`), when given.
 * @property fbTraceId Quote this to Meta support.
 */
public data class WhatsAppError(
    val kind: Kind,
    val message: String,
    val code: Int? = null,
    val subcode: Int? = null,
    val httpStatus: Int? = null,
    val details: String? = null,
    val fbTraceId: String? = null,
    val cause: Throwable? = null,
) {
    /** Groups Meta's error codes by what the caller should do about them. */
    public enum class Kind {
        /** The access token is missing, expired or revoked. Get a new one. */
        AUTH,

        /** The token lacks a permission, or the account is restricted. Fix it in Meta Business Manager. */
        PERMISSION,

        /** Too many messages; retry later. */
        RATE_LIMITED,

        /** The number isn't on WhatsApp, blocked the business, or the message can't reach it. */
        RECIPIENT_UNAVAILABLE,

        /** Free-form text sent outside the 24-hour reply window; send a template instead. */
        OUTSIDE_REPLY_WINDOW,

        /** The template is missing, unapproved, paused, or its parameters don't fit. */
        TEMPLATE,

        /** The account has no working payment method. */
        PAYMENT,

        /** The request itself is malformed. */
        INVALID_REQUEST,

        /** Meta had a temporary problem; retry later. */
        SERVER,

        /** No response: offline, timed out, or the response couldn't be read. */
        NETWORK,

        UNKNOWN,
    }

    public val isRetryable: Boolean get() = kind == Kind.RATE_LIMITED || kind == Kind.SERVER || kind == Kind.NETWORK

    /** Creating a template that already exists in that language. */
    public val isDuplicateTemplate: Boolean get() = code == 100 && subcode == 2388024

    public companion object {
        /** What Meta's error [code] means for the caller; see https://developers.facebook.com/docs/whatsapp/cloud-api/support/error-codes. */
        public fun kindOf(code: Int): Kind = when (code) {
            0, 190 -> Kind.AUTH
            3, 10, 368, 130497, 131005, 131031, 131037, 131045, 133010 -> Kind.PERMISSION
            in 200..299 -> Kind.PERMISSION
            4, 80007, 130429, 131048, 131056, 131064 -> Kind.RATE_LIMITED
            130403, 131021, 131026, 131049, 131050 -> Kind.RECIPIENT_UNAVAILABLE
            131047 -> Kind.OUTSIDE_REPLY_WINDOW
            132000, 132001, 132005, 132007, 132012, 132015, 132016 -> Kind.TEMPLATE
            131042 -> Kind.PAYMENT
            33, 100, 131008, 131009, 131051, 135000 -> Kind.INVALID_REQUEST
            1, 2, 131000, 131016, 131057, 133004 -> Kind.SERVER
            else -> Kind.UNKNOWN
        }
    }
}
