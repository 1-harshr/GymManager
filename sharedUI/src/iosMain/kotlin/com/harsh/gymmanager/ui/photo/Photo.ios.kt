package com.harsh.gymmanager.ui.photo

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.uikit.LocalUIViewController
import com.harsh.gymmanager.ui.components.AkhadaButton
import com.harsh.gymmanager.ui.components.ButtonKind
import com.harsh.gymmanager.ui.platform.PhotoPickerDelegate
import com.harsh.gymmanager.ui.platform.presentPhotoPicker
import com.harsh.gymmanager.ui.platform.resolveStoredPath
import com.harsh.gymmanager.ui.platform.saveImage
import com.harsh.gymmanager.ui.platform.squareScaled
import com.harsh.gymmanager.ui.platform.toByteArray
import com.harsh.gymmanager.ui.theme.AkhadaSpace
import com.harsh.gymmanager.ui.theme.AkhadaTheme
import gymmanager.sharedui.generated.resources.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.skia.Image as SkiaImage
import platform.Foundation.NSData
import platform.Foundation.dataWithContentsOfFile
import platform.UIKit.UIImage
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerCameraDevice
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerEditedImage
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UIModalPresentationFullScreen
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.darwin.NSObject

@Composable
actual fun MemberPhoto(path: String, contentDescription: String?, modifier: Modifier) {
    val bitmap by produceState<ImageBitmap?>(null, path) {
        value = withContext(Dispatchers.Default) { loadBitmap(resolveStoredPath(path)) }
    }
    val image = bitmap
    if (image != null) {
        Image(image, contentDescription, modifier, contentScale = ContentScale.Crop)
    } else {
        Box(modifier.background(AkhadaTheme.colors.surfaceSunken))
    }
}

private fun loadBitmap(path: String): ImageBitmap? {
    val data = NSData.dataWithContentsOfFile(path) ?: return null
    return runCatching { SkiaImage.makeFromEncoded(data.toByteArray()).toComposeImageBitmap() }.getOrNull()
}

/** Photos show as circles everywhere, so they're saved as small squares. */
private const val PHOTO_SIDE = 720.0

private fun savePhoto(image: UIImage): String? = saveImage(image.squareScaled(PHOTO_SIDE), "photos", "member", png = false)

/**
 * Uses the system camera, which has its own square crop, retake and use steps. Devices without
 * a camera, such as the simulator, offer the photo library instead.
 */
@Composable
actual fun CameraCapture(onPhotoTaken: (String) -> Unit, onClose: () -> Unit) {
    val host = LocalUIViewController.current
    val taken by rememberUpdatedState(onPhotoTaken)
    val close by rememberUpdatedState(onClose)
    val cameraDelegate = remember { CameraDelegate(onImage = { image -> image?.let(::savePhoto)?.let(taken) ?: close() }) }
    val libraryDelegate = remember { PhotoPickerDelegate { image -> image?.let(::savePhoto)?.let(taken) ?: close() } }
    val hasCamera = remember { UIImagePickerController.isSourceTypeAvailable(UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera) }

    if (hasCamera) {
        LaunchedEffect(Unit) {
            val picker = UIImagePickerController().apply {
                sourceType = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
                if (UIImagePickerController.isCameraDeviceAvailable(UIImagePickerControllerCameraDevice.UIImagePickerControllerCameraDeviceFront)) {
                    cameraDevice = UIImagePickerControllerCameraDevice.UIImagePickerControllerCameraDeviceFront
                }
                allowsEditing = true
                delegate = cameraDelegate
                // A sheet could be swiped away without telling the delegate, leaving the camera unreachable.
                modalPresentationStyle = UIModalPresentationFullScreen
            }
            host.presentViewController(picker, animated = true, completion = null)
        }
        return
    }
    Column(
        Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().navigationBarsPadding().padding(AkhadaSpace.xl),
        verticalArrangement = Arrangement.spacedBy(AkhadaSpace.lg, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(Res.string.camera_unavailable_title), style = AkhadaTheme.typography.title, color = Color.White)
        Text(
            stringResource(Res.string.camera_unavailable_body),
            style = AkhadaTheme.typography.body,
            color = Color.White.copy(alpha = 0.8f),
            textAlign = TextAlign.Center,
        )
        AkhadaButton(stringResource(Res.string.choose_from_photos), { presentPhotoPicker(host, libraryDelegate) }, Modifier.fillMaxWidth())
        AkhadaButton(stringResource(Res.string.not_now), onClose, Modifier.fillMaxWidth(), kind = ButtonKind.Secondary)
    }
}

private class CameraDelegate(private val onImage: (UIImage?) -> Unit) :
    NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {
    override fun imagePickerController(picker: UIImagePickerController, didFinishPickingMediaWithInfo: Map<Any?, *>) {
        val image = (didFinishPickingMediaWithInfo[UIImagePickerControllerEditedImage]
            ?: didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage]) as? UIImage
        picker.dismissViewControllerAnimated(true, completion = null)
        onImage(image)
    }

    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
        picker.dismissViewControllerAnimated(true, completion = null)
        onImage(null)
    }
}
