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

    override fun observeMember(id: String): Flow<Member?> = dao.observeById(id.toLong()).map { it?.toDomain() }

    override suspend fun addMember(member: Member): String {
        val now = clock.now()
        return dao.insert(member.copy(id = "").toEntity(createdAt = now, updatedAt = now)).toString()
    }

    override suspend fun addMembers(members: List<Member>): List<String> {
        val now = clock.now()
        return dao.insertAll(members.map { it.copy(id = "").toEntity(createdAt = now, updatedAt = now) }).map(Long::toString)
    }

    override suspend fun getMembers(ids: List<String>): List<Member> = dao.getByIds(ids.map(String::toLong)).map { it.toDomain() }

    override suspend fun getPhones(): List<String> = dao.getPhones()

    override suspend fun archiveMembers(ids: List<String>): Int = dao.setArchived(ids.map(String::toLong), archived = true, updatedAt = clock.now())

    override suspend fun restoreMembers(ids: List<String>): Int = dao.setArchived(ids.map(String::toLong), archived = false, updatedAt = clock.now())

    override suspend fun deleteMembersPermanently(ids: List<String>): Int = dao.deleteByIds(ids.map(String::toLong))

    override suspend fun updateMember(member: Member) {
        val existing = requireNotNull(dao.getById(member.id.toLong())) { "Member ${member.id} does not exist" }
        dao.update(member.toEntity(createdAt = existing.createdAt, updatedAt = clock.now()))
    }

    override suspend fun archiveMember(id: String) = dao.setArchived(id.toLong(), archived = true, updatedAt = clock.now())

    override suspend fun restoreMember(id: String) = dao.setArchived(id.toLong(), archived = false, updatedAt = clock.now())

    override suspend fun deleteMemberPermanently(id: String) = dao.deleteById(id.toLong())

    // Status depends on today's date, so it is computed when each list is emitted.
    private fun Flow<List<MemberSummaryView>>.toSummaries(): Flow<List<MemberSummary>> = map { rows ->
        val today = today()
        rows.map { it.toDomain(today, EXPIRING_SOON_DAYS) }
    }
}
