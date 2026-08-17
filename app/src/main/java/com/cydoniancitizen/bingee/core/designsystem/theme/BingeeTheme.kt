package com.cydoniancitizen.bingee.core.designsystem.theme

import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cydoniancitizen.bingee.R

private val LightColorScheme =
    lightColorScheme(
        primary = Color(0xFF7A5B00),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFFFE08A),
        onPrimaryContainer = Color(0xFF241A00),
        inversePrimary = Color(0xFFFFD86A),
        secondary = Color(0xFF4E5568),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFE1E5F1),
        onSecondaryContainer = Color(0xFF151B2C),
        tertiary = Color(0xFF675A4A),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFF0E0C9),
        onTertiaryContainer = Color(0xFF211A10),
        background = Color(0xFFF7F7FA),
        onBackground = Color(0xFF141824),
        surface = Color.White,
        onSurface = Color(0xFF141824),
        surfaceVariant = Color(0xFFE0E2E9),
        onSurfaceVariant = Color(0xFF414754),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color(0xFFF7F7FA),
        surfaceContainer = Color(0xFFF1F1F5),
        surfaceContainerHigh = Color(0xFFEBEBF0),
        surfaceContainerHighest = Color(0xFFE5E5EA),
        surfaceDim = Color(0xFFDADAE0),
        surfaceBright = Color.White,
        inverseSurface = Color(0xFF2C303B),
        inverseOnSurface = Color(0xFFF2F0F4),
        outline = Color(0xFF717784),
        outlineVariant = Color(0xFFC1C5CD),
        error = Color(0xFFBA1A1A),
        onError = Color.White,
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        scrim = Color.Black
    )

private val DarkColorScheme =
    darkColorScheme(
        primary = Color(0xFFFFCC33),
        onPrimary = Color(0xFF141824),
        primaryContainer = Color(0xFF5B4500),
        onPrimaryContainer = Color(0xFFFFE8A6),
        inversePrimary = Color(0xFF7A5B00),
        secondary = Color(0xFFC2C7D9),
        onSecondary = Color(0xFF2C3142),
        secondaryContainer = Color(0xFF41475A),
        onSecondaryContainer = Color(0xFFE0E4F6),
        tertiary = Color(0xFFDBC6AB),
        onTertiary = Color(0xFF3C3022),
        tertiaryContainer = Color(0xFF524534),
        onTertiaryContainer = Color(0xFFF8E8D0),
        background = Color(0xFF141824),
        onBackground = Color(0xFFF7F7FA),
        surface = Color(0xFF141824),
        onSurface = Color(0xFFF7F7FA),
        surfaceVariant = Color(0xFF414754),
        onSurfaceVariant = Color(0xFFC1C7D6),
        surfaceContainerLowest = Color(0xFF0E121D),
        surfaceContainerLow = Color(0xFF1A1E2B),
        surfaceContainer = Color(0xFF1E2433),
        surfaceContainerHigh = Color(0xFF22283A),
        surfaceContainerHighest = Color(0xFF2C3345),
        surfaceDim = Color(0xFF141824),
        surfaceBright = Color(0xFF30384D),
        inverseSurface = Color(0xFFE6E6EA),
        inverseOnSurface = Color(0xFF292D38),
        outline = Color(0xFF8B91A0),
        outlineVariant = Color(0xFF414754),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        scrim = Color.Black
    )

private val BingeeTypography = Typography()

private val BingeeShapes =
    Shapes(
        medium = RoundedCornerShape(16.dp)
    )

object BingeeDimensions {
    val screenPadding = 24.dp
    val contentSpacing = 16.dp
    val elementSpacing = 8.dp
}

@Composable
fun BingeeTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = BingeeTypography,
        shapes = BingeeShapes,
        content = content
    )
}

@Preview(name = "Light", showBackground = true)
@Preview(
    name = "Dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
private fun BingeeThemePreview() {
    BingeeTheme {
        Surface {
            Text(
                text = stringResource(R.string.app_name),
                modifier = Modifier.padding(BingeeDimensions.screenPadding)
            )
        }
    }
}
