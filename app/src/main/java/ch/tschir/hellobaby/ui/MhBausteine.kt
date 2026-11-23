package ch.tschir.hellobaby.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ch.tschir.hellobaby.R

// ============================================================================
// Minze & Honig 2.0 – gemeinsame Bausteine (Guide Abschnitt 7).
// Entsprechen den Web-Klassen in DesignGuide/web/components.css.
// ============================================================================

// ── Symbole ─────────────────────────────────────────────────────────────────

/** Liniensymbol aus res/drawable/ic_mh_*.xml, Farbe aus dem Kontext. */
@Composable
fun MhSymbol(
    @DrawableRes symbol: Int,
    beschreibung: String? = null,
    modifier: Modifier = Modifier,
    groesse: Dp = Mh.symbol,
    farbe: Color = LocalContentColor.current,
) {
    Icon(
        painter = painterResource(symbol),
        contentDescription = beschreibung,
        tint = farbe,
        modifier = modifier.size(groesse),
    )
}

/** Symbol-Knopf (`.icon-btn`): 48 dp, ohne Rahmen, Nebentextfarbe. */
@Composable
fun MhSymbolButton(
    @DrawableRes symbol: Int,
    beschreibung: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    farbe: Color = Mh.farben.textSecondary,
    enabled: Boolean = true,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(Mh.touch),
        colors = IconButtonDefaults.iconButtonColors(
            contentColor = farbe,
            disabledContentColor = Mh.farben.textDisabled,
        ),
    ) {
        MhSymbol(symbol, beschreibung)
    }
}

// ── Kopf ────────────────────────────────────────────────────────────────────

/**
 * App-Kopf wie im Web (`ui_kopf()`): oben „‹ Zurück“ und Symbol-Knöpfe,
 * darunter der Titel (H1, linksbündig) mit optional einer kleinen
 * Hauptaktion rechts daneben. Ohne Zurück-Ziel stehen die Symbole in der
 * Titelzeile. Gehört in `Scaffold(topBar = …)`; berücksichtigt die
 * Statusleiste selbst.
 */
@Composable
fun MhKopf(
    titel: String,
    modifier: Modifier = Modifier,
    onZurueck: (() -> Unit)? = null,
    zurueckText: String = "Zurück",
    untertitel: String? = null,
    symbole: @Composable RowScope.() -> Unit = {},
    hauptaktion: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(Mh.farben.bg)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .padding(horizontal = Mh.rand),
    ) {
        if (onZurueck != null) {
            Row(
                Modifier.fillMaxWidth().height(56.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MhZurueck(zurueckText, onZurueck)
                Spacer(Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically, content = symbole)
            }
        } else {
            Spacer(Modifier.height(Mh.abstand2))
        }
        Row(
            Modifier.fillMaxWidth().heightIn(min = Mh.touch),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Mh.abstand2),
        ) {
            Text(
                titel,
                style = MhTypo.h1,
                color = Mh.farben.textStrong,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            if (hauptaktion != null) hauptaktion()
            if (onZurueck == null) Row(verticalAlignment = Alignment.CenterVertically, content = symbole)
        }
        if (untertitel != null) {
            Text(untertitel, style = MhTypo.small, color = Mh.farben.textSecondary)
        }
        Spacer(Modifier.height(Mh.abstand3))
    }
}

/** „‹ Zurück“ als Link in Minze. */
@Composable
fun MhZurueck(text: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        contentPadding = PaddingValues(start = 0.dp, end = Mh.abstand2),
        colors = ButtonDefaults.textButtonColors(contentColor = Mh.farben.link),
        shape = RoundedCornerShape(Mh.radiusMd),
    ) {
        MhSymbol(R.drawable.ic_mh_back, null, groesse = Mh.symbol)
        Spacer(Modifier.width(2.dp))
        Text(text, style = MhTypo.button)
    }
}

// ── Buttons ─────────────────────────────────────────────────────────────────

private val buttonForm = RoundedCornerShape(10.dp)

