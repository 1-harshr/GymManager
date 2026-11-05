package com.harsh.gymmanager.domain.usecase

import androidx.room.Room
import com.harsh.gymmanager.data.local.GymDatabase
import com.harsh.gymmanager.data.local.buildGymDatabase
import com.harsh.gymmanager.data.repository.InboxRepositoryImpl
import com.harsh.gymmanager.data.repository.MemberRepositoryImpl
import com.harsh.gymmanager.data.repository.MembershipRepositoryImpl
import com.harsh.gymmanager.data.repository.PlanRepositoryImpl
import com.harsh.gymmanager.domain.model.DurationUnit
import com.harsh.gymmanager.domain.model.InboxKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Instant

class InboxUseCasesTest {
    private val clock = object : Clock {
        override fun now() = Instant.parse("2026-11-20T10:00:00Z")
    }
    private val tz = TimeZone.UTC

    private lateinit var db: GymDatabase
    private lateinit var members: MemberUseCases
    private lateinit var plans: PlanUseCases
    private lateinit var memberships: MembershipUseCases
    private lateinit var inbox: InboxUseCases

    @BeforeTest
    fun setUp() {
        db = buildGymDatabase(Room.inMemoryDatabaseBuilder<GymDatabase>())
        val memberRepo = MemberRepositoryImpl(db.memberDao(), clock, tz)
        val membershipRepo = MembershipRepositoryImpl(db.membershipDao(), clock)
        members = MemberUseCases(memberRepo, clock, tz)
        plans = PlanUseCases(PlanRepositoryImpl(db.planDao(), clock))
        memberships = MembershipUseCases(membershipRepo, clock, tz)
        inbox = InboxUseCases(InboxRepositoryImpl(db.inboxDao()), memberRepo, membershipRepo, clock, tz)
    }

    @AfterTest
    fun tearDown() {
        db.close()
    }

    private suspend fun sell(name: String, start: LocalDate, end: LocalDate, paid: Long?): Long {
        val planId = plans.addPlan("Monthly", 1, DurationUnit.MONTHS, defaultPrice = 1500).getOrThrow()
        val plan = plans.observePlans().first().single { it.id == planId }
        val id = members.addMember(name).getOrThrow()
        memberships.sellMembership(id, plan, startDate = start, endDate = end, firstPaymentAmount = paid).getOrThrow()
        return id
    }

    @Test
    fun addsEachAlertOnceAndDropsThemWithTheMember() = runTest {
        sell("Ravi", LocalDate(2026, 10, 21), LocalDate(2026, 11, 20), paid = 1500) // ends today
        sell("Priya", LocalDate(2026, 10, 18), LocalDate(2026, 11, 17), paid = 1500) // lapsed 3 days ago
        val amit = sell("Amit", LocalDate(2026, 11, 1), LocalDate(2026, 11, 30), paid = 500) // 1000 owed for 19 days

        assertEquals(3, inbox.refresh().getOrThrow())
        assertEquals(0, inbox.refresh().getOrThrow())

        val items = inbox.observeItems().first()
        assertEquals(
            mapOf("Ravi" to InboxKind.ENDS_TODAY, "Priya" to InboxKind.LAPSED, "Amit" to InboxKind.DUE_OVERDUE),
            items.associate { it.memberName to it.kind },
        )
        assertEquals(1000, items.single { it.memberName == "Amit" }.amount)
        assertEquals(3, inbox.observeUnreadCount().first())

        inbox.markAllRead().getOrThrow()
        assertEquals(0, inbox.observeUnreadCount().first())

        members.deleteMemberPermanently(amit).getOrThrow()
        assertEquals(listOf("Priya", "Ravi"), inbox.observeItems().first().map { it.memberName }.sorted())
    }
}
