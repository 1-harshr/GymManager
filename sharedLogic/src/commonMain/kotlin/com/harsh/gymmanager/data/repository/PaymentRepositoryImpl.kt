package com.harsh.gymmanager.data.repository

import com.harsh.gymmanager.data.local.dao.PaymentDao
import com.harsh.gymmanager.data.mapper.toDomain
import com.harsh.gymmanager.data.mapper.toEntity
import com.harsh.gymmanager.domain.model.Payment
import com.harsh.gymmanager.domain.model.PaymentRecord
import com.harsh.gymmanager.domain.repository.PaymentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlin.time.Clock

class PaymentRepositoryImpl(
    private val dao: PaymentDao,
    private val clock: Clock = Clock.System,
) : PaymentRepository {

    override suspend fun addPayment(payment: Payment): String =
        dao.addPayment(payment.copy(id = "").toEntity(createdAt = clock.now())).toString()

    override suspend fun deletePayment(id: String) = dao.deleteById(id.toLong())

    override suspend fun getAmountDue(membershipId: String): Long? = dao.getAmountDue(membershipId.toLong())

    override fun observeRevenue(from: LocalDate, to: LocalDate): Flow<Long> = dao.observeRevenue(from, to)

    override fun observeDailyRevenue(from: LocalDate, to: LocalDate): Flow<Map<LocalDate, Long>> =
        dao.observeDailyRevenue(from, to).map { rows -> rows.associate { it.day to it.total } }

    override fun observePayments(from: LocalDate, to: LocalDate, limit: Int?): Flow<List<PaymentRecord>> =
        dao.observePayments(from, to, limit ?: -1).map { rows -> rows.map { it.toDomain() } }
}
