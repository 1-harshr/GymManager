package com.harsh.gymmanager.data.repository

import com.harsh.gymmanager.data.local.dao.UsageDao
import com.harsh.gymmanager.domain.suggestions.UsageRepository
import com.harsh.gymmanager.domain.suggestions.UsageStats

class UsageRepositoryImpl(private val dao: UsageDao) : UsageRepository {
    override suspend fun stats(): UsageStats = UsageStats(
        planUses = dao.planUses().associate { it.value to it.uses },
        paymentAmounts = dao.paymentAmounts(LIMIT).map { it.value },
        discounts = dao.discounts(LIMIT).map { it.value },
    )

    private companion object {
        const val LIMIT = 6
    }
}
