package ch.tschir.hellobaby.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ch.tschir.hellobaby.Entry
import ch.tschir.hellobaby.R
import ch.tschir.hellobaby.data.ApiService
import ch.tschir.hellobaby.isImageFile
import ch.tschir.hellobaby.isLocalMediaSource
import ch.tschir.hellobaby.isVideoFile
import ch.tschir.hellobaby.kDiaries
import coil3.compose.AsyncImage
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// ── Medienquellen ───────────────────────────────────────────────────────────

/** Lokale absolute Pfade bleiben Dateipfade, Serverpfade werden zu URLs. */
fun ApiService.mediaSource(path: String, download: Boolean = false): Any =
    if (isLocalMediaSource(path)) java.io.File(path) else mediaUrl(path, download)

fun ApiService.thumbnailSource(path: String, width: Int = 400): Any =
    if (isLocalMediaSource(path)) java.io.File(path) else thumbUrl(path, width)

// ── Datums-Helfer ───────────────────────────────────────────────────────────

private val deutschesDatum = DateTimeFormatter.ofPattern("dd.MM.yyyy")
private val wochentagDatum = DateTimeFormatter.ofPattern("EEEE, dd.MM.yyyy", Locale.GERMAN)

/**
 * Tag aus ISO („2026-11-15“, lokale Ablage) oder deutscher Schreibweise
 * („15.11.2026“, liefert stats.php des Servers); null, wenn es kein Datum
 * ist (z. B. „Keine Einträge“).
 */
fun tagLesen(text: String): LocalDate? {
    val t = text.trim()
    return runCatching { LocalDate.parse(t) }.getOrNull()
        ?: runCatching { LocalDate.parse(t, deutschesDatum) }.getOrNull()
}

fun anzeigeDatum(isoDate: String): String =
    tagLesen(isoDate)?.format(deutschesDatum) ?: isoDate

fun wochentagsDatum(isoDate: String): String =
    runCatching { LocalDate.parse(isoDate).format(wochentagDatum) }.getOrDefault(isoDate)

/** Uhrzeit „07:01“ aus `created_at` (Server „2026-11-15 07:01:00“, lokal ISO). */
fun uhrzeit(createdAt: String): String =
    Regex("""[ T](\d{2}):(\d{2})""").find(createdAt)?.let { "${it.groupValues[1]}:${it.groupValues[2]}" }.orEmpty()

/** Zahl in deutscher Schreibweise („9.3“ → „9,3“). */
fun deutscheZahl(wert: String): String = wert.trim().replace('.', ',')

// ── Meldungen über Bildschirmgrenzen ────────────────────────────────────────

/**
 * Kurze Meldung für die nächste Ansicht („Eintrag gespeichert“), wenn die
 * auslösende Ansicht sich gleich schließt. Gepuffert, bis sie jemand abholt;
 * jede Meldung erscheint genau einmal.
 */
object AppMeldung {
    private val kanal = Channel<String>(Channel.BUFFERED)
    val fluss: Flow<String> = kanal.receiveAsFlow()
    fun senden(text: String) {
        kanal.trySend(text)
    }
}

// ── Zustands-Ansichten ──────────────────────────────────────────────────────

@Composable
fun FehlerAnsicht(meldung: String, onErneut: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = Mh.rand, vertical = Mh.abstand8),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        MhHinweis(meldung, art = MhHinweisArt.Fehler)
        Spacer(Modifier.height(Mh.abstand4))
        MhZweitButton("Erneut versuchen", onErneut, symbol = R.drawable.ic_mh_refresh)
    }
}

@Composable
fun LadeAnsicht() = MhLaden()

@Composable
fun LeerAnsicht(text: String, untertitel: String? = null) {
    Column(Modifier.fillMaxWidth()) {
        MhLeer(text)
        if (untertitel != null) {
            Text(
                untertitel,
                style = MhTypo.small,
                color = Mh.farben.textTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Mh.abstand4),
            )
        }
    }
}

// ── Eintrags-Karte ──────────────────────────────────────────────────────────

