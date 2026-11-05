package com.harsh.gymmanager.data.settings

import com.harsh.gymmanager.domain.ValidationError
import com.harsh.gymmanager.domain.ValidationException
import com.harsh.gymmanager.domain.model.AppLanguage
import com.harsh.gymmanager.domain.model.AppSettings
import com.harsh.gymmanager.domain.model.ThemeMode
import com.harsh.gymmanager.domain.usecase.SettingsUseCases
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
class SettingsRepositoryTest {
    private val path = NSTemporaryDirectory() + NSUUID().UUIDString + "-" + SETTINGS_FILE_NAME
    private val settings = SettingsUseCases(DataStoreSettingsRepository(createSettingsDataStore(path)))

    @AfterTest
    fun tearDown() {
        NSFileManager.defaultManager.removeItemAtPath(path, null)
    }

    @Test
    fun startsUnconfiguredThenStoresGymThemeAndLanguage() = runTest {
        assertEquals(AppSettings(), settings.observe().first())

        settings.saveGym("  Akhada Gym & MMA ", "Daltonganj", logoPath = null).getOrThrow()
        settings.setTheme(ThemeMode.DARK).getOrThrow()
        settings.setLanguage(AppLanguage.HINDI).getOrThrow()

        val stored = settings.observe().first()
        assertEquals("Akhada Gym & MMA", stored.gym?.name)
        assertEquals("Daltonganj", stored.gym?.address)
        assertEquals(ThemeMode.DARK, stored.theme)
        assertEquals(AppLanguage.HINDI, stored.language)

        settings.saveGym("Akhada", " ", logoPath = "/logo.png").getOrThrow()
        val updated = settings.observe().first().gym!!
        assertNull(updated.address)
        assertEquals("/logo.png", updated.logoPath)
    }

    @Test
    fun rejectsBlankGymName() = runTest {
        val error = assertIs<ValidationException>(settings.saveGym(" ", null, null).exceptionOrNull())
        assertEquals(ValidationError.GYM_NAME_REQUIRED, error.error)
        assertNull(settings.observe().first().gym)
    }
}
