package com.hyzin.whtsappclone.ui.theme

import androidx.compose.ui.graphics.Color

// ── Starry Night Theme (Dark Default) ──────────────────────────────────────
val SkyNightDeep = Color(0xFF02040A)           // Deep space black-blue
val SkyNightLight = Color(0xFF0B1A31)          // Dark navy for gradient
val StarColor = Color(0xFFFEFCD7)              // Warm starlight
val StarGlow = Color(0x66FEFCD7)               // Star glow alpha

// ── Light Mode (Clean & Elegant) ──────────────────────────────────────────
val LightBg = Color(0xFFF8FAFC)                // Very light gray/slate
val LightSurface = Color(0xFFFFFFFF)           // Pure white
val LightBorder = Color(0xFFE2E8F0)            // Soft border
val LightTextPrimary = Color(0xFF0F172A)       // Deep slate for text
val LightTextSecondary = Color(0xFF64748B)     // Muted slate

// ── Ocean Blue Theme (Premium & Refreshing) ───────────────────────────────
val OceanBluePrimary = Color(0xFF0077B6)       // Deep Ocean Blue
val OceanBlueSecondary = Color(0xFF00B4D8)     // Mid Ocean Blue
val OceanBlueLight = Color(0xFFCAF0F8)         // Very light wave blue

// ── Vibrant Action Colors ──────────────────────────────────────────────────
val VibrantGreenAction = Color(0xFF25D366)
val VibrantGreenDark = Color(0xFF1DA851)
val VibrantGreenSoft = Color(0xFFD1FAE5)       // For light mode backgrounds

// ── Dark Mode Text & Contrast ──────────────────────────────────────────────
val TextPrimary = Color(0xFFE2E8F0)            // Soft light gray/white for dark mode
val TextSecondary = Color(0xFF94A3B8)          // Muted slate blue

// ── Bubble Colors ──────────────────────────────────────────────────────────
val IncomingBubbleDark = Color(0xFF1E293B)     // Dark slate
val IncomingBubbleLight = Color(0xFFF1F5F9)    // Light slate
val OutgoingBubbleColor = OceanBluePrimary     // Stays consistent
val OutgoingBubbleLight = Color(0xFF00B4D8)    // Lighter blue for light mode

// ── Mappings ───────────────────────────────────────────────────────────────
val SoftBlueGrayBg = SkyNightDeep
val SoftBlueGraySurface = SkyNightLight
val IncomingBubbleColor = IncomingBubbleDark

// Compatibility Layer
val AppGreen = VibrantGreenAction
val AppDarkGreen = VibrantGreenDark
val AppCardBg = IncomingBubbleDark
val AppDarkBg = SkyNightDeep
val BlueLink = OceanBluePrimary
val VoiceGreen = VibrantGreenAction
val GreenSage = VibrantGreenAction
val SkyBlueLight = OceanBlueLight
val SkyBlueDeep = OceanBluePrimary
val SkyBlueSecondary = OceanBlueSecondary
val BeigeTan = OceanBlueLight
val SoftBlueGrayLight = OceanBlueSecondary
val SkyBlueAccent = OceanBlueSecondary
val SkyBlueBackground = SkyNightDeep
val SkyBlueSurface = SkyNightLight