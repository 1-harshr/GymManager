package com.harsh.gymmanager.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.harsh.gymmanager.data.local.entity.MemberEntity
import com.harsh.gymmanager.data.local.entity.MemberSummaryView
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

@Dao
interface MemberDao {
    @Insert
    suspend fun insert(member: MemberEntity): Long

    /** Inserts all rows in one transaction. */
    @Insert
    suspend fun insertAll(members: List<MemberEntity>): List<Long>

    @Update
    suspend fun update(member: MemberEntity)

    /** Permanently removes the member along with their memberships and payments. Prefer [setArchived]. */
    @Query("DELETE FROM members WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM members WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>): Int

    @Query("UPDATE members SET isArchived = :archived, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean, updatedAt: Instant)

    @Query("UPDATE members SET isArchived = :archived, updatedAt = :updatedAt WHERE id IN (:ids)")
    suspend fun setArchived(ids: List<Long>, archived: Boolean, updatedAt: Instant): Int

    @Query("SELECT * FROM members WHERE id IN (:ids) ORDER BY name COLLATE NOCASE")
    suspend fun getByIds(ids: List<Long>): List<MemberEntity>

    @Query("SELECT phone FROM members WHERE phone IS NOT NULL")
    suspend fun getPhones(): List<String>

    @Query("SELECT * FROM members WHERE id = :id")
    suspend fun getById(id: Long): MemberEntity?

    @Query("SELECT * FROM members WHERE id = :id")
    fun observeById(id: Long): Flow<MemberEntity?>

    @Query(
        """
        SELECT * FROM member_summary
        WHERE isArchived = :archived
            AND (:query = '' OR name LIKE '%' || :query || '%' OR phone LIKE '%' || :query || '%')
        ORDER BY name COLLATE NOCASE
        """
    )
    fun observeSummaries(query: String = "", archived: Boolean = false): Flow<List<MemberSummaryView>>

    @Query(
        """
        SELECT * FROM member_summary
        WHERE isArchived = 0 AND latestEndDate BETWEEN :today AND :until
        ORDER BY latestEndDate
        """
    )
    fun observeExpiring(today: LocalDate, until: LocalDate): Flow<List<MemberSummaryView>>

    @Query(
        """
        SELECT * FROM member_summary
        WHERE isArchived = 0 AND latestEndDate < :today
        ORDER BY latestEndDate DESC
        """
    )
    fun observeExpired(today: LocalDate): Flow<List<MemberSummaryView>>

    @Query("SELECT * FROM member_summary WHERE isArchived = 0 AND amountDue > 0 ORDER BY amountDue DESC")
    fun observeWithDues(): Flow<List<MemberSummaryView>>
}
