package com.example.admute.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = AdmuteYellowDark,
    onPrimary = Color.Black,
    secondary = AdmuteTealDark,
    onSecondary = Color.Black,
    tertiary = AdmuteTeal,
    background = AdmuteDarkBackground,
    onBackground = Color.White,
    surface = AdmuteDarkBackground,
    onSurface = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = AdmuteYellow,
    onPrimary = Color.Black,
    secondary = AdmuteTeal,
    onSecondary = Color.Black,
    tertiary = AdmuteTealDark,
    background = AdmuteLightBackground,
    onBackground = Color.Black,
    surface = Color.White,
    onSurface = Color.Black
)

@Composable
fun ADMUTETheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Keep brand palette as default; can be re-enabled later in settings.
    dynamicColor: Boolean = false,
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

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}