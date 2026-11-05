@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.harsh.gymmanager.ui.platform

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.Foundation.timeIntervalSince1970
import platform.Foundation.writeToFile
import platform.UIKit.UIGraphicsImageRenderer
import platform.UIKit.UIGraphicsImageRendererFormat
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePNGRepresentation
import platform.posix.memcpy

internal fun documentsDirectory(): String =
    NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true).first() as String

/**
 * iOS moves the app's folder on updates, so a saved absolute path can go stale. Keep the
 * folder and file name and re-root them in today's Documents folder.
 */
internal fun resolveStoredPath(path: String): String {
    if (NSFileManager.defaultManager.fileExistsAtPath(path)) return path
    return "${documentsDirectory()}/${path.split('/').takeLast(2).joinToString("/")}"
}

internal fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    val out = ByteArray(size)
    if (size > 0) out.usePinned { memcpy(it.addressOf(0), bytes, length) }
    return out
}

/** Crops the centre square and shrinks it to at most [side] points, drawn upright. */
internal fun UIImage.squareScaled(side: Double): UIImage {
    val (w, h) = size.useContents { width to height }
    val square = minOf(w, h)
    val target = minOf(side, square)
    val scale = target / square
    val format = UIGraphicsImageRendererFormat.defaultFormat().apply { this.scale = 1.0 }
    return UIGraphicsImageRenderer(CGSizeMake(target, target), format).imageWithActions {
        drawInRect(CGRectMake(-(w - square) / 2 * scale, -(h - square) / 2 * scale, w * scale, h * scale))
    }
}

/** Saves [image] into Documents/[folder] and returns the path, or null if it couldn't be written. */
internal fun saveImage(image: UIImage, folder: String, prefix: String, png: Boolean, clearFolder: Boolean = false): String? {
    val dir = "${documentsDirectory()}/$folder"
    val files = NSFileManager.defaultManager
    files.createDirectoryAtPath(dir, withIntermediateDirectories = true, attributes = null, error = null)
    if (clearFolder) {
        files.contentsOfDirectoryAtPath(dir, null)?.forEach { files.removeItemAtPath("$dir/$it", null) }
    }
    val stamp = (NSDate().timeIntervalSince1970 * 1000).toLong()
    val path = "$dir/$prefix-$stamp.${if (png) "png" else "jpg"}"
    val data = (if (png) UIImagePNGRepresentation(image) else UIImageJPEGRepresentation(image, 0.85)) ?: return null
    return if (data.writeToFile(path, atomically = true)) path else null
}
