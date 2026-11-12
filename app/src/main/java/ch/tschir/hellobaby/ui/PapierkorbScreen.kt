package ch.tschir.hellobaby.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ch.tschir.hellobaby.PapierkorbEintrag
import ch.tschir.hellobaby.data.ApiService
import ch.tschir.hellobaby.data.meldung
import ch.tschir.hellobaby.kDiaries
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
                .onFailure { snackbar.showSnackbar("Fehler: ${it.meldung()}") }
            inArbeit = null
        }
    }

    Scaffold(
        topBar = {
            HbTopBar(titel = "Papierkorb", onZurueck = onZurueck) {
                IconButton(onClick = { ladeZaehler++ }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Neu laden")
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innen ->
        Column(modifier = Modifier.padding(innen).fillMaxSize()) {
            when {
                laedt && eintraege.isEmpty() -> LadeAnsicht()
                fehler != null -> FehlerAnsicht("Fehler: $fehler") { ladeZaehler++ }
                eintraege.isEmpty() -> LeerAnsicht(
                    Icons.Filled.DeleteOutline,
                    "Der Papierkorb ist leer",
                    "Gelöschte Einträge bleiben 30 Tage hier liegen und verschwinden danach endgültig.",
                )
                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(eintraege, key = { it.kennung }) { eintrag ->
                        PapierkorbKarte(
                            eintrag = eintrag,
                            gesperrt = inArbeit != null,
                            onWiederherstellen = {
                                ausfuehren(eintrag, "Der Eintrag wurde wiederhergestellt.") {
                                    api.papierkorbWiederherstellen(eintrag.kennung)
                                }
                            },
                            onEndgueltig = { endgueltig = eintrag },
                        )
                    }
                }
            }
        }
    }

    endgueltig?.let { eintrag ->
        AlertDialog(
            onDismissRequest = { endgueltig = null },
            title = { Text("Endgültig löschen") },
            text = {
                Text("Diesen Eintrag samt Medien endgültig löschen? Das lässt sich nicht rückgängig machen.")
            },
            confirmButton = {
                TextButton(onClick = {
                    endgueltig = null
                    ausfuehren(eintrag, "Der Eintrag wurde endgültig gelöscht.") {
                        api.papierkorbLoeschen(eintrag.kennung)
                    }
                }) { Text("Endgültig löschen", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { endgueltig = null }) { Text("Abbrechen") }
            },
        )
    }
}

@Composable
private fun PapierkorbKarte(
    eintrag: PapierkorbEintrag,
    gesperrt: Boolean,
    onWiederherstellen: () -> Unit,
    onEndgueltig: () -> Unit,
) {
    val diary = kDiaries[eintrag.diary]
    val zeile = eintrag.eintrag
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                papierkorbTitel(eintrag),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            if (zeile.vonName.isNotEmpty()) {
                Text(
                    "von ${zeile.vonName}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))

            if (diary == null) {
                Text(
                    "Zu diesen Dateien gab es keinen Eintrag mehr; sie lassen sich nicht wiederherstellen.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                for (feld in diary.fields) {
                    val wert = zeile.field(feld.key)
                    if (wert.isEmpty() || (feld.numeric && wert == "0")) continue
                    val einheit = if (feld.unit.isNotEmpty()) " ${feld.unit}" else ""
                    Text(
                        "${feld.label}: $wert$einheit",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 2.dp),
                    )
                }
            }
            if (eintrag.medien > 0) {
                Text(
                    papierkorbMedien(eintrag.medien, eintrag.bytes),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                papierkorbFrist(eintrag),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (eintrag.wiederherstellbar) {
                    Button(
                        onClick = onWiederherstellen,
                        enabled = !gesperrt,
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Icon(Icons.Filled.RestoreFromTrash, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Wiederherstellen")
                    }
                }
                OutlinedButton(
                    onClick = onEndgueltig,
                    enabled = !gesperrt,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Icon(
                        Icons.Filled.DeleteForever,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Endgültig löschen", color = MaterialTheme.colorScheme.error)
                }
            }
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
