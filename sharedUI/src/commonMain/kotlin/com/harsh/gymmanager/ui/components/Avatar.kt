package com.harsh.gymmanager.ui.components

import gymmanager.sharedui.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.harsh.gymmanager.ui.format.initials
import com.harsh.gymmanager.ui.photo.MemberPhoto
import com.harsh.gymmanager.ui.theme.AkhadaSize
import com.harsh.gymmanager.ui.theme.AkhadaTheme

/** The member's photo, or their initials in the display face when there is none. */
@Composable
fun Avatar(name: String, photoPath: String?, modifier: Modifier = Modifier, size: Dp = AkhadaSize.avatar) {
    val colors = AkhadaTheme.colors
    Box(
        modifier.size(size).clip(CircleShape).background(colors.surfaceSunken),
        contentAlignment = Alignment.Center,
    ) {
        if (photoPath != null) {
            MemberPhoto(photoPath, contentDescription = stringResource(Res.string.photo_of, name), modifier = Modifier.size(size))
        } else {
            Text(
                initials(name),
                style = AkhadaTheme.typography.stat.copy(fontSize = (size.value * 0.4f).sp, lineHeight = (size.value * 0.45f).sp),
                color = colors.ink,
            )
        }
    }
}
