package com.harsh.gymmanager.presentation

import androidx.room.Room
import com.harsh.gymmanager.data.local.GymDatabase
import com.harsh.gymmanager.data.local.buildGymDatabase
import com.harsh.gymmanager.data.repository.MemberRepositoryImpl
import com.harsh.gymmanager.data.repository.MembershipRepositoryImpl
import com.harsh.gymmanager.data.repository.PaymentRepositoryImpl
import com.harsh.gymmanager.data.repository.PlanRepositoryImpl
import com.harsh.gymmanager.domain.MembershipStatus
import com.harsh.gymmanager.domain.model.DurationUnit
import com.harsh.gymmanager.domain.model.PaymentMode
import com.harsh.gymmanager.domain.usecase.MemberFilter
import com.harsh.gymmanager.domain.usecase.MemberUseCases
import com.harsh.gymmanager.domain.usecase.MembershipUseCases
import com.harsh.gymmanager.domain.usecase.ObserveDashboardUseCase
import com.harsh.gymmanager.domain.usecase.ObserveMemberDetailUseCase
import com.harsh.gymmanager.domain.usecase.PaymentUseCases
import com.harsh.gymmanager.domain.usecase.PlanUseCases
import com.harsh.gymmanager.presentation.dashboard.DashboardViewModel
import com.harsh.gymmanager.presentation.memberdetail.MemberDetailEvent
import com.harsh.gymmanager.presentation.memberdetail.MemberDetailViewModel
import com.harsh.gymmanager.presentation.memberedit.MemberEditEvent
import com.harsh.gymmanager.presentation.memberedit.MemberEditViewModel
import com.harsh.gymmanager.presentation.members.MembersViewModel
import com.harsh.gymmanager.presentation.planedit.PlanEditEvent
import com.harsh.gymmanager.presentation.planedit.PlanEditViewModel
import com.harsh.gymmanager.presentation.sellmembership.SellMembershipEvent
import com.harsh.gymmanager.presentation.sellmembership.SellMembershipViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ViewModelTest {
    private val today = LocalDate(2026, 11, 5)
    private val clock = object : Clock {
        override fun now() = Instant.parse("2026-11-05T10:00:00Z")
    }
    private val tz = TimeZone.UTC

    private lateinit var db: GymDatabase
    private lateinit var members: MemberUseCases
    private lateinit var plans: PlanUseCases
    private lateinit var memberships: MembershipUseCases
    private lateinit var payments: PaymentUseCases
    private lateinit var memberDetail: ObserveMemberDetailUseCase
    private lateinit var dashboard: ObserveDashboardUseCase

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = buildGymDatabase(Room.inMemoryDatabaseBuilder<GymDatabase>())
        val memberRepo = MemberRepositoryImpl(db.memberDao(), clock, tz)
        val membershipRepo = MembershipRepositoryImpl(db.membershipDao(), clock)
        val paymentRepo = PaymentRepositoryImpl(db.paymentDao(), clock)
        members = MemberUseCases(memberRepo, clock, tz)
        plans = PlanUseCases(PlanRepositoryImpl(db.planDao(), clock))
        memberships = MembershipUseCases(membershipRepo, clock, tz)
        payments = PaymentUseCases(paymentRepo, clock, tz)
        memberDetail = ObserveMemberDetailUseCase(memberRepo, membershipRepo, clock, tz)
        dashboard = ObserveDashboardUseCase(memberRepo, paymentRepo, clock, tz)
    }

    @AfterTest
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private suspend fun monthlyPlanId() = plans.addPlan("Monthly", 1, DurationUnit.MONTHS, 1500).getOrThrow()

    @Test
    fun memberEditRequiresNameThenSaves() = runTest {
        val vm = MemberEditViewModel(null, members)
        vm.save()
        assertEquals("Name is required", vm.state.value.nameError)

        vm.onNameChange("Ravi")
        assertNull(vm.state.value.nameError)
        vm.onPhoneChange("9876543210")
        vm.save()
        val saved = assertIs<MemberEditEvent.Saved>(vm.events.first())
        val member = members.observeMember(saved.memberId).first()!!
        assertEquals("9876543210", member.phone)
        assertEquals(today, member.joinedOn)
    }

    @Test
    fun memberEditLoadsAndUpdatesExisting() = runTest {
        val id = members.addMember("Ravi").getOrThrow()
        val vm = MemberEditViewModel(id, members)
        val loaded = vm.state.first { !it.isLoading }
        assertEquals("Ravi", loaded.form.name)

        vm.onNameChange("Ravi Kumar")
        vm.save()
        assertIs<MemberEditEvent.Saved>(vm.events.first())
        assertEquals("Ravi Kumar", members.observeMember(id).first()!!.name)
    }

    @Test
    fun planEditValidatesFields() = runTest {
        val vm = PlanEditViewModel(null, plans)
        vm.onDurationChange("0")
        vm.onPriceChange("abc")
        vm.save()
        with(vm.state.value) {
            assertEquals("Plan name is required", nameError)
            assertEquals("Enter a number of days or months", durationError)
            assertEquals("Enter a price in rupees", priceError)
        }

        vm.onNameChange("Quarterly")
        vm.onDurationChange("3")
        vm.onPriceChange("₹4,000")
        vm.save()
        assertIs<PlanEditEvent.Saved>(vm.events.first())
        assertEquals(4000, plans.observePlans().first().single().defaultPrice)
    }

    @Test
    fun sellMembershipPrefillsFromPlanAndValidatesAmounts() = runTest {
        monthlyPlanId()
        val memberId = members.addMember("Ravi").getOrThrow()
        val vm = SellMembershipViewModel(memberId, plans, memberships)
        val plan = vm.state.first { it.plans.isNotEmpty() }.plans.single()

        vm.onPlanSelect(plan)
        val prefilled = vm.state.first { it.selectedPlan != null }
        assertEquals(today, prefilled.startDate)
        assertEquals(LocalDate(2026, 12, 4), prefilled.endDate)
        assertEquals("1500", prefilled.priceText)

        vm.onStartDateChange(LocalDate(2026, 11, 10))
        assertEquals(LocalDate(2026, 12, 9), vm.state.value.endDate)

        vm.onDiscountChange("200")
        vm.onFirstPaymentChange("1400")
        vm.save()
        assertEquals("Amount paid can't be more than ₹1,300", vm.state.value.errorMessage)

        vm.onFirstPaymentChange("1000")
        vm.onPaymentModeChange(PaymentMode.UPI)
        assertEquals(300, vm.state.value.amountDueAfterPayment)
        vm.save()
        assertIs<SellMembershipEvent.Sold>(vm.events.first())

        val membership = memberships.observeMemberships(memberId).first().single()
        assertEquals(1300, membership.amountCharged)
        assertEquals(PaymentMode.UPI, membership.payments.single().mode)
    }

    @Test
    fun memberDetailRecordsPartialPaymentAndRejectsOverpayment() = runTest {
        val planId = monthlyPlanId()
        val memberId = members.addMember("Ravi").getOrThrow()
        val plan = plans.getPlan(planId)!!
        val membershipId = memberships.sellMembership(memberId, plan, firstPaymentAmount = 500).getOrThrow()

        val vm = MemberDetailViewModel(memberId, memberDetail, members, memberships, payments)
        val loaded = vm.state.first { it.detail != null }
        assertEquals(MembershipStatus.ACTIVE, loaded.detail!!.status)
        assertEquals(1000, loaded.detail.totalDue)

        vm.openPaymentForm(membershipId)
        assertEquals("1000", vm.state.value.paymentForm!!.amountText)

        vm.onPaymentAmountChange("1001")
        vm.submitPayment()
        assertEquals("Amount is more than the ₹1,000 due", vm.state.first { it.paymentForm?.error != null }.paymentForm!!.error)

        vm.onPaymentAmountChange("600")
        vm.submitPayment()
        assertEquals(MemberDetailEvent.ShowMessage("Payment of ₹600 recorded"), vm.events.first())
        val after = vm.state.first { it.detail?.totalDue == 400L }
        assertNull(after.paymentForm)
    }

    @Test
    fun membersSearchAndFilterAndDashboard() = runTest {
        val plan = plans.getPlan(monthlyPlanId())!!
        val ravi = members.addMember("Ravi", phone = "9876543210").getOrThrow()
        memberships.sellMembership(ravi, plan).getOrThrow()
        members.addMember("Asha")

        val vm = MembersViewModel(members)
        assertEquals(2, vm.state.first { !it.isLoading }.members.size)

        vm.onFilterChange(MemberFilter.WITH_DUES)
        assertEquals(listOf("Ravi"), vm.state.first { it.members.size == 1 }.members.map { it.name })

        vm.onFilterChange(MemberFilter.ALL)
        vm.onQueryChange("asha")
        // Results lag the typed query by the debounce, so wait for the results themselves.
        vm.state.first { it.members.map { m -> m.name } == listOf("Asha") }

        val board = DashboardViewModel(dashboard).state.first { !it.isLoading }.dashboard!!
        assertEquals(1, board.activeCount)
        assertEquals(1500, board.totalDue)
    }

    @Test
    fun membersSelectModeArchivesTickedMembersAndClears() = runTest {
        val ravi = members.addMember("Ravi").getOrThrow()
        val asha = members.addMember("Asha").getOrThrow()
        members.addMember("Mohit")
        val vm = MembersViewModel(members)
        vm.state.first { it.members.size == 3 }

        vm.toggleSelection(ravi)
        vm.toggleSelection(asha)
        assertEquals(setOf(ravi, asha), vm.state.first { it.selectedIds.size == 2 }.selectedIds)
        vm.toggleSelection(asha)
        vm.toggleSelection(asha)

        vm.archiveSelected()
        assertEquals("Archived 2 members", vm.messages.first())
        val after = vm.state.first { it.members.size == 1 }
        assertEquals(listOf("Mohit"), after.members.map { it.name })
        assertEquals(false, after.isSelecting)

        vm.selectAll()
        assertEquals(1, vm.state.first { it.isSelecting }.selectedIds.size)
        vm.onFilterChange(MemberFilter.ARCHIVED)
        assertEquals(false, vm.state.first { it.filter == MemberFilter.ARCHIVED }.isSelecting)
    }
}
