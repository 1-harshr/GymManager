package com.harsh.gymmanager.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.uikit.LocalUIViewController
import platform.Foundation.NSLocale
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSData
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfURL
import platform.Foundation.preferredLanguages
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIApplication
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNUserNotificationCenter
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIImage
import platform.UIKit.UIStatusBarStyleDarkContent
import platform.UIKit.UIStatusBarStyleLightContent
import platform.UIKit.UIViewController
import platform.UIKit.setStatusBarStyle
import platform.UniformTypeIdentifiers.UTTypeCommaSeparatedText
import platform.UniformTypeIdentifiers.UTTypePlainText
import platform.UniformTypeIdentifiers.UTTypeText
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.writeToFile
import platform.UniformTypeIdentifiers.UTTypeJSON
import platform.UniformTypeIdentifiers.UTTypeData
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/** iOS has no system back button; edge-swipe back is handled by navigation itself. */
@Composable
actual fun SystemBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit

/** Larger than any real member list; guards against picking a photo or video by mistake. */
private const val MAX_IMPORT_BYTES = 2_097_152uL

@Composable
actual fun rememberTextFilePicker(onPicked: (name: String, text: String?) -> Unit): () -> Unit {
    val host = LocalUIViewController.current
    val callback by rememberUpdatedState(onPicked)
    val delegate = remember { DocumentDelegate { name, text -> callback(name, text) } }
    return {
        val picker = UIDocumentPickerViewController(
            forOpeningContentTypes = listOf(UTTypeCommaSeparatedText, UTTypePlainText, UTTypeText),
            asCopy = true,
        )
        picker.delegate = delegate
        host.presentViewController(picker, animated = true, completion = null)
    }
}

private class DocumentDelegate(private val onPicked: (String, String?) -> Unit) : NSObject(), UIDocumentPickerDelegateProtocol {
    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL ?: return
        val data = NSData.dataWithContentsOfURL(url)
        val text = data?.takeIf { it.length <= MAX_IMPORT_BYTES }?.let { NSString.create(data = it, encoding = NSUTF8StringEncoding) as String? }
        onPicked(url.lastPathComponent ?: "file.csv", text)
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) = Unit
}

/** Far above any real backup (photos are about 100 KB each); guards against picking a video. */
private const val MAX_BACKUP_BYTES = 209_715_200uL

@Composable
@Suppress("UNUSED_PARAMETER") // The export sheet takes the type from the file name.
actual fun rememberFileSaver(mimeType: String, onDone: (saved: Boolean) -> Unit): (fileName: String, bytes: ByteArray) -> Unit {
    val host = LocalUIViewController.current
    val callback by rememberUpdatedState(onDone)
    val delegate = remember { ExportDelegate { saved -> callback(saved) } }
    return { fileName, bytes ->
        // The export sheet copies from a real file, so stage it in the temporary folder first.
        val path = NSTemporaryDirectory() + fileName
        if (!bytes.toNSData().writeToFile(path, atomically = true)) {
            callback(false)
        } else {
            val picker = UIDocumentPickerViewController(forExportingURLs = listOf(NSURL.fileURLWithPath(path)), asCopy = true)
            picker.delegate = delegate
            host.presentViewController(picker, animated = true, completion = null)
        }
    }
}

private class ExportDelegate(private val onDone: (Boolean) -> Unit) : NSObject(), UIDocumentPickerDelegateProtocol {
    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) = onDone(true)
    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) = onDone(false)
}

@Composable
actual fun rememberBackupPicker(onPicked: (name: String, bytes: ByteArray?) -> Unit): () -> Unit {
    val host = LocalUIViewController.current
    val callback by rememberUpdatedState(onPicked)
    val delegate = remember { BackupOpenDelegate { name, bytes -> callback(name, bytes) } }
    return {
        val picker = UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeJSON, UTTypeData), asCopy = true)
        picker.delegate = delegate
        host.presentViewController(picker, animated = true, completion = null)
    }
}

