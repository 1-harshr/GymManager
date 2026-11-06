package com.harsh.gymmanager.data.remote

import com.harsh.gymmanager.domain.model.Member
import com.harsh.gymmanager.domain.model.MemberSummary
import com.harsh.gymmanager.domain.repository.GymRepository
import com.harsh.gymmanager.domain.repository.MemberRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Clock

/**
 * The gym's members online. Lists come from the member_summary view, which adds each member's latest
 * end date and what they owe; the status is worked out here from those, against today.
 */
internal class SupabaseMemberRepository(
    private val supabase: SupabaseClient,
    private val refresher: Refresher,
    private val gyms: GymRepository,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : MemberRepository {
    private val members get() = supabase.postgrest.from("members")

    private fun today() = clock.todayIn(timeZone)

    /** Everyone archived or not, by name ignoring case, as the phone's own list orders them. */
    private fun observeSummaries(archived: Boolean): Flow<List<MemberSummary>> = refresher.observe {
        val today = today()
        supabase.postgrest.from("member_summary")
            .select { filter { eq("is_archived", archived) } }
            .decodeList<MemberSummaryRow>()
            .map { it.toDomain(today) }
            .sortedBy { it.name.lowercase() }
    }

    override fun observeMembers(query: String, archived: Boolean): Flow<List<MemberSummary>> {
        val q = query.trim()
        return observeSummaries(archived).map { all ->
            if (q.isEmpty()) all else all.filter { it.name.contains(q, ignoreCase = true) || it.phone?.contains(q) == true }
        }
    }

    override fun observeExpiring(withinDays: Int): Flow<List<MemberSummary>> = observeSummaries(archived = false).map { all ->
        val today = today()
        val until = today.plus(withinDays, DateTimeUnit.DAY)
        all.filter { it.latestEndDate != null && it.latestEndDate in today..until }.sortedBy { it.latestEndDate }
    }

    override fun observeExpired(): Flow<List<MemberSummary>> = observeSummaries(archived = false).map { all ->
        val today = today()
        all.filter { it.latestEndDate != null && it.latestEndDate < today }.sortedByDescending { it.latestEndDate }
    }

    override fun observeWithDues(): Flow<List<MemberSummary>> = observeSummaries(archived = false).map { all ->
        all.filter { it.amountDue > 0 }.sortedByDescending { it.amountDue }
    }

    override fun observeMember(id: String): Flow<Member?> = refresher.observe {
        members.select { filter { eq("id", id) } }.decodeSingleOrNull<MemberRow>()?.toDomain()
    }

    override suspend fun addMember(member: Member): String = write {
        members.insert(member.toRow(gyms.currentGymId())) { select(Columns.raw("id")) }.decodeSingle<IdRow>().id
    }

    /** One request, so either all are added or none. */
    override suspend fun addMembers(members: List<Member>): List<String> {
        if (members.isEmpty()) return emptyList()
        val gymId = gyms.currentGymId()
        return write {
            this.members.insert(members.map { it.toRow(gymId) }) { select(Columns.raw("id")) }.decodeList<IdRow>().map { it.id }
        }
    }

    override suspend fun getMembers(ids: List<String>): List<Member> {
        if (ids.isEmpty()) return emptyList()
        return remote {
            members.select { filter { isIn("id", ids) } }.decodeList<MemberRow>().map { it.toDomain() }.sortedBy { it.name.lowercase() }
        }
    }

    override suspend fun getPhones(): List<String> = remote {
        members.select(Columns.raw("phone")).decodeList<PhoneRow>().mapNotNull { it.phone }
    }

    override suspend fun updateMember(member: Member) = write {
        members.update(member.toRow(gymId = null)) { filter { eq("id", member.id) } }
        Unit
    }

    override suspend fun archiveMember(id: String) {
        setArchived(listOf(id), true)
    }

    override suspend fun restoreMember(id: String) {
        setArchived(listOf(id), false)
    }

    override suspend fun deleteMemberPermanently(id: String) {
        deleteMembersPermanently(listOf(id))
    }

    override suspend fun archiveMembers(ids: List<String>): Int = setArchived(ids, true)

    override suspend fun restoreMembers(ids: List<String>): Int = setArchived(ids, false)

    /** Their memberships and payments go with them. */
    override suspend fun deleteMembersPermanently(ids: List<String>): Int = write {
        members.delete {
            filter { isIn("id", ids) }
            select(Columns.raw("id"))
        }.decodeList<IdRow>().size
    }

    private suspend fun setArchived(ids: List<String>, archived: Boolean): Int = write {
        members.update(buildJsonObject { put("is_archived", archived) }) {
            filter { isIn("id", ids) }
            select(Columns.raw("id"))
        }.decodeList<IdRow>().size
    }

    private suspend fun <T> write(block: suspend () -> T): T = remote(block).also { refresher.changed() }

    /** [gymId] only for a new member; an update leaves the gym as it is. Blank text is stored as nothing. */
    private fun Member.toRow(gymId: String?): JsonObject = buildJsonObject {
        gymId?.let { put("gym_id", it) }
        put("name", name.trim())
        put("phone", phone?.trim()?.ifEmpty { null })
        put("gender", gender?.name)
        put("date_of_birth", dateOfBirth?.toString())
        put("photo_path", photoPath)
        put("notes", notes?.trim()?.ifEmpty { null })
        put("joined_on", joinedOn.toString())
        put("is_archived", isArchived)
    }
}

@Serializable
private data class PhoneRow(val phone: String? = null)
