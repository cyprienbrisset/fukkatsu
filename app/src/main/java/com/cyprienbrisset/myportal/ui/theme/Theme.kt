package com.cyprienbrisset.myportal.ui.theme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val PortalColors = darkColorScheme(
    primary = PortalAccent,
    background = PortalBg,
    surface = PortalSurface,
    surfaceVariant = PortalSurfaceHi,
    onBackground = PortalOnDark,
    onSurface = PortalOnDark,
)

@Composable
fun MyPortalTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(colorScheme = PortalColors, typography = PortalTypography, content = content)
}
