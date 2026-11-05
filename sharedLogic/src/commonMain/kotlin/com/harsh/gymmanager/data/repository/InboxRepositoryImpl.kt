package com.harsh.gymmanager.data.repository

import com.harsh.gymmanager.data.local.dao.InboxDao
import com.harsh.gymmanager.data.local.entity.InboxEntity
import com.harsh.gymmanager.domain.insights.InboxAlert
import com.harsh.gymmanager.domain.model.InboxItem
import com.harsh.gymmanager.domain.repository.InboxRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

class InboxRepositoryImpl(private val dao: InboxDao) : InboxRepository {
    override fun observeItems(): Flow<List<InboxItem>> = dao.observeAll().map { rows ->
        rows.map { InboxItem(it.id, it.kind, it.memberId, it.memberName, it.amount, it.date, it.createdOn, it.isRead) }
    }

    override fun observeUnreadCount(): Flow<Int> = dao.observeUnreadCount()

    override suspend fun addNew(alerts: List<InboxAlert>, createdOn: LocalDate): Int = dao.addNew(
        alerts.map { InboxEntity(eventKey = it.eventKey, kind = it.kind, memberId = it.memberId, memberName = it.memberName, amount = it.amount, date = it.date, createdOn = createdOn) }
    )

    override suspend fun markAllRead() = dao.markAllRead()

    override suspend fun deleteCreatedBefore(date: LocalDate) = dao.deleteCreatedBefore(date)
}
