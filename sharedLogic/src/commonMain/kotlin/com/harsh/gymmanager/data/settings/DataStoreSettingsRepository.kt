package com.harsh.gymmanager.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.harsh.gymmanager.domain.model.AppLanguage
import com.harsh.gymmanager.domain.model.AppSettings
import com.harsh.gymmanager.domain.model.GymProfile
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
    }

    override fun observe(): Flow<AppSettings> = store.data.map { prefs ->
        AppSettings(
            gym = prefs[Keys.gymName]?.takeIf { it.isNotBlank() }?.let { name ->
                GymProfile(name = name, address = prefs[Keys.gymAddress], logoPath = prefs[Keys.gymLogo])
            },
            theme = prefs[Keys.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            language = prefs[Keys.language]?.let { runCatching { AppLanguage.valueOf(it) }.getOrNull() } ?: AppLanguage.SYSTEM,
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
}
