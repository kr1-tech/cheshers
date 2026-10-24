package app.cheshers.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import app.cheshers.core.data.repository.CheshersSettings
import app.cheshers.core.data.repository.ThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF256B58), onPrimary = Color.White, primaryContainer = Color(0xFFB9E9D8),
    secondary = Color(0xFF715C9E), background = Color(0xFFF7F8F4), surface = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFEDF0EA), onBackground = Color(0xFF17211D), onSurface = Color(0xFF17211D),
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFF8FD6B4), onPrimary = Color(0xFF09241B), primaryContainer = Color(0xFF214E40),
    secondary = Color(0xFFC9B8F0), background = Color(0xFF101714), surface = Color(0xFF16201C),
    surfaceContainer = Color(0xFF1E2A25), onBackground = Color(0xFFE4EAE5), onSurface = Color(0xFFE4EAE5),
)

@Composable
fun CheshersTheme(settings: CheshersSettings, content: @Composable () -> Unit) {
    val dark = when (settings.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
}
