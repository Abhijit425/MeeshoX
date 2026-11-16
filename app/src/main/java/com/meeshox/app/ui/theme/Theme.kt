package com.meeshox.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Violet = Color(0xFF6D28D9)
val VioletDeep = Color(0xFF4C1D95)
val Rose = Color(0xFFDB2777)
val Saffron = Color(0xFFF59E0B)
val Mint = Color(0xFF059669)
val DealRed = Color(0xFFDC2626)

val BrandGradient = Brush.linearGradient(listOf(VioletDeep, Violet, Rose))

private val Light = lightColorScheme(
    primary = Violet, onPrimary = Color.White, primaryContainer = Color(0xFFEDE4FF), onPrimaryContainer = VioletDeep,
    secondary = Rose, onSecondary = Color.White, secondaryContainer = Color(0xFFFCE7F3), onSecondaryContainer = Color(0xFF831843),
    tertiary = Saffron, background = Color(0xFFF7F4FB), surface = Color.White, surfaceVariant = Color(0xFFF1ECF8),
    onSurfaceVariant = Color(0xFF5B5568), outline = Color(0xFFD6D0E0), error = DealRed
)
private val Dark = darkColorScheme(
    primary = Color(0xFFC4B5FD), onPrimary = Color(0xFF2E1065), primaryContainer = Color(0xFF4C1D95), onPrimaryContainer = Color(0xFFEDE4FF),
    secondary = Color(0xFFF9A8D4), secondaryContainer = Color(0xFF831843), tertiary = Saffron,
    background = Color(0xFF110D18), surface = Color(0xFF1A1523), surfaceVariant = Color(0xFF2A2335), onSurfaceVariant = Color(0xFFCFC6DD)
)

private val AppTypography = Typography(
    headlineSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp)
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp), medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(24.dp)
)

@Composable
fun MeeshoXTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Dark else Light, typography = AppTypography, shapes = AppShapes, content = content)
}
