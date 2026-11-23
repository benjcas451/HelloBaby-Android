package ch.tschir.hellobaby.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import ch.tschir.hellobaby.R

// ============================================================================
// Minze & Honig 2.0 – Theme-Schicht
// Quelle: DesignGuide/tokens/design-tokens.json (semantic.light / semantic.dark,
// typography, radius, spacing). Komponenten benutzen nur diese Rollen, nie
// Skalenwerte. Farbe ist Signal, keine Fläche: genau eine Hauptaktion
// (primary) je Ansicht, alles andere Haarlinie oder Text.
// ============================================================================

/** Rollen-Tokens eines Farbmodus. Namen wie in design-tokens.json. */
@Immutable
data class MhFarben(
    val dunkel: Boolean,
    val bg: Color,
    val surface: Color,
    val surfaceSubtle: Color,
    val surfaceRaised: Color,
    val segmentOn: Color,
    val fieldBg: Color,
    val border: Color,
    val borderStrong: Color,
    val text: Color,
    val textStrong: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val textDisabled: Color,
    val link: Color,
    val primary: Color,
    val onPrimary: Color,
    val danger: Color,
    val onDanger: Color,
    val focusRing: Color,
    val selectedBg: Color,
    val onSelected: Color,
    val tint: Color,
    val onTint: Color,
    val highlight: Color,
    val onHighlight: Color,
    val textGreen: Color,
    val textYellow: Color,
    val textPurple: Color,
    val data1: Color,
    val data2: Color,
    val data3: Color,
    val dataTrack: Color,
    val successFg: Color,
    val successBg: Color,
    val warningFg: Color,
    val warningBg: Color,
    val errorFg: Color,
    val errorBg: Color,
    val infoFg: Color,
    val infoBg: Color,
)

val MhHell = MhFarben(
    dunkel = false,
    bg = Color(0xFFFFFFFF),
    surface = Color(0xFFFFFFFF),
    surfaceSubtle = Color(0xFFF6F8F6),
    surfaceRaised = Color(0xFFFFFFFF),
    segmentOn = Color(0xFFFFFFFF),
    fieldBg = Color(0xFFFFFFFF),
    border = Color(0xFFE7EBE8),
    borderStrong = Color(0xFFDFE4E1),
    text = Color(0xFF2E332F),
    textStrong = Color(0xFF1F2221),
    textSecondary = Color(0xFF5D655F),
    textTertiary = Color(0xFF7C857F),
    textDisabled = Color(0xFFA2ABA6),
    link = Color(0xFF38664C),
    primary = Color(0xFF38664C),
    onPrimary = Color(0xFFFFFFFF),
    danger = Color(0xFFB3453E),
    onDanger = Color(0xFFFFFFFF),
    focusRing = Color(0xFF5FA07C),
    selectedBg = Color(0xFFE1F3E8),
    onSelected = Color(0xFF2C4F3B),
    tint = Color(0xFFF2FAF5),
    onTint = Color(0xFF38664C),
    highlight = Color(0xFFFBF3D3),
    onHighlight = Color(0xFF473A17),
    textGreen = Color(0xFF38664C),
    textYellow = Color(0xFF8A6F26),
    textPurple = Color(0xFF7D5591),
    data1 = Color(0xFF82BD9B),
    data2 = Color(0xFFEDD374),
    data3 = Color(0xFFB592CA),
    dataTrack = Color(0xFFEDF0EE),
    successFg = Color(0xFF38664C),
    successBg = Color(0xFFE1F3E8),
    warningFg = Color(0xFF8A6F26),
    warningBg = Color(0xFFFBF3D3),
    errorFg = Color(0xFFB3453E),
    errorBg = Color(0xFFFAE3E1),
    infoFg = Color(0xFF7D5591),
    infoBg = Color(0xFFF1E9F6),
)

val MhDunkel = MhFarben(
    dunkel = true,
    bg = Color(0xFF1F2221),
    surface = Color(0xFF262A28),
    surfaceSubtle = Color(0xFF2B302D),
    surfaceRaised = Color(0xFF2E3330),
    segmentOn = Color(0xFF343A36),
    fieldBg = Color(0xFF1F2221),
    border = Color(0xFF343A36),
    borderStrong = Color(0xFF454C47),
    text = Color(0xFFE6EAE7),
    textStrong = Color(0xFFFFFFFF),
    textSecondary = Color(0xFFA9B0AB),
    textTertiary = Color(0xFF8A938D),
    textDisabled = Color(0xFF6B736E),
    link = Color(0xFFA8D5BA),
    primary = Color(0xFF82BD9B),
    onPrimary = Color(0xFF1F2221),
    danger = Color(0xFFF0B6B1),
    onDanger = Color(0xFF1F2221),
    focusRing = Color(0xFFA8D5BA),
    selectedBg = Color(0xFF2C4F3B),
    onSelected = Color(0xFFC8E8D4),
    tint = Color(0xFF263B2F),
    onTint = Color(0xFFA8D5BA),
    highlight = Color(0xFF3B3524),
    onHighlight = Color(0xFFF9EDBB),
    textGreen = Color(0xFFA8D5BA),
    textYellow = Color(0xFFF7E8A4),
    textPurple = Color(0xFFCDB4DB),
    data1 = Color(0xFFA8D5BA),
    data2 = Color(0xFFF7E8A4),
    data3 = Color(0xFFCDB4DB),
    dataTrack = Color(0xFF343A36),
    successFg = Color(0xFFA8D5BA),
    successBg = Color(0xFF263B2F),
    warningFg = Color(0xFFF7E8A4),
    warningBg = Color(0xFF3B3524),
    errorFg = Color(0xFFF0B6B1),
    errorBg = Color(0xFF3F2523),
    infoFg = Color(0xFFCDB4DB),
    infoBg = Color(0xFF352B3C),
)

val LocalMhFarben = staticCompositionLocalOf { MhHell }

/** Figtree, nur die Schnitte 400/500/600 (keine Kursive). */
val Figtree = FontFamily(
    Font(R.font.figtree_regular, FontWeight.Normal),
    Font(R.font.figtree_medium, FontWeight.Medium),
    Font(R.font.figtree_semibold, FontWeight.SemiBold),
)

/** Typo-Skala aus dem Guide (Abschnitt 3), Größen in sp. */
object MhTypo {
    val display = TextStyle(fontFamily = Figtree, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 1.15.em, letterSpacing = (-0.025).em)
    val h1 = TextStyle(fontFamily = Figtree, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 1.2.em, letterSpacing = (-0.015).em)
    val h2 = TextStyle(fontFamily = Figtree, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, lineHeight = 1.3.em, letterSpacing = (-0.015).em)
    val h3 = TextStyle(fontFamily = Figtree, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 1.4.em)
    val body = TextStyle(fontFamily = Figtree, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 1.55.em)
    val label = TextStyle(fontFamily = Figtree, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 1.4.em)
    /** Buttons und Reiter: Label in 15 sp wie im Web. */
    val button = TextStyle(fontFamily = Figtree, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 1.4.em)
    val small = TextStyle(fontFamily = Figtree, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 1.5.em)
    /** Marken („Gewicht 9,3 kg“). */
    val chip = TextStyle(fontFamily = Figtree, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 1.4.em)
    /** Nur Gruppenlabels; Versalien setzt [MhGruppenLabel]. */
    val caption = TextStyle(fontFamily = Figtree, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 1.4.em, letterSpacing = 0.08.em)
    /** Große Werte: dünn (400), Tabellenziffern. */
    val number = TextStyle(fontFamily = Figtree, fontWeight = FontWeight.Normal, fontSize = 28.sp, lineHeight = 1.1.em, letterSpacing = (-0.02).em, fontFeatureSettings = "tnum")
}

/** Zugriff auf Rollen und Maße: `Mh.farben.primary`, `Mh.radiusMd` … */
object Mh {
    val farben: MhFarben
        @Composable @ReadOnlyComposable get() = LocalMhFarben.current

    // Radien (Guide Abschnitt 5)
    val radiusSm = 6.dp
    val radiusMd = 10.dp
    val radiusLg = 14.dp
    val radiusXl = 20.dp

    // 4-dp-Raster (Guide Abschnitt 4)
    val abstand1 = 4.dp
    val abstand2 = 8.dp
    val abstand3 = 12.dp
    val abstand4 = 16.dp
    val abstand5 = 20.dp
    val abstand6 = 24.dp
    val abstand8 = 32.dp
    val abstand12 = 48.dp

    /** Seitenrand auf dem Handy. */
    val rand = 16.dp

    /** Touch-Ziel auf Android. */
    val touch = 48.dp

    /** Symbolgrößen */
    val symbolKlein = 16.dp
    val symbol = 20.dp
    val symbolGross = 24.dp

    /** Haarlinie */
    val linie = 1.dp
}

private fun farbschema(f: MhFarben): ColorScheme =
    if (f.dunkel) {
        darkColorScheme(
            primary = f.primary, onPrimary = f.onPrimary,
            primaryContainer = f.selectedBg, onPrimaryContainer = f.onSelected,
            inversePrimary = MhHell.primary,
            secondary = f.link, onSecondary = f.onPrimary,
            secondaryContainer = f.selectedBg, onSecondaryContainer = f.onSelected,
            tertiary = f.textPurple, onTertiary = f.bg,
            tertiaryContainer = f.tint, onTertiaryContainer = f.onTint,
            background = f.bg, onBackground = f.textStrong,
            surface = f.surface, onSurface = f.text,
            surfaceVariant = f.surfaceSubtle, onSurfaceVariant = f.textSecondary,
            // Keine farbige Tönung erhöhter Flächen.
            surfaceTint = Color.Transparent,
            // Snackbar = Toast: hell auf dunkel.
            inverseSurface = f.textStrong, inverseOnSurface = f.bg,
            error = f.danger, onError = f.onDanger,
            errorContainer = f.errorBg, onErrorContainer = f.text,
            outline = f.borderStrong, outlineVariant = f.border,
            scrim = Color.Black,
            surfaceBright = f.surface, surfaceDim = f.surfaceSubtle,
            surfaceContainerLowest = f.bg, surfaceContainerLow = f.surface,
            surfaceContainer = f.surfaceSubtle,
            // Dialoge und Menüs schweben: eigene, neutrale Fläche.
            surfaceContainerHigh = f.surfaceRaised,
            surfaceContainerHighest = f.surfaceSubtle,
        )
    } else {
        lightColorScheme(
            primary = f.primary, onPrimary = f.onPrimary,
            primaryContainer = f.selectedBg, onPrimaryContainer = f.onSelected,
            inversePrimary = MhDunkel.primary,
            secondary = f.link, onSecondary = f.onPrimary,
            secondaryContainer = f.selectedBg, onSecondaryContainer = f.onSelected,
            tertiary = f.textPurple, onTertiary = f.bg,
            tertiaryContainer = f.tint, onTertiaryContainer = f.onTint,
            background = f.bg, onBackground = f.textStrong,
            surface = f.surface, onSurface = f.text,
            surfaceVariant = f.surfaceSubtle, onSurfaceVariant = f.textSecondary,
            surfaceTint = Color.Transparent,
            // Snackbar = Toast: dunkel auf hell.
            inverseSurface = f.textStrong, inverseOnSurface = f.bg,
            error = f.danger, onError = f.onDanger,
            errorContainer = f.errorBg, onErrorContainer = f.text,
            outline = f.borderStrong, outlineVariant = f.border,
            scrim = Color.Black,
            surfaceBright = f.surface, surfaceDim = f.surfaceSubtle,
            surfaceContainerLowest = f.bg, surfaceContainerLow = f.surface,
            surfaceContainer = f.surfaceSubtle,
            surfaceContainerHigh = f.surfaceRaised,
            surfaceContainerHighest = f.surfaceSubtle,
        )
    }

private val MhMaterialTypo = Typography(
    displayLarge = MhTypo.display,
    displayMedium = MhTypo.display,
    displaySmall = MhTypo.display,
    headlineLarge = MhTypo.display,
    headlineMedium = MhTypo.h1,
    headlineSmall = MhTypo.h2,
    titleLarge = MhTypo.h2,
    titleMedium = MhTypo.h3,
    titleSmall = MhTypo.label,
    bodyLarge = MhTypo.body,
    bodyMedium = MhTypo.small,
    bodySmall = MhTypo.chip,
    labelLarge = MhTypo.button,
    labelMedium = MhTypo.label,
    labelSmall = MhTypo.caption,
)

private val MhFormen = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(20.dp),
)

/**
 * Theme der App. Folgt der Systemeinstellung (hell/dunkel); Dynamic Color
 * bleibt bewusst aus, sonst überschriebe das System die Markenfarben.
 */
@Composable
fun MhTheme(dunkel: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val farben = if (dunkel) MhDunkel else MhHell
    CompositionLocalProvider(LocalMhFarben provides farben) {
        MaterialTheme(
            colorScheme = farbschema(farben),
            typography = MhMaterialTypo,
            shapes = MhFormen,
            content = content,
        )
    }
}
