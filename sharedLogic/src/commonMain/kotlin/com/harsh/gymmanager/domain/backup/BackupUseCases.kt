package com.harsh.gymmanager.domain.backup

import com.harsh.gymmanager.domain.model.GymProfile
import com.harsh.gymmanager.domain.repository.BackupRepository
import com.harsh.gymmanager.domain.repository.SettingsRepository
import com.harsh.gymmanager.domain.usecase.resultOf
import kotlinx.coroutines.flow.first
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.time.Clock

/**
 * Saves everything to one file and brings it back. Restoring replaces what is on the phone:
 * it is meant for a new or reset phone, not for merging two gyms.
 */
class BackupUseCases(
    private val repository: BackupRepository,
    private val settings: SettingsRepository,
    private val files: StoredFiles,
    private val clock: Clock,
    private val timeZone: TimeZone,
) {
    private val json = Json {
        ignoreUnknownKeys = true // a newer app may add fields within the same format
        explicitNulls = false
    }

    /** The backup as file bytes, ready to save. */
    suspend fun create(): Result<ByteArray> = resultOf {
        val (records, photoPaths) = repository.snapshot(clock.now().toEpochMilliseconds())
        val current = settings.observe().first()
        val backup = records.copy(
            gym = current.gym?.let { GymBackup(it.name, it.address, it.logoPath?.let { path -> files.read(path)?.let(Base64::encode) }) },
            theme = current.theme,
            language = current.language,
            members = records.members.map { m -> m.copy(photo = photoPaths[m.id]?.let { files.read(it) }?.let(Base64::encode)) },
        )
        json.encodeToString(BackupFile.serializer(), backup).encodeToByteArray()
    }

    /** "gym-manager-backup-2026-11-05.json", dated today. */
    fun fileName(): String = "gym-manager-backup-${clock.now().toLocalDateTime(timeZone).date}.json"

    /** Call once the file has actually been saved somewhere. */
    suspend fun markSaved(): Result<Unit> = resultOf { settings.setLastBackupAt(clock.now()) }

    /** Fails with [BackupException] if [bytes] aren't a backup this app can restore. */
    fun read(bytes: ByteArray): Result<BackupFile> = resultOf {
        val backup = try {
            json.decodeFromString(BackupFile.serializer(), bytes.decodeToString())
        } catch (e: SerializationException) {
            throw BackupException(BackupProblem.NOT_A_BACKUP)
        } catch (e: IllegalArgumentException) {
            throw BackupException(BackupProblem.NOT_A_BACKUP)
        }
        if (backup.app != BACKUP_APP) throw BackupException(BackupProblem.NOT_A_BACKUP)
        if (backup.format > BACKUP_FORMAT) throw BackupException(BackupProblem.FROM_NEWER_APP)
        backup
    }

    /**
     * Replaces every record, the gym's details and the theme and language with [backup]'s.
     * Photos are written first and old ones removed last, so a failure leaves the old data whole.
     */
    suspend fun restore(backup: BackupFile): Result<Unit> = resultOf {
        val stamp = clock.now().toEpochMilliseconds()
        val photoPaths = backup.members.mapNotNull { m ->
            val bytes = m.photo?.let(::decodeOrNull) ?: return@mapNotNull null
            m.id to files.write(PHOTOS_FOLDER, "member-${m.id}-$stamp.jpg", bytes)
        }.toMap()
        val logoPath = backup.gym?.logo?.let(::decodeOrNull)?.let { files.write(BRANDING_FOLDER, "logo-$stamp.png", it) }

        repository.replaceAll(backup, photoPaths)
        backup.gym?.let { settings.setGym(GymProfile(it.name, it.address, logoPath)) }
        settings.setTheme(backup.theme)
        settings.setLanguage(backup.language)

        files.deleteOthers(PHOTOS_FOLDER, photoPaths.values.toSet())
        if (backup.gym != null) files.deleteOthers(BRANDING_FOLDER, setOfNotNull(logoPath))
    }

    /** A damaged photo is dropped rather than failing the whole restore. */
    private fun decodeOrNull(base64: String): ByteArray? = runCatching { Base64.decode(base64) }.getOrNull()
}
