package com.harsh.gymmanager.ui.platform

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
actual fun SystemBackHandler(enabled: Boolean, onBack: () -> Unit) = BackHandler(enabled, onBack)

/** Larger than any real member list; guards against picking a photo or video by mistake. */
private const val MAX_IMPORT_BYTES = 2 * 1024 * 1024

@Composable
actual fun rememberTextFilePicker(onPicked: (name: String, text: String?) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val callback by rememberUpdatedState(onPicked)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val resolver = context.contentResolver
            val (name, text) = withContext(Dispatchers.IO) {
                val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                } ?: "the file"
                val text = runCatching {
                    resolver.openInputStream(uri)?.use { input -> readCapped(input)?.decodeToString() }
                }.getOrNull()
                name to text
            }
            callback(name, text)
        }
    }
    return {
        launcher.launch(arrayOf("text/csv", "text/comma-separated-values", "application/csv", "text/plain", "application/vnd.ms-excel"))
    }
}

/** Reads at most [MAX_IMPORT_BYTES]; null if the file is bigger. */
private fun readCapped(input: java.io.InputStream): ByteArray? {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8 * 1024)
    while (true) {
        val read = input.read(buffer)
        if (read < 0) return out.toByteArray()
        out.write(buffer, 0, read)
        if (out.size() > MAX_IMPORT_BYTES) return null
    }
}
