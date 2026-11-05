package com.harsh.gymmanager.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.harsh.gymmanager.data.local.entity.PaymentEntity
import com.harsh.gymmanager.domain.OverpaymentException
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

@Dao
interface PaymentDao {
    /** Records a payment, rejecting it if it is more than what is still owed on the membership. */
    @Transaction
    suspend fun addPayment(payment: PaymentEntity): Long {
        val due = getAmountDue(payment.membershipId)
            ?: throw IllegalArgumentException("Membership ${payment.membershipId} does not exist")
        if (payment.amount > due) throw OverpaymentException(due)
        return insert(payment)
    }

    @Insert
    suspend fun insert(payment: PaymentEntity): Long

    @Query("DELETE FROM payments WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query(
        """
        SELECT ms.price - ms.discount
            - (SELECT COALESCE(SUM(p.amount), 0) FROM payments p WHERE p.membershipId = ms.id)
        FROM memberships ms WHERE ms.id = :membershipId
        """
    )
    suspend fun getAmountDue(membershipId: Long): Long?

    /** Total collected between [from] and [to], both inclusive, in whole rupees. */
    @Query("SELECT COALESCE(SUM(amount), 0) FROM payments WHERE paidOn BETWEEN :from AND :to")
    fun observeRevenue(from: LocalDate, to: LocalDate): Flow<Long>
}
