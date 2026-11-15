package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val SentinelDarkColorScheme = darkColorScheme(
    primary = EmeraldShield,
    onPrimary = OnEmerald,
    primaryContainer = EmeraldContainer,
    onPrimaryContainer = Color(0xFFA7F3D0),
    secondary = CyanTelemetry,
    onSecondary = Color(0xFF042233),
    secondaryContainer = CyanContainer,
    onSecondaryContainer = Color(0xFFBAE6FD),
    tertiary = AmberWarn,
    onTertiary = Color(0xFF2B1900),
    tertiaryContainer = AmberContainer,
    onTertiaryContainer = Color(0xFFFDE68A),
    error = CrimsonAlert,
    onError = Color.White,
    errorContainer = CrimsonContainer,
    onErrorContainer = Color(0xFFFECACA),
    background = MidnightNavy,
    onBackground = TextPrimary,
    surface = DeepSlate,
    onSurface = TextPrimary,
    surfaceVariant = ElevatedSurface,
    onSurfaceVariant = TextSecondary,
    outline = BorderSlate,
    outlineVariant = Color(0xFF1C304E),
)

@Composable
fun SentinelTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = SentinelDarkColorScheme,
        typography = SentinelTypography,
        content = content,
    )
}
