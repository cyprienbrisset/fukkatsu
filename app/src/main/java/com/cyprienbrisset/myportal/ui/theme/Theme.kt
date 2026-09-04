package com.cyprienbrisset.myportal.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val SumiColors = darkColorScheme(
    primary = Shu,
    onPrimary = OnShu,
    background = Sumi,
    onBackground = Kinari,
    surface = SumiSurface,
    onSurface = Kinari,
    surfaceVariant = SumiSurface,
    onSurfaceVariant = SumiMuted,
    outline = SumiLine,
)

@Composable
fun MyPortalTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = SumiColors, typography = PortalTypography, content = content)
}
