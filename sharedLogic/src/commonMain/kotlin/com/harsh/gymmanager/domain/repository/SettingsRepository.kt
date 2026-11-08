package com.harsh.gymmanager.domain.repository

import com.harsh.gymmanager.domain.model.AppLanguage
import com.harsh.gymmanager.domain.model.AppSettings
import com.harsh.gymmanager.domain.model.GymProfile
import com.harsh.gymmanager.domain.model.HomeFeatures
import com.harsh.gymmanager.domain.model.MorningUpdate
import com.harsh.gymmanager.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    fun observe(): Flow<AppSettings>

    suspend fun setGym(profile: GymProfile)

    suspend fun setTheme(theme: ThemeMode)

    suspend fun setLanguage(language: AppLanguage)


    suspend fun setMorningUpdate(update: MorningUpdate)

    suspend fun setHomeFeatures(features: HomeFeatures)

    suspend fun setMembersAsGrid(grid: Boolean)
}
