package com.harsh.gymmanager.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import com.harsh.gymmanager.data.local.entity.UsageRow

/** What the owner does most often, for ordering choices and suggesting values. Most used first. */
@Dao
interface UsageDao {
    @Query("SELECT planId AS value, COUNT(*) AS uses FROM memberships WHERE planId IS NOT NULL GROUP BY planId ORDER BY uses DESC")
    suspend fun planUses(): List<UsageRow>

    @Query("SELECT amount AS value, COUNT(*) AS uses FROM payments GROUP BY amount ORDER BY uses DESC, amount DESC LIMIT :limit")
    suspend fun paymentAmounts(limit: Int): List<UsageRow>

    @Query("SELECT discount AS value, COUNT(*) AS uses FROM memberships WHERE discount > 0 GROUP BY discount ORDER BY uses DESC, discount DESC LIMIT :limit")
    suspend fun discounts(limit: Int): List<UsageRow>

    /** Dates are stored as epoch days, so the difference is the length; both ends count. */
    @Query("SELECT endDate - startDate + 1 AS value, COUNT(*) AS uses FROM memberships GROUP BY value ORDER BY uses DESC, value LIMIT :limit")
    suspend fun saleLengths(limit: Int): List<UsageRow>
}
