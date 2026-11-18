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

private val StudioDarkColorScheme = darkColorScheme(
    primary = CyanPrimary,
    onPrimary = OnCyanPrimary,
    primaryContainer = CyanPrimaryContainer,
    onPrimaryContainer = CyanPrimary,
    secondary = CoralSecondary,
    onSecondary = OnCoralSecondary,
    secondaryContainer = CoralSecondaryContainer,
    onSecondaryContainer = CoralSecondary,
    tertiary = VioletTertiary,
    onTertiary = Color(0xFF1A0E3A),
    tertiaryContainer = VioletTertiaryContainer,
    onTertiaryContainer = VioletTertiary,
    background = StudioBackground,
    onBackground = TextPrimary,
    surface = StudioSurface,
    onSurface = TextPrimary,
    surfaceVariant = StudioSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = StudioBackground,
    surfaceContainerLow = StudioSurface,
    surfaceContainer = StudioSurfaceElevated,
    surfaceContainerHigh = StudioSurfaceHigh,
    outline = StudioBorder,
    outlineVariant = StudioBorder.copy(alpha = 0.5f),
    error = ErrorRed,
    onError = Color.White,
)

private val AmoledDarkColorScheme = StudioDarkColorScheme.copy(
    background = AmoledBackground,
    surface = AmoledSurface,
    surfaceContainerLowest = AmoledBackground,
    surfaceContainerLow = AmoledSurface,
)

val CutoutShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun CutoutProTheme(
    amoledBlack: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (amoledBlack) AmoledDarkColorScheme else StudioDarkColorScheme
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
        colorScheme = colorScheme,
        typography = CutoutTypography,
        shapes = CutoutShapes,
        content = content,
    )
}
