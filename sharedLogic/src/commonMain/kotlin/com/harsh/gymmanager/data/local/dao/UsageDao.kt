package com.harsh.gymmanager.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import com.harsh.gymmanager.data.local.entity.UsageRow

/** What the owner does most often, for ordering plans and suggesting amounts. Most used first. */
@Dao
interface UsageDao {
    @Query("SELECT planId AS value, COUNT(*) AS uses FROM memberships WHERE planId IS NOT NULL GROUP BY planId ORDER BY uses DESC")
    suspend fun planUses(): List<UsageRow>

    @Query("SELECT amount AS value, COUNT(*) AS uses FROM payments GROUP BY amount ORDER BY uses DESC, amount DESC LIMIT :limit")
    suspend fun paymentAmounts(limit: Int): List<UsageRow>

    @Query("SELECT discount AS value, COUNT(*) AS uses FROM memberships WHERE discount > 0 GROUP BY discount ORDER BY uses DESC, discount DESC LIMIT :limit")
    suspend fun discounts(limit: Int): List<UsageRow>
}
