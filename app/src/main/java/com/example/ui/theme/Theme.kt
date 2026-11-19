package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val StudioDarkColorScheme = darkColorScheme(
    primary = SageSignal,
    onPrimary = GraphiteBase,
    primaryContainer = SageWell,
    onPrimaryContainer = SageBright,
    secondary = BrassCaution,
    onSecondary = GraphiteBase,
    secondaryContainer = BrassWell,
    onSecondaryContainer = BrassBright,
    tertiary = SteelChannel,
    onTertiary = GraphiteBase,
    background = GraphiteBase,
    onBackground = BonePrimary,
    surface = GraphiteDeck,
    onSurface = BonePrimary,
    surfaceVariant = GraphitePanel,
    onSurfaceVariant = BoneSecondary,
    surfaceContainerLow = GraphiteDeck,
    surfaceContainer = GraphitePanel,
    surfaceContainerHigh = GraphiteRaised,
    outline = SeamLine,
    outlineVariant = GridLine,
    error = MadderAlert,
    onError = GraphiteBase,
    inverseSurface = GraphiteRaised,
    inverseOnSurface = BonePrimary
)

val StudioShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp)
)

@Composable
fun HarmoniEqTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = StudioDarkColorScheme,
        typography = Typography,
        shapes = StudioShapes,
        content = content
    )
}
