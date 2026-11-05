package com.harsh.gymmanager.ui.photo

import gymmanager.sharedui.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.harsh.gymmanager.ui.components.AkhadaButton
import com.harsh.gymmanager.ui.components.AkhadaIconButton
import com.harsh.gymmanager.ui.components.ButtonKind
import com.harsh.gymmanager.ui.icons.AkhadaIcons
import com.harsh.gymmanager.ui.theme.AkhadaSpace
import com.harsh.gymmanager.ui.theme.AkhadaTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

@Composable
actual fun MemberPhoto(path: String, contentDescription: String?, modifier: Modifier) {
    val bitmap by produceState<ImageBitmap?>(null, path) {
        value = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(path)?.asImageBitmap() }
    }
    val image = bitmap
    if (image != null) {
        Image(image, contentDescription, modifier, contentScale = ContentScale.Crop)
    } else {
        Box(modifier.background(AkhadaTheme.colors.surfaceSunken))
    }
}

@Composable
actual fun CameraCapture(onPhotoTaken: (String) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasCameraPermission(context)) }
    var asked by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
        asked = true
    }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }
    BackHandler(onBack = onClose)

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when {
            granted -> CameraScreen(onPhotoTaken, onClose)
            asked -> PermissionNeeded(onRetry = { launcher.launch(Manifest.permission.CAMERA) }, onClose = onClose)
        }
    }
}

private fun hasCameraPermission(context: Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

@Composable
private fun PermissionNeeded(onRetry: () -> Unit, onClose: () -> Unit) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(AkhadaSpace.xl),
        verticalArrangement = Arrangement.spacedBy(AkhadaSpace.lg, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(Res.string.camera_off_title), style = AkhadaTheme.typography.title, color = Color.White)
        Text(
            stringResource(Res.string.camera_off_body),
            style = AkhadaTheme.typography.body,
            color = Color.White.copy(alpha = 0.8f),
            textAlign = TextAlign.Center,
        )
        AkhadaButton(stringResource(Res.string.allow_camera), onRetry, Modifier.fillMaxWidth())
        AkhadaButton(stringResource(Res.string.not_now), onClose, Modifier.fillMaxWidth(), kind = ButtonKind.Secondary)
    }
}

