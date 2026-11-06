package com.harsh.gymmanager.data.remote

import com.harsh.gymmanager.domain.model.GymMembership
import com.harsh.gymmanager.domain.model.StaffInvite
import com.harsh.gymmanager.domain.model.StaffMember
import com.harsh.gymmanager.domain.model.StaffRole
import com.harsh.gymmanager.domain.repository.AuthRepository
import com.harsh.gymmanager.domain.repository.AuthState
import com.harsh.gymmanager.domain.repository.GymException
import com.harsh.gymmanager.domain.repository.GymProblem
import com.harsh.gymmanager.domain.repository.GymRepository
import com.harsh.gymmanager.domain.repository.MembershipState
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.CoroutineScope
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Clock
import kotlin.time.Instant

internal class SupabaseGymRepository(
    private val supabase: SupabaseClient,
    private val auth: AuthRepository,
    private val photos: PhotoSync,
    scope: CoroutineScope,
    private val clock: Clock = Clock.System,
) : GymRepository {
    private val _membership = MutableStateFlow<MembershipState>(MembershipState.Loading)
    override val membership: StateFlow<MembershipState> = _membership.asStateFlow()

    init {
        scope.launch {
            auth.authState.collectLatest { state ->
                if (state is AuthState.SignedIn) load(state.userId) else _membership.value = MembershipState.Loading
            }
        }
    }

    private val signedInUserId: String?
        get() = (auth.authState.value as? AuthState.SignedIn)?.userId

    override suspend fun refresh() {
        signedInUserId?.let { load(it) }
    }

    private suspend fun load(userId: String) {
        _membership.value = try {
            val row = supabase.postgrest.from("gym_staff")
                .select(Columns.raw("gym_id, user_id, role, name, joined_at, gyms(name, address, logo_path)")) {
                    filter { eq("user_id", userId) }
                }
                .decodeSingleOrNull<StaffRow>()
            if (row?.gym == null) {
                MembershipState.NoGym
            } else {
                MembershipState.Member(
                    GymMembership(
                        gymId = row.gymId,
                        gymName = row.gym.name,
                        gymAddress = row.gym.address,
                        gymLogo = row.gym.logoPath,
                        role = row.role.toStaffRole(),
                        yourName = row.name,
                    ),
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Usually no connection. Someone already in keeps their gym until a check gets through;
            // otherwise they get a way to try again rather than a crash.
            _membership.value.takeIf { it is MembershipState.Member } ?: MembershipState.Unavailable
        }
    }

    override suspend fun createGym(name: String, address: String?, yourName: String) = explained {
        supabase.postgrest.rpc(
            "create_gym",
            buildJsonObject {
                put("name", name.trim())
                put("address", address?.trim()?.ifEmpty { null })
                put("owner_name", yourName.trim())
            },
        )
        refresh()
    }

    override suspend fun joinGym(code: String, yourName: String) = explained {
        supabase.postgrest.rpc(
            "accept_invite",
            buildJsonObject {
                put("code", code)
                put("staff_name", yourName.trim())
            },
        )
        refresh()
    }

    override suspend fun updateGym(name: String, address: String?, logoPath: String?) = explained {
        val gymId = currentMembership().gymId
        val logo = photos.toStored(GYM_LOGOS_BUCKET, logoPath, gymId, owner = "logo")
        supabase.postgrest.from("gyms").update(
            buildJsonObject {
                put("name", name.trim())
                put("address", address?.trim()?.ifEmpty { null })
                put("logo_path", logo)
            },
        ) {
            filter { eq("id", gymId) }
        }
        refresh()
    }

    override suspend fun staff(): List<StaffMember> = explained {
        val you = signedInUserId
        supabase.postgrest.from("gym_staff")
            .select(Columns.raw("gym_id, user_id, role, name, joined_at")) {
                order("joined_at", Order.ASCENDING)
            }
            .decodeList<StaffRow>()
            .map {
                StaffMember(
                    userId = it.userId,
                    name = it.name,
                    role = it.role.toStaffRole(),
                    joinedAt = Instant.parse(it.joinedAt),
                    isYou = it.userId == you,
                )
            }
            .sortedBy { it.role != StaffRole.OWNER }
    }

    override suspend fun pendingInvites(): List<StaffInvite> = explained {
        supabase.postgrest.from("gym_invites")
            .select(Columns.raw("code, role, expires_at")) {
                filter {
                    exact("used_at", null)
                    gt("expires_at", clock.now().toString())
                }
                order("created_at", Order.DESCENDING)
            }
            .decodeList<InviteRow>()
            .map { it.toInvite() }
    }

    override suspend fun createInvite(role: StaffRole): StaffInvite = explained {
        val code = supabase.postgrest
            .rpc("create_invite", buildJsonObject { put("role", role.toColumn()) })
            .decodeAs<String>()
        supabase.postgrest.from("gym_invites")
            .select(Columns.raw("code, role, expires_at")) { filter { eq("code", code) } }
            .decodeSingle<InviteRow>()
            .toInvite()
    }

    override suspend fun cancelInvite(code: String) = explained {
        supabase.postgrest.from("gym_invites").delete { filter { eq("code", code) } }
        Unit
    }

    override suspend fun setRole(userId: String, role: StaffRole) = explained {
        supabase.postgrest.from("gym_staff").update(buildJsonObject { put("role", role.toColumn()) }) {
            filter { eq("user_id", userId) }
        }
        Unit
    }

    override suspend fun removeStaff(userId: String) = explained {
        supabase.postgrest.from("gym_staff").delete { filter { eq("user_id", userId) } }
        Unit
    }

    override suspend fun leaveGym() = explained {
        val you = signedInUserId ?: return@explained
        supabase.postgrest.from("gym_staff").delete { filter { eq("user_id", you) } }
        refresh()
    }

    private fun currentMembership(): GymMembership =
        (membership.value as? MembershipState.Member)?.membership ?: throw IllegalStateException("Not part of a gym")

    /** Turns the database's short error keys and lost connections into [GymException]s. */
    private inline fun <T> explained(block: () -> T): T =
        try {
            block()
        } catch (e: PostgrestRestException) {
            val problem = when (e.error) {
                "already_in_gym" -> GymProblem.ALREADY_IN_GYM
                "invalid_invite" -> GymProblem.INVALID_INVITE
                "not_owner" -> GymProblem.NOT_OWNER
                else -> throw e
            }
            throw GymException(problem, e)
        } catch (e: Exception) {
            if (e.isConnectionProblem()) throw GymException(GymProblem.NO_CONNECTION, e) else throw e
        }

    private fun Exception.isConnectionProblem() = this is HttpRequestException || this is HttpRequestTimeoutException

    @Serializable
    private data class StaffRow(
        @SerialName("gym_id") val gymId: String,
        @SerialName("user_id") val userId: String,
        val role: String,
        val name: String? = null,
        @SerialName("joined_at") val joinedAt: String,
        @SerialName("gyms") val gym: GymRow? = null,
    )

    @Serializable
    private data class GymRow(
        val name: String,
        val address: String? = null,
        @SerialName("logo_path") val logoPath: String? = null,
    )

    @Serializable
    private data class InviteRow(
        val code: String,
        val role: String,
        @SerialName("expires_at") val expiresAt: String,
    ) {
        fun toInvite() = StaffInvite(code = code, role = role.toStaffRole(), expiresAt = Instant.parse(expiresAt))
    }
}

private fun String.toStaffRole(): StaffRole = if (this == "owner") StaffRole.OWNER else StaffRole.STAFF

private fun StaffRole.toColumn(): String = when (this) {
    StaffRole.OWNER -> "owner"
    StaffRole.STAFF -> "staff"
}
