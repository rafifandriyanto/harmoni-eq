package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Warm machined-hardware palette: graphite + bone, brass/sage/steel/clay signals.
// Flat tonal surfaces only. No gradients, no neon, no purple/blue wash.
// Contrast targets: body text >= 4.5:1 on its surface; UI signals >= 3:1.

// Surfaces (warm graphite, tonal steps instead of tinted borders)
val GraphiteBase = Color(0xFF181410)     // app background
val GraphiteDeck = Color(0xFF201B16)     // deck / surface
val GraphitePanel = Color(0xFF29231D)    // console panel
val GraphiteRaised = Color(0xFF332C24)   // raised module
val GraphiteWell = Color(0xFF120E0A)     // recessed control well
val SeamLine = Color(0xFF3F372D)         // neutral panel seam (structural hairline)
val GridLine = Color(0xFF322B23)         // meter grid / outlineVariant

// Left channel / attenuation: cool steel (functional coding, not decoration)
val SteelChannel = Color(0xFF82A7BD)
val SteelBright = Color(0xFFA9C4D4)
val SteelWell = Color(0xFF1E2C33)

// Caution / dirty state / favorites: brass
val BrassCaution = Color(0xFFD9A441)
val BrassBright = Color(0xFFE4C078)
val BrassWell = Color(0xFF3A2E14)

// Primary signal: sage (active / OK / headroom), VU low zone
val SageSignal = Color(0xFF9DBE8D)
val SageBright = Color(0xFFB9D3A8)
val SageWell = Color(0xFF26301F)

// Right channel / sub-bass group: warm clay
val ClayChannel = Color(0xFFD0855A)
val ClayBright = Color(0xFFE0A183)
val ClayWell = Color(0xFF38251A)

// Clipping / peak alert: madder, VU top zone
val MadderAlert = Color(0xFFDE8175)
val MadderWell = Color(0xFF3A1D18)

// Text (bone, high contrast on graphite)
val BonePrimary = Color(0xFFF1E9DD)
val BoneSecondary = Color(0xFFD3C9BB)
val BoneMuted = Color(0xFFA2968A)