package com.harsh.gymmanager.data.repository

import com.harsh.gymmanager.data.local.dao.MembershipDao
import com.harsh.gymmanager.data.mapper.toDomain
import com.harsh.gymmanager.data.mapper.toEntity
import com.harsh.gymmanager.domain.OverpaymentException
import com.harsh.gymmanager.domain.model.Membership
import com.harsh.gymmanager.domain.model.Payment
import com.harsh.gymmanager.domain.repository.MembershipRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlin.time.Clock

class MembershipRepositoryImpl(
    private val dao: MembershipDao,
    private val clock: Clock = Clock.System,
) : MembershipRepository {

    override fun observeMemberships(memberId: String): Flow<List<Membership>> =
        dao.observeForMember(memberId.toLong()).map { rows -> rows.map { it.toDomain() } }

    override fun observeAllMemberships(): Flow<List<Membership>> =
        dao.observeAllActiveMembers().map { rows -> rows.map { it.toDomain() } }

    override suspend fun getLatestEndDate(memberId: String): LocalDate? = dao.getLatestEndDate(memberId.toLong())

    override suspend fun addMembership(membership: Membership, firstPayment: Payment?): String {
        if (firstPayment != null && firstPayment.amount > membership.amountCharged) {
            throw OverpaymentException(membership.amountCharged)
        }
        val now = clock.now()
        return dao.insertWithFirstPayment(
            membership = membership.copy(id = "").toEntity(createdAt = now),
            firstPayment = firstPayment?.copy(id = "")?.toEntity(createdAt = now),
        ).toString()
    }

    override suspend fun addMemberships(items: List<Pair<Membership, Payment?>>): List<String> {
        items.forEach { (membership, payment) ->
            if (payment != null && payment.amount > membership.amountCharged) throw OverpaymentException(membership.amountCharged)
        }
        val now = clock.now()
        return dao.insertAllWithFirstPayments(
            items.map { (membership, payment) ->
                membership.copy(id = "").toEntity(createdAt = now) to payment?.copy(id = "")?.toEntity(createdAt = now)
            },
        ).map(Long::toString)
    }

    override suspend fun updateMembership(membership: Membership) {
        val existing = requireNotNull(dao.getById(membership.id.toLong())) { "Membership ${membership.id} does not exist" }
        dao.update(membership.toEntity(createdAt = existing.createdAt))
    }

    override suspend fun deleteMembership(id: String) = dao.deleteById(id.toLong())

    // Demo mode's sample gym doesn't offer pausing; the screens don't show it there.
    override suspend fun pauseMembership(id: String, pausedFrom: LocalDate, resumesOn: LocalDate) =
        throw UnsupportedOperationException("Pausing needs the gym online")

    override suspend fun resumeMembership(id: String) = throw UnsupportedOperationException("Pausing needs the gym online")
}
