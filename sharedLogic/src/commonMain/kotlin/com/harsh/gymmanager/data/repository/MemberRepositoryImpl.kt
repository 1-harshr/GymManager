package com.harsh.gymmanager.data.repository

import com.harsh.gymmanager.data.local.dao.MemberDao
import com.harsh.gymmanager.data.local.entity.MemberSummaryView
import com.harsh.gymmanager.data.mapper.toDomain
import com.harsh.gymmanager.data.mapper.toEntity
import com.harsh.gymmanager.domain.EXPIRING_SOON_DAYS
import com.harsh.gymmanager.domain.model.Member
import com.harsh.gymmanager.domain.model.MemberSummary
import com.harsh.gymmanager.domain.repository.MemberRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlin.time.Clock

class MemberRepositoryImpl(
    private val dao: MemberDao,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : MemberRepository {

    private fun today() = clock.todayIn(timeZone)

    override fun observeMembers(query: String, archived: Boolean): Flow<List<MemberSummary>> =
        dao.observeSummaries(query.trim(), archived).toSummaries()

    override fun observeExpiring(withinDays: Int): Flow<List<MemberSummary>> {
        val today = today()
        return dao.observeExpiring(today, today.plus(withinDays, DateTimeUnit.DAY)).toSummaries()
    }

    override fun observeExpired(): Flow<List<MemberSummary>> = dao.observeExpired(today()).toSummaries()

    override fun observeWithDues(): Flow<List<MemberSummary>> = dao.observeWithDues().toSummaries()

    override fun observeMember(id: Long): Flow<Member?> = dao.observeById(id).map { it?.toDomain() }

    override suspend fun addMember(member: Member): Long {
        val now = clock.now()
        return dao.insert(member.copy(id = 0).toEntity(createdAt = now, updatedAt = now))
    }

    override suspend fun updateMember(member: Member) {
        val existing = requireNotNull(dao.getById(member.id)) { "Member ${member.id} does not exist" }
        dao.update(member.toEntity(createdAt = existing.createdAt, updatedAt = clock.now()))
    }

    override suspend fun archiveMember(id: Long) = dao.setArchived(id, archived = true, updatedAt = clock.now())

    override suspend fun restoreMember(id: Long) = dao.setArchived(id, archived = false, updatedAt = clock.now())

    override suspend fun deleteMemberPermanently(id: Long) = dao.deleteById(id)

    // Status depends on today's date, so it is computed when each list is emitted.
    private fun Flow<List<MemberSummaryView>>.toSummaries(): Flow<List<MemberSummary>> = map { rows ->
        val today = today()
        rows.map { it.toDomain(today, EXPIRING_SOON_DAYS) }
    }
}
