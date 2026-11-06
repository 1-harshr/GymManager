package com.harsh.gymmanager.domain.backup

/** The app's own files (member photos, the logo), which a backup carries along with the records. */
interface StoredFiles {
    /** The file's bytes, or null if it is gone. */
    suspend fun read(path: String): ByteArray?

    /** The path of [fileName] in the app's [folder] if it's there, otherwise null. */
    suspend fun pathIfStored(folder: String, fileName: String): String?

    /** Writes [bytes] as [fileName] in the app's [folder] and returns the stored path. */
    suspend fun write(folder: String, fileName: String, bytes: ByteArray): String

    /** Removes files in [folder] whose paths aren't in [keep]. */
    suspend fun deleteOthers(folder: String, keep: Set<String>)
}

const val PHOTOS_FOLDER = "photos"
const val BRANDING_FOLDER = "branding"

/** Photos and logos downloaded from the gym online, named after where they're stored there. */
const val CLOUD_PHOTOS_FOLDER = "cloud-photos"
