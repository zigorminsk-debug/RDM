package com.rdm.remote.desktop.manager.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = RdmPrimaryDark,
    onPrimary = RdmOnPrimaryDark,
    primaryContainer = RdmPrimaryContainerDark,
    onPrimaryContainer = RdmOnPrimaryContainerDark,
    secondary = RdmSecondaryDark,
    onSecondary = RdmOnSecondaryDark,
    secondaryContainer = RdmSecondaryContainerDark,
    onSecondaryContainer = RdmOnSecondaryContainerDark,
    tertiary = RdmTertiayDark ?: RdmTertiary,
    background = RdmBackgroundDark,
    surface = RdmSurfaceDark,
    surfaceVariant = RdmSurfaceVariantDark,
    onBackground = RdmOnSurfaceDark,
    onSurface = RdmOnSurfaceDark
)

private val LightColorScheme = lightColorScheme(
    primary = RdmPrimary,
    onPrimary = RdmOnPrimary,
    primaryContainer = RdmPrimaryContainer,
    onPrimaryContainer = RdmOnPrimaryContainer,
    secondary = RdmSecondary,
    onSecondary = RdmOnSecondary,
    secondaryContainer = RdmSecondaryContainer,
    onSecondaryContainer = RdmOnSecondaryContainer,
    tertiary = RdmTertiary,
    onTertiary = RdmOnTertiary,
    tertiaryContainer = RdmTertiaryContainer,
    onTertiaryContainer = RdmOnTertiaryContainer
)

private val RdmTertiayDark = Color(0xFFC7C2EA)

@Composable
fun RemoteDesktopManagerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = colorScheme.surface.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
