package com.bolin.photohelper.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val PhotoHelperColors = darkColorScheme(
    primary = Color(0xFF8DCDFF),
    onPrimary = Color(0xFF00344D),
    primaryContainer = Color(0xFF004B6E),
    onPrimaryContainer = Color(0xFFC8E6FF),
    secondary = Color(0xFFBAC8D3),
    onSecondary = Color(0xFF25333D),
    surface = Color(0xFF111315),
    onSurface = Color(0xFFE2E2E5),
    surfaceVariant = Color(0xFF282C30),
    onSurfaceVariant = Color(0xFFC2C7CC),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

@Composable
fun PhotoHelperTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PhotoHelperColors,
        typography = Typography(),
        content = content,
    )
}
