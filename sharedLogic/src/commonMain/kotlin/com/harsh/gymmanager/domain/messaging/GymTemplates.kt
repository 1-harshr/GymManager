package com.harsh.gymmanager.domain.messaging

import com.harsh.gymmanager.domain.MembershipStatus
import com.harsh.gymmanager.presentation.formatRupees
import com.harsh.whatsapp.cloud.Template
import kotlinx.datetime.LocalDate

/**
 * The English templates the owner creates in WhatsApp Manager (`:gym-templates` holds their wording),
 * with their variables in order. Names and order must match those definitions. Hindi versions would be
 * the same names approved in `hi`.
 *
 * - `gym_welcome`: first name, gym
 * - `gym_receipt`: gym, receipt number, first name, plan, start date, end date, paid, due
 * - `gym_membership_ending`: first name, gym, end date
 * - `gym_membership_ended`: first name, gym, end date
 * - `gym_payment_due`: first name, amount due, gym
 * - `gym_miss_you`: first name, gym
 *
 * A renewal reminder doesn't mention dues; the link message does both in one.
 */
fun gymTemplate(message: MemberMessage): Template {
    val name = message.memberName.trim().substringBefore(' ')
    val gym = message.gymName
    return when (message) {
        is MemberMessage.Welcome -> Template("gym_welcome", "en", listOf(name, gym))
        is MemberMessage.Receipt -> Template(
            "gym_receipt",
            "en",
            listOf(
                gym,
                message.receiptNumber,
                name,
                message.planName,
                message.startDate.templateDate(),
                message.endDate.templateDate(),
                formatRupees(message.amountPaid),
                formatRupees(message.amountDue),
            ),
        )
        is MemberMessage.Reminder -> {
            val end = message.endDate
            when {
                end != null && message.status == MembershipStatus.EXPIRING_SOON ->
                    Template("gym_membership_ending", "en", listOf(name, gym, end.templateDate()))
                end != null && message.status == MembershipStatus.EXPIRED ->
                    Template("gym_membership_ended", "en", listOf(name, gym, end.templateDate()))
                else -> Template("gym_payment_due", "en", listOf(name, formatRupees(message.amountDue), gym))
            }
        }
        is MemberMessage.MissYou -> Template("gym_miss_you", "en", listOf(name, gym))
    }
}

private fun LocalDate.templateDate() = "$day ${MONTHS[month.ordinal]} $year"

private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
