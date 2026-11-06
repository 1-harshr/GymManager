package com.harsh.gymmanager.ui.platform

import android.content.Context
import com.harsh.gymmanager.domain.backup.StoredFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Photos and the logo live under the app's private files folder. */
class AndroidStoredFiles(private val context: Context) : StoredFiles {
    override suspend fun read(path: String): ByteArray? = withContext(Dispatchers.IO) {
        File(path).takeIf { it.isFile }?.readBytes()
    }

    override suspend fun pathIfStored(folder: String, fileName: String): String? = withContext(Dispatchers.IO) {
        File(File(context.filesDir, folder), fileName).takeIf { it.isFile }?.absolutePath
    }

    override suspend fun write(folder: String, fileName: String, bytes: ByteArray): String = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, folder).apply { mkdirs() }
        File(dir, fileName).apply { writeBytes(bytes) }.absolutePath
    }

    override suspend fun deleteOthers(folder: String, keep: Set<String>) = withContext(Dispatchers.IO) {
        File(context.filesDir, folder).listFiles()?.filter { it.absolutePath !in keep }?.forEach { it.delete() }
        Unit
    }
}
