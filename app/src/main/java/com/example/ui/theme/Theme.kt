package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.example.domain.model.ThemeOption

private val PulseDarkColorScheme = darkColorScheme(
    primary = PulseWhite,
    onPrimary = PulseBlack,
    primaryContainer = PulseSurfaceHighlight,
    onPrimaryContainer = PulseWhite,
    secondary = PulseSilver,
    onSecondary = PulseBlack,
    secondaryContainer = PulseSurfaceElevated,
    onSecondaryContainer = PulseWhite,
    tertiary = PulseSecondaryText,
    onTertiary = PulseBlack,
    background = PulseBlack,
    onBackground = PulseWhite,
    surface = PulseSurface,
    onSurface = PulseWhite,
    surfaceVariant = PulseSurfaceElevated,
    onSurfaceVariant = PulseSecondaryText,
    surfaceContainer = PulseSurface,
    surfaceContainerHigh = PulseSurfaceElevated,
    surfaceContainerHighest = PulseSurfaceHighlight,
    outline = PulseBorder,
    outlineVariant = PulseDivider,
    error = PulseError,
    onError = PulseBlack
)

private val PulseLightColorScheme = lightColorScheme(
    primary = PulseLightPrimaryText,
    onPrimary = PulseLightSurface,
    primaryContainer = PulseLightSurfaceElevated,
    onPrimaryContainer = PulseLightPrimaryText,
    secondary = PulseLightSecondaryText,
    onSecondary = PulseLightSurface,
    secondaryContainer = PulseLightSurfaceElevated,
    onSecondaryContainer = PulseLightPrimaryText,
    tertiary = PulseLightSecondaryText,
    onTertiary = PulseLightSurface,
    background = PulseLightBackground,
    onBackground = PulseLightPrimaryText,
    surface = PulseLightSurface,
    onSurface = PulseLightPrimaryText,
    surfaceVariant = PulseLightSurfaceElevated,
    onSurfaceVariant = PulseLightSecondaryText,
    surfaceContainer = PulseLightSurface,
    surfaceContainerHigh = PulseLightSurfaceElevated,
    surfaceContainerHighest = Color(0xFFE4E4E7),
    outline = PulseLightBorder,
    outlineVariant = Color(0xFFE4E4E7),
    error = Color(0xFFDC2626),
    onError = Color.White
)

val PulseShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun PulseTheme(
    themeOption: ThemeOption = ThemeOption.DARK,
    content: @Composable () -> Unit
) {
    val useDarkTheme = when (themeOption) {
        ThemeOption.DARK -> true
        ThemeOption.LIGHT -> false
        ThemeOption.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = if (useDarkTheme) PulseDarkColorScheme else PulseLightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !useDarkTheme
                controller.isAppearanceLightNavigationBars = !useDarkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = PulseShapes,
        content = content
    )
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    PulseTheme(
        themeOption = if (darkTheme) ThemeOption.DARK else ThemeOption.LIGHT,
        content = content
    )
}