private class BackupOpenDelegate(private val onPicked: (String, ByteArray?) -> Unit) : NSObject(), UIDocumentPickerDelegateProtocol {
    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL ?: return
        val data = NSData.dataWithContentsOfURL(url)
        onPicked(url.lastPathComponent ?: "backup.json", data?.takeIf { it.length <= MAX_BACKUP_BYTES }?.toByteArray())
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) = Unit
}

/** Logos show as circles up to about 80pt; 512px covers every screen scale. */
private const val LOGO_SIDE = 512.0

@Composable
actual fun rememberLogoPicker(onPicked: (path: String?) -> Unit): () -> Unit {
    val host = LocalUIViewController.current
    val callback by rememberUpdatedState(onPicked)
    val delegate = remember {
        PhotoPickerDelegate { image ->
            callback(image?.let { saveImage(it.squareScaled(LOGO_SIDE), "branding", "logo", png = true, clearFolder = true) })
        }
    }
    return { presentPhotoPicker(host, delegate) }
}

internal fun presentPhotoPicker(host: UIViewController, delegate: PhotoPickerDelegate) {
    val config = PHPickerConfiguration().apply {
        filter = PHPickerFilter.imagesFilter
        selectionLimit = 1
    }
    val picker = PHPickerViewController(config)
    picker.delegate = delegate
    host.presentViewController(picker, animated = true, completion = null)
}

/** Loads the chosen photo off the main thread and reports back on it; cancelling reports nothing. */
internal class PhotoPickerDelegate(private val onImage: (UIImage?) -> Unit) : NSObject(), PHPickerViewControllerDelegateProtocol {
    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        picker.dismissViewControllerAnimated(true, completion = null)
        val result = didFinishPicking.firstOrNull() as? PHPickerResult ?: return
        result.itemProvider.loadDataRepresentationForTypeIdentifier("public.image") { data, _ ->
            val image = data?.let { UIImage(data = it) }
            dispatch_async(dispatch_get_main_queue()) { onImage(image) }
        }
    }
}

/** Follows the Compose Multiplatform guide for overriding the resource locale on iOS. */
actual object LocalAppLocale {
    private const val LANGUAGES_KEY = "AppleLanguages"
    private val systemDefault = NSLocale.preferredLanguages.first() as String
    private val LocalLocale = staticCompositionLocalOf { systemDefault }

    @Composable
    actual infix fun provides(tag: String?): ProvidedValue<*> {
        val defaults = NSUserDefaults.standardUserDefaults
        if (tag == null) defaults.removeObjectForKey(LANGUAGES_KEY) else defaults.setObject(listOf(tag), LANGUAGES_KEY)
        return LocalLocale.provides(tag ?: systemDefault)
    }
}

actual val sendsRemindersInBackground: Boolean = false

actual val rebuildUiOnLanguageChange: Boolean = true

/** Needs UIViewControllerBasedStatusBarAppearance = NO in Info.plist. */
@Composable
actual fun SystemBarsFor(darkTheme: Boolean) {
    SideEffect {
        @Suppress("DEPRECATION")
        UIApplication.sharedApplication.setStatusBarStyle(
            if (darkTheme) UIStatusBarStyleLightContent else UIStatusBarStyleDarkContent,
            animated = true,
        )
    }
}

@Composable
actual fun rememberNotificationPermission(onResult: (granted: Boolean) -> Unit): () -> Unit {
    val currentOnResult by rememberUpdatedState(onResult)
    return remember {
        {
            // Prompts only the first time; afterwards it reports the choice made in Settings.
            UNUserNotificationCenter.currentNotificationCenter().requestAuthorizationWithOptions(
                UNAuthorizationOptionAlert or UNAuthorizationOptionSound,
            ) { granted, _ ->
                dispatch_async(dispatch_get_main_queue()) { currentOnResult(granted) }
            }
        }
    }
}
