package com.harsh.gymmanager.domain.usecase

import com.harsh.gymmanager.domain.MembershipStatus
import com.harsh.gymmanager.domain.messaging.MemberMessage
import com.harsh.gymmanager.domain.messaging.MessageKind
import com.harsh.gymmanager.domain.messaging.whatsAppNumber
import com.harsh.gymmanager.domain.model.CheckIn
import com.harsh.gymmanager.domain.model.MemberSummary
import com.harsh.gymmanager.domain.repository.AttendanceRepository
import com.harsh.gymmanager.domain.repository.MemberRepository
import com.harsh.gymmanager.domain.repository.MessageLogRepository
import com.harsh.gymmanager.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/** A member with a running membership who hasn't come in this many days has gone quiet. */
const val GONE_QUIET_DAYS = 14

/** A member sent a "we miss you" more recently than this is shown as already nudged. */
const val MISS_YOU_COOLDOWN_DAYS = 7

/** Weeks of visits a member's page shows as a grid. */
const val MEMBER_HEATMAP_WEEKS = 12

/**
 * How many of a member's visits their page reads. A member checks in at most once a day, so this
 * many always covers every day the grid shows.
 */
private const val MEMBER_VISITS = MEMBER_HEATMAP_WEEKS * 7

/** Someone checked in today, with what staff should know about them. */
data class Visit(val checkIn: CheckIn, val member: MemberSummary)

data class MemberAttendance(
    /** Today's check-in, if they've come in. */
    val today: CheckIn?,
    val lastVisit: LocalDate?,
    val visitsThisMonth: Int,
    /** Latest first. */
    val recent: List<CheckIn>,
)

/** A member who has stopped coming, and whether a "we miss you" makes sense now. */
data class QuietMember(
    val member: MemberSummary,
    val lastVisit: LocalDate,
    val daysAway: Int,
    /** Null when the member has no usable WhatsApp number. */
    val message: MemberMessage.MissYou?,
    val lastSentAt: Instant?,
    val sentRecently: Boolean,
)

/** Checking members in, who's here today, each member's visits, and who has stopped coming. */
class AttendanceUseCases(
    private val attendance: AttendanceRepository,
    private val members: MemberRepository,
    private val settings: SettingsRepository,
    private val messageLog: MessageLogRepository,
    private val clock: Clock,
    private val timeZone: TimeZone,
) {
    /** Checking in someone already in today succeeds and returns that check-in. */
    suspend fun checkIn(memberId: String): Result<CheckIn> = resultOf { attendance.checkIn(memberId) }

    suspend fun undoCheckIn(id: String): Result<Unit> = resultOf { attendance.undoCheckIn(id) }

    /** Today's visits, latest first. "Today" is read when collecting starts. */
    fun observeToday(): Flow<List<Visit>> =
        combine(attendance.observeCheckIns(today()), members.observeMembers()) { checkIns, members ->
            val byId = members.associateBy { it.memberId }
            checkIns.mapNotNull { checkIn -> byId[checkIn.memberId]?.let { Visit(checkIn, it) } }
        }

    fun observeTodayCount(): Flow<Int> = attendance.observeCheckIns(today()).map { it.size }

    fun observeMember(memberId: String): Flow<MemberAttendance> =
        attendance.observeMemberCheckIns(memberId, MEMBER_VISITS).map { visits ->
            val today = today()
            MemberAttendance(
                today = visits.firstOrNull { it.date == today },
                lastVisit = visits.firstOrNull()?.date,
                visitsThisMonth = visits.count { it.date.year == today.year && it.date.month == today.month },
                recent = visits,
            )
        }

    /**
     * Members with a running membership whose latest visit was [GONE_QUIET_DAYS] or more days ago,
     * most recently gone first, since they're the likeliest to come back. Members who have never
     * checked in are left out, so starting to use attendance doesn't flag everyone.
     */
    fun observeGoneQuiet(): Flow<List<QuietMember>> =
        combine(members.observeMembers(), attendance.observeLastVisits(), messageLog.observeLastSent(), settings.observe()) { members, lastVisits, lastSent, settings ->
            val today = today()
            val now = clock.now()
            val gymName = settings.gym?.name.orEmpty()
            members
                .filter { it.status == MembershipStatus.ACTIVE || it.status == MembershipStatus.EXPIRING_SOON }
                .mapNotNull { member ->
                    val lastVisit = lastVisits[member.memberId] ?: return@mapNotNull null
                    val daysAway = lastVisit.daysUntil(today)
                    if (daysAway < GONE_QUIET_DAYS) return@mapNotNull null
                    val sentAt = lastSent[member.memberId]?.get(MessageKind.MISS_YOU)
                    QuietMember(
                        member = member,
                        lastVisit = lastVisit,
                        daysAway = daysAway,
                        message = whatsAppNumber(member.phone)?.let { number ->
                            MemberMessage.MissYou(member.memberId, member.name, number, gymName, lastVisit)
                        },
                        lastSentAt = sentAt,
                        sentRecently = sentAt != null && now - sentAt < MISS_YOU_COOLDOWN_DAYS.days,
                    )
                }
                .sortedWith(compareBy({ it.daysAway }, { it.member.name.lowercase() }))
        }

    /** Call once WhatsApp has been opened with [message]. */
    suspend fun markSent(message: MemberMessage): Result<Unit> = resultOf { messageLog.record(message.memberId, message.kind) }

    private fun today(): LocalDate = clock.todayIn(timeZone)
}