/** Die eine Hauptaktion einer Ansicht (`.btn-primary`). */
@Composable
fun MhHauptButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes symbol: Int? = null,
    enabled: Boolean = true,
    klein: Boolean = false,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = if (klein) 36.dp else Mh.touch),
        shape = buttonForm,
        contentPadding = PaddingValues(horizontal = if (klein) Mh.abstand3 else Mh.abstand4),
        colors = ButtonDefaults.buttonColors(
            containerColor = Mh.farben.primary,
            contentColor = Mh.farben.onPrimary,
            disabledContainerColor = Mh.farben.surfaceSubtle,
            disabledContentColor = Mh.farben.textDisabled,
        ),
        elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
    ) {
        ButtonInhalt(text, symbol)
    }
}

/** Zweitaktion (`.btn-secondary`): transparent mit Haarlinie. */
@Composable
fun MhZweitButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes symbol: Int? = null,
    enabled: Boolean = true,
    klein: Boolean = false,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = if (klein) 36.dp else Mh.touch),
        shape = buttonForm,
        contentPadding = PaddingValues(horizontal = if (klein) Mh.abstand3 else Mh.abstand4),
        border = BorderStroke(1.dp, if (enabled) Mh.farben.borderStrong else Mh.farben.border),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.Transparent,
            contentColor = Mh.farben.textStrong,
            disabledContentColor = Mh.farben.textDisabled,
        ),
    ) {
        ButtonInhalt(text, symbol)
    }
}

/** Leise Aktion (`.btn-ghost`): nur Linkfarbe. */
@Composable
fun MhTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes symbol: Int? = null,
    enabled: Boolean = true,
    farbe: Color = Mh.farben.link,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = buttonForm,
        contentPadding = PaddingValues(horizontal = Mh.abstand3),
        colors = ButtonDefaults.textButtonColors(contentColor = farbe, disabledContentColor = Mh.farben.textDisabled),
    ) {
        ButtonInhalt(text, symbol, symbolGroesse = Mh.symbolKlein)
    }
}

/** Löschen/Rückgängig (`.btn-danger`): nur rote Schrift. */
@Composable
fun MhLoeschenButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes symbol: Int? = null,
    enabled: Boolean = true,
) = MhTextButton(text, onClick, modifier, symbol, enabled, farbe = Mh.farben.danger)

@Composable
private fun ButtonInhalt(text: String, @DrawableRes symbol: Int?, symbolGroesse: Dp = Mh.symbol) {
    if (symbol != null) {
        MhSymbol(symbol, null, groesse = symbolGroesse)
        Spacer(Modifier.width(Mh.abstand2))
    }
    Text(text, style = MhTypo.button, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

// ── Segmentschalter, Reiter, Auswahl ────────────────────────────────────────

/**
 * Segmentschalter (`.segmented`) für 2–4 sich ausschließende Ansichten,
 * z. B. „Schwangerschaft | Entwicklung“.
 */
@Composable
fun MhSegmente(
    optionen: List<String>,
    gewaehlt: Int,
    onWahl: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val f = Mh.farben
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Mh.radiusMd))
            .background(f.surfaceSubtle)
            .border(1.dp, f.border, RoundedCornerShape(Mh.radiusMd))
            .padding(horizontal = 2.dp)
            .selectableGroup(),
    ) {
        optionen.forEachIndexed { i, text ->
            val aktiv = i == gewaehlt
            // Tippfläche = volle Höhe (48 dp); die sichtbare Form sitzt 3 dp
            // eingerückt darin, 2 dp Abstand zum Nachbarn wie im Web.
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = Mh.touch)
                    .selectable(selected = aktiv, role = Role.Tab, onClick = { onWahl(i) })
                    .padding(horizontal = 1.dp, vertical = 3.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .matchParentSize()
                        .then(if (aktiv && !f.dunkel) Modifier.shadow(1.dp, RoundedCornerShape(7.dp)) else Modifier)
                        .clip(RoundedCornerShape(7.dp))
                        .background(if (aktiv) f.segmentOn else Color.Transparent),
                )
                Text(
                    text,
                    style = MhTypo.label,
                    color = if (aktiv) f.textStrong else f.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = Mh.abstand3),
                )
            }
        }
    }
}

/**
 * Reiter (`.tabs`): Navigation zwischen Ansichten einer App. Aktiv:
 * Textfarbe + 2 dp Linie in primary, darunter eine Haarlinie.
 */
@Composable
fun MhReiter(
    optionen: List<String>,
    gewaehlt: Int,
    onWahl: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val f = Mh.farben
    Box(modifier.fillMaxWidth()) {
        HorizontalDivider(Modifier.align(Alignment.BottomStart), thickness = 1.dp, color = f.border)
        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(Mh.abstand5),
        ) {
            optionen.forEachIndexed { i, text ->
                val aktiv = i == gewaehlt
                Box(
                    Modifier
                        .heightIn(min = Mh.touch)
                        .selectable(selected = aktiv, role = Role.Tab, onClick = { onWahl(i) }),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text,
                        style = MhTypo.button,
                        color = if (aktiv) f.textStrong else f.textSecondary,
                        modifier = Modifier.padding(vertical = Mh.abstand3),
                    )
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(if (aktiv) f.primary else Color.Transparent),
                    )
                }
            }
        }
    }
}

/**
 * Auswahl-Option (`.choice`) für Formulare: Seite, Kind, Intensität.
 * Haarlinie; gewählt `selectedBg` + `onSelected`. Optional ein Farbpunkt.
 */
@Composable
fun MhAuswahl(
    text: String,
    gewaehlt: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    punkt: Color? = null,
    enabled: Boolean = true,
) {
    val f = Mh.farben
    val form = RoundedCornerShape(Mh.radiusSm)
    Row(
        modifier
            .minimumInteractiveComponentSize()
            .heightIn(min = 36.dp)
            .clip(form)
            .background(if (gewaehlt) f.selectedBg else Color.Transparent)
            .then(if (gewaehlt) Modifier else Modifier.border(1.dp, f.borderStrong, form))
            .selectable(selected = gewaehlt, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = Mh.abstand3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (punkt != null) {
            MhPunkt(punkt)
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text,
            style = MhTypo.label.copy(fontWeight = if (gewaehlt) FontWeight.Medium else FontWeight.Normal),
            color = when {
                !enabled -> f.textDisabled
                gewaehlt -> f.onSelected
                else -> f.text
            },
            maxLines = 1,
        )
    }
}

// ── Karten, Listen, Gruppen ─────────────────────────────────────────────────

/** Karte (`.card`) – nur für echte Objekte, z. B. einen Tagebucheintrag. */
@Composable
fun MhKarte(
    modifier: Modifier = Modifier,
    innen: Dp = Mh.abstand4,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val f = Mh.farben
    val form = RoundedCornerShape(Mh.radiusLg)
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            shape = form,
            color = f.surface,
            contentColor = f.text,
            border = BorderStroke(1.dp, f.border),
        ) { Column(Modifier.padding(innen), content = content) }
    } else {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = form,
            color = f.surface,
            contentColor = f.text,
            border = BorderStroke(1.dp, f.border),
        ) { Column(Modifier.padding(innen), content = content) }
    }
}

/** Haarlinie zwischen Listenzeilen und Abschnitten. */
@Composable
fun MhTrennlinie(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier, thickness = 1.dp, color = Mh.farben.border)
}

/**
 * Listenzeile (`.list-row`): ab 52 dp, optional Symbol links, Meta rechts
 * und ein Pfeil, wenn sie zu einer anderen Ansicht führt.
 */
@Composable
fun MhListenZeile(
    titel: String,
    modifier: Modifier = Modifier,
    untertitel: String? = null,
    meta: String? = null,
    @DrawableRes symbol: Int? = null,
    symbolFarbe: Color = Mh.farben.textSecondary,
    punkt: Color? = null,
    pfeil: Boolean = false,
    titelFarbe: Color = Mh.farben.textStrong,
    onClick: (() -> Unit)? = null,
    rechts: (@Composable RowScope.() -> Unit)? = null,
) {
    val f = Mh.farben
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = Mh.abstand3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Mh.abstand3),
    ) {
        if (symbol != null) MhSymbol(symbol, null, farbe = symbolFarbe)
        if (punkt != null) MhPunkt(punkt)
        Column(Modifier.weight(1f)) {
            Text(titel, style = MhTypo.body.copy(fontWeight = FontWeight.Medium), color = titelFarbe)
            if (untertitel != null) Text(untertitel, style = MhTypo.small, color = f.textSecondary)
        }
        if (meta != null) {
            Text(meta, style = MhTypo.small.copy(fontFeatureSettings = "tnum"), color = f.textSecondary)
        }
        if (rechts != null) rechts()
        if (pfeil) MhSymbol(R.drawable.ic_mh_chevron_right, null, groesse = Mh.symbolKlein + 2.dp, farbe = f.textTertiary)
    }
}

