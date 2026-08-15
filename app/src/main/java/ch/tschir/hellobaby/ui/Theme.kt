package ch.tschir.hellobaby.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Zentrales Theme der Baby-Tagebuch-App — portiert aus der Flutter-AppTheme
 * („Clean Modern Redesign“): Baby-Grün als Marke, ruhige warm getönte
 * Neutralflächen, Radius 16/12, vollständiger Dark Mode.
 */
object Hb {
    /** Marken-Grün (entspricht dem Launcher-Icon). */
    val seed = Color(0xFF78B856)

    /** Akzent-/Semantik-Töne, die in Screens wiederverwendet werden. */
    val accentDeep = Color(0xFF5A9A3C)
    val gold = Color(0xFFE0A920)

    // Home-Aktionsfarben (identisch zur Flutter-App).
    val aktionErstellen = Color(0xFF5A9A3C)
    val aktionTag = Color(0xFF4F86C6)
    val aktionMonat = Color(0xFF8E7CC3)
    val aktionZufall = Color(0xFF6FAF53)
    val aktionFavoriten = Color(0xFFDAA520)
    val aktionGalerie = Color(0xFFCB6CA8)

    // Flächen (aus der Flutter-AppTheme).
    val scaffoldHell = Color(0xFFF5F7F0)
    val scaffoldDunkel = Color(0xFF0F1310)
    val karteHell = Color(0xFFFFFFFF)
    val karteDunkel = Color(0xFF1A1F1B)
    val outlineHell = Color(0xFFE3E7DD)
    val outlineDunkel = Color(0xFF2C332D)
    val chipHell = Color(0xFFEDF3E6)
    val chipDunkel = Color(0xFF20271F)
}

private val HellesSchema = lightColorScheme(
    // Primärton nahe an Flutters ColorScheme.fromSeed(0xFF78B856).
    primary = Color(0xFF3F6A26),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFBFF09E),
    onPrimaryContainer = Color(0xFF0B2000),
    secondary = Color(0xFF55624C),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD9E7CB),
    onSecondaryContainer = Color(0xFF131F0D),
    tertiary = Color(0xFF386663),
    onTertiary = Color.White,
    background = Hb.scaffoldHell,
    onBackground = Color(0xFF1A1C18),
    surface = Hb.karteHell,
    onSurface = Color(0xFF1A1C18),
    surfaceVariant = Color(0xFFE0E4D6),
    onSurfaceVariant = Color(0xFF44483E),
    outline = Hb.outlineHell,
    outlineVariant = Color(0xFFC4C8BA),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Hb.scaffoldHell,
    surfaceContainer = Hb.scaffoldHell,
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Color(0xFFE0E4D6),
)

private val DunklesSchema = darkColorScheme(
    primary = Color(0xFFA4D585),
    onPrimary = Color(0xFF163800),
    primaryContainer = Color(0xFF285010),
    onPrimaryContainer = Color(0xFFBFF09E),
    secondary = Color(0xFFBDCBB0),
    onSecondary = Color(0xFF283420),
    secondaryContainer = Color(0xFF3E4A35),
    onSecondaryContainer = Color(0xFFD9E7CB),
    tertiary = Color(0xFFA0CFCC),
    onTertiary = Color(0xFF003735),
    background = Hb.scaffoldDunkel,
    onBackground = Color(0xFFE2E3DC),
    surface = Hb.karteDunkel,
    onSurface = Color(0xFFE2E3DC),
    surfaceVariant = Color(0xFF44483E),
    onSurfaceVariant = Color(0xFFC4C8BA),
    outline = Hb.outlineDunkel,
    outlineVariant = Color(0xFF44483E),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    surfaceContainerLowest = Hb.scaffoldDunkel,
    surfaceContainerLow = Hb.karteDunkel,
    surfaceContainer = Hb.karteDunkel,
    surfaceContainerHigh = Hb.karteDunkel,
    surfaceContainerHighest = Color(0xFF2C332D),
)

private val HbFormen = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(16.dp),
)

/** Chip-/Hinweisfläche je nach Modus (aus der Flutter-AppTheme). */
@Composable
fun hbChipFlaeche(): Color = if (isSystemInDarkTheme()) Hb.chipDunkel else Hb.chipHell

@Composable
fun HelloBabyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DunklesSchema else HellesSchema,
        shapes = HbFormen,
        content = content,
    )
}
