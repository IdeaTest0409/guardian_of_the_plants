package com.example.smartphonapptest001.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFF005B73),
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = androidx.compose.ui.graphics.Color(0xFFBCECF8),
    onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFF00202A),
    secondary = androidx.compose.ui.graphics.Color(0xFF4F635F),
    onSecondary = androidx.compose.ui.graphics.Color.White,
    secondaryContainer = androidx.compose.ui.graphics.Color(0xFFD1E8E2),
    onSecondaryContainer = androidx.compose.ui.graphics.Color(0xFF0E1F1C),
)

private val DarkColors = darkColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFF7AD7F0),
    onPrimary = androidx.compose.ui.graphics.Color(0xFF003545),
    primaryContainer = androidx.compose.ui.graphics.Color(0xFF004E62),
    onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFFBCECF8),
    secondary = androidx.compose.ui.graphics.Color(0xFFB5CCC7),
    onSecondary = androidx.compose.ui.graphics.Color(0xFF213330),
    secondaryContainer = androidx.compose.ui.graphics.Color(0xFF384944),
    onSecondaryContainer = androidx.compose.ui.graphics.Color(0xFFD1E8E2),
)

@Composable
fun SmartphoneAppTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        typography = androidx.compose.material3.Typography(),
        content = content,
    )
}
