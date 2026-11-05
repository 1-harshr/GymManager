package com.harsh.gymmanager.domain.usecase

import com.harsh.gymmanager.domain.model.AppLanguage
import com.harsh.gymmanager.domain.model.AppSettings
import com.harsh.gymmanager.domain.model.GymProfile
import com.harsh.gymmanager.domain.model.HomeFeatures
import com.harsh.gymmanager.domain.model.MorningUpdate
import com.harsh.gymmanager.domain.model.ThemeMode
import com.harsh.gymmanager.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow

class SettingsUseCases(private val repository: SettingsRepository) {
    fun observe(): Flow<AppSettings> = repository.observe()

    /** Fails with a ValidationException if [name] is blank. */
    suspend fun saveGym(name: String, address: String?, logoPath: String?): Result<Unit> = resultOf {
        repository.setGym(GymProfile(name = name, address = address, logoPath = logoPath))
    }

    suspend fun setTheme(theme: ThemeMode): Result<Unit> = resultOf { repository.setTheme(theme) }

    suspend fun setLanguage(language: AppLanguage): Result<Unit> = resultOf { repository.setLanguage(language) }

    suspend fun setMorningUpdate(update: MorningUpdate): Result<Unit> = resultOf { repository.setMorningUpdate(update) }

    suspend fun setHomeFeatures(features: HomeFeatures): Result<Unit> = resultOf { repository.setHomeFeatures(features) }

    suspend fun setMembersAsGrid(grid: Boolean): Result<Unit> = resultOf { repository.setMembersAsGrid(grid) }
}
