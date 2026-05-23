package com.example.admute.ui.theme

import android.content.res.Configuration
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner

private val DarkColorScheme = darkColorScheme(
    primary = AdmutePrimaryDark,
    onPrimary = Color.Black,
    secondary = AdmuteSecondaryDark,
    onSecondary = Color.Black,
    tertiary = AdmutePrimaryDark,
    background = AdmuteDarkBackground,
    onBackground = Color.White,
    surface = AdmuteSurfaceDark,
    onSurface = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = AdmutePrimaryLight,
    onPrimary = Color.White,
    secondary = AdmuteSecondaryLight,
    onSecondary = Color.White,
    tertiary = AdmutePrimaryLight,
    background = AdmuteLightBackground,
    onBackground = Color(0xFF23847d),
    surface = AdmuteSurfaceLight,
    onSurface = Color(0xFF23847d)
)

@Composable
fun ADVOIDTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Keep brand palette as default; can be re-enabled later in settings.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val targetColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val animationSpec = tween<Color>(durationMillis = 420)
    val primary = animateColorAsState(targetColorScheme.primary, animationSpec = animationSpec, label = "theme-primary")
    val onPrimary = animateColorAsState(targetColorScheme.onPrimary, animationSpec = animationSpec, label = "theme-on-primary")
    val secondary = animateColorAsState(targetColorScheme.secondary, animationSpec = animationSpec, label = "theme-secondary")
    val onSecondary = animateColorAsState(targetColorScheme.onSecondary, animationSpec = animationSpec, label = "theme-on-secondary")
    val tertiary = animateColorAsState(targetColorScheme.tertiary, animationSpec = animationSpec, label = "theme-tertiary")
    val background = animateColorAsState(targetColorScheme.background, animationSpec = animationSpec, label = "theme-background")
    val onBackground = animateColorAsState(targetColorScheme.onBackground, animationSpec = animationSpec, label = "theme-on-background")
    val surface = animateColorAsState(targetColorScheme.surface, animationSpec = animationSpec, label = "theme-surface")
    val onSurface = animateColorAsState(targetColorScheme.onSurface, animationSpec = animationSpec, label = "theme-on-surface")
    val surfaceVariant = animateColorAsState(targetColorScheme.surfaceVariant, animationSpec = animationSpec, label = "theme-surface-variant")
    val onSurfaceVariant = animateColorAsState(targetColorScheme.onSurfaceVariant, animationSpec = animationSpec, label = "theme-on-surface-variant")
    val outline = animateColorAsState(targetColorScheme.outline, animationSpec = animationSpec, label = "theme-outline")
    val outlineVariant = animateColorAsState(targetColorScheme.outlineVariant, animationSpec = animationSpec, label = "theme-outline-variant")

    val colorScheme = targetColorScheme.copy(
        primary = primary.value,
        onPrimary = onPrimary.value,
        secondary = secondary.value,
        onSecondary = onSecondary.value,
        tertiary = tertiary.value,
        background = background.value,
        onBackground = onBackground.value,
        surface = surface.value,
        onSurface = onSurface.value,
        surfaceVariant = surfaceVariant.value,
        onSurfaceVariant = onSurfaceVariant.value,
        outline = outline.value,
        outlineVariant = outlineVariant.value
    )

    val context = LocalContext.current
    val configuration = remember(context, darkTheme) {
        Configuration(context.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    (if (darkTheme) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO)
        }
    }
    val themeContext = remember(context, configuration) {
        context.createConfigurationContext(configuration)
    }

    val registryOwner = LocalActivityResultRegistryOwner.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val providers = remember(themeContext, configuration, registryOwner, lifecycleOwner) {
        buildList {
            add(LocalContext provides themeContext)
            add(LocalConfiguration provides configuration)
            add(LocalLifecycleOwner provides lifecycleOwner)
            registryOwner?.let { add(LocalActivityResultRegistryOwner provides it) }
        }.toTypedArray()
    }

    CompositionLocalProvider(*providers) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