/** Zeilen mit Haarlinie dazwischen (`.list`). */
@Composable
fun <T> MhListe(eintraege: List<T>, modifier: Modifier = Modifier, zeile: @Composable (T) -> Unit) {
    Column(modifier.fillMaxWidth()) {
        eintraege.forEachIndexed { i, e ->
            if (i > 0) MhTrennlinie()
            zeile(e)
        }
    }
}

/** Gruppenlabel (`.group-label`): Caption in Versalien, Dritttextfarbe. */
@Composable
fun MhGruppenLabel(
    text: String,
    modifier: Modifier = Modifier,
    rechts: String? = null,
) {
    Row(
        modifier.fillMaxWidth().padding(top = Mh.abstand6, bottom = Mh.abstand2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text.uppercase(),
            style = MhTypo.caption,
            color = Mh.farben.textTertiary,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        if (rechts != null) Text(rechts, style = MhTypo.small, color = Mh.farben.textSecondary)
    }
}

// ── Zahlen, Balken, Punkte ──────────────────────────────────────────────────

/** Statistik (`.stat-value` + `.stat-label`): Wert dünn in Textfarbe. */
@Composable
fun MhStat(
    wert: String,
    label: String,
    modifier: Modifier = Modifier,
    einheit: String? = null,
) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(wert, style = MhTypo.number, color = Mh.farben.textStrong)
            if (einheit != null) {
                Text(
                    einheit,
                    style = MhTypo.small,
                    color = Mh.farben.textSecondary,
                    modifier = Modifier.padding(start = 2.dp, bottom = 2.dp),
                )
            }
        }
        Text(label, style = MhTypo.small, color = Mh.farben.textSecondary)
    }
}

/** Balken (`.bar`): 4 dp, Spur `dataTrack`, Füllung `data1/2/3`. */
@Composable
fun MhBalken(anteil: Float, modifier: Modifier = Modifier, farbe: Color = Mh.farben.data1) {
    Box(
        modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(CircleShape)
            .background(Mh.farben.dataTrack),
    ) {
        Box(
            Modifier
                .fillMaxWidth(anteil.coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(CircleShape)
                .background(farbe),
        )
    }
}

/** Punkt (`.dot`): 10 dp, kennzeichnet eine Kategorie – immer mit Text. */
@Composable
fun MhPunkt(farbe: Color, modifier: Modifier = Modifier, groesse: Dp = 10.dp) {
    Box(modifier.size(groesse).clip(CircleShape).background(farbe))
}

// ── Marken, Hinweise, Leerzustand ───────────────────────────────────────────

/** Marke (`.chip`): Pille in `tint`, z. B. „Gewicht 9,3 kg“. */
@Composable
fun MhMarke(text: String, modifier: Modifier = Modifier, @DrawableRes symbol: Int? = null) {
    Row(
        modifier
            .heightIn(min = 26.dp)
            .clip(CircleShape)
            .background(Mh.farben.tint)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (symbol != null) {
            MhSymbol(symbol, null, groesse = 14.dp, farbe = Mh.farben.onTint)
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = MhTypo.chip, color = Mh.farben.onTint)
    }
}

/** Kleines Hinweis-Badge (`.badge`), sparsam. */
@Composable
fun MhBadge(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MhTypo.caption,
        color = Mh.farben.onHighlight,
        modifier = modifier
            .clip(CircleShape)
            .background(Mh.farben.highlight)
            .padding(horizontal = 8.dp, vertical = 1.dp),
    )
}

enum class MhHinweisArt { Info, Erfolg, Warnung, Fehler }

