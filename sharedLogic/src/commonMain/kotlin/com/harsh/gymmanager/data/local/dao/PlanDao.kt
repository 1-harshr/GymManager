package com.harsh.gymmanager.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.harsh.gymmanager.data.local.entity.PlanEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlanDao {
    @Insert
    suspend fun insert(plan: PlanEntity): Long

    @Update
    suspend fun update(plan: PlanEntity)

    @Query("UPDATE plans SET isArchived = :archived WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean)

    @Query("SELECT * FROM plans WHERE id = :id")
    suspend fun getById(id: Long): PlanEntity?

    @Query("SELECT * FROM plans WHERE isArchived = 0 ORDER BY name COLLATE NOCASE")
    fun observeActive(): Flow<List<PlanEntity>>

    @Query("SELECT * FROM plans ORDER BY isArchived, name COLLATE NOCASE")
    fun observeAll(): Flow<List<PlanEntity>>
}
