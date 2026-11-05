package com.harsh.gymmanager.domain.usecase

import androidx.room.Room
import com.harsh.gymmanager.data.local.GymDatabase
import com.harsh.gymmanager.data.local.buildGymDatabase
import com.harsh.gymmanager.data.repository.MemberRepositoryImpl
import com.harsh.gymmanager.data.repository.MembershipRepositoryImpl
import com.harsh.gymmanager.data.repository.PlanRepositoryImpl
import com.harsh.gymmanager.domain.model.DurationUnit
import com.harsh.gymmanager.domain.model.PaymentMode
import com.harsh.gymmanager.domain.model.Plan
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

class BulkOperationsTest {
    private val today = LocalDate(2026, 11, 5)
    private val clock = object : Clock {
        override fun now() = Instant.parse("2026-11-05T10:00:00Z")
    }
    private val tz = TimeZone.UTC

    private lateinit var db: GymDatabase
    private lateinit var members: MemberUseCases
    private lateinit var plans: PlanUseCases
    private lateinit var memberships: MembershipUseCases
    private lateinit var bulkRenew: BulkRenewUseCase
    private lateinit var importMembers: ImportMembersUseCase

    @BeforeTest
    fun setUp() {
        db = buildGymDatabase(Room.inMemoryDatabaseBuilder<GymDatabase>())
        val memberRepo = MemberRepositoryImpl(db.memberDao(), clock, tz)
        val membershipRepo = MembershipRepositoryImpl(db.membershipDao(), clock)
        members = MemberUseCases(memberRepo, clock, tz)
        plans = PlanUseCases(PlanRepositoryImpl(db.planDao(), clock))
        memberships = MembershipUseCases(membershipRepo, clock, tz)
        bulkRenew = BulkRenewUseCase(memberRepo, membershipRepo, clock, tz)
        importMembers = ImportMembersUseCase(memberRepo, clock, tz)
    }

    @AfterTest
    fun tearDown() {
        db.close()
    }

    private suspend fun monthly(): Plan {
        val id = plans.addPlan("Gym Monthly", 1, DurationUnit.MONTHS, 800).getOrThrow()
        return plans.getPlan(id)!!
    }

    @Test
    fun archiveRestoreAndDeleteMany() = runTest {
        val ids = listOf("Ravi", "Asha", "Mohit").map { members.addMember(it).getOrThrow() }

        assertEquals(2, members.bulk.archive(ids.take(2)).getOrThrow())
        assertEquals(listOf("Mohit"), members.observeMembers().first().map { it.name })
        assertEquals(2, members.bulk.restore(ids.take(2)).getOrThrow())
        assertEquals(3, members.observeMembers().first().size)

        memberships.sellMembership(ids[0], monthly(), firstPaymentAmount = 800).getOrThrow()
        assertEquals(2, members.bulk.deletePermanently(ids.take(2)).getOrThrow())
        assertEquals(listOf("Mohit"), members.observeMembers().first().map { it.name })
        assertEquals(emptyList(), memberships.observeMemberships(ids[0]).first())
    }

    @Test
    fun bulkRenewContinuesEachMembersOwnDatesAndCanMarkPaid() = runTest {
        val plan = monthly()
        val running = members.addMember("Running").getOrThrow()
        memberships.sellMembership(running, plan, startDate = LocalDate(2026, 10, 20)).getOrThrow()
        val lapsed = members.addMember("Lapsed").getOrThrow()
        memberships.sellMembership(lapsed, plan, startDate = LocalDate(2026, 8, 1)).getOrThrow()
        val archived = members.addMember("Archived").getOrThrow()
        members.archiveMember(archived)

        val preview = bulkRenew.preview(listOf(running, lapsed, archived), plan)
        assertEquals(listOf("Lapsed", "Running"), preview.map { it.member.name })
        assertEquals(LocalDate(2026, 11, 20), preview.single { it.member.id == running }.startDate)
        assertEquals(today, preview.single { it.member.id == lapsed }.startDate)

        assertEquals(2, bulkRenew(listOf(running, lapsed, archived), plan, markPaid = true, paymentMode = PaymentMode.UPI).getOrThrow())
        val renewed = memberships.observeMemberships(running).first().first()
        assertEquals(LocalDate(2026, 12, 19), renewed.endDate)
        assertEquals(0, renewed.amountDue)
        assertEquals(PaymentMode.UPI, renewed.payments.single().mode)
        assertEquals(emptyList(), memberships.observeMemberships(archived).first())
    }

    @Test
    fun bulkArchiveAndRestorePlans() = runTest {
        val a = plans.addPlan("A", 1, DurationUnit.MONTHS, 100).getOrThrow()
        val b = plans.addPlan("B", 1, DurationUnit.MONTHS, 200).getOrThrow()
        assertEquals(2, plans.bulk.archive(listOf(a, b)).getOrThrow())
        assertEquals(emptyList(), plans.observePlans().first())
        assertEquals(2, plans.bulk.restore(listOf(a, b)).getOrThrow())
        assertEquals(2, plans.observePlans().first().size)
    }

    @Test
    fun importSkipsPhonesAlreadySavedAndAddsTheRest() = runTest {
        members.addMember("Existing", phone = "9876543210").getOrThrow()
        val preview = importMembers.preview("Name,Phone\nRavi,98765 43210\nAsha,9123456780\nMohit,\n").getOrThrow()
        assertEquals(listOf("Asha", "Mohit"), preview.members.map { it.name })
        assertEquals(1, preview.skippedCount)

        assertEquals(2, importMembers.import(preview.members).getOrThrow())
        assertEquals(listOf("Asha", "Existing", "Mohit"), members.observeMembers().first().map { it.name })
    }
}
