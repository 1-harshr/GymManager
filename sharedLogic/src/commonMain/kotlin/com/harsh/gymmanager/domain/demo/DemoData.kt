package com.harsh.gymmanager.domain.demo

import com.harsh.gymmanager.domain.model.DurationUnit
import com.harsh.gymmanager.domain.model.Gender
import com.harsh.gymmanager.domain.model.PaymentMode
import com.harsh.gymmanager.domain.model.PaymentMode.CASH
import com.harsh.gymmanager.domain.model.PaymentMode.UPI
import com.harsh.gymmanager.domain.model.Plan
import com.harsh.gymmanager.domain.usecase.MemberUseCases
import com.harsh.gymmanager.domain.usecase.MembershipUseCases
import com.harsh.gymmanager.domain.usecase.PaymentUseCases
import com.harsh.gymmanager.domain.usecase.PlanUseCases
import com.harsh.gymmanager.domain.usecase.resultOf
import kotlinx.coroutines.flow.first
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlin.time.Clock

/**
 * Sample members, plans and payments for showing the app. Dates are relative to today, so every
 * part of the app has something to show: renewals due this week and today, recent lapses, an old
 * due, a renewal, a day-pass trial, and payments across this month and last. It only adds; the
 * gym's own data stays. Members get no phone numbers, so no message can reach a real person.
 */
class DemoDataUseCase(
    private val members: MemberUseCases,
    private val plans: PlanUseCases,
    private val memberships: MembershipUseCases,
    private val payments: PaymentUseCases,
    private val clock: Clock,
    private val timeZone: TimeZone,
) {
    /** Returns how many members were added. */
    suspend fun seed(): Result<Int> = resultOf {
        val today = clock.todayIn(timeZone)
        fun day(offset: Int) = today.plus(offset, DateTimeUnit.DAY)
        val plansByName = demoPlans().associate { it.name to planNamed(it) }

        DEMO_MEMBERS.forEach { demo ->
            val memberId = members.addMember(demo.name, gender = demo.gender, joinedOn = day(demo.joined)).getOrThrow()
            demo.memberships.forEach { sale ->
                val plan = plansByName.getValue(sale.plan)
                val membershipId = memberships.sellMembership(
                    memberId, plan, startDate = day(sale.start), endDate = day(sale.end), discount = sale.discount,
                ).getOrThrow()
                sale.payments.forEach { (amount, on, mode) ->
                    payments.recordPayment(membershipId, amount, mode, paidOn = day(on)).getOrThrow()
                }
            }
        }
        DEMO_MEMBERS.size
    }

    /** The gym's own plan of that name if it has one, otherwise a new one. */
    private suspend fun planNamed(plan: Plan): Plan =
        plans.observePlans().first().firstOrNull { it.name.equals(plan.name, ignoreCase = true) }
            ?: plans.getPlan(plans.addPlan(plan.name, plan.durationValue, plan.durationUnit, plan.defaultPrice).getOrThrow())!!
}

private const val GYM_MONTHLY = "Gym Monthly"
private const val GYM_QUARTER = "Gym 3 months"
private const val MMA = "MMA Monthly"
private const val ZUMBA = "Zumba Monthly"
private const val TRIAL = "1-day trial"

private fun demoPlans() = listOf(
    Plan(name = GYM_MONTHLY, durationValue = 1, durationUnit = DurationUnit.MONTHS, defaultPrice = 1200),
    Plan(name = GYM_QUARTER, durationValue = 3, durationUnit = DurationUnit.MONTHS, defaultPrice = 3300),
    Plan(name = MMA, durationValue = 1, durationUnit = DurationUnit.MONTHS, defaultPrice = 1500),
    Plan(name = ZUMBA, durationValue = 1, durationUnit = DurationUnit.MONTHS, defaultPrice = 1000),
    Plan(name = TRIAL, durationValue = 1, durationUnit = DurationUnit.DAYS, defaultPrice = 100),
)

/** Days are offsets from today; payments are (amount, day, mode). */
private class DemoSale(val plan: String, val start: Int, val end: Int, val discount: Long = 0, val payments: List<Triple<Long, Int, PaymentMode>>)

private class DemoMember(val name: String, val gender: Gender, val joined: Int, val memberships: List<DemoSale>)

