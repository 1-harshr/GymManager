package com.harsh.gymmanager.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** Spacing, radius and size tokens from the Akhada design system. */
object AkhadaSpace {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp

    /** Screen side gutter. */
    val gutter = lg
}

object AkhadaRadius {
    val field = 14.dp
    val card = 20.dp
    val sheet = 28.dp

    val fieldShape = RoundedCornerShape(field)
    val cardShape = RoundedCornerShape(card)
    val sheetShape = RoundedCornerShape(topStart = sheet, topEnd = sheet)
    val pillShape = RoundedCornerShape(percent = 50)
}

object AkhadaSize {
    val touchTarget = 48.dp
    val tabBarHeight = 64.dp
    val tabBarPadding = 6.dp
    val tabItemWidth = 76.dp
    val tabIcon = 22.dp
    val avatar = 44.dp
    val addButton = 60.dp
    val iconButton = 44.dp
    val logo = 40.dp

    /** Bottom padding on scrolling screens so the last row clears the floating tab bar. */
    val contentBottomClearance = 104.dp
}

object AkhadaElevation {
    /** Only things that float over content: tab bar, add button, snackbar. */
    val float = 12.dp
}
