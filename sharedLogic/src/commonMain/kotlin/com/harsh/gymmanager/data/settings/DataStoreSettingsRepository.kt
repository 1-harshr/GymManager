package com.harsh.gymmanager.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.harsh.gymmanager.domain.model.AppLanguage
import com.harsh.gymmanager.domain.model.AppSettings
import com.harsh.gymmanager.domain.model.GymProfile
import com.harsh.gymmanager.domain.model.HomeFeatures
import com.harsh.gymmanager.domain.model.MorningUpdate
import com.harsh.gymmanager.domain.model.ThemeMode
import com.harsh.gymmanager.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import okio.Path.Companion.toPath

const val SETTINGS_FILE_NAME = "settings.preferences_pb"

/** [path] is an absolute path in app storage, ending in [SETTINGS_FILE_NAME]. */
fun createSettingsDataStore(path: String): DataStore<Preferences> =
    PreferenceDataStoreFactory.createWithPath(produceFile = { path.toPath() })

class DataStoreSettingsRepository(private val store: DataStore<Preferences>) : SettingsRepository {
    private object Keys {
        val gymName = stringPreferencesKey("gym_name")
        val gymAddress = stringPreferencesKey("gym_address")
        val gymLogo = stringPreferencesKey("gym_logo_path")
        val theme = stringPreferencesKey("theme")
        val language = stringPreferencesKey("language")
        val morningUpdateEnabled = booleanPreferencesKey("morning_update_enabled")
        val morningUpdateMinute = intPreferencesKey("morning_update_minute")
        val aiInsights = booleanPreferencesKey("home_ai_insights")
        val collectionsChart = booleanPreferencesKey("home_collections_chart")
        val membersAsGrid = booleanPreferencesKey("members_as_grid")
    }

    override fun observe(): Flow<AppSettings> = store.data.map { prefs ->
        AppSettings(
            gym = prefs[Keys.gymName]?.takeIf { it.isNotBlank() }?.let { name ->
                GymProfile(name = name, address = prefs[Keys.gymAddress], logoPath = prefs[Keys.gymLogo])
            },
            theme = prefs[Keys.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            language = prefs[Keys.language]?.let { runCatching { AppLanguage.valueOf(it) }.getOrNull() } ?: AppLanguage.SYSTEM,
            morningUpdate = MorningUpdate(
                enabled = prefs[Keys.morningUpdateEnabled] ?: false,
                minuteOfDay = prefs[Keys.morningUpdateMinute]
                    ?.takeIf { it in 0 until MorningUpdate.MINUTES_PER_DAY } ?: MorningUpdate.DEFAULT_MINUTE_OF_DAY,
            ),
            homeFeatures = HomeFeatures(
                aiInsights = prefs[Keys.aiInsights] ?: false,
                collectionsChart = prefs[Keys.collectionsChart] ?: false,
            ),
            membersAsGrid = prefs[Keys.membersAsGrid] ?: false,
        )
    }.distinctUntilChanged()

    override suspend fun setGym(profile: GymProfile) {
        store.edit { prefs ->
            prefs[Keys.gymName] = profile.name.trim()
            profile.address?.trim()?.takeIf { it.isNotEmpty() }?.let { prefs[Keys.gymAddress] = it } ?: prefs.remove(Keys.gymAddress)
            profile.logoPath?.let { prefs[Keys.gymLogo] = it } ?: prefs.remove(Keys.gymLogo)
        }
    }

    override suspend fun setTheme(theme: ThemeMode) {
        store.edit { it[Keys.theme] = theme.name }
    }

    override suspend fun setLanguage(language: AppLanguage) {
        store.edit { it[Keys.language] = language.name }
    }


    override suspend fun setMorningUpdate(update: MorningUpdate) {
        store.edit {
            it[Keys.morningUpdateEnabled] = update.enabled
            it[Keys.morningUpdateMinute] = update.minuteOfDay
        }
    }

    override suspend fun setHomeFeatures(features: HomeFeatures) {
        store.edit {
            it[Keys.aiInsights] = features.aiInsights
            it[Keys.collectionsChart] = features.collectionsChart
        }
    }

    override suspend fun setMembersAsGrid(grid: Boolean) {
        store.edit { it[Keys.membersAsGrid] = grid }
    }
}
