package com.harsh.gymmanager.domain.repository

import com.harsh.gymmanager.domain.backup.BackupFile

/** Reads and replaces all records at once. Photos and settings are handled by the caller. */
interface BackupRepository {
    data class Snapshot(val backup: BackupFile, val photoPaths: Map<Long, String>)

    /** Every record, without photos, plus where each member's photo is stored. */
    suspend fun snapshot(createdAt: Long): Snapshot

    /**
     * Deletes every record and inserts the backup's, all or nothing.
     * [photoPaths] maps member ids to their restored photo files.
     */
    suspend fun replaceAll(backup: BackupFile, photoPaths: Map<Long, String>)
}
