package com.harsh.gymmanager.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import gymmanager.sharedui.generated.resources.Res
import gymmanager.sharedui.generated.resources.barlow_condensed_bold
import gymmanager.sharedui.generated.resources.barlow_condensed_semibold
import gymmanager.sharedui.generated.resources.barlow_medium
import gymmanager.sharedui.generated.resources.barlow_regular
import gymmanager.sharedui.generated.resources.barlow_semibold
import org.jetbrains.compose.resources.Font

/** Text styles from the My Gym design system: Barlow Condensed for titles and numbers, Barlow for the rest. */
@Immutable
data class MyGymTypography(
    /** The one hero figure per screen, e.g. revenue on Home. */
    val displayXl: TextStyle,
    val screenTitle: TextStyle,
    /** Numbers in stat tiles and plan prices; tabular figures. */
    val stat: TextStyle,
    val title: TextStyle,
    val body: TextStyle,
    val bodyStrong: TextStyle,
    val caption: TextStyle,
    /** Section labels; set the text in capitals. */
    val label: TextStyle,
    val tab: TextStyle,
)

@Composable
internal fun myGymTypography(): MyGymTypography {
    val display = FontFamily(
        Font(Res.font.barlow_condensed_semibold, FontWeight.SemiBold),
        Font(Res.font.barlow_condensed_bold, FontWeight.Bold),
    )
    val body = FontFamily(
        Font(Res.font.barlow_regular, FontWeight.Normal),
        Font(Res.font.barlow_medium, FontWeight.Medium),
        Font(Res.font.barlow_semibold, FontWeight.SemiBold),
    )
    fun style(family: FontFamily, size: Int, line: Int, weight: FontWeight, tracking: Double = 0.0, numbers: Boolean = false) =
        TextStyle(
            fontFamily = family,
            fontSize = size.sp,
            lineHeight = line.sp,
            fontWeight = weight,
            letterSpacing = tracking.em,
            fontFeatureSettings = if (numbers) "tnum" else null,
        )
    return MyGymTypography(
        displayXl = style(display, 44, 46, FontWeight.Bold, numbers = true),
        screenTitle = style(display, 30, 34, FontWeight.Bold),
        stat = style(display, 30, 34, FontWeight.Bold, numbers = true),
        title = style(body, 17, 22, FontWeight.SemiBold),
        body = style(body, 15, 22, FontWeight.Normal),
        bodyStrong = style(body, 15, 22, FontWeight.SemiBold),
        caption = style(body, 13, 18, FontWeight.Medium),
        label = style(body, 12, 16, FontWeight.SemiBold, tracking = 0.08),
        tab = style(body, 11, 14, FontWeight.SemiBold),
    )
}
