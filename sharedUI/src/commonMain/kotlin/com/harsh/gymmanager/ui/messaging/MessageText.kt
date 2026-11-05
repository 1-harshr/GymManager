package com.harsh.gymmanager.ui.messaging

import androidx.compose.runtime.Composable
import com.harsh.gymmanager.domain.MembershipStatus
import com.harsh.gymmanager.domain.messaging.MemberMessage
import com.harsh.gymmanager.domain.messaging.MessageKind
import com.harsh.gymmanager.presentation.formatRupees
import com.harsh.gymmanager.ui.format.formatFull
import com.harsh.gymmanager.ui.format.label
import com.harsh.gymmanager.ui.format.rememberToday
import gymmanager.sharedui.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/** The message as the member will read it, in the app's language. *Stars* are WhatsApp bold. */
@Composable
fun MemberMessage.text(): String = when (this) {
    is MemberMessage.Welcome -> stringResource(Res.string.wa_welcome, firstName(memberName), gymName)
    is MemberMessage.Receipt -> receiptText()
    is MemberMessage.Reminder -> reminderText()
}

@Composable
fun MessageKind.label(): String = stringResource(
    when (this) {
        MessageKind.WELCOME -> Res.string.wa_kind_welcome
        MessageKind.RECEIPT -> Res.string.wa_kind_receipt
        MessageKind.REMINDER -> Res.string.wa_kind_reminder
    }
)

@Composable
private fun MemberMessage.Receipt.receiptText(): String {
    val details = buildList {
        add(stringResource(Res.string.wa_line_plan, planName))
        add(stringResource(Res.string.wa_line_valid, startDate.formatFull(), endDate.formatFull()))
        add(stringResource(Res.string.wa_line_price, formatRupees(price)))
        if (discount > 0) add(stringResource(Res.string.wa_line_discount, formatRupees(discount)))
        val payment = payment
        if (payment != null) {
            add(stringResource(Res.string.wa_line_received, formatRupees(payment.amount), payment.mode.label(), payment.paidOn.formatFull()))
            add(stringResource(Res.string.wa_line_paid_total, formatRupees(amountPaid)))
        } else {
            add(stringResource(Res.string.wa_line_paid, formatRupees(amountPaid)))
        }
        add(if (amountDue > 0) stringResource(Res.string.wa_line_due, formatRupees(amountDue)) else stringResource(Res.string.wa_line_fully_paid))
    }
    return listOf(
        stringResource(Res.string.wa_receipt_title, gymName) + "\n" + stringResource(Res.string.wa_receipt_number, receiptNumber),
        stringResource(Res.string.wa_receipt_greeting, firstName(memberName)),
        details.joinToString("\n"),
    ).joinToString("\n\n")
}

@Composable
private fun MemberMessage.Reminder.reminderText(): String {
    val name = firstName(memberName)
    val end = endDate
    val today = rememberToday()
    val renewal = when {
        end == null -> null
        status == MembershipStatus.EXPIRING_SOON && end == today -> stringResource(Res.string.wa_reminder_ending_today, name, gymName)
        status == MembershipStatus.EXPIRING_SOON -> stringResource(Res.string.wa_reminder_ending, name, gymName, end.formatFull())
        status == MembershipStatus.EXPIRED -> stringResource(Res.string.wa_reminder_ended, name, gymName, end.formatFull())
        else -> null
    }
    return when {
        renewal == null -> stringResource(Res.string.wa_reminder_due, name, formatRupees(amountDue), gymName)
        amountDue > 0 -> renewal + "\n\n" + stringResource(Res.string.wa_reminder_due_line, formatRupees(amountDue))
        else -> renewal
    }
}

/** Messages greet by first name: "Hi Ravi", not "Hi Ravi Kumar Singh". */
private fun firstName(name: String): String = name.trim().substringBefore(' ')
