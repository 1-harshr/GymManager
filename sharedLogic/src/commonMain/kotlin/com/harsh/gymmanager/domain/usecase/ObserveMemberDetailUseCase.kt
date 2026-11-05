package com.harsh.gymmanager.domain.usecase

import com.harsh.gymmanager.domain.EXPIRING_SOON_DAYS
import com.harsh.gymmanager.domain.MembershipStatus
import com.harsh.gymmanager.domain.membershipStatus
import com.harsh.gymmanager.domain.model.Member
import com.harsh.gymmanager.domain.model.Membership
import com.harsh.gymmanager.domain.repository.MemberRepository
import com.harsh.gymmanager.domain.repository.MembershipRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

data class MemberDetail(
    val member: Member,
    /** Newest first, each with its payments. */
    val memberships: List<Membership>,
    val status: MembershipStatus,
    val latestEndDate: LocalDate?,
    /** Whole rupees owed across all memberships. */
    val totalDue: Long,
)

class ObserveMemberDetailUseCase(
    private val memberRepository: MemberRepository,
    private val membershipRepository: MembershipRepository,
    private val clock: Clock,
    private val timeZone: TimeZone,
) {
    /** Emits null if the member does not exist. */
    operator fun invoke(memberId: Long): Flow<MemberDetail?> =
        combine(
            memberRepository.observeMember(memberId),
            membershipRepository.observeMemberships(memberId),
        ) { member, memberships ->
            member?.let {
                val latestEndDate = memberships.maxOfOrNull { m -> m.endDate }
                MemberDetail(
                    member = it,
                    memberships = memberships,
                    status = membershipStatus(latestEndDate, clock.todayIn(timeZone), EXPIRING_SOON_DAYS),
                    latestEndDate = latestEndDate,
                    totalDue = memberships.sumOf { m -> m.amountDue },
                )
            }
        }
}
