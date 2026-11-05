package com.harsh.gymmanager.data.local.entity

import kotlinx.datetime.LocalDate

/** Whole rupees collected on one day; read-only, not a table. */
data class DailyTotalRow(val day: LocalDate, val total: Long)
