package com.harsh.gymmanager.ui.platform

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import java.io.File
import java.util.Locale
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
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
                } ?: "file.csv"
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

/** Far above any real backup (photos are about 100 KB each); guards against picking a video. */
private const val MAX_BACKUP_BYTES = 200 * 1024 * 1024

@Composable
actual fun rememberBackupSaver(onDone: (saved: Boolean) -> Unit): (fileName: String, bytes: ByteArray) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val callback by rememberUpdatedState(onDone)
    var pending by remember { mutableStateOf<ByteArray?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        val bytes = pending
        pending = null
        if (uri == null || bytes == null) {
            callback(false)
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val saved = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) } != null }.getOrDefault(false)
            }
            callback(saved)
        }
    }
    return { fileName, bytes ->
        pending = bytes
        launcher.launch(fileName)
    }
}

@Composable
actual fun rememberBackupPicker(onPicked: (name: String, bytes: ByteArray?) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val callback by rememberUpdatedState(onPicked)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val resolver = context.contentResolver
            val (name, bytes) = withContext(Dispatchers.IO) {
                val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                } ?: "backup.json"
                name to runCatching { resolver.openInputStream(uri)?.use { readCapped(it, MAX_BACKUP_BYTES) } }.getOrNull()
            }
            callback(name, bytes)
        }
    }
    // Drive and file managers label JSON inconsistently, so accept the usual stand-ins too.
    return { launcher.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) }
}

/** Reads at most [max] bytes; null if the file is bigger. */
private fun readCapped(input: java.io.InputStream, max: Int = MAX_IMPORT_BYTES): ByteArray? {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8 * 1024)
    while (true) {
        val read = input.read(buffer)
        if (read < 0) return out.toByteArray()
        out.write(buffer, 0, read)
        if (out.size() > max) return null
    }
}

/** Logos show as circles up to about 80dp; 512px covers every screen density. */
private const val LOGO_SIDE = 512

@Composable
actual fun rememberLogoPicker(onPicked: (path: String?) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val callback by rememberUpdatedState(onPicked)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val path = withContext(Dispatchers.IO) { runCatching { saveLogo(context, uri) }.getOrNull() }
            callback(path)
        }
    }
    return { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
}

/** Decodes upright, crops the centre square, shrinks to [LOGO_SIDE] and keeps transparency as PNG. */
private fun saveLogo(context: Context, uri: Uri): String {
    val decoded: Bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val longest = maxOf(info.size.width, info.size.height)
            if (longest > LOGO_SIDE * 2) {
                val scale = LOGO_SIDE * 2f / longest
                decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
            }
        }
    } else {
        context.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it) }
    }
    val side = minOf(decoded.width, decoded.height)
    val square = Bitmap.createBitmap(decoded, (decoded.width - side) / 2, (decoded.height - side) / 2, side, side)
    val scaled = if (side > LOGO_SIDE) Bitmap.createScaledBitmap(square, LOGO_SIDE, LOGO_SIDE, true) else square
    val dir = File(context.filesDir, "branding").apply { mkdirs() }
    // Only the newest logo is kept; older ones are no longer referenced.
    dir.listFiles()?.forEach { it.delete() }
    val file = File(dir, "logo-${System.currentTimeMillis()}.png")
    file.outputStream().use { scaled.compress(Bitmap.CompressFormat.PNG, 100, it) }
    return file.absolutePath
}

/** Follows the Compose Multiplatform guide for overriding the resource locale on Android. */
actual object LocalAppLocale {
    private var systemDefault: Locale? = null

    @Composable
    actual infix fun provides(tag: String?): ProvidedValue<*> {
        val configuration = LocalConfiguration.current
        if (systemDefault == null) systemDefault = Locale.getDefault()
        val locale = tag?.let { Locale.forLanguageTag(it) } ?: systemDefault!!
        Locale.setDefault(locale)
        val updated = Configuration(configuration).apply { setLocale(locale) }
        val resources = LocalContext.current.resources
        @Suppress("DEPRECATION")
        resources.updateConfiguration(updated, resources.displayMetrics)
        return LocalConfiguration.provides(updated)
    }
}

actual val rebuildUiOnLanguageChange: Boolean = false

@Composable
actual fun SystemBarsFor(darkTheme: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !darkTheme
            isAppearanceLightNavigationBars = !darkTheme
        }
    }
}
