package com.joy.murmur.ui.theme

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joy.murmur.R
import com.joy.murmur.domain.settings.AppearanceMode

val LoraFontFamily = FontFamily(
    Font(R.font.lora, FontWeight.Normal),
    Font(R.font.lora, FontWeight.Medium),
    Font(R.font.lora, FontWeight.SemiBold),
)

val JakartaFontFamily = FontFamily(
    Font(R.font.plus_jakarta_sans, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans, FontWeight.SemiBold),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF245E63),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD3ECEE),
    onPrimaryContainer = Color(0xFF062023),
    secondary = Color(0xFF4C6265),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCFE6E9),
    onSecondaryContainer = Color(0xFF081F22),
    tertiary = Color(0xFFB86B35),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDCC6),
    onTertiaryContainer = Color(0xFF321300),
    background = Color(0xFFF7F4EE),
    onBackground = Color(0xFF191C1C),
    surface = Color(0xFFFFFDF9),
    onSurface = Color(0xFF191C1C),
    surfaceVariant = Color(0xFFEAE4D8),
    onSurfaceVariant = Color(0xFF474E4F),
    surfaceContainerLow = Color(0xFFF3EFE7),
    surfaceContainer = Color(0xFFEDE8DE),
    surfaceContainerHigh = Color(0xFFE6E0D4),
    surfaceContainerHighest = Color(0xFFDFD9CC),
    outline = Color(0xFF777E7F),
    outlineVariant = Color(0xFFC9C4B8),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7CC2C9),
    onPrimary = Color(0xFF00363B),
    primaryContainer = Color(0xFF004F56),
    onPrimaryContainer = Color(0xFF9EEFF7),
    secondary = Color(0xFFB3CBD0),
    onSecondary = Color(0xFF1E3438),
    secondaryContainer = Color(0xFF344B4F),
    onSecondaryContainer = Color(0xFFCFE7EC),
    tertiary = Color(0xFFE09A63),
    onTertiary = Color(0xFF4D2600),
    tertiaryContainer = Color(0xFF6B390D),
    onTertiaryContainer = Color(0xFFFFDCC6),
    background = Color(0xFF111718),
    onBackground = Color(0xFFE2E6E6),
    surface = Color(0xFF182123),
    onSurface = Color(0xFFE2E6E6),
    surfaceVariant = Color(0xFF253134),
    onSurfaceVariant = Color(0xFFC0C9CA),
    surfaceContainerLow = Color(0xFF161E20),
    surfaceContainer = Color(0xFF1C2628),
    surfaceContainerHigh = Color(0xFF232F32),
    surfaceContainerHighest = Color(0xFF2C3A3D),
    outline = Color(0xFF8A9394),
    outlineVariant = Color(0xFF3F494B),
)

val MurmurTypography = Typography(
    displayLarge = TextStyle(fontFamily = LoraFontFamily, fontSize = 54.sp, fontWeight = FontWeight.Normal, lineHeight = 62.sp),
    displayMedium = TextStyle(fontFamily = LoraFontFamily, fontSize = 44.sp, fontWeight = FontWeight.Normal, lineHeight = 52.sp),
    displaySmall = TextStyle(fontFamily = LoraFontFamily, fontSize = 36.sp, fontWeight = FontWeight.Normal, lineHeight = 44.sp),
    headlineLarge = TextStyle(fontFamily = LoraFontFamily, fontSize = 32.sp, fontWeight = FontWeight.Medium, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontFamily = LoraFontFamily, fontSize = 28.sp, fontWeight = FontWeight.Medium, lineHeight = 36.sp),
    headlineSmall = TextStyle(fontFamily = LoraFontFamily, fontSize = 24.sp, fontWeight = FontWeight.Medium, lineHeight = 32.sp),
    titleLarge = TextStyle(fontFamily = JakartaFontFamily, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = JakartaFontFamily, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 24.sp),
    titleSmall = TextStyle(fontFamily = JakartaFontFamily, fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = JakartaFontFamily, fontSize = 16.sp, fontWeight = FontWeight.Normal, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = JakartaFontFamily, fontSize = 14.sp, fontWeight = FontWeight.Normal, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = JakartaFontFamily, fontSize = 12.sp, fontWeight = FontWeight.Normal, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = JakartaFontFamily, fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontFamily = JakartaFontFamily, fontSize = 12.sp, fontWeight = FontWeight.Medium, lineHeight = 16.sp, letterSpacing = 0.5.sp),
    labelSmall = TextStyle(fontFamily = JakartaFontFamily, fontSize = 11.sp, fontWeight = FontWeight.Medium, lineHeight = 16.sp, letterSpacing = 0.5.sp),
)

val MurmurShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

data class MurmurElevationTokens(
    val level0: Dp = 0.dp,
    val level1: Dp = 1.dp,
    val level2: Dp = 3.dp,
    val level3: Dp = 6.dp,
    val level4: Dp = 10.dp,
)

data class MurmurSpacingTokens(
    val s1: Dp = 4.dp,
    val s2: Dp = 8.dp,
    val s3: Dp = 12.dp,
    val s4: Dp = 16.dp,
    val s5: Dp = 20.dp,
    val s6: Dp = 24.dp,
    val s7: Dp = 32.dp,
    val s8: Dp = 40.dp,
    val s9: Dp = 48.dp,
)

data class MurmurMotionTokens(
    val durationShort: Long = 150,
    val durationMedium: Long = 250,
    val durationLong: Long = 350,
    val easingStandard: Easing = FastOutSlowInEasing,
    val easingEmphasized: Easing = FastOutSlowInEasing,
    val easingAccelerate: Easing = FastOutSlowInEasing,
    val easingDecelerate: Easing = LinearOutSlowInEasing,
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