@file:OptIn(ExperimentalForeignApi::class)

package com.harsh.gymmanager.ui.platform

import com.harsh.gymmanager.domain.backup.StoredFiles
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.writeToFile

/** Photos and the logo live under the app's Documents folder, which moves between installs. */
class IosStoredFiles : StoredFiles {
    override suspend fun read(path: String): ByteArray? = withContext(Dispatchers.IO) {
        NSData.dataWithContentsOfFile(resolveStoredPath(path))?.toByteArray()
    }

    override suspend fun pathIfStored(folder: String, fileName: String): String? = withContext(Dispatchers.IO) {
        "${documentsDirectory()}/$folder/$fileName".takeIf { NSFileManager.defaultManager.fileExistsAtPath(it) }
    }

    override suspend fun write(folder: String, fileName: String, bytes: ByteArray): String = withContext(Dispatchers.IO) {
        val dir = "${documentsDirectory()}/$folder"
        NSFileManager.defaultManager.createDirectoryAtPath(dir, withIntermediateDirectories = true, attributes = null, error = null)
        val path = "$dir/$fileName"
        check(bytes.toNSData().writeToFile(path, atomically = true)) { "Couldn't write $path" }
        path
    }

    override suspend fun deleteOthers(folder: String, keep: Set<String>) = withContext(Dispatchers.IO) {
        val dir = "${documentsDirectory()}/$folder"
        val files = NSFileManager.defaultManager
        files.contentsOfDirectoryAtPath(dir, error = null).orEmpty()
            .map { "$dir/$it" }
            .filter { it !in keep }
            .forEach { files.removeItemAtPath(it, error = null) }
    }
}

internal fun ByteArray.toNSData(): NSData =
    if (isEmpty()) NSData() else usePinned { NSData.create(bytes = it.addressOf(0), length = size.toULong()) }
