package com.harsh.gymmanager.ui.photo

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Shows a member photo stored in app storage at [path]. */
@Composable
expect fun MemberPhoto(path: String, contentDescription: String?, modifier: Modifier = Modifier)

/**
 * Full-screen camera that asks for permission, takes a photo, lets the person retake it, and
 * hands back the saved file's path.
 */
@Composable
expect fun CameraCapture(onPhotoTaken: (String) -> Unit, onClose: () -> Unit)
