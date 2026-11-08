package com.harsh.gymmanager.data.remote

import com.harsh.gymmanager.domain.model.CheckIn
import com.harsh.gymmanager.domain.repository.AttendanceRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Instant

/**
 * The gym's check-ins online. The database dates each one by the gym's own clock and keeps one per
 * member a day, so checking in twice leaves the first one as it was.
 */
internal class SupabaseAttendanceRepository(
    private val supabase: SupabaseClient,
    private val refresher: Refresher,
    private val gym: GymGate,
) : AttendanceRepository {
    private val checkIns get() = supabase.postgrest.from("check_ins")

    override suspend fun checkIn(memberId: String): CheckIn = write {
        checkIns.upsert(
            buildJsonObject {
                put("gym_id", gym.gymId())
                put("member_id", memberId)
            },
        ) {
            onConflict = "member_id,checked_in_on"
            ignoreDuplicates = true
        }
        checkIns.select(COLUMNS) {
            filter { eq("member_id", memberId) }
            order("checked_in_at", Order.DESCENDING)
            limit(1)
        }.decodeSingle<CheckInRow>().toDomain()
    }

    override suspend fun undoCheckIn(id: String) = write {
        checkIns.delete { filter { eq("id", id) } }
        Unit
    }

    override fun observeCheckIns(date: LocalDate): Flow<List<CheckIn>> = refresher.observe {
        checkIns.select(COLUMNS) {
            filter { eq("checked_in_on", date.toString()) }
            order("checked_in_at", Order.DESCENDING)
        }.decodeList<CheckInRow>().map { it.toDomain() }
    }

    override fun observeMemberCheckIns(memberId: String, limit: Int): Flow<List<CheckIn>> = refresher.observe {
        checkIns.select(COLUMNS) {
            filter { eq("member_id", memberId) }
            order("checked_in_on", Order.DESCENDING)
            limit(limit.toLong())
        }.decodeList<CheckInRow>().map { it.toDomain() }
    }

    override fun observeLastVisits(): Flow<Map<String, LocalDate>> = refresher.observe {
        supabase.postgrest.from("member_last_check_in")
            .select()
            .decodeList<LastVisitRow>()
            .associate { it.memberId to it.lastCheckInOn }
    }

    private suspend fun <T> write(block: suspend () -> T): T = remote(block).also { refresher.changed() }

    @Serializable
    private data class CheckInRow(
        val id: String,
        @SerialName("member_id") val memberId: String,
        @SerialName("checked_in_on") val checkedInOn: LocalDate,
        @SerialName("checked_in_at") val checkedInAt: String,
    ) {
        fun toDomain() = CheckIn(id, memberId, checkedInOn, Instant.parse(checkedInAt))
    }

    @Serializable
    private data class LastVisitRow(
        @SerialName("member_id") val memberId: String,
        @SerialName("last_check_in_on") val lastCheckInOn: LocalDate,
    )

    private companion object {
        val COLUMNS = Columns.raw("id, member_id, checked_in_on, checked_in_at")
    }
}
