package com.outloop.prototype.ui.theme

import androidx.compose.ui.graphics.Color

// ============================================================================
// STITCH DESIGN SYSTEM TOKENS
// Visual source of truth: stitch_reference/outloop_design_system/DESIGN.md &
// stitch_reference/outloop_dark_minimalist/DESIGN.md
// ============================================================================

// Kinetic Brand Accent (Used in both Light and Dark themes)
val StitchPrimaryLime = Color(0xFFB9F45A)          // Electric Lime primary accent
val StitchOnPrimaryDark = Color(0xFF111318)        // Text/icon placed on primary lime

// Dark Minimalist Theme Palette (Source: outloop_dark_minimalist/DESIGN.md)
val StitchDarkCanvasRoot = Color(0xFF111318)       // Deepest canvas foundation (Level 0)
val StitchDarkSurfaceContainer = Color(0xFF1A1D24) // Base container for sheets, bars, frames (Level 1)
val StitchDarkSurfaceCard = Color(0xFF222630)      // Elevated interactive card surface (Level 2)
val StitchDarkSurfaceBorder = Color(0xFF2D323E)    // 1px hairline structural boundary
val StitchDarkTextPrimary = Color(0xFFF7F8F4)      // High-emphasis headline & critical data
val StitchDarkTextSecondary = Color(0xFFA0A5AA)    // Medium-emphasis body & descriptions
val StitchDarkTextMuted = Color(0xFF686D76)        // Low-emphasis timestamps & telemetry tags

// Light Theme Palette (Source: outloop_design_system/DESIGN.md)
val StitchLightCanvasRoot = Color(0xFFF9FAF6)      // Serene non-glare ground (Level 0)
val StitchLightSurfaceContainer = Color(0xFFEDEEEA)// Inset modules & subtle frames
val StitchLightSurfaceCard = Color(0xFFFFFFFF)     // Pure white elevated card container (Level 1)
val StitchLightSurfaceBorder = Color(0xFFE4E7DF)   // 1px subtle boundary
val StitchLightTextPrimary = Color(0xFF1A1C1A)     // Stark primary typography
val StitchLightTextSecondary = Color(0xFF434937)   // Supporting body & subtitle copy
val StitchLightTextMuted = Color(0xFF72766F)       // Accessible subdued metadata
val StitchLightPrimaryContainer = Color(0xFFB9F45A)// Kinetic lime container

// Functional Semantic Tones
val StitchStatusSuccessDark = Color(0xFF4ADE80)    // Goal completion, active hooks (Dark)
val StitchStatusSuccessLight = Color(0xFF3A9B68)   // Goal completion (Light)
val StitchStatusWarning = Color(0xFFE7A23B)        // Ambient threshold warning
val StitchStatusErrorDark = Color(0xFFF87171)      // Error / destructive trigger (Dark)
val StitchStatusErrorLight = Color(0xFFBA1A1A)     // Error / destructive trigger (Light)

// ============================================================================
// BACKWARD-COMPATIBILITY ALIASES
// Kept to preserve existing screens (DiagnosticsScreen.kt, ReminderSettingsCard.kt)
// while upgrading visual tones to the Stitch Dark Minimalist palette.
// ============================================================================

val DarkBackground = StitchDarkCanvasRoot
val SurfaceDark = StitchDarkSurfaceContainer
val SurfaceLightDark = StitchDarkSurfaceCard

val PrimaryIndigo = StitchPrimaryLime
val PrimaryIndigoLight = StitchPrimaryLime
val AccentEmerald = StitchStatusSuccessDark
val AccentAmber = StitchStatusWarning
val AccentRose = StitchStatusErrorDark
val AccentCyan = Color(0xFF38BDF8)

val TextPrimary = StitchDarkTextPrimary
val TextSecondary = StitchDarkTextSecondary
val TextMuted = StitchDarkTextMuted

// Screen State Badges (Stitch harmonious palette)
val StateReels = StitchPrimaryLime                 // Electric Lime for Reels Tab
val StateDm = Color(0xFF38BDF8)                    // Soft Cyan for DMs
val StateStories = Color(0xFFA78BFA)               // Lavender for Stories
val StateFeed = StitchStatusSuccessDark            // Emerald for Home Feed
val StateProfile = Color(0xFF818CF8)               // Slate Indigo for Profile
val StateVideo = Color(0xFF60A5FA)                 // Blue for Full-Screen Video
val StateUnknown = StitchDarkTextMuted             // Muted for Unknown
