package com.harsh.gymmanager.domain.repository

import com.harsh.gymmanager.domain.model.GymMembership
import com.harsh.gymmanager.domain.model.StaffInvite
import com.harsh.gymmanager.domain.model.StaffMember
import com.harsh.gymmanager.domain.model.StaffRole
import kotlinx.coroutines.flow.StateFlow

sealed interface MembershipState {
    /** Not known yet: signing in, or still asking. */
    data object Loading : MembershipState

    /** Signed in, but not part of any gym yet: they create one or join one. */
    data object NoGym : MembershipState

    data class Member(val membership: GymMembership) : MembershipState

    /** Couldn't be checked, usually for want of a connection; [GymRepository.refresh] tries again. */
    data object Unavailable : MembershipState
}

enum class GymProblem {
    /** The person already belongs to a gym. */
    ALREADY_IN_GYM,
    /** The invite code is wrong, used or expired. */
    INVALID_INVITE,
    /** Only the owner can do that. */
    NOT_OWNER,
    /** The gym's only owner can't delete their account while others work there; someone else must be made owner first. */
    HAND_OVER_FIRST,
    NO_CONNECTION,
}

/** What deleting the signed-in person's account would do. */
enum class AccountDeletion {
    /** Only the account goes; the gym and everything recorded in it stay. */
    ACCOUNT,
    /** The gym goes too, with every record and photo: they're its only owner and nobody else works there. */
    ACCOUNT_AND_GYM,
    /** They're the only owner and others work there, so someone else must be made owner first. */
    HAND_OVER_FIRST,
}

class GymException(val problem: GymProblem, cause: Throwable? = null) : Exception(problem.name, cause)

/**
 * The signed-in person's gym and its staff. [membership] follows sign-in and out by itself.
 * Failures are [GymException]s where the reason is known.
 */
interface GymRepository {
    val membership: StateFlow<MembershipState>

    /** Asks again, e.g. after [MembershipState.Unavailable] or when someone else may have changed it. */
    suspend fun refresh()

    /** Sets up a gym with the signed-in person as its owner. */
    suspend fun createGym(name: String, address: String?, yourName: String)

    /** Joins the gym that issued [code], as the role the invite was made for. */
    suspend fun joinGym(code: String, yourName: String)

    /** Owner only. [logoPath] is a logo picked on this phone, the one the gym already has, or null for none. */
    suspend fun updateGym(name: String, address: String?, logoPath: String?)

    /** Everyone at the gym, owners first. */
    suspend fun staff(): List<StaffMember>

    /** Owner only: invites not yet used or expired, newest first. */
    suspend fun pendingInvites(): List<StaffInvite>

    /** Owner only. */
    suspend fun createInvite(role: StaffRole): StaffInvite

    /** Owner only. */
    suspend fun cancelInvite(code: String)

    /** Owner only, and not for themselves. */
    suspend fun setRole(userId: String, role: StaffRole)

    /** Owner only, and not for themselves. */
    suspend fun removeStaff(userId: String)

    /** Staff only; an owner can't leave their own gym. */
    suspend fun leaveGym()

    suspend fun accountDeletion(): AccountDeletion

    /**
     * Deletes the signed-in person's account for good, and the gym with its photos when
     * [accountDeletion] says [AccountDeletion.ACCOUNT_AND_GYM], then signs out.
     */
    suspend fun deleteAccount()
}
