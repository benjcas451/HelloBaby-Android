package ch.tschir.hellobaby.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import ch.tschir.hellobaby.PapierkorbEintrag
import ch.tschir.hellobaby.R
import ch.tschir.hellobaby.data.ApiService
import ch.tschir.hellobaby.data.AppSettings
import ch.tschir.hellobaby.data.meldung
import ch.tschir.hellobaby.isVideoFile
import ch.tschir.hellobaby.kDiaries
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Papierkorb des Servers: gelöschte Einträge der letzten Tage ansehen,
 * zurückholen oder endgültig entfernen.
 *
 * Zeigt beide Tagebücher zugleich — wer etwas vermisst, soll nicht erst
 * umschalten müssen.
 */
@Composable
fun PapierkorbScreen(
    settings: AppSettings,
    api: ApiService,
    onZurueck: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var eintraege by remember { mutableStateOf<List<PapierkorbEintrag>>(emptyList()) }
    var laedt by remember { mutableStateOf(true) }
    var fehler by remember { mutableStateOf<String?>(null) }
    var ladeZaehler by remember { mutableIntStateOf(0) }
    /** Kennung des Eintrags, an dem gerade etwas läuft: sperrt dessen Knöpfe. */
    var inArbeit by remember { mutableStateOf<String?>(null) }
    var endgueltig by remember { mutableStateOf<PapierkorbEintrag?>(null) }
    /** Medien ohne Eintrag, für die gerade das Tagebuch gewählt wird. */
    var tagebuchWahl by remember { mutableStateOf<PapierkorbEintrag?>(null) }

    LaunchedEffect(ladeZaehler) {
        laedt = true
        fehler = null
        runCatching { api.getPapierkorb() }
            .onSuccess { eintraege = it }
            .onFailure { fehler = it.meldung() }
        laedt = false
    }

    fun ausfuehren(eintrag: PapierkorbEintrag, erfolg: String, aktion: suspend () -> Unit) {
        if (inArbeit != null) return
        inArbeit = eintrag.kennung
        scope.launch {
            runCatching { aktion() }
                .onSuccess {
                    // Sofort aus der Liste nehmen; das Neuladen bestätigt es.
                    eintraege = eintraege.filterNot { it.kennung == eintrag.kennung }
                    ladeZaehler++
                    snackbar.showSnackbar(erfolg)
                }
                .onFailure { snackbar.showSnackbar("Das hat nicht geklappt: ${it.meldung()}") }
            inArbeit = null
        }
    }

    Scaffold(
        topBar = {
            MhKopf(
                titel = "Papierkorb",
                onZurueck = onZurueck,
                untertitel = "Gelöschte Einträge bleiben 30 Tage hier.",
                symbole = { MhSymbolButton(R.drawable.ic_mh_refresh, "Neu laden", { ladeZaehler++ }) },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = Mh.farben.bg,
    ) { innen ->
        Box(Modifier.padding(innen).fillMaxSize()) {
            when {
                laedt && eintraege.isEmpty() -> LadeAnsicht()
                fehler != null -> FehlerAnsicht("Der Papierkorb konnte nicht geladen werden. $fehler") { ladeZaehler++ }
                eintraege.isEmpty() -> LeerAnsicht("Der Papierkorb ist leer")
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = Mh.rand, end = Mh.rand, top = Mh.abstand2, bottom = Mh.abstand8),
                    verticalArrangement = Arrangement.spacedBy(Mh.abstand3),
                ) {
                    items(eintraege, key = { it.kennung }) { eintrag ->
                        PapierkorbKarte(
                            eintrag = eintrag,
                            api = api,
                            gesperrt = inArbeit != null,
                            onWiederherstellen = {
                                if (eintrag.diary.isEmpty()) {
                                    // Ohne Eintrag weiß niemand mehr, in welchem
                                    // Tagebuch die Medien standen.
                                    tagebuchWahl = eintrag
                                } else {
                                    ausfuehren(eintrag, "Eintrag wiederhergestellt") {
                                        api.papierkorbWiederherstellen(eintrag.kennung)
                                    }
                                }
                            },
                            onEndgueltig = { endgueltig = eintrag },
                        )
                    }
                }
            }
        }
    }

    tagebuchWahl?.let { eintrag ->
        AlertDialog(
            onDismissRequest = { tagebuchWahl = null },
            title = { Text("Wiederherstellen", style = MhTypo.h2) },
            text = {
                Column {
                    Text(
                        "Zu diesen Medien gibt es keinen Text mehr. In welches Tagebuch soll der Eintrag ohne Text?",
                        style = MhTypo.body,
                    )
                    Spacer(Modifier.height(Mh.abstand2))
                    MhListe(kDiaries.values.toList()) { diary ->
                        MhListenZeile(
                            diary.label,
                            meta = if (diary.id == eintrag.diaryVorschlag) "Vorschlag" else null,
                            pfeil = true,
                            onClick = {
                                tagebuchWahl = null
                                ausfuehren(eintrag, "Medien als Eintrag ohne Text wiederhergestellt") {
                                    api.papierkorbWiederherstellen(
                                        eintrag.kennung,
                                        diary = diary.id,
                                        vonName = settings.selectedUser,
                                    )
                                }
                            },
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = { MhTextButton("Abbrechen", { tagebuchWahl = null }) },
        )
    }

    endgueltig?.let { eintrag ->
        AlertDialog(
            onDismissRequest = { endgueltig = null },
            title = { Text("Endgültig löschen", style = MhTypo.h2) },
            text = {
                Text(
                    "Diesen Eintrag samt Medien endgültig löschen? Das lässt sich nicht rückgängig machen.",
                    style = MhTypo.body,
                )
            },
            confirmButton = {
                MhLoeschenButton("Endgültig löschen", {
                    endgueltig = null
                    ausfuehren(eintrag, "Eintrag endgültig gelöscht") {
                        api.papierkorbLoeschen(eintrag.kennung)
                    }
                })
            },
            dismissButton = { MhTextButton("Abbrechen", { endgueltig = null }) },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PapierkorbKarte(
    eintrag: PapierkorbEintrag,
    api: ApiService,
    gesperrt: Boolean,
    onWiederherstellen: () -> Unit,
    onEndgueltig: () -> Unit,
) {
    val f = Mh.farben
    val diary = kDiaries[eintrag.diary]
    val zeile = eintrag.eintrag
    MhKarte {
        Text(papierkorbTitel(eintrag), style = MhTypo.h3, color = f.textStrong)
        if (zeile.vonName.isNotEmpty()) {
            Text("von ${zeile.vonName}", style = MhTypo.small, color = f.textSecondary)
        }
        Spacer(Modifier.height(Mh.abstand2))
        if (diary == null) {
            Text(papierkorbOhneEintrag(eintrag), style = MhTypo.small, color = f.text)
        } else {
            for (feld in diary.fields.filter { it.textarea }) {
                val wert = zeile.field(feld.key)
                if (wert.isEmpty()) continue
                Text(wert, style = MhTypo.body, color = f.text, modifier = Modifier.padding(bottom = 2.dp))
            }
            val zahlen = diary.fields.filter { it.numeric }.mapNotNull { feld ->
                val wert = zeile.field(feld.key).trim()
                if (wert.isEmpty() || wert == "0") null
                else "${feld.label} ${deutscheZahl(wert)}${if (feld.unit.isNotEmpty()) " ${feld.unit}" else ""}"
            }
            if (zahlen.isNotEmpty()) {
                Spacer(Modifier.height(Mh.abstand2))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) { zahlen.forEach { MhMarke(it) } }
            }
        }
        if (eintrag.medien > 0) {
            Spacer(Modifier.height(Mh.abstand2))
            Text(papierkorbMedien(eintrag.medien, eintrag.bytes), style = MhTypo.small, color = f.textSecondary)
        }
        // Vorschaubilder: zeigen, was wiederhergestellt oder gelöscht wird.
        if (eintrag.dateien.isNotEmpty()) {
            Spacer(Modifier.height(Mh.abstand2))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                for (datei in eintrag.dateien) {
                    Box(
                        Modifier.size(72.dp).clip(RoundedCornerShape(Mh.radiusSm)).background(f.surfaceSubtle),
                    ) {
                        AsyncImage(
                            model = api.papierkorbVorschauUrl(eintrag.kennung, datei),
                            contentDescription = if (isVideoFile(datei)) "Standbild des Videos" else "Foto",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                        if (isVideoFile(datei)) VideoMarke(Modifier.align(Alignment.Center))
                    }
                }
            }
        }
        Spacer(Modifier.height(Mh.abstand2))
        Text(papierkorbFrist(eintrag), style = MhTypo.small, color = f.textTertiary)
        Spacer(Modifier.height(Mh.abstand2))
        Row(horizontalArrangement = Arrangement.spacedBy(Mh.abstand2), verticalAlignment = Alignment.CenterVertically) {
            if (eintrag.wiederherstellbar) {
                MhZweitButton("Wiederherstellen", onWiederherstellen, symbol = R.drawable.ic_mh_undo, enabled = !gesperrt)
            }
            MhLoeschenButton("Endgültig löschen", onEndgueltig, enabled = !gesperrt)
        }
    }
}

// ── Texte (ohne Compose, damit sie sich prüfen lassen) ──────────────────────

/** Überschrift einer Karte: Tagebuch und Tag des Eintrags. */
internal fun papierkorbTitel(eintrag: PapierkorbEintrag): String {
    val tag = eintrag.eintrag.kalenderDatum
    val datum = if (tag.isEmpty()) "" else anzeigeDatum(tag)
    val diary = kDiaries[eintrag.diary]
        ?: return if (datum.isEmpty()) "Medien ohne Eintrag" else "Medien ohne Eintrag vom $datum"
    return "${diary.label}: Eintrag vom $datum"
}

/** Hinweis auf Karten ohne Eintrag: was Wiederherstellen dort bedeutet. */
internal fun papierkorbOhneEintrag(eintrag: PapierkorbEintrag): String =
    if (eintrag.wiederherstellbar) {
        "Zu diesen Medien gibt es keinen Text mehr. Wiederherstellen legt einen Eintrag ohne Text an."
    } else {
        "Der Ordner ist leer; hier gibt es nichts wiederherzustellen."
    }

internal fun papierkorbMedien(anzahl: Int, bytes: Long): String {
    val groesse = String.format(Locale.GERMAN, "%.1f MB", bytes / 1048576.0)
    return if (anzahl == 1) "1 Foto/Video ($groesse)" else "$anzahl Fotos/Videos ($groesse)"
}

private val serverZeit = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
private val anzeigeZeit = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")

/** „Gelöscht am … · wird am … endgültig entfernt (noch n Tage)“. */
internal fun papierkorbFrist(eintrag: PapierkorbEintrag): String {
    val geloescht = runCatching { LocalDateTime.parse(eintrag.geloescht, serverZeit).format(anzeigeZeit) }
        .getOrDefault(eintrag.geloescht)
    val tage = if (eintrag.tageUebrig == 1) "noch 1 Tag" else "noch ${eintrag.tageUebrig} Tage"
    return "Gelöscht am $geloescht · wird am ${anzeigeDatum(eintrag.verfaellt)} endgültig entfernt ($tage)"
}
