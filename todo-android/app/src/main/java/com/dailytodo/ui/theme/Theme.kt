package com.dailytodo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dailytodo.ui.Tab

// Home: warm peach & coral. University: calm indigo & lavender.
private val HomeLight = lightColorScheme(
    primary = Color(0xFFE0674E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDCD1),
    onPrimaryContainer = Color(0xFF5A1A0C),
    secondary = Color(0xFFF2A65A),
    secondaryContainer = Color(0xFFFFE8CC),
    onSecondaryContainer = Color(0xFF4F2C00),
    tertiary = Color(0xFF4DAA82),
    tertiaryContainer = Color(0xFFD3F2E3),
    onTertiaryContainer = Color(0xFF0B3B26),
    background = Color(0xFFFFF8F4),
    onBackground = Color(0xFF2B1F1B),
    surface = Color(0xFFFFFBF8),
    onSurface = Color(0xFF2B1F1B),
    surfaceVariant = Color(0xFFF6E7E0),
    onSurfaceVariant = Color(0xFF6E5A52),
    surfaceContainer = Color(0xFFFCEEE8),
    surfaceContainerHigh = Color(0xFFF8E6DE),
    outline = Color(0xFFD9C2B8),
    outlineVariant = Color(0xFFEEDDD5),
    error = Color(0xFFD64545),
)

private val HomeDark = darkColorScheme(
    primary = Color(0xFFFF9C82),
    onPrimary = Color(0xFF4A160A),
    primaryContainer = Color(0xFF6B2B1C),
    onPrimaryContainer = Color(0xFFFFDCD1),
    secondary = Color(0xFFFFC38A),
    secondaryContainer = Color(0xFF5C3A12),
    onSecondaryContainer = Color(0xFFFFE8CC),
    tertiary = Color(0xFF7FD6AE),
    tertiaryContainer = Color(0xFF1C4A36),
    onTertiaryContainer = Color(0xFFD3F2E3),
    background = Color(0xFF1C1513),
    onBackground = Color(0xFFF3E3DD),
    surface = Color(0xFF231B18),
    onSurface = Color(0xFFF3E3DD),
    surfaceVariant = Color(0xFF3A2E29),
    onSurfaceVariant = Color(0xFFD6C2BA),
    surfaceContainer = Color(0xFF2B221F),
    surfaceContainerHigh = Color(0xFF342A26),
    outline = Color(0xFF6E5A52),
    outlineVariant = Color(0xFF4A3C36),
    error = Color(0xFFFF8A80),
)

private val UniLight = lightColorScheme(
    primary = Color(0xFF5563E8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDFE2FF),
    onPrimaryContainer = Color(0xFF111A6B),
    secondary = Color(0xFF9B7BEA),
    secondaryContainer = Color(0xFFEDE4FF),
    onSecondaryContainer = Color(0xFF2E1566),
    tertiary = Color(0xFF3BA99C),
    tertiaryContainer = Color(0xFFD0F2EC),
    onTertiaryContainer = Color(0xFF073A33),
    background = Color(0xFFF6F7FF),
    onBackground = Color(0xFF1B1D2E),
    surface = Color(0xFFFBFBFF),
    onSurface = Color(0xFF1B1D2E),
    surfaceVariant = Color(0xFFE6E8F7),
    onSurfaceVariant = Color(0xFF585C78),
    surfaceContainer = Color(0xFFEEF0FD),
    surfaceContainerHigh = Color(0xFFE6E9FA),
    outline = Color(0xFFC3C7E0),
    outlineVariant = Color(0xFFDDE0F2),
    error = Color(0xFFD64545),
)

private val UniDark = darkColorScheme(
    primary = Color(0xFFA9B2FF),
    onPrimary = Color(0xFF16207A),
    primaryContainer = Color(0xFF2F3A99),
    onPrimaryContainer = Color(0xFFDFE2FF),
    secondary = Color(0xFFCDB8FF),
    secondaryContainer = Color(0xFF45307E),
    onSecondaryContainer = Color(0xFFEDE4FF),
    tertiary = Color(0xFF7ED9CC),
    tertiaryContainer = Color(0xFF16483F),
    onTertiaryContainer = Color(0xFFD0F2EC),
    background = Color(0xFF13141F),
    onBackground = Color(0xFFE3E4F5),
    surface = Color(0xFF1A1B28),
    onSurface = Color(0xFFE3E4F5),
    surfaceVariant = Color(0xFF2E3044),
    onSurfaceVariant = Color(0xFFC0C3DC),
    surfaceContainer = Color(0xFF222436),
    surfaceContainerHigh = Color(0xFF2A2C40),
    outline = Color(0xFF5E627E),
    outlineVariant = Color(0xFF3A3D55),
    error = Color(0xFFFF8A80),
)

// Alarms: fresh dawn teal.
private val AlarmLight = lightColorScheme(
    primary = Color(0xFF128C88),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC9F0EA),
    onPrimaryContainer = Color(0xFF00302D),
    secondary = Color(0xFF4C9BE0),
    secondaryContainer = Color(0xFFDCEBFF),
    onSecondaryContainer = Color(0xFF0B2F55),
    tertiary = Color(0xFF4DAA82),
    tertiaryContainer = Color(0xFFD3F2E3),
    onTertiaryContainer = Color(0xFF0B3B26),
    background = Color(0xFFF3FAF9),
    onBackground = Color(0xFF172322),
    surface = Color(0xFFFAFEFD),
    onSurface = Color(0xFF172322),
    surfaceVariant = Color(0xFFDFEDEB),
    onSurfaceVariant = Color(0xFF4F6461),
    surfaceContainer = Color(0xFFE9F5F3),
    surfaceContainerHigh = Color(0xFFE0EFEC),
    outline = Color(0xFFB7CCC9),
    outlineVariant = Color(0xFFD5E5E2),
    error = Color(0xFFD64545),
)

private val AlarmDark = darkColorScheme(
    primary = Color(0xFF7FDCD3),
    onPrimary = Color(0xFF003733),
    primaryContainer = Color(0xFF0F4F4B),
    onPrimaryContainer = Color(0xFFC9F0EA),
    secondary = Color(0xFFA4CBFF),
    secondaryContainer = Color(0xFF1E3F66),
    onSecondaryContainer = Color(0xFFDCEBFF),
    tertiary = Color(0xFF7FD6AE),
    tertiaryContainer = Color(0xFF1C4A36),
    onTertiaryContainer = Color(0xFFD3F2E3),
    background = Color(0xFF0F1817),
    onBackground = Color(0xFFDCEBE8),
    surface = Color(0xFF152120),
    onSurface = Color(0xFFDCEBE8),
    surfaceVariant = Color(0xFF2A3B39),
    onSurfaceVariant = Color(0xFFB9CDCA),
    surfaceContainer = Color(0xFF1C2928),
    surfaceContainerHigh = Color(0xFF243331),
    outline = Color(0xFF5A6F6C),
    outlineVariant = Color(0xFF344644),
    error = Color(0xFFFF8A80),
)

fun tabColors(tab: Tab, dark: Boolean): ColorScheme = when (tab) {
    Tab.HOME -> if (dark) HomeDark else HomeLight
    Tab.UNIVERSITY -> if (dark) UniDark else UniLight
    Tab.ALARMS -> if (dark) AlarmDark else AlarmLight
}

private val AppTypography = Typography().run {
    copy(
        headlineLarge = headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

private val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
)

@Composable
fun TodoTheme(tab: Tab, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = tabColors(tab, isSystemInDarkTheme()),
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}

val SectionTitle = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp)
