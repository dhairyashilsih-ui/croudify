package com.crowdpulse.camera.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val CrowdPulseColorScheme = lightColorScheme(
    primary          = PrimaryAccent,
    onPrimary        = TextOnAccent,
    primaryContainer = SurfaceCard,
    onPrimaryContainer = TextPrimary,

    secondary        = SecondaryAccent,
    onSecondary      = TextPrimary,
    secondaryContainer = SecondaryDim,
    onSecondaryContainer = TextPrimary,

    tertiary         = SuccessGreen,
    onTertiary       = TextOnAccent,

    background       = BackgroundLight,
    onBackground     = TextPrimary,

    surface          = SurfaceLight,
    onSurface        = TextPrimary,
    surfaceVariant   = SurfaceElevated,
    onSurfaceVariant = TextSecondary,

    outline          = BorderSubtle,
    error            = DangerRed,
    onError          = TextOnAccent,
)

@Composable
fun CrowdPulseTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CrowdPulseColorScheme,
        typography  = CrowdPulseTypography,
        content     = content
    )
}