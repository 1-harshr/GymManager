package com.harsh.gymmanager.data.remote

import com.harsh.gymmanager.domain.backup.CLOUD_PHOTOS_FOLDER
import com.harsh.gymmanager.domain.backup.StoredFiles
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds

internal const val MEMBER_PHOTOS_BUCKET = "member-photos"
internal const val GYM_LOGOS_BUCKET = "gym-logos"

/**
 * Member photos and the gym's logo, kept in Supabase Storage so every phone at the gym has them.
 *
 * The database stores where a photo is kept online, e.g. "<gym id>/<member id>-1730900000000.jpg";
 * the screens only ever see paths on the phone. [localPath] gives the phone's copy, fetching it the
 * first time; [toStored] uploads a photo just taken on this phone and returns where it went.
 */
internal class PhotoSync(
    private val supabase: SupabaseClient,
    private val files: StoredFiles,
    private val scope: CoroutineScope,
    /** Called once photos fetched in a burst have arrived, so lists showing them can be read again. */
    private val onArrived: () -> Unit = {},
    private val clock: Clock = Clock.System,
) {
    private val fetching = Mutex()
    private val inFlight = mutableSetOf<String>()
    private val arrived = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Goes up once photos fetched in a burst have all arrived, so whatever shows them can look again. */
    private val _updates = MutableStateFlow(0)
    val updates: StateFlow<Int> = _updates.asStateFlow()

    init {
        @OptIn(FlowPreview::class)
        scope.launch {
            arrived.debounce(SETTLE).collect {
                _updates.update { it + 1 }
                onArrived()
            }
        }
    }

    /**
     * The phone's copy of a stored photo, or null while it's still being fetched; [updates] goes up
     * when it arrives. A photo that can't be fetched now is tried again the next time it's asked for.
     */
    suspend fun localPath(bucket: String, stored: String): String? {
        val name = cacheName(bucket, stored)
        files.pathIfStored(CLOUD_PHOTOS_FOLDER, name)?.let { return it }
        val start = fetching.withLock { inFlight.add(name) }
        if (start) {
            scope.launch {
                try {
                    val bytes = supabase.storage.from(bucket).downloadAuthenticated(stored)
                    files.write(CLOUD_PHOTOS_FOLDER, name, bytes)
                    arrived.tryEmit(Unit)
                } catch (_: Exception) {
                    // No connection, or the photo was replaced meanwhile; asked for again, it's tried again.
                } finally {
                    fetching.withLock { inFlight.remove(name) }
                }
            }
        }
        return null
    }

    /**
     * What to show for a stored photo: the phone's copy once it's here; until then the stored name
     * itself, which draws as an empty frame and, saved back unchanged, keeps the photo.
     */
    suspend fun shown(bucket: String, stored: String): String = localPath(bucket, stored) ?: stored

    /**
     * Where [path] is kept online. A stored name, or a photo fetched from there, maps straight back;
     * one just taken on this phone is uploaded first, as [owner]'s in the [gymId]'s folder. Null stays null.
     */
    suspend fun toStored(bucket: String, path: String?, gymId: String, owner: String): String? {
        if (path == null) return null
        // Paths on the phone are always absolute; stored names never are.
        if (!path.startsWith("/")) return path
        storedFromCache(bucket, path)?.let { return it }
        val bytes = files.read(path) ?: return null
        val stored = "$gymId/$owner-${clock.now().toEpochMilliseconds()}.${path.substringAfterLast('.', "jpg")}"
        remote {
            supabase.storage.from(bucket).upload(stored, bytes) {
                upsert = true
                contentType = contentTypeOf(stored)
            }
        }
        // Keep this phone's copy under its new name, so it isn't fetched straight back.
        files.write(CLOUD_PHOTOS_FOLDER, cacheName(bucket, stored), bytes)
        return stored
    }

    /** The stored name of a photo this phone fetched, read back from its file name; null for any other path. */
    private fun storedFromCache(bucket: String, path: String): String? {
        val fileName = path.substringAfterLast('/')
        if (!path.contains("/$CLOUD_PHOTOS_FOLDER/") || !fileName.startsWith("$bucket$SEPARATOR")) return null
        return fileName.removePrefix("$bucket$SEPARATOR").replace(SEPARATOR, "/")
    }

    private fun cacheName(bucket: String, stored: String) = "$bucket$SEPARATOR${stored.replace("/", SEPARATOR)}"

    private fun contentTypeOf(name: String): ContentType = when (name.substringAfterLast('.').lowercase()) {
        "png" -> ContentType.Image.PNG
        "webp" -> ContentType("image", "webp")
        "heic" -> ContentType("image", "heic")
        else -> ContentType.Image.JPEG
    }

    private companion object {
        /** Stands in for "/" in file names; never part of a gym id, member id or bucket name. */
        const val SEPARATOR = "__"
        val SETTLE = 300.milliseconds
    }
}