/**
 * Karte eines Tagebucheintrags wie im Web: Kopfzeile „[Datum ·] 07:01 · Name“
 * mit Stern, Haupttext ohne Label, weitere Texte mit kleinem Label, Zahlen
 * als Marken, Fotos als Raster, „Löschen“ als roter Text.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EntryCard(
    entry: Entry,
    api: ApiService,
    activeDiary: String,
    onGalerie: (String) -> Unit,
    onDeleted: (() -> Unit)?,
    onMeldung: (String) -> Unit,
    mitDatum: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val f = Mh.farben
    val scope = rememberCoroutineScope()
    var favorit by remember(entry.id) { mutableStateOf(entry.favorit) }
    var favLaedt by remember(entry.id) { mutableStateOf(false) }
    var loeschDialog by remember { mutableStateOf(false) }
    val diary = kDiaries[entry.diary.ifEmpty { activeDiary }] ?: kDiaries.values.first()

    MhKarte(modifier = modifier) {
        // Kopfzeile
        Row(verticalAlignment = Alignment.CenterVertically) {
            val teile = buildList {
                if (mitDatum) add(anzeigeDatum(entry.kalenderDatum))
                uhrzeit(entry.createdAt).takeIf { it.isNotEmpty() }?.let(::add)
                entry.vonName.takeIf { it.isNotBlank() }?.let(::add)
            }
            Text(
                teile.joinToString(" · "),
                style = MhTypo.small.copy(fontFeatureSettings = "tnum"),
                color = f.textSecondary,
                modifier = Modifier.weight(1f),
            )
            Box(Modifier.size(Mh.touch), contentAlignment = Alignment.Center) {
                if (favLaedt) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = f.primary, strokeWidth = 2.dp)
                } else {
                    MhSymbolButton(
                        symbol = if (favorit == 1) R.drawable.ic_mh_star_gefuellt else R.drawable.ic_mh_star,
                        beschreibung = if (favorit == 1) "Favorit entfernen" else "Als Favorit markieren",
                        farbe = if (favorit == 1) f.textYellow else f.textSecondary,
                        onClick = {
                            favLaedt = true
                            scope.launch {
                                // Den aktuellen Stand mitgeben: ohne Verbindung
                                // bildet der Client den neuen Wert daraus selbst,
                                // die API liefert ihn sonst erst in ihrer Antwort.
                                runCatching {
                                    api.toggleFavorite(
                                        entry.id,
                                        entry.diary.ifEmpty { activeDiary },
                                        aktuell = favorit,
                                    )
                                }
                                    .onSuccess {
                                        favorit = it
                                        entry.favorit = it
                                    }
                                    .onFailure { onMeldung("Favorit nicht gespeichert: ${it.message}") }
                                favLaedt = false
                            }
                        },
                    )
                }
            }
        }

        // Texte: das Pflichtfeld als Haupttext ohne Label, weitere mit Label.
        val texte = diary.fields.filter { it.textarea && entry.field(it.key).isNotBlank() }
        texte.forEachIndexed { i, feld ->
            if (i > 0 || !feld.required) {
                Spacer(Modifier.height(if (i > 0) Mh.abstand3 else 0.dp))
                Text(feld.label, style = MhTypo.label, color = f.textSecondary)
            }
            Text(entry.field(feld.key), style = MhTypo.body, color = f.text)
        }

        // Zahlen als Marken
        val zahlen = diary.fields.filter { it.numeric }
            .mapNotNull { feld ->
                val wert = entry.field(feld.key).trim()
                if (wert.isEmpty() || wert == "0") null
                else "${feld.label} ${deutscheZahl(wert)}${if (feld.unit.isNotEmpty()) " ${feld.unit}" else ""}"
            }
        if (zahlen.isNotEmpty()) {
            Spacer(Modifier.height(Mh.abstand3))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) { zahlen.forEach { MhMarke(it) } }
        }

        // Fotos als Raster (bis zu drei Vorschaubilder), sonst ein leiser Link.
        val medien = entry.bilderFiles.filter { isImageFile(it) || isVideoFile(it) }
        if (medien.isNotEmpty()) {
            Spacer(Modifier.height(Mh.abstand3))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                medien.take(3).forEachIndexed { i, datei ->
                    val rest = medien.size - 3
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(Mh.radiusSm))
                            .background(f.surfaceSubtle)
                            .clickable { onGalerie(entry.bilder) },
                    ) {
                        AsyncImage(
                            model = api.thumbnailSource("${entry.bilder}/$datei"),
                            contentDescription = if (i == 0) "Fotos ansehen" else null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                        if (isVideoFile(datei)) VideoMarke(Modifier.align(Alignment.Center))
                        if (i == 2 && rest > 0) {
                            Box(
                                Modifier.fillMaxSize().background(Color(0x99000000)),
                                contentAlignment = Alignment.Center,
                            ) { Text("+$rest", style = MhTypo.h3, color = Color.White) }
                        }
                    }
                }
                repeat(3 - medien.take(3).size) { Spacer(Modifier.weight(1f)) }
            }
        } else if (entry.bilder.isNotEmpty()) {
            Spacer(Modifier.height(Mh.abstand2))
            MhTextButton(
                "Fotos ansehen",
                { onGalerie(entry.bilder) },
                symbol = R.drawable.ic_mh_image,
                modifier = Modifier.padding(start = 0.dp),
            )
        }

        if (onDeleted != null) {
            Spacer(Modifier.height(Mh.abstand1))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                MhLoeschenButton("Löschen", { loeschDialog = true })
            }
        }
    }

    if (loeschDialog && onDeleted != null) {
        AlertDialog(
            onDismissRequest = { loeschDialog = false },
            title = { Text("Eintrag löschen", style = MhTypo.h2) },
            text = {
                // Der Server hebt Gelöschtes auf, die lokale Ablage nicht.
                Text(
                    if (api.hatPapierkorb) {
                        "Der Eintrag wandert samt Medien in den Papierkorb und lässt sich dort " +
                            "30 Tage lang wiederherstellen."
                    } else {
                        "Möchtest du diesen Eintrag wirklich löschen?"
                    },
                    style = MhTypo.body,
                )
            },
            confirmButton = {
                MhLoeschenButton("Löschen", {
                    loeschDialog = false
                    scope.launch {
                        runCatching { api.deleteEntry(entry.id, entry.diary.ifEmpty { activeDiary }) }
                            .onSuccess {
                                onMeldung("Eintrag gelöscht")
                                onDeleted()
                            }
                            .onFailure { onMeldung("Löschen fehlgeschlagen: ${it.message}") }
                    }
                })
            },
            dismissButton = { MhTextButton("Abbrechen", { loeschDialog = false }) },
        )
    }
}

/** Kleines Abspielzeichen über Video-Vorschaubildern. */
@Composable
fun VideoMarke(modifier: Modifier = Modifier, gross: Boolean = false) {
    Box(
        modifier
            .size(if (gross) 56.dp else 32.dp)
            .clip(CircleShape)
            .background(Color(0x8A000000)),
        contentAlignment = Alignment.Center,
    ) {
        MhSymbol(R.drawable.ic_mh_play, "Video", farbe = Color.White, groesse = if (gross) 28.dp else 18.dp)
    }
}
