package com.joy.murmur.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

val MurmurTypography = Typography(
    displayLarge = TextStyle(fontSize = 57.sp, fontWeight = FontWeight.Normal, lineHeight = 64.sp),
    displayMedium = TextStyle(fontSize = 45.sp, fontWeight = FontWeight.Normal, lineHeight = 52.sp),
    displaySmall = TextStyle(fontSize = 36.sp, fontWeight = FontWeight.Normal, lineHeight = 44.sp),
    headlineLarge = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Normal, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Normal, lineHeight = 36.sp),
    headlineSmall = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Normal, lineHeight = 32.sp),
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Medium, lineHeight = 28.sp),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium, lineHeight = 24.sp),
    titleSmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal, lineHeight = 16.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, lineHeight = 16.sp, letterSpacing = 0.5.sp),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, lineHeight = 16.sp, letterSpacing = 0.5.sp),
)

val MurmurShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
)

data class MurmurElevationTokens(
    val level0: androidx.compose.ui.unit.Dp = 0.dp,
    val level1: androidx.compose.ui.unit.Dp = 1.dp,
    val level2: androidx.compose.ui.unit.Dp = 3.dp,
    val level3: androidx.compose.ui.unit.Dp = 6.dp,
    val level4: androidx.compose.ui.unit.Dp = 10.dp,
)

data class MurmurSpacingTokens(
    val s1: androidx.compose.ui.unit.Dp = 4.dp,
    val s2: androidx.compose.ui.unit.Dp = 8.dp,
    val s3: androidx.compose.ui.unit.Dp = 12.dp,
    val s4: androidx.compose.ui.unit.Dp = 16.dp,
    val s5: androidx.compose.ui.unit.Dp = 20.dp,
    val s6: androidx.compose.ui.unit.Dp = 24.dp,
    val s7: androidx.compose.ui.unit.Dp = 32.dp,
    val s8: androidx.compose.ui.unit.Dp = 40.dp,
    val s9: androidx.compose.ui.unit.Dp = 48.dp,
)

data class MurmurMotionTokens(
    val durationShort: Long = 150,
    val durationMedium: Long = 250,
    val durationLong: Long = 350,
    val easingStandard: androidx.compose.animation.core.Easing = androidx.compose.animation.core.FastOutSlowInEasing,
    val easingEmphasized: androidx.compose.animation.core.Easing = androidx.compose.animation.core.FastOutSlowInEasing,
    val easingAccelerate: androidx.compose.animation.core.Easing = androidx.compose.animation.core.FastOutSlowInEasing,
    val easingDecelerate: androidx.compose.animation.core.Easing = androidx.compose.animation.core.LinearOutSlowInEasing,
)

val MurmurElevationTokensInstance = MurmurElevationTokens()
val MurmurSpacingTokensInstance = MurmurSpacingTokens()
val MurmurMotionTokensInstance = MurmurMotionTokens()

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
        typography = MurmurTypography,
        shapes = MurmurShapes,
        content = content,
    )
}

val MaterialTheme.murmurTypography: Typography
    get() = MurmurTypography

val MaterialTheme.murmurShapes: Shapes
    get() = MurmurShapes

val MaterialTheme.murmurElevation: MurmurElevationTokens
    get() = MurmurElevationTokensInstance

val MaterialTheme.murmurSpacing: MurmurSpacingTokens
    get() = MurmurSpacingTokensInstance

val MaterialTheme.murmurMotion: MurmurMotionTokens
    get() = MurmurMotionTokensInstance