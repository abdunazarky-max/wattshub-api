package com.hyzin.whtsappclone.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Brush
import com.hyzin.whtsappclone.AnimatedBackground
import com.hyzin.whtsappclone.utils.getActivity

private val DarkColorScheme = darkColorScheme(
    primary = OceanBluePrimary,
    secondary = AppGreen,
    tertiary = SkyBlueAccent,
    background = SkyNightDeep,
    surface = SkyNightLight,
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onTertiary = Color.White,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    surfaceVariant = Color.White.copy(alpha = 0.05f),
    onSurfaceVariant = TextSecondary
)

private val LightColorScheme = lightColorScheme(
    primary = OceanBluePrimary,
    secondary = AppGreen,
    tertiary = SkyBlueAccent,
    background = LightBg,
    surface = LightSurface,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = LightTextPrimary,
    onSurface = LightTextPrimary,
    surfaceVariant = LightBorder,
    onSurfaceVariant = LightTextSecondary
)

enum class ThemeType {
    LIGHT,
    STARRY_NIGHT,
    OCEAN_BLUE
}

@Composable
fun WhtsAppCloneTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    selectedTheme: ThemeType = ThemeType.STARRY_NIGHT,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when (selectedTheme) {
        ThemeType.LIGHT -> LightColorScheme
        ThemeType.OCEAN_BLUE -> DarkColorScheme
        ThemeType.STARRY_NIGHT -> DarkColorScheme
    }

    androidx.compose.runtime.SideEffect {
        val window = context.getActivity()?.window
        if (window != null) {
            val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
            val isAppearanceLight = selectedTheme == ThemeType.LIGHT
            insetsController.isAppearanceLightStatusBars = isAppearanceLight
            insetsController.isAppearanceLightNavigationBars = isAppearanceLight
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                when (selectedTheme) {
                    ThemeType.STARRY_NIGHT -> {
                        AnimatedBackground()
                    }
                    ThemeType.OCEAN_BLUE -> {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(OceanBluePrimary, OceanBlueSecondary, OceanBlueLight)
                                )
                            )
                        }
                    }
                    ThemeType.LIGHT -> {
                        // Clean light background
                    }
                }
                content()
            }
        }
    }
}