package com.harsh.gymmanager.domain.usecase

import com.harsh.gymmanager.domain.insights.inboxAlerts
import com.harsh.gymmanager.domain.model.InboxItem
import com.harsh.gymmanager.domain.repository.InboxRepository
import com.harsh.gymmanager.domain.repository.MemberRepository
import com.harsh.gymmanager.domain.repository.MembershipRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.todayIn
import kotlin.time.Clock

/** The bell's inbox. Alerts come from the data, so [refresh] runs whenever the app opens and each morning. */
class InboxUseCases(
    private val inbox: InboxRepository,
    private val members: MemberRepository,
    private val memberships: MembershipRepository,
    private val clock: Clock,
    private val timeZone: TimeZone,
) {
    fun observeItems(): Flow<List<InboxItem>> = inbox.observeItems()

    fun observeUnreadCount(): Flow<Int> = inbox.observeUnreadCount()

    /** Adds today's new alerts and drops old ones; returns how many were added. */
    suspend fun refresh(): Result<Int> = resultOf {
        val today = clock.todayIn(timeZone)
        inbox.deleteCreatedBefore(today.minus(KEEP_DAYS, DateTimeUnit.DAY))
        inbox.addNew(inboxAlerts(today, members.observeMembers().first(), memberships.observeAllMemberships().first()), createdOn = today)
    }

    suspend fun markAllRead(): Result<Unit> = resultOf { inbox.markAllRead() }

    private companion object {
        const val KEEP_DAYS = 60
    }
}
