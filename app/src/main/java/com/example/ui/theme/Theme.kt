package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val KinetikDarkColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = Color(0xFF002026),
    primaryContainer = CyanContainer,
    onPrimaryContainer = ElectricCyan,
    secondary = CoralAmber,
    onSecondary = Color(0xFF280A04),
    secondaryContainer = CoralContainer,
    onSecondaryContainer = CoralAmber,
    tertiary = FxViolet,
    onTertiary = Color(0xFF1C0C38),
    tertiaryContainer = FxVioletContainer,
    onTertiaryContainer = FxViolet,
    background = ObsidianBg,
    onBackground = TextPrimary,
    surface = StudioSurface,
    onSurface = TextPrimary,
    surfaceVariant = ElevatedSurface,
    onSurfaceVariant = TextSecondary,
    outline = HairlineOutline,
    outlineVariant = CardHighlight,
    error = ErrorRed,
    onError = Color(0xFF2A0009)
)

val KinetikShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun KinetikStudioTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = KinetikDarkColorScheme,
        typography = KinetikTypography,
        shapes = KinetikShapes,
        content = content
    )
}
