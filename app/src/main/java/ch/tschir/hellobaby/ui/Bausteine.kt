package ch.tschir.hellobaby.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ch.tschir.hellobaby.Entry
import ch.tschir.hellobaby.isLocalMediaSource
import ch.tschir.hellobaby.kDiaries
import ch.tschir.hellobaby.data.ApiService
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

fun anzeigeDatum(isoDate: String): String =
    runCatching { LocalDate.parse(isoDate).format(deutschesDatum) }.getOrDefault(isoDate)

fun wochentagsDatum(isoDate: String): String =
    runCatching { LocalDate.parse(isoDate).format(wochentagDatum) }.getOrDefault(isoDate)

// ── App-Bar / Zustands-Ansichten ────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HbTopBar(
    titel: String,
    onZurueck: (() -> Unit)? = null,
    aktionen: @Composable () -> Unit = {},
) {
    TopAppBar(
        title = {
            Text(titel, fontWeight = FontWeight.Bold)
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
        ),
        navigationIcon = {
            if (onZurueck != null) {
                IconButton(onClick = onZurueck) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                }
            }
        },
        actions = { aktionen() },
    )
}

@Composable
fun FehlerAnsicht(meldung: String, icon: ImageVector = Icons.Filled.ErrorOutline, onErneut: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(16.dp))
        Text(meldung, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onErneut, shape = MaterialTheme.shapes.medium) {
            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Erneut versuchen")
        }
    }
}

@Composable
fun LadeAnsicht() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
fun LeerAnsicht(icon: ImageVector, text: String, untertitel: String? = null) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(12.dp))
        Text(text, textAlign = TextAlign.Center)
        if (untertitel != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                untertitel,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ── Eintrags-Karte ──────────────────────────────────────────────────────────

/**
 * Karte eines Tagebucheintrags: Kopf (erstellt am / von), die diary-
 * spezifischen Felder, Aktionen (Favorit, Galerie, Löschen).
 */
@Composable
fun EntryCard(
    entry: Entry,
    api: ApiService,
    activeDiary: String,
    onGalerie: (String) -> Unit,
    onDeleted: (() -> Unit)?,
    onMeldung: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var favorit by remember(entry.id) { mutableStateOf(entry.favorit) }
    var favLaedt by remember(entry.id) { mutableStateOf(false) }
    var loeschDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Erstellt am ${entry.createdAt}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "von ${entry.vonName}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(8.dp))

            // Diary-Felder gemäß aktiver Konfiguration.
            val diary = kDiaries[activeDiary] ?: kDiaries.values.first()
            for (feld in diary.fields) {
                val wert = entry.field(feld.key)
                if (feld.numeric) {
                    if (wert.isEmpty() || wert == "0") continue
                    val einheit = if (feld.unit.isNotEmpty()) " ${feld.unit}" else ""
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 4.dp),
                    ) {
                        Icon(
                            iconFuer(feld.key),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("${feld.label}: ", fontWeight = FontWeight.Bold)
                        Text("$wert$einheit")
                    }
                } else {
                    if (wert.isEmpty()) continue
                    Column(modifier = Modifier.padding(bottom = 6.dp)) {
                        Text("${feld.label}:", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(2.dp))
                        Text(wert)
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (favLaedt) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    IconButton(onClick = {
                        favLaedt = true
                        scope.launch {
                            runCatching { api.toggleFavorite(entry.id, entry.diary.ifEmpty { activeDiary }) }
                                .onSuccess {
                                    favorit = it
                                    entry.favorit = it
                                }
                                .onFailure { onMeldung("Fehler: ${it.message}") }
                            favLaedt = false
                        }
                    }) {
                        Icon(
                            if (favorit == 1) Icons.Filled.Star else Icons.Filled.StarBorder,
                            contentDescription = if (favorit == 1) "Favorit entfernen" else "Als Favorit markieren",
                            tint = if (favorit == 1) Hb.gold else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (entry.bilder.isNotEmpty()) {
                    TextButton(onClick = { onGalerie(entry.bilder) }) {
                        Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Galerie")
                    }
                }

                Spacer(Modifier.weight(1f))

                if (onDeleted != null) {
                    IconButton(onClick = { loeschDialog = true }) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Löschen",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }

    if (loeschDialog && onDeleted != null) {
        AlertDialog(
            onDismissRequest = { loeschDialog = false },
            title = { Text("Eintrag löschen") },
            text = { Text("Möchtest du diesen Eintrag wirklich löschen?") },
            confirmButton = {
                TextButton(onClick = {
                    loeschDialog = false
                    scope.launch {
                        runCatching { api.deleteEntry(entry.id, entry.diary.ifEmpty { activeDiary }) }
                            .onSuccess { onDeleted() }
                            .onFailure { onMeldung("Fehler beim Löschen: ${it.message}") }
                    }
                }) { Text("Löschen", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { loeschDialog = false }) { Text("Abbrechen") }
            },
        )
    }
}

private fun iconFuer(key: String): ImageVector = when (key) {
    "gewicht" -> Icons.Filled.MonitorWeight
    "kopfumfang" -> Icons.Filled.Face
    else -> Icons.Filled.Straighten
}