private val DEMO_MEMBERS = listOf(
    DemoMember("Rohan Verma", Gender.MALE, -70, listOf(DemoSale(GYM_QUARTER, -70, 21, payments = listOf(Triple(3300, -70, UPI))))),
    // Renewals due this week, and one today.
    DemoMember("Priya Sharma", Gender.FEMALE, -27, listOf(DemoSale(ZUMBA, -27, 3, payments = listOf(Triple(1000, -27, CASH))))),
    DemoMember("Aman Gupta", Gender.MALE, -29, listOf(DemoSale(MMA, -29, 1, payments = listOf(Triple(1500, -29, UPI))))),
    DemoMember("Neha Kumari", Gender.FEMALE, -30, listOf(DemoSale(GYM_MONTHLY, -30, 0, payments = listOf(Triple(1200, -30, CASH))))),
    // Recently lapsed: worth winning back.
    DemoMember("Vikash Yadav", Gender.MALE, -33, listOf(DemoSale(GYM_MONTHLY, -33, -3, payments = listOf(Triple(1200, -33, CASH))))),
    DemoMember("Sunita Devi", Gender.FEMALE, -40, listOf(DemoSale(ZUMBA, -40, -10, payments = listOf(Triple(1000, -40, CASH))))),
    DemoMember(
        "Deepak Oraon", Gender.MALE, -80,
        listOf(
            DemoSale(MMA, -80, -51, payments = listOf(Triple(1500, -80, CASH))),
            DemoSale(MMA, -50, -20, payments = listOf(Triple(1500, -50, UPI))),
        ),
    ),
    DemoMember("Sneha Raj", Gender.FEMALE, -1, listOf(DemoSale(TRIAL, -1, -1, payments = listOf(Triple(100, -1, CASH))))),
    // Money still owed: one for weeks, one for days, one never paid.
    DemoMember("Arjun Singh", Gender.MALE, -20, listOf(DemoSale(MMA, -20, 10, discount = 200, payments = listOf(Triple(800, -20, UPI))))),
    DemoMember("Rahul Pandey", Gender.MALE, -8, listOf(DemoSale(GYM_MONTHLY, -8, 22, payments = listOf(Triple(600, -8, CASH))))),
    DemoMember("Nisha Gupta", Gender.FEMALE, -25, listOf(DemoSale(ZUMBA, -25, 5, payments = emptyList()))),
    // Paid up, in instalments, with a discount, today, and a renewal.
    DemoMember("Kavya Mishra", Gender.FEMALE, -45, listOf(DemoSale(GYM_QUARTER, -45, 46, payments = listOf(Triple(2000, -45, UPI), Triple(1300, -12, UPI))))),
    DemoMember("Pooja Kumari", Gender.FEMALE, -15, listOf(DemoSale(ZUMBA, -15, 15, payments = listOf(Triple(1000, -15, CASH))))),
    DemoMember("Anjali Tirkey", Gender.FEMALE, -12, listOf(DemoSale(GYM_MONTHLY, -12, 18, payments = listOf(Triple(1200, -2, UPI))))),
    DemoMember("Manish Kumar", Gender.MALE, -5, listOf(DemoSale(GYM_QUARTER, -5, 86, discount = 300, payments = listOf(Triple(3000, -5, UPI))))),
    DemoMember("Suraj Mahto", Gender.MALE, -3, listOf(DemoSale(MMA, -3, 27, payments = listOf(Triple(1500, 0, CASH))))),
    DemoMember(
        "Ritu Singh", Gender.FEMALE, -61,
        listOf(
            DemoSale(GYM_MONTHLY, -61, -31, payments = listOf(Triple(1200, -61, CASH))),
            DemoSale(GYM_MONTHLY, -30, -5, payments = listOf(Triple(1200, -30, CASH))),
            DemoSale(GYM_MONTHLY, -4, 26, payments = listOf(Triple(1200, -4, UPI))),
        ),
    ),
    // Joined but hasn't bought a plan yet.
    DemoMember("Ravi Shankar", Gender.MALE, -2, emptyList()),
)

/** The password the demo sheet asks for, so data isn't added by a stray tap. */
const val DEMO_PASSWORD = "0000"
