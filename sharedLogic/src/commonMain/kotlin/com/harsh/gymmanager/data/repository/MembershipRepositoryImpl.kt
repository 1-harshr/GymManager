package com.harsh.gymmanager.data.repository

import com.harsh.gymmanager.data.local.dao.MembershipDao
import com.harsh.gymmanager.data.mapper.toDomain
import com.harsh.gymmanager.data.mapper.toEntity
import com.harsh.gymmanager.domain.model.Membership
import com.harsh.gymmanager.domain.repository.MembershipRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlin.time.Clock

class MembershipRepositoryImpl(
    private val dao: MembershipDao,
    private val clock: Clock = Clock.System,
) : MembershipRepository {

    override fun observeMemberships(memberId: Long): Flow<List<Membership>> =
        dao.observeForMember(memberId).map { rows -> rows.map { it.toDomain() } }

    override suspend fun getLatestEndDate(memberId: Long): LocalDate? = dao.getLatestEndDate(memberId)

    override suspend fun addMembership(membership: Membership): Long =
        dao.insert(membership.copy(id = 0).toEntity(createdAt = clock.now()))

    override suspend fun updateMembership(membership: Membership) {
        val existing = requireNotNull(dao.getById(membership.id)) { "Membership ${membership.id} does not exist" }
        dao.update(membership.toEntity(createdAt = existing.createdAt))
    }

    override suspend fun deleteMembership(id: Long) = dao.deleteById(id)
}
