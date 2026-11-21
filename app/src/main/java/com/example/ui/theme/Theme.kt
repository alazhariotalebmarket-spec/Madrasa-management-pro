package com.example.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val DarkColorScheme =
  darkColorScheme(
    primary = PrimaryDarkMode,
    onPrimary = Color(0xFF06283D),
    primaryContainer = SurfaceVariantDark,
    onPrimaryContainer = MadrasaSky,
    secondary = MadrasaGold,
    onSecondary = Color(0xFF1E1B0A),
    tertiary = MadrasaGreen,
    background = BackgroundDark,
    onBackground = TextLight,
    surface = SurfaceDark,
    onSurface = TextLight,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = TextMutedDark,
    outline = OutlineDark,
    outlineVariant = OutlineDark,
    error = Color(0xFFF87171),
  )

private val LightColorScheme =
  lightColorScheme(
    primary = MadrasaPrimary,
    onPrimary = Color.White,
    primaryContainer = MadrasaContainer,
    onPrimaryContainer = MadrasaPrimaryDark,
    secondary = MadrasaAccent,
    onSecondary = Color.White,
    tertiary = MadrasaGreen,
    background = BackgroundLight,
    onBackground = TextDark,
    surface = SurfaceLight,
    onSurface = TextDark,
    surfaceVariant = SurfaceSubtle,
    onSurfaceVariant = TextMuted,
    outline = OutlineSubtle,
    outlineVariant = OutlineSubtle,
    error = MadrasaRed,
  )

val MadrasaShapes =
  Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(26.dp),
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
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
      val window = (view.context as? Activity)?.window
      if (window != null) {
        WindowCompat.getInsetsController(window, view).apply {
          // Keep status bar icons crisp white over the Deep Ocean Teal headers
          isAppearanceLightStatusBars = false
          isAppearanceLightNavigationBars = !darkTheme
        }
      }
    }
  }

  MaterialTheme(
    colorScheme = colorScheme,
    typography = Typography,
    shapes = MadrasaShapes,
    content = content,
  )
}
