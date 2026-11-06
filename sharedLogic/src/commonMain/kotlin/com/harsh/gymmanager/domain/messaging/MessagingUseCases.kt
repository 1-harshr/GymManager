package com.harsh.gymmanager.domain.messaging

import com.harsh.gymmanager.domain.MembershipStatus
import com.harsh.gymmanager.domain.model.MemberSummary
import com.harsh.gymmanager.domain.repository.MemberRepository
import com.harsh.gymmanager.domain.repository.MembershipRepository
import com.harsh.gymmanager.domain.repository.MessageLogRepository
import com.harsh.gymmanager.domain.repository.SettingsRepository
import com.harsh.gymmanager.domain.usecase.resultOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/** Expired members stay on the reminder list this long after their last day. */
const val REMIND_EXPIRED_FOR_DAYS = 30

/** A member reminded more recently than this isn't offered again in "send next". */
const val REMINDER_COOLDOWN_DAYS = 3

/** One member who could be sent a reminder, and whether that's sensible right now. */
data class ReminderItem(
    val member: MemberSummary,
    /** Null when the member has no usable WhatsApp number. */
    val message: MemberMessage.Reminder?,
    val lastSentAt: Instant?,
    val sentRecently: Boolean,
)

/**
 * Builds WhatsApp messages from stored data and keeps a log of what was handed to WhatsApp.
 * Each builder returns null when the member has no usable number or the data is gone.
 */
class MessagingUseCases(
    private val memberRepository: MemberRepository,
    private val membershipRepository: MembershipRepository,
    private val settingsRepository: SettingsRepository,
    private val messageLog: MessageLogRepository,
    private val clock: Clock,
    private val timeZone: TimeZone,
) {
    suspend fun welcome(memberId: String): MemberMessage.Welcome? {
        val member = memberRepository.observeMember(memberId).first() ?: return null
        val number = whatsAppNumber(member.phone) ?: return null
        return MemberMessage.Welcome(memberId, member.name, number, gymName())
    }

    /** A receipt for [membershipId]; pass [paymentId] for a payment recorded after the sale. */
    suspend fun receipt(memberId: String, membershipId: String, paymentId: String? = null): MemberMessage.Receipt? {
        val member = memberRepository.observeMember(memberId).first() ?: return null
        val number = whatsAppNumber(member.phone) ?: return null
        val membership = membershipRepository.observeMemberships(memberId).first().find { it.id == membershipId } ?: return null
        val payment = paymentId?.let { id -> membership.payments.find { it.id == id } ?: return null }
        return MemberMessage.Receipt(
            memberId = memberId,
            memberName = member.name,
            whatsAppNumber = number,
            gymName = gymName(),
            receiptNumber = if (payment != null) "${membership.id}-${payment.id}" else membership.id.toString(),
            planName = membership.planName,
            startDate = membership.startDate,
            endDate = membership.endDate,
            price = membership.price,
            discount = membership.discount,
            amountPaid = membership.amountPaid,
            amountDue = membership.amountDue,
            payment = payment,
        )
    }

    /** A receipt for the member's newest membership. */
    suspend fun latestReceipt(memberId: String): MemberMessage.Receipt? {
        val latest = membershipRepository.observeMemberships(memberId).first().firstOrNull() ?: return null
        return receipt(memberId, latest.id)
    }

    suspend fun reminder(memberId: String): MemberMessage.Reminder? =
        observeReminders().first().find { it.member.memberId == memberId }?.message

    /**
     * Members worth reminding: expiring this week, expired in the last [REMIND_EXPIRED_FOR_DAYS]
     * days, or owing money. Soonest expiry first, then recently expired, then dues only.
     */
    fun observeReminders(): Flow<List<ReminderItem>> =
        combine(memberRepository.observeMembers(), messageLog.observeLastSent(), settingsRepository.observe()) { members, lastSent, settings ->
            val today = clock.todayIn(timeZone)
            val now = clock.now()
            val gymName = settings.gym?.name.orEmpty()
            members
                .filter { member ->
                    val end = member.latestEndDate
                    member.status == MembershipStatus.EXPIRING_SOON ||
                        (member.status == MembershipStatus.EXPIRED && end != null && end.daysUntil(today) <= REMIND_EXPIRED_FOR_DAYS) ||
                        member.amountDue > 0
                }
                .sortedWith(
                    compareBy<MemberSummary>(
                        { if (it.status == MembershipStatus.EXPIRING_SOON) 0 else if (it.status == MembershipStatus.EXPIRED) 1 else 2 },
                        { if (it.status == MembershipStatus.EXPIRED) -(it.latestEndDate?.toEpochDays() ?: 0) else it.latestEndDate?.toEpochDays() ?: 0 },
                        { it.name.lowercase() },
                    )
                )
                .map { member ->
                    val sentAt = lastSent[member.memberId]?.get(MessageKind.REMINDER)
                    ReminderItem(
                        member = member,
                        message = whatsAppNumber(member.phone)?.let { number ->
                            MemberMessage.Reminder(
                                memberId = member.memberId,
                                memberName = member.name,
                                whatsAppNumber = number,
                                gymName = gymName,
                                status = member.status,
                                endDate = member.latestEndDate,
                                amountDue = member.amountDue,
                            )
                        },
                        lastSentAt = sentAt,
                        sentRecently = sentAt != null && now - sentAt < REMINDER_COOLDOWN_DAYS.days,
                    )
                }
        }

    /** How many members "send next" would go through now. */
    fun observeRemindersToSend(): Flow<Int> =
        observeReminders().map { items -> items.count { it.message != null && !it.sentRecently } }

    /** Call once WhatsApp has been opened with [message]; the owner still taps send there. */
    suspend fun markSent(message: MemberMessage): Result<Unit> = resultOf { messageLog.record(message.memberId, message.kind) }

    private suspend fun gymName(): String = settingsRepository.observe().first().gym?.name.orEmpty()
}
