package com.harsh.gymmanager.domain.messaging

import com.harsh.gymmanager.domain.MembershipStatus
import com.harsh.gymmanager.domain.importing.normalizeIndianPhone
import com.harsh.gymmanager.domain.model.Payment
import kotlinx.datetime.LocalDate

/**
 * The facts for one WhatsApp message to a member. The UI turns these into text in the app's
 * language, so the wording lives with the other strings.
 */
sealed interface MemberMessage {
    val memberId: String
    val memberName: String
    /** Digits with the country code, as WhatsApp links expect: 919876543210. */
    val whatsAppNumber: String
    val gymName: String
    val kind: MessageKind

    data class Welcome(
        override val memberId: String,
        override val memberName: String,
        override val whatsAppNumber: String,
        override val gymName: String,
    ) : MemberMessage {
        override val kind get() = MessageKind.WELCOME
    }

    /**
     * A receipt for a membership. [payment] is set when the receipt is for one payment made later;
     * otherwise it covers the sale itself. Amounts are whole rupees, after this payment.
     */
    data class Receipt(
        override val memberId: String,
        override val memberName: String,
        override val whatsAppNumber: String,
        override val gymName: String,
        val receiptNumber: String,
        val planName: String,
        val startDate: LocalDate,
        val endDate: LocalDate,
        val price: Long,
        val discount: Long,
        val amountPaid: Long,
        val amountDue: Long,
        val payment: Payment?,
    ) : MemberMessage {
        override val kind get() = MessageKind.RECEIPT
    }

    /** A nudge to renew ([status] says whether it is ending or ended) and/or to clear [amountDue]. */
    data class Reminder(
        override val memberId: String,
        override val memberName: String,
        override val whatsAppNumber: String,
        override val gymName: String,
        val status: MembershipStatus,
        val endDate: LocalDate?,
        val amountDue: Long,
    ) : MemberMessage {
        override val kind get() = MessageKind.REMINDER
    }

    /** For a member who has stopped coming: [lastVisit] was their latest check-in. */
    data class MissYou(
        override val memberId: String,
        override val memberName: String,
        override val whatsAppNumber: String,
        override val gymName: String,
        val lastVisit: LocalDate,
    ) : MemberMessage {
        override val kind get() = MessageKind.MISS_YOU
    }
}

/** An Indian mobile number in the form WhatsApp links need, or null if [phone] isn't one. */
fun whatsAppNumber(phone: String?): String? = phone?.let(::normalizeIndianPhone)?.let { "91$it" }

/** Opens a chat with [number] and [text] typed in, in the WhatsApp app if installed. */
fun whatsAppLink(number: String, text: String): String = "https://wa.me/$number?text=${percentEncode(text)}"

private fun percentEncode(text: String): String = buildString {
    for (byte in text.encodeToByteArray()) {
        val c = byte.toInt() and 0xFF
        if (c.toChar().isLetterOrDigit() && c < 0x80 || c.toChar() in "-._~") {
            append(c.toChar())
        } else {
            append('%').append(HEX[c shr 4]).append(HEX[c and 0x0F])
        }
    }
}

private const val HEX = "0123456789ABCDEF"