/** Hinweis (`.notice`): getönte Fläche, Symbol in Semantikfarbe, Text in Textfarbe. */
@Composable
fun MhHinweis(
    text: String,
    modifier: Modifier = Modifier,
    titel: String? = null,
    art: MhHinweisArt = MhHinweisArt.Info,
    @DrawableRes symbol: Int = when (art) {
        MhHinweisArt.Info -> R.drawable.ic_mh_info
        MhHinweisArt.Erfolg -> R.drawable.ic_mh_check
        MhHinweisArt.Warnung, MhHinweisArt.Fehler -> R.drawable.ic_mh_alert
    },
    aktion: (@Composable () -> Unit)? = null,
) {
    val f = Mh.farben
    val (grund, vorne) = when (art) {
        MhHinweisArt.Info -> f.infoBg to f.infoFg
        MhHinweisArt.Erfolg -> f.successBg to f.successFg
        MhHinweisArt.Warnung -> f.warningBg to f.warningFg
        MhHinweisArt.Fehler -> f.errorBg to f.errorFg
    }
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Mh.radiusMd))
            .background(grund)
            .padding(horizontal = Mh.abstand4, vertical = Mh.abstand3),
        horizontalArrangement = Arrangement.spacedBy(Mh.abstand3),
    ) {
        MhSymbol(symbol, null, farbe = vorne, modifier = Modifier.padding(top = 1.dp))
        Column(Modifier.weight(1f)) {
            if (titel != null) Text(titel, style = MhTypo.label, color = f.textStrong)
            Text(text, style = MhTypo.small, color = f.text)
            if (aktion != null) aktion()
        }
    }
}

/** Leerzustand (`.empty`): zentriert, Nebentext, ein Satz, ohne Emoji. */
@Composable
fun MhLeer(
    text: String,
    modifier: Modifier = Modifier,
    aktion: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = Mh.abstand4, vertical = Mh.abstand8),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, style = MhTypo.small, color = Mh.farben.textSecondary, textAlign = TextAlign.Center)
        if (aktion != null) {
            Spacer(Modifier.height(Mh.abstand2))
            aktion()
        }
    }
}

/** Ladeanzeige: dünner Kreis in primary. */
@Composable
fun MhLaden(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Mh.farben.primary, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
    }
}

// ── Formulare ───────────────────────────────────────────────────────────────

/** Feld (`.field`): Label über dem Eingabefeld, Hinweis darunter. */
@Composable
fun MhFeld(
    label: String,
    modifier: Modifier = Modifier,
    zusatz: String? = null,
    hinweis: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(label, style = MhTypo.label, color = Mh.farben.textStrong)
            if (zusatz != null) {
                Spacer(Modifier.width(Mh.abstand2))
                Text(zusatz, style = MhTypo.small, color = Mh.farben.textSecondary)
            }
        }
        content()
        if (hinweis != null) Text(hinweis, style = MhTypo.small, color = Mh.farben.textSecondary)
    }
}

/** Farben für OutlinedTextField: Grund `fieldBg`, Rand `borderStrong`, Fokus `focusRing`. */
@Composable
fun mhFeldFarben(): TextFieldColors {
    val f = Mh.farben
    return OutlinedTextFieldDefaults.colors(
        focusedTextColor = f.text,
        unfocusedTextColor = f.text,
        disabledTextColor = f.textDisabled,
        focusedContainerColor = f.fieldBg,
        unfocusedContainerColor = f.fieldBg,
        disabledContainerColor = f.surfaceSubtle,
        focusedBorderColor = f.focusRing,
        unfocusedBorderColor = f.borderStrong,
        disabledBorderColor = f.border,
        cursorColor = f.primary,
        focusedLabelColor = f.textSecondary,
        unfocusedLabelColor = f.textSecondary,
        focusedPlaceholderColor = f.textTertiary,
        unfocusedPlaceholderColor = f.textTertiary,
        focusedTrailingIconColor = f.textSecondary,
        unfocusedTrailingIconColor = f.textSecondary,
        focusedSupportingTextColor = f.textSecondary,
        unfocusedSupportingTextColor = f.textSecondary,
        errorBorderColor = f.danger,
        errorSupportingTextColor = f.errorFg,
    )
}

/** Form der Eingabefelder (Radius 10). */
val MhFeldForm = RoundedCornerShape(10.dp)

/** Inhalt mit Seitenrand und Abschnittsabstand – für scrollende Spalten. */
val MhInhaltRand = PaddingValues(start = Mh.rand, end = Mh.rand, bottom = Mh.abstand8)
