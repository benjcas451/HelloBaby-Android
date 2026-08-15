package ch.tschir.hellobaby.ui

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ch.tschir.hellobaby.StatsResult
import ch.tschir.hellobaby.Ziel
import ch.tschir.hellobaby.data.ApiService
import ch.tschir.hellobaby.data.AppSettings
import ch.tschir.hellobaby.data.DataSourceMode
import ch.tschir.hellobaby.kDiaries
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun HomeScreen(
    settings: AppSettings,
    api: ApiService,
    onNavigate: (Ziel) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var stats by remember { mutableStateOf<StatsResult?>(null) }
    var laedt by remember { mutableStateOf(true) }
    var fehler by remember { mutableStateOf<String?>(null) }
    var aktivesDiary by remember { mutableStateOf(settings.activeDiary) }
    var ladeZaehler by remember { mutableIntStateOf(0) }
    var zufallLaeuft by remember { mutableStateOf(false) }
    var letzterZufallstag by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(ladeZaehler, aktivesDiary) {
        laedt = true
        fehler = null
        runCatching { api.getStats(aktivesDiary) }
            .onSuccess { stats = it }
            .onFailure { fehler = it.message ?: it.toString() }
        laedt = false
    }

    Scaffold(
        topBar = {
            HbTopBar(titel = "Hello ${settings.appName}! 🍼") {
                IconButton(onClick = { ladeZaehler++ }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Neu laden")
                }
                // Immer erreichbar – auch ohne Verbindung.
                IconButton(onClick = { onNavigate(Ziel.Settings) }) {
                    Icon(Icons.Filled.Settings, contentDescription = "Einstellungen")
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innen ->
        when {
            laedt -> LadeAnsicht()
            fehler != null -> {
                val lokal = settings.mode == DataSourceMode.LOCAL
                Column(modifier = Modifier.padding(innen)) {
                    FehlerAnsicht(
                        meldung = (if (lokal) "Lokaler Speicher konnte nicht geöffnet werden\n"
                        else "Verbindung fehlgeschlagen\n") + fehler.orEmpty(),
                        icon = if (lokal) Icons.Filled.Storage else Icons.Filled.WifiOff,
                        onErneut = { ladeZaehler++ },
                    )
                }
            }
            else -> Column(
                modifier = Modifier
                    .padding(innen)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
            ) {
                // Tagebuch-Umschalter
                SingleChoiceSegmentedButtonRow(modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    kDiaries.values.forEachIndexed { index, diary ->
                        SegmentedButton(
                            selected = aktivesDiary == diary.id,
                            onClick = {
                                settings.activeDiary = diary.id
                                aktivesDiary = diary.id
                            },
                            shape = SegmentedButtonDefaults.itemShape(index, kDiaries.size),
                        ) { Text(diary.label) }
                    }
                }
                Spacer(Modifier.height(16.dp))

                // Info-Karte
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            kDiaries[aktivesDiary]?.title.orEmpty(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        StatsZeile("Erster Eintrag", stats?.first?.ifEmpty { "–" } ?: "–")
                        StatsZeile("Letzter Eintrag", stats?.last?.ifEmpty { "–" } ?: "–")
                        StatsZeile("Heute", LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")))
                    }
                }

                Spacer(Modifier.height(24.dp))

                AktionsButton(Icons.Filled.AddCircleOutline, "Eintrag erstellen", Hb.aktionErstellen) {
                    onNavigate(Ziel.Create())
                }
                AktionsButton(Icons.Filled.CalendarToday, "Tagesansicht", Hb.aktionTag) {
                    onNavigate(Ziel.Day())
                }
                AktionsButton(Icons.Filled.CalendarMonth, "Monatsansicht", Hb.aktionMonat) {
                    onNavigate(Ziel.Month)
                }
                AktionsButton(Icons.Filled.Shuffle, "Zufälliger Tag", Hb.aktionZufall) {
                    if (zufallLaeuft) return@AktionsButton
                    zufallLaeuft = true
                    scope.launch {
                        runCatching { api.getRandomDate(aktivesDiary, exclude = letzterZufallstag) }
                            .onSuccess { datum ->
                                if (datum == null) {
                                    snackbar.showSnackbar("Noch keine Einträge vorhanden.")
                                } else {
                                    letzterZufallstag = datum
                                    onNavigate(Ziel.Day(initialDate = datum))
                                }
                            }
                            .onFailure {
                                snackbar.showSnackbar("Zufälliger Tag konnte nicht geladen werden: ${it.message}")
                            }
                        zufallLaeuft = false
                    }
                }
                AktionsButton(Icons.Filled.Star, "Favoriten", Hb.aktionFavoriten) {
                    onNavigate(Ziel.Favorites)
                }
                AktionsButton(Icons.Filled.PhotoLibrary, "Galerie", Hb.aktionGalerie) {
                    onNavigate(Ziel.ImageFeed)
                }
            }
        }
    }
}

@Composable
private fun StatsZeile(label: String, wert: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(wert, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun AktionsButton(
    icon: ImageVector,
    label: String,
    farbe: Color,
    onTap: () -> Unit,
) {
    // Lesbare Vordergrundfarbe je nach Helligkeit der Button-Farbe.
    val vordergrund = if (farbe.luminance() < 0.4f) Color.White else Color(0xDE000000)
    Button(
        onClick = onTap,
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(containerColor = farbe, contentColor = vordergrund),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .height(52.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}
