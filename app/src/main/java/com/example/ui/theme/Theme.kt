package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val DashDarkColorScheme =
  darkColorScheme(
    primary = ElectricCyan,
    onPrimary = DeepAsphalt,
    primaryContainer = DeepCyan,
    onPrimaryContainer = CloudWhite,
    secondary = HyperOrange,
    onSecondary = CloudWhite,
    secondaryContainer = ColorTokens.OrangeContainer,
    onSecondaryContainer = CloudWhite,
    tertiary = SolarGold,
    onTertiary = DeepAsphalt,
    background = DeepAsphalt,
    onBackground = CloudWhite,
    surface = MidnightSlate,
    onSurface = CloudWhite,
    surfaceVariant = ElevatedCard,
    onSurfaceVariant = MutedSteel,
    outline = SubtleBorder,
    error = CrimsonAlert,
    onError = CloudWhite,
  )

private object ColorTokens {
  val OrangeContainer = androidx.compose.ui.graphics.Color(0xFF4A2104)
}

private val DashShapes =
  Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
  )

@Composable
fun DashRunnersTheme(content: @Composable () -> Unit) {
  MaterialTheme(
    colorScheme = DashDarkColorScheme,
    typography = DashTypography,
    shapes = DashShapes,
    content = content,
  )
}
