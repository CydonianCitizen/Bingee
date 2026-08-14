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
        primary = Color(0xFF4255A5),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFDDE1FF),
        onPrimaryContainer = Color(0xFF09164B),
        secondary = Color(0xFF596078),
        background = Color(0xFFFBF8FF),
        surface = Color(0xFFFBF8FF)
    )

private val DarkColorScheme =
    darkColorScheme(
        primary = Color(0xFFBAC3FF),
        onPrimary = Color(0xFF10245F),
        primaryContainer = Color(0xFF293C8C),
        onPrimaryContainer = Color(0xFFDDE1FF),
        secondary = Color(0xFFC1C6DD),
        background = Color(0xFF121318),
        surface = Color(0xFF121318)
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
