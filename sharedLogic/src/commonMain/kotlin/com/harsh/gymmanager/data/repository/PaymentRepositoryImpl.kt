package com.harsh.gymmanager.data.repository

import com.harsh.gymmanager.data.local.dao.PaymentDao
import com.harsh.gymmanager.data.mapper.toEntity
import com.harsh.gymmanager.domain.model.Payment
import com.harsh.gymmanager.domain.repository.PaymentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate
import kotlin.time.Clock

class PaymentRepositoryImpl(
    private val dao: PaymentDao,
    private val clock: Clock = Clock.System,
) : PaymentRepository {

    override suspend fun addPayment(payment: Payment): Long =
        dao.addPayment(payment.copy(id = 0).toEntity(createdAt = clock.now()))

    override suspend fun deletePayment(id: Long) = dao.deleteById(id)

    override suspend fun getAmountDue(membershipId: Long): Long? = dao.getAmountDue(membershipId)

    override fun observeRevenue(from: LocalDate, to: LocalDate): Flow<Long> = dao.observeRevenue(from, to)
}
