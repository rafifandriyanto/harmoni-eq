package com.example.ui.theme

import android.app.Activity
import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.launch

val StudioShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(GlassTokens.CornerRadius),
    extraLarge = RoundedCornerShape(GlassTokens.CornerRadius)
)

@Composable
fun HarmoniEqTheme(
    previewSettings: ThemeSettings? = null,
    content: @Composable () -> Unit
) {
    val inspection = LocalInspectionMode.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val repository = remember(context, inspection) {
        if (inspection) null else ThemePreferencesRepository(context)
    }
    val persisted by (repository?.settingsFlow?.collectAsState(initial = ThemeSettings())
        ?: remember { androidx.compose.runtime.mutableStateOf(ThemeSettings()) })

    val settings = previewSettings ?: persisted
    val systemDark = isSystemInDarkTheme()
    val isDark = when (settings.mode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }

    val systemReduceMotion = remember(context, inspection) {
        if (inspection) false else runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }

    val palette = remember(isDark, settings.accent, settings.customHex) {
        buildHarmoniPalette(isDark, settings.accent, settings.customHex)
    }

    val colorScheme = remember(palette) {
        if (palette.isDark) {
            darkColorScheme(
                primary = palette.accent,
                onPrimary = palette.onAccent,
                primaryContainer = palette.accentContainer,
                onPrimaryContainer = palette.accent,
                secondary = palette.channelSecondary,
                onSecondary = palette.canvas,
                secondaryContainer = palette.accentContainer,
                onSecondaryContainer = palette.accent,
                background = palette.canvas,
                onBackground = palette.textPrimary,
                surface = palette.surface,
                onSurface = palette.textPrimary,
                surfaceVariant = palette.surfaceElevated,
                onSurfaceVariant = palette.textSecondary,
                outline = palette.border,
                outlineVariant = palette.borderSubtle,
                error = palette.statusDanger,
                onError = palette.canvas
            )
        } else {
            lightColorScheme(
                primary = palette.accent,
                onPrimary = palette.onAccent,
                primaryContainer = palette.accentContainer,
                onPrimaryContainer = palette.accent,
                secondary = palette.channelSecondary,
                onSecondary = palette.surfaceElevated,
                secondaryContainer = palette.accentContainer,
                onSecondaryContainer = palette.accent,
                background = palette.canvas,
                onBackground = palette.textPrimary,
                surface = palette.surface,
                onSurface = palette.textPrimary,
                surfaceVariant = palette.controlWell,
                onSurfaceVariant = palette.textSecondary,
                outline = palette.border,
                outlineVariant = palette.borderSubtle,
                error = palette.statusDanger,
                onError = palette.surfaceElevated
            )
        }
    }

    val controller = remember(settings, systemReduceMotion, repository) {
        ThemeController(
            settings = settings,
            effectiveReduceMotion = settings.reduceMotion || systemReduceMotion,
            setThemeMode = { m -> scope.launch { repository?.setThemeMode(m) } },
            setAccentPreset = { a -> scope.launch { repository?.setAccentPreset(a) } },
            setCustomHex = { h -> scope.launch { repository?.setCustomHex(h) } },
            setReduceTransparency = { r -> scope.launch { repository?.setReduceTransparency(r) } },
            setReduceMotion = { m -> scope.launch { repository?.setReduceMotion(m) } }
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val insets = WindowCompat.getInsetsController(window, view)
                insets.isAppearanceLightStatusBars = !isDark
                insets.isAppearanceLightNavigationBars = !isDark
            }
        }
    }

    CompositionLocalProvider(
        LocalHarmoniPalette provides palette,
        LocalThemeController provides controller
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = StudioShapes,
            content = content
        )
    }
}
