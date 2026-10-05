package com.joy.murmur.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.joy.murmur.domain.settings.AppearanceMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF4B8E96),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF3F7880),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFEEF2F2),
    onBackground = Color(0xFF112022),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF112022),
    surfaceVariant = Color(0xFFE5EEEE),
    onSurfaceVariant = Color(0xFF66777A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF84C5CC),
    onPrimary = Color(0xFF00363C),
    secondary = Color(0xFF9DCBD0),
    onSecondary = Color(0xFF14383C),
    background = Color(0xFF101819),
    onBackground = Color(0xFFDDE5E5),
    surface = Color(0xFF172122),
    onSurface = Color(0xFFDDE5E5),
    surfaceVariant = Color(0xFF354446),
    onSurfaceVariant = Color(0xFFBAC8C9),
)

@Composable
fun MurmurTheme(
    appearanceMode: AppearanceMode,
    content: @Composable () -> Unit,
) {
    val useDarkTheme = when (appearanceMode) {
        AppearanceMode.SYSTEM -> isSystemInDarkTheme()
        AppearanceMode.LIGHT -> false
        AppearanceMode.DARK -> true
    }

    MaterialTheme(
        colorScheme = if (useDarkTheme) DarkColors else LightColors,
        content = content,
    )
}
