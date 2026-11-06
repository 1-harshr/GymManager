package com.harsh.gymmanager.data.remote

import com.harsh.gymmanager.domain.model.AppSettings
import com.harsh.gymmanager.domain.model.GymProfile
import com.harsh.gymmanager.domain.repository.GymRepository
import com.harsh.gymmanager.domain.repository.MembershipState
import com.harsh.gymmanager.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * The phone's settings, except that the gym's name and address come from the gym online once
 * someone has signed in to one, so every phone at the gym shows the same. Saving them changes the
 * gym for everyone. The logo stays on this phone for now.
 */
class CloudGymSettingsRepository(
    private val local: SettingsRepository,
    private val gyms: GymRepository,
) : SettingsRepository by local {
    override fun observe(): Flow<AppSettings> =
        combine(local.observe(), gyms.membership) { settings, membership ->
            if (membership is MembershipState.Member) {
                val gym = membership.membership
                settings.copy(gym = GymProfile(name = gym.gymName, address = gym.gymAddress, logoPath = settings.gym?.logoPath))
            } else {
                settings
            }
        }

    override suspend fun setGym(profile: GymProfile) {
        if (gyms.membership.value is MembershipState.Member) gyms.updateGym(profile.name, profile.address)
        local.setGym(profile)
    }
}
