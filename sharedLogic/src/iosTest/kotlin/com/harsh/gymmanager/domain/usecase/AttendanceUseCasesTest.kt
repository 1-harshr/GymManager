package com.harsh.gymmanager.domain.usecase

import androidx.room.Room
import com.harsh.gymmanager.data.local.GymDatabase
import com.harsh.gymmanager.data.local.buildGymDatabase
import com.harsh.gymmanager.data.repository.AttendanceRepositoryImpl
import com.harsh.gymmanager.data.repository.MemberRepositoryImpl
import com.harsh.gymmanager.data.repository.MembershipRepositoryImpl
import com.harsh.gymmanager.data.repository.MessageLogRepositoryImpl
import com.harsh.gymmanager.data.repository.PlanRepositoryImpl
import com.harsh.gymmanager.domain.messaging.MessageKind
import com.harsh.gymmanager.domain.model.AppLanguage
import com.harsh.gymmanager.domain.model.AppSettings
import com.harsh.gymmanager.domain.model.DurationUnit
import com.harsh.gymmanager.domain.model.GymProfile
import com.harsh.gymmanager.domain.model.HomeFeatures
import com.harsh.gymmanager.domain.model.MorningUpdate
import com.harsh.gymmanager.domain.model.ThemeMode
import com.harsh.gymmanager.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class AttendanceUseCasesTest {
    private val today = LocalDate(2026, 11, 20)
    private var now = Instant.parse("2026-11-20T10:00:00Z")
    private val clock = object : Clock {
        override fun now() = this@AttendanceUseCasesTest.now
    }
    private val tz = TimeZone.UTC

    private class FakeSettings : SettingsRepository {
        val state = MutableStateFlow(AppSettings(gym = GymProfile("Iron House")))
        override fun observe() = state
        override suspend fun setGym(profile: GymProfile) { state.value = state.value.copy(gym = profile) }
        override suspend fun setTheme(theme: ThemeMode) {}
        override suspend fun setLanguage(language: AppLanguage) {}
        override suspend fun setMorningUpdate(update: MorningUpdate) {}
        override suspend fun setHomeFeatures(features: HomeFeatures) {}
        override suspend fun setMembersAsGrid(grid: Boolean) {}
    }

    private lateinit var db: GymDatabase
    private lateinit var members: MemberUseCases
    private lateinit var memberships: MembershipUseCases
    private lateinit var plans: PlanUseCases
    private lateinit var attendance: AttendanceUseCases

    @BeforeTest
    fun setUp() {
        db = buildGymDatabase(Room.inMemoryDatabaseBuilder<GymDatabase>())
        val memberRepo = MemberRepositoryImpl(db.memberDao(), clock, tz)
        members = MemberUseCases(memberRepo, clock, tz)
        memberships = MembershipUseCases(MembershipRepositoryImpl(db.membershipDao(), clock), clock, tz)
        plans = PlanUseCases(PlanRepositoryImpl(db.planDao(), clock))
        attendance = AttendanceUseCases(
            AttendanceRepositoryImpl(db.checkInDao(), clock, tz),
            memberRepo,
            FakeSettings(),
            MessageLogRepositoryImpl(db.messageLogDao(), clock),
            clock,
            tz,
        )
    }

    @AfterTest
    fun tearDown() {
        db.close()
    }

    /** Moves the clock [days] from the test's today. */
    private fun on(days: Int) {
        now = Instant.parse("2026-11-20T10:00:00Z") + days.days
    }

    /** A member with a monthly plan running from [startedDaysAgo] days ago. */
    private suspend fun activeMember(name: String, phone: String? = null, startedDaysAgo: Int = 25): String {
        val planId = plans.addPlan("Monthly", 2, DurationUnit.MONTHS, defaultPrice = 1000).getOrThrow()
        val plan = plans.getPlan(planId)!!
        val id = members.addMember(name, phone = phone).getOrThrow()
        memberships.sellMembership(id, plan, startDate = LocalDate.fromEpochDays(today.toEpochDays() - startedDaysAgo)).getOrThrow()
        return id
    }

    @Test
    fun checkingInTwiceInADayKeepsTheFirst() = runTest {
        val ravi = activeMember("Ravi")
        val first = attendance.checkIn(ravi).getOrThrow()
        now += 1.days / 24
        val again = attendance.checkIn(ravi).getOrThrow()

        assertEquals(first, again)
        assertEquals(today, first.date)
        assertEquals(listOf(ravi), attendance.observeToday().first().map { it.member.memberId })
        assertEquals(1, attendance.observeTodayCount().first())
    }

    @Test
    fun undoRemovesTheCheckIn() = runTest {
        val ravi = activeMember("Ravi")
        val checkIn = attendance.checkIn(ravi).getOrThrow()
        attendance.undoCheckIn(checkIn.id).getOrThrow()

        assertTrue(attendance.observeToday().first().isEmpty())
        assertNull(attendance.observeMember(ravi).first().today)
    }

    @Test
    fun memberPageCountsThisMonthAndKnowsTheLastVisit() = runTest {
        val ravi = activeMember("Ravi")
        on(-25); attendance.checkIn(ravi) // 26 Oct, last month
        on(-5); attendance.checkIn(ravi)
        on(-2); attendance.checkIn(ravi)
        on(0)

        val visits = attendance.observeMember(ravi).first()
        assertNull(visits.today)
        assertEquals(LocalDate(2026, 11, 18), visits.lastVisit)
        assertEquals(2, visits.visitsThisMonth)
        assertEquals(3, visits.recent.size)

        attendance.checkIn(ravi)
        assertNotNull(attendance.observeMember(ravi).first().today)
    }

    @Test
    fun goneQuietListsRunningMembersAwayTwoWeeksOrMore() = runTest {
        val away = activeMember("Away Long", phone = "9876543210")
        val recent = activeMember("Away Recently")
        val regular = activeMember("Regular")
        activeMember("Never Came")
        val lapsed = members.addMember("Lapsed").getOrThrow()
        on(-30); attendance.checkIn(away); attendance.checkIn(lapsed)
        on(-GONE_QUIET_DAYS); attendance.checkIn(recent)
        on(-3); attendance.checkIn(regular)
        on(0)

        val quiet = attendance.observeGoneQuiet().first()
        assertEquals(listOf("Away Recently", "Away Long"), quiet.map { it.member.name }, "most recently gone first; never-came and no-plan members left out")
        assertEquals(GONE_QUIET_DAYS, quiet.first().daysAway)
        assertNull(quiet.first().message, "no phone, no message")
        val message = assertNotNull(quiet.last().message)
        assertEquals(MessageKind.MISS_YOU, message.kind)
        assertEquals("Iron House", message.gymName)
        assertFalse(quiet.last().sentRecently)

        attendance.markSent(message).getOrThrow()
        assertTrue(attendance.observeGoneQuiet().first().last().sentRecently)
    }

    @Test
    fun deletingAMemberRemovesTheirCheckIns() = runTest {
        val ravi = activeMember("Ravi")
        attendance.checkIn(ravi)
        members.deleteMemberPermanently(ravi).getOrThrow()
        assertEquals(0, attendance.observeTodayCount().first())
    }
}
