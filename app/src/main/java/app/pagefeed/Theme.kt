package app.pagefeed

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Colors sampled from the Google Play Books (dark) screenshots. */
object PB {
    val bg = Color(0xFF0E1517)
    val surface = Color(0xFF1A2124)
    val searchBar = Color(0xFF1B292C)
    val chip = Color(0xFF394950)
    val fab = Color(0xFF1F4D5C)
    val accent = Color(0xFF8FD1E9)
    val onAccent = Color(0xFF003544)
    val text = Color(0xFFE1E3E4)
    val text2 = Color(0xFFC1C8CB)
    val outline = Color(0xFF41484B)
    val outlineStrong = Color(0xFF8B9296)
    val menu = Color(0xFF111719)
    val noteHighlight = Color(0xFF604E1E)
    val noteIcon = Color(0xFFE0B040)
    val placeholder = Color(0xFF2A3133)
}

@Composable
fun PagefeedTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = PB.accent,
            onPrimary = PB.onAccent,
            background = PB.bg,
            onBackground = PB.text,
            surface = PB.bg,
            onSurface = PB.text,
            surfaceVariant = PB.surface,
            onSurfaceVariant = PB.text2,
            outline = PB.outline,
            secondaryContainer = PB.chip,
            onSecondaryContainer = PB.text,
        ),
        content = content,
    )
}
