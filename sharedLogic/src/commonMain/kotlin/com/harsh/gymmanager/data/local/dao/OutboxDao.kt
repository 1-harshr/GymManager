package com.harsh.gymmanager.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.harsh.gymmanager.data.local.entity.OutboxEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OutboxDao {
    @Insert
    suspend fun insert(message: OutboxEntity): Long

    @Update
    suspend fun update(message: OutboxEntity)

    @Query("SELECT * FROM whatsapp_outbox WHERE status = 'PENDING' ORDER BY id")
    suspend fun pending(): List<OutboxEntity>

    @Query("SELECT * FROM whatsapp_outbox ORDER BY id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<OutboxEntity>>
}
