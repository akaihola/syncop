package fi.kaihola.syncop.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Ink = Color(0xFF141820)
val Panel = Color(0xFF1C2230)
val Fog = Color(0xFF9AA4B2)
val Paper = Color(0xFFE8ECF2)
val Accent = Color(0xFF5B8DEF)
val RecordRed = Color(0xFFE5484D)
val OnBeatGreen = Color(0xFF3DDC84)

private val scheme = darkColorScheme(
    primary = Accent,
    onPrimary = Paper,
    background = Ink,
    onBackground = Paper,
    surface = Panel,
    onSurface = Paper,
    surfaceVariant = Panel,
    onSurfaceVariant = Fog,
    error = RecordRed,
)

@Composable
fun SyncopTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