@Composable
private fun CameraScreen(onPhotoTaken: (String) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var surfaceRequest by remember { mutableStateOf<SurfaceRequest?>(null) }
    var lens by remember { mutableIntStateOf(CameraSelector.LENS_FACING_FRONT) }
    var captured by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val shutterLabel = stringResource(Res.string.take_picture)
    val imageCapture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build() }

    LaunchedEffect(lens, captured) {
        if (captured != null) return@LaunchedEffect
        val provider = ProcessCameraProvider.awaitInstance(context)
        val preview = Preview.Builder().build().apply { setSurfaceProvider { surfaceRequest = it } }
        val selector = CameraSelector.Builder().requireLensFacing(lens).build()
        provider.unbindAll()
        try {
            provider.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture)
            awaitCancellation()
        } catch (e: IllegalArgumentException) {
            // This phone has no camera facing that way; fall back to the other one.
            lens = if (lens == CameraSelector.LENS_FACING_FRONT) CameraSelector.LENS_FACING_BACK else CameraSelector.LENS_FACING_FRONT
        } finally {
            provider.unbindAll()
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(AkhadaSpace.gutter), verticalAlignment = Alignment.CenterVertically) {
            AkhadaIconButton(AkhadaIcons.X, stringResource(Res.string.close_camera), onClose, container = Color.White.copy(alpha = 0.16f), content = Color.White)
            Spacer(Modifier.weight(1f))
            Text(if (captured == null) stringResource(Res.string.member_photo) else stringResource(Res.string.use_this_photo), style = AkhadaTheme.typography.title, color = Color.White)
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.size(44.dp))
        }
        // A square frame, since photos show as circles everywhere in the app.
        Box(
            Modifier.fillMaxWidth().padding(horizontal = AkhadaSpace.gutter).aspectRatio(1f).clip(androidx.compose.foundation.shape.RoundedCornerShape(28.dp)).background(Color(0xFF1A1A1A)),
            contentAlignment = Alignment.Center,
        ) {
            val path = captured
            if (path != null) {
                MemberPhoto(path, stringResource(Res.string.captured_photo), Modifier.fillMaxSize())
            } else {
                surfaceRequest?.let { CameraXViewfinder(surfaceRequest = it, modifier = Modifier.fillMaxSize()) }
            }
        }
        if (failed) {
            Text(stringResource(Res.string.photo_failed), Modifier.fillMaxWidth().padding(AkhadaSpace.lg), style = AkhadaTheme.typography.body, color = Color.White, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.weight(1f))
        val path = captured
        if (path == null) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = AkhadaSpace.xl, vertical = AkhadaSpace.xl),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Spacer(Modifier.size(48.dp))
                Surface(
                    onClick = {
                        if (busy) return@Surface
                        busy = true
                        failed = false
                        takePhoto(context, imageCapture, mirror = lens == CameraSelector.LENS_FACING_FRONT) { result ->
                            scope.launch {
                                busy = false
                                result.onSuccess { captured = it }.onFailure { failed = true }
                            }
                        }
                    },
                    shape = CircleShape,
                    color = Color.White,
                    modifier = Modifier
                        .size(76.dp)
                        .border(4.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                        .padding(6.dp)
                        .semantics { contentDescription = shutterLabel },
                ) {}
                AkhadaIconButton(
                    AkhadaIcons.SwitchCamera, stringResource(Res.string.switch_camera),
                    onClick = { lens = if (lens == CameraSelector.LENS_FACING_FRONT) CameraSelector.LENS_FACING_BACK else CameraSelector.LENS_FACING_FRONT },
                    size = 48.dp, container = Color.White.copy(alpha = 0.16f), content = Color.White,
                )
            }
        } else {
            Column(Modifier.fillMaxWidth().padding(AkhadaSpace.gutter), verticalArrangement = Arrangement.spacedBy(AkhadaSpace.md)) {
                AkhadaButton(stringResource(Res.string.use_photo), { onPhotoTaken(path) }, Modifier.fillMaxWidth())
                AkhadaButton(
                    stringResource(Res.string.retake),
                    { File(path).delete(); captured = null },
                    Modifier.fillMaxWidth(),
                    kind = ButtonKind.Secondary,
                )
            }
        }
    }
}

/** Captures a frame, then rotates, mirrors and shrinks it before saving, so files stay small and upright. */
private fun takePhoto(context: Context, imageCapture: ImageCapture, mirror: Boolean, done: (Result<String>) -> Unit) {
    imageCapture.takePicture(
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val rotation = image.imageInfo.rotationDegrees
                val bitmap = image.toBitmap()
                image.close()
                Thread {
                    done(runCatching { savePhoto(context, bitmap, rotation, mirror) })
                }.start()
            }

            override fun onError(exception: ImageCaptureException) = done(Result.failure(exception))
        },
    )
}

private const val MAX_PHOTO_SIDE = 720

private fun savePhoto(context: Context, source: Bitmap, rotation: Int, mirror: Boolean): String {
    val side = minOf(source.width, source.height)
    val scale = MAX_PHOTO_SIDE.toFloat() / max(side, 1)
    val matrix = Matrix().apply {
        postRotate(rotation.toFloat())
        if (mirror) postScale(-1f, 1f)
        if (scale < 1f) postScale(scale, scale)
    }
    // Crop the centre square first: the app only ever shows photos as circles.
    val square = Bitmap.createBitmap(source, (source.width - side) / 2, (source.height - side) / 2, side, side, matrix, true)
    val dir = File(context.filesDir, "photos").apply { mkdirs() }
    val file = File(dir, "member-${System.currentTimeMillis()}.jpg")
    file.outputStream().use { square.compress(Bitmap.CompressFormat.JPEG, 85, it) }
    return file.absolutePath
}
