package com.harsh.gymmanager.domain

import com.harsh.gymmanager.data.local.entity.DurationUnit
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus

enum class MembershipStatus { ACTIVE, EXPIRING_SOON, EXPIRED, NONE }

/** Inclusive last day of a membership, e.g. a 1-month plan from 15 Jan ends on 14 Feb. */
fun membershipEndDate(start: LocalDate, durationValue: Int, durationUnit: DurationUnit): LocalDate {
    val period = when (durationUnit) {
        DurationUnit.DAYS -> DatePeriod(days = durationValue)
        DurationUnit.MONTHS -> DatePeriod(months = durationValue)
    }
    return start.plus(period).minus(1, DateTimeUnit.DAY)
}

/** Renewals continue from the current membership if it is still running, otherwise they start today. */
fun renewalStartDate(latestEndDate: LocalDate?, today: LocalDate): LocalDate =
    if (latestEndDate != null && latestEndDate >= today) latestEndDate.plus(1, DateTimeUnit.DAY) else today

fun membershipStatus(latestEndDate: LocalDate?, today: LocalDate, expiringWithinDays: Int): MembershipStatus =
    when {
        latestEndDate == null -> MembershipStatus.NONE
        latestEndDate < today -> MembershipStatus.EXPIRED
        today.daysUntil(latestEndDate) <= expiringWithinDays -> MembershipStatus.EXPIRING_SOON
        else -> MembershipStatus.ACTIVE
    }
