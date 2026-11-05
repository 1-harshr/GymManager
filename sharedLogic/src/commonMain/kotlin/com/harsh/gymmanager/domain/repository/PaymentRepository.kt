package com.harsh.gymmanager.domain.repository

import com.harsh.gymmanager.domain.model.Payment
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

interface PaymentRepository {
    /** @throws com.harsh.gymmanager.domain.OverpaymentException if [payment] is more than the amount due. */
    suspend fun addPayment(payment: Payment): Long

    suspend fun deletePayment(id: Long)

    suspend fun getAmountDue(membershipId: Long): Long?

    /** Total collected between [from] and [to], both inclusive, in whole rupees. */
    fun observeRevenue(from: LocalDate, to: LocalDate): Flow<Long>
}
