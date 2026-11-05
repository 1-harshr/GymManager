package com.harsh.gymmanager.data.local

import androidx.room.TypeConverter
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/** Dates are stored as epoch days so date-range queries stay simple integer comparisons. */
class Converters {
    @TypeConverter
    fun localDateToEpochDays(date: LocalDate?): Long? = date?.toEpochDays()

    @TypeConverter
    fun epochDaysToLocalDate(days: Long?): LocalDate? = days?.let(LocalDate::fromEpochDays)

    @TypeConverter
    fun instantToEpochMillis(instant: Instant?): Long? = instant?.toEpochMilliseconds()

    @TypeConverter
    fun epochMillisToInstant(millis: Long?): Instant? = millis?.let(Instant::fromEpochMilliseconds)
}
