package com.harsh.gymmanager.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** Colour roles from the Akhada design system's tokens.json; names match the tokens. */
@Immutable
data class AkhadaColors(
    val bg: Color,
    val surface: Color,
    val surfaceSunken: Color,
    val line: Color,
    val ink: Color,
    val inkMuted: Color,
    val onInk: Color,
    val accent: Color,
    val onAccent: Color,
    val accentSoft: Color,
    val onAccentSoft: Color,
    val statusActive: Color,
    val statusActiveSoft: Color,
    val statusExpiring: Color,
    val statusExpiringSoft: Color,
    val statusExpired: Color,
    val statusExpiredSoft: Color,
    val statusDue: Color,
    val statusDueSoft: Color,
    val scrim: Color,
    /** The logo is black on light grey, so it always sits on white. */
    val logoGround: Color,
    val isDark: Boolean,
)

val AkhadaLightColors = AkhadaColors(
    bg = Color(0xFFF3F3F0),
    surface = Color(0xFFFFFFFF),
    surfaceSunken = Color(0xFFE9E9E5),
    line = Color(0xFFDCDCD6),
    ink = Color(0xFF121213),
    inkMuted = Color(0xFF5C5C61),
    onInk = Color(0xFFFFFFFF),
    accent = Color(0xFFB0441B),
    onAccent = Color(0xFFFFFFFF),
    accentSoft = Color(0xFFF7E4DA),
    onAccentSoft = Color(0xFF7A2A0C),
    statusActive = Color(0xFF1C7347),
    statusActiveSoft = Color(0xFFE0F2E8),
    statusExpiring = Color(0xFF995600),
    statusExpiringSoft = Color(0xFFFAEBD4),
    statusExpired = Color(0xFF5F5F66),
    statusExpiredSoft = Color(0xFFEBEBEE),
    statusDue = Color(0xFFBC2C25),
    statusDueSoft = Color(0xFFFBE3E1),
    scrim = Color(0x66121213),
    logoGround = Color(0xFFFFFFFF),
    isDark = false,
)

val AkhadaDarkColors = AkhadaColors(
    bg = Color(0xFF0F0F10),
    surface = Color(0xFF19191B),
    surfaceSunken = Color(0xFF252528),
    line = Color(0xFF2F2F33),
    ink = Color(0xFFF2F2EF),
    inkMuted = Color(0xFFA4A4AA),
    onInk = Color(0xFF121213),
    accent = Color(0xFFEE8A60),
    onAccent = Color(0xFF1C0D05),
    accentSoft = Color(0xFF3B2218),
    onAccentSoft = Color(0xFFF6C2A8),
    statusActive = Color(0xFF5DCB92),
    statusActiveSoft = Color(0xFF12301F),
    statusExpiring = Color(0xFFF2B45C),
    statusExpiringSoft = Color(0xFF3A2A10),
    statusExpired = Color(0xFFA1A1A8),
    statusExpiredSoft = Color(0xFF2A2A2E),
    statusDue = Color(0xFFFF8077),
    statusDueSoft = Color(0xFF3D1613),
    scrim = Color(0x99000000),
    logoGround = Color(0xFFFFFFFF),
    isDark = true,
)
