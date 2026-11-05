package com.harsh.gymmanager.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.harsh.gymmanager.data.local.entity.MemberEntity
import com.harsh.gymmanager.data.local.entity.MembershipEntity
import com.harsh.gymmanager.data.local.entity.MessageLogEntity
import com.harsh.gymmanager.data.local.entity.PaymentEntity
import com.harsh.gymmanager.data.local.entity.PlanEntity

/** Whole-table reads and a full replace, for backup and restore. */
@Dao
interface BackupDao {
    @Query("SELECT * FROM members ORDER BY id") suspend fun members(): List<MemberEntity>
    @Query("SELECT * FROM plans ORDER BY id") suspend fun plans(): List<PlanEntity>
    @Query("SELECT * FROM memberships ORDER BY id") suspend fun memberships(): List<MembershipEntity>
    @Query("SELECT * FROM payments ORDER BY id") suspend fun payments(): List<PaymentEntity>
    @Query("SELECT * FROM message_log ORDER BY id") suspend fun messages(): List<MessageLogEntity>

    /** All five tables read in one transaction, so the backup is a consistent moment. */
    @Transaction
    suspend fun everything(): Tables = Tables(members(), plans(), memberships(), payments(), messages())

    @Transaction
    suspend fun replaceAll(tables: Tables) {
        // Children first, so no foreign key is ever left pointing at nothing.
        deleteMessages()
        deletePayments()
        deleteMemberships()
        deleteMembers()
        deletePlans()
        insertPlans(tables.plans)
        insertMembers(tables.members)
        insertMemberships(tables.memberships)
        insertPayments(tables.payments)
        insertMessages(tables.messages)
    }

    @Query("DELETE FROM message_log") suspend fun deleteMessages()
    @Query("DELETE FROM payments") suspend fun deletePayments()
    @Query("DELETE FROM memberships") suspend fun deleteMemberships()
    @Query("DELETE FROM members") suspend fun deleteMembers()
    @Query("DELETE FROM plans") suspend fun deletePlans()

    @Insert suspend fun insertPlans(plans: List<PlanEntity>)
    @Insert suspend fun insertMembers(members: List<MemberEntity>)
    @Insert suspend fun insertMemberships(memberships: List<MembershipEntity>)
    @Insert suspend fun insertPayments(payments: List<PaymentEntity>)
    @Insert suspend fun insertMessages(messages: List<MessageLogEntity>)

    data class Tables(
        val members: List<MemberEntity>,
        val plans: List<PlanEntity>,
        val memberships: List<MembershipEntity>,
        val payments: List<PaymentEntity>,
        val messages: List<MessageLogEntity>,
    )
}
