package com.harsh.gymmanager.data.remote

import com.harsh.gymmanager.domain.model.AppSettings
import com.harsh.gymmanager.domain.model.GymProfile
import com.harsh.gymmanager.domain.repository.GymRepository
import com.harsh.gymmanager.domain.repository.MembershipState
import com.harsh.gymmanager.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * The phone's settings, except that the gym's name, address and logo come from the gym online once
 * someone has signed in to one, so every phone at the gym shows the same. Saving them changes the
 * gym for everyone. Made by [SupabaseBackend.gymSettings].
 */
internal class CloudGymSettingsRepository(
    private val local: SettingsRepository,
    private val gyms: GymRepository,
    private val photos: PhotoSync,
) : SettingsRepository by local {
    override fun observe(): Flow<AppSettings> =
        combine(local.observe(), gyms.membership, photos.updates) { settings, membership, _ ->
            if (membership is MembershipState.Member) {
                val gym = membership.membership
                val logo = gym.gymLogo?.let { photos.shown(GYM_LOGOS_BUCKET, it) }
                settings.copy(gym = GymProfile(name = gym.gymName, address = gym.gymAddress, logoPath = logo))
            } else {
                settings
            }
        }

    override suspend fun setGym(profile: GymProfile) {
        if (gyms.membership.value is MembershipState.Member) {
            gyms.updateGym(profile.name, profile.address, profile.logoPath)
        } else {
            local.setGym(profile)
        }
    }
}
