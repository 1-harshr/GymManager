package com.harsh.gymmanager.domain.repository

import com.harsh.gymmanager.domain.model.Payment
import com.harsh.gymmanager.domain.model.PaymentRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

interface PaymentRepository {
    /** @throws com.harsh.gymmanager.domain.OverpaymentException if [payment] is more than the amount due. */
    suspend fun addPayment(payment: Payment): String

    suspend fun deletePayment(id: String)

    suspend fun getAmountDue(membershipId: String): Long?

    /** Total collected between [from] and [to], both inclusive, in whole rupees. */
    fun observeRevenue(from: LocalDate, to: LocalDate): Flow<Long>

    /** Whole rupees collected on each day between [from] and [to]; days without payments are left out. */
    fun observeDailyRevenue(from: LocalDate, to: LocalDate): Flow<Map<LocalDate, Long>>

    /** Payments made between [from] and [to], both inclusive, newest first; at most [limit] if given. */
    fun observePayments(from: LocalDate, to: LocalDate, limit: Int? = null): Flow<List<PaymentRecord>>
}
