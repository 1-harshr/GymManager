package com.harsh.gymmanager.domain.usecase

import com.harsh.gymmanager.domain.MembershipStatus
import com.harsh.gymmanager.domain.model.Gender
import com.harsh.gymmanager.domain.model.Member
import com.harsh.gymmanager.domain.model.MemberSummary
import com.harsh.gymmanager.domain.repository.MemberRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

enum class MemberFilter { ALL, ACTIVE, EXPIRING_SOON, EXPIRED, NO_MEMBERSHIP, WITH_DUES, ARCHIVED }

/** Members matching a name/phone search, narrowed by [MemberFilter]. */
class ObserveMembersUseCase(private val repository: MemberRepository) {
    operator fun invoke(query: String = "", filter: MemberFilter = MemberFilter.ALL): Flow<List<MemberSummary>> =
        repository.observeMembers(query, archived = filter == MemberFilter.ARCHIVED).map { members ->
            when (filter) {
                MemberFilter.ALL, MemberFilter.ARCHIVED -> members
                MemberFilter.ACTIVE -> members.filter { it.status == MembershipStatus.ACTIVE }
                MemberFilter.EXPIRING_SOON -> members.filter { it.status == MembershipStatus.EXPIRING_SOON }
                MemberFilter.EXPIRED -> members.filter { it.status == MembershipStatus.EXPIRED }
                MemberFilter.NO_MEMBERSHIP -> members.filter { it.status == MembershipStatus.NONE }
                MemberFilter.WITH_DUES -> members.filter { it.amountDue > 0 }
            }
        }
}

class ObserveMemberUseCase(private val repository: MemberRepository) {
    operator fun invoke(id: Long): Flow<Member?> = repository.observeMember(id)
}

class AddMemberUseCase(
    private val repository: MemberRepository,
    private val clock: Clock,
    private val timeZone: TimeZone,
) {
    /** [joinedOn] defaults to today. */
    suspend operator fun invoke(
        name: String,
        phone: String? = null,
        gender: Gender? = null,
        dateOfBirth: LocalDate? = null,
        photoPath: String? = null,
        notes: String? = null,
        joinedOn: LocalDate? = null,
    ): Result<Long> = resultOf {
        repository.addMember(
            Member(
                name = name,
                phone = phone,
                gender = gender,
                dateOfBirth = dateOfBirth,
                photoPath = photoPath,
                notes = notes,
                joinedOn = joinedOn ?: clock.todayIn(timeZone),
            )
        )
    }
}

class UpdateMemberUseCase(private val repository: MemberRepository) {
    suspend operator fun invoke(member: Member): Result<Unit> = resultOf { repository.updateMember(member) }
}

class ArchiveMemberUseCase(private val repository: MemberRepository) {
    suspend operator fun invoke(id: Long): Result<Unit> = resultOf { repository.archiveMember(id) }
}

class RestoreMemberUseCase(private val repository: MemberRepository) {
    suspend operator fun invoke(id: Long): Result<Unit> = resultOf { repository.restoreMember(id) }
}

/** Also removes the member's memberships and payments. Prefer [ArchiveMemberUseCase]. */
class DeleteMemberPermanentlyUseCase(private val repository: MemberRepository) {
    suspend operator fun invoke(id: Long): Result<Unit> = resultOf { repository.deleteMemberPermanently(id) }
}

/** Archives, restores or deletes many members at once; each returns how many changed. */
class BulkMemberActionsUseCase(private val repository: MemberRepository) {
    suspend fun archive(ids: Collection<Long>): Result<Int> = resultOf { repository.archiveMembers(ids.toList()) }

    suspend fun restore(ids: Collection<Long>): Result<Int> = resultOf { repository.restoreMembers(ids.toList()) }

    /** Also removes their memberships and payments. */
    suspend fun deletePermanently(ids: Collection<Long>): Result<Int> = resultOf { repository.deleteMembersPermanently(ids.toList()) }
}

class MemberUseCases(repository: MemberRepository, clock: Clock, timeZone: TimeZone) {
    val observeMembers = ObserveMembersUseCase(repository)
    val observeMember = ObserveMemberUseCase(repository)
    val addMember = AddMemberUseCase(repository, clock, timeZone)
    val updateMember = UpdateMemberUseCase(repository)
    val archiveMember = ArchiveMemberUseCase(repository)
    val restoreMember = RestoreMemberUseCase(repository)
    val deleteMemberPermanently = DeleteMemberPermanentlyUseCase(repository)
    val bulk = BulkMemberActionsUseCase(repository)
}
