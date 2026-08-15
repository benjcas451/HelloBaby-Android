package ch.tschir.hellobaby.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ch.tschir.hellobaby.Entry
import ch.tschir.hellobaby.Ziel
import ch.tschir.hellobaby.data.ApiService
import ch.tschir.hellobaby.data.AppSettings
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

// ── Tagesansicht ────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayViewScreen(
    settings: AppSettings,
    api: ApiService,
    initialDate: String?,
    onZurueck: () -> Unit,
    onNavigate: (Ziel) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var datum by remember {
        mutableStateOf(initialDate?.let(LocalDate::parse) ?: LocalDate.now())
    }
    var eintraege by remember { mutableStateOf<List<Entry>>(emptyList()) }
    var laedt by remember { mutableStateOf(true) }
    var fehler by remember { mutableStateOf<String?>(null) }
    var ladeZaehler by remember { mutableIntStateOf(0) }
    var zeigeDatumswahl by remember { mutableStateOf(false) }

    LaunchedEffect(datum, ladeZaehler) {
        laedt = true
        fehler = null
        runCatching { api.getEntriesByDate(datum.toString(), settings.activeDiary) }
            .onSuccess { eintraege = it }
            .onFailure { fehler = it.message ?: it.toString() }
        laedt = false
    }

    Scaffold(
        topBar = {
            HbTopBar(titel = "Tagesansicht", onZurueck = onZurueck) {
                IconButton(onClick = { onNavigate(Ziel.Create(initialDate = datum.toString())) }) {
                    Icon(Icons.Filled.Add, contentDescription = "Eintrag erstellen")
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innen ->
        Column(modifier = Modifier.padding(innen).fillMaxSize()) {
            // Datums-Navigation
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { datum = datum.minusDays(1) }) {
                    Icon(Icons.Filled.ChevronLeft, contentDescription = "Vortag")
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { zeigeDatumswahl = true },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        datum.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        datum.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.GERMAN),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { datum = datum.plusDays(1) }) {
                    Icon(Icons.Filled.ChevronRight, contentDescription = "Folgetag")
                }
            }

            when {
                laedt -> LadeAnsicht()
                fehler != null -> FehlerAnsicht("Fehler: $fehler") { ladeZaehler++ }
                eintraege.isEmpty() -> LeerAnsicht(
                    Icons.Filled.Inbox, "Keine Einträge für diesen Tag",
                )
                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(eintraege, key = { it.id }) { eintrag ->
                        EntryCard(
                            entry = eintrag,
                            api = api,
                            activeDiary = settings.activeDiary,
                            onGalerie = { onNavigate(Ziel.Gallery(it)) },
                            onDeleted = { ladeZaehler++ },
                            onMeldung = { scope.launch { snackbar.showSnackbar(it) } },
                        )
                    }
                }
            }
        }
    }

    if (zeigeDatumswahl) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = datum.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { zeigeDatumswahl = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        datum = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    zeigeDatumswahl = false
                }) { Text("Übernehmen") }
            },
            dismissButton = {
                TextButton(onClick = { zeigeDatumswahl = false }) { Text("Abbrechen") }
            },
        ) { DatePicker(state = state, showModeToggle = false) }
    }
}

// ── Monatsansicht ───────────────────────────────────────────────────────────

@Composable
fun MonthViewScreen(
    settings: AppSettings,
    api: ApiService,
    onZurueck: () -> Unit,
    onNavigate: (Ziel) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var jahrMonat by remember { mutableStateOf(LocalDate.now().withDayOfMonth(1)) }
    var eintraege by remember { mutableStateOf<List<Entry>>(emptyList()) }
    var laedt by remember { mutableStateOf(true) }
    var fehler by remember { mutableStateOf<String?>(null) }
    var ladeZaehler by remember { mutableIntStateOf(0) }

    LaunchedEffect(jahrMonat, ladeZaehler) {
        laedt = true
        fehler = null
        runCatching {
            api.getEntriesByMonth(jahrMonat.year, jahrMonat.monthValue, settings.activeDiary)
        }
            .onSuccess { eintraege = it }
            .onFailure { fehler = it.message ?: it.toString() }
        laedt = false
    }

    Scaffold(
        topBar = { HbTopBar(titel = "Monatsansicht", onZurueck = onZurueck) },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innen ->
        Column(modifier = Modifier.padding(innen).fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { jahrMonat = jahrMonat.minusMonths(1) }) {
                    Icon(Icons.Filled.ChevronLeft, contentDescription = "Vormonat")
                }
                Text(
                    jahrMonat.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.GERMAN)),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(onClick = { jahrMonat = jahrMonat.plusMonths(1) }) {
                    Icon(Icons.Filled.ChevronRight, contentDescription = "Folgemonat")
                }
            }

            when {
                laedt -> LadeAnsicht()
                fehler != null -> FehlerAnsicht("Fehler: $fehler") { ladeZaehler++ }
                eintraege.isEmpty() -> LeerAnsicht(
                    Icons.Filled.Inbox, "Keine Einträge für diesen Monat",
                )
                else -> {
                    val gruppen = eintraege.groupBy { it.kalenderDatum }.toSortedMap()
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        gruppen.forEach { (tag, tagesEintraege) ->
                            item(key = "tag-$tag") {
                                Text(
                                    wochentagsDatum(tag),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 4.dp),
                                )
                            }
                            items(tagesEintraege, key = { it.id }) { eintrag ->
                                EntryCard(
                                    entry = eintrag,
                                    api = api,
                                    activeDiary = settings.activeDiary,
                                    onGalerie = { onNavigate(Ziel.Gallery(it)) },
                                    onDeleted = { ladeZaehler++ },
                                    onMeldung = { scope.launch { snackbar.showSnackbar(it) } },
                                )
                            }
                        }
                        item(key = "gesamt") {
                            Text(
                                "Gesamt: ${eintraege.size} Einträge",
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Favoriten ───────────────────────────────────────────────────────────────

@Composable
fun FavoritesScreen(
    settings: AppSettings,
    api: ApiService,
    onZurueck: () -> Unit,
    onNavigate: (Ziel) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var eintraege by remember { mutableStateOf<List<Entry>>(emptyList()) }
    var laedt by remember { mutableStateOf(true) }
    var fehler by remember { mutableStateOf<String?>(null) }
    var ladeZaehler by remember { mutableIntStateOf(0) }

    LaunchedEffect(ladeZaehler) {
        laedt = true
        fehler = null
        runCatching { api.getFavorites(settings.activeDiary) }
            .onSuccess { eintraege = it }
            .onFailure { fehler = it.message ?: it.toString() }
        laedt = false
    }

    Scaffold(
        topBar = {
            HbTopBar(titel = "Favoriten", onZurueck = onZurueck) {
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
                laedt -> LadeAnsicht()
                fehler != null -> FehlerAnsicht("Fehler: $fehler") { ladeZaehler++ }
                eintraege.isEmpty() -> LeerAnsicht(
                    Icons.Filled.StarBorder,
                    "Noch keine Favoriten vorhanden",
                    "Markiere Einträge mit dem Stern ☆ um sie hier zu sehen.",
                )
                else -> {
                    val gruppen = eintraege.groupBy { it.kalenderDatum }
                        .toSortedMap(compareByDescending { it })
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        gruppen.forEach { (tag, tagesEintraege) ->
                            item(key = "tag-$tag") {
                                Text(
                                    wochentagsDatum(tag),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 4.dp),
                                )
                            }
                            items(tagesEintraege, key = { it.id }) { eintrag ->
                                EntryCard(
                                    entry = eintrag,
                                    api = api,
                                    activeDiary = settings.activeDiary,
                                    onGalerie = { onNavigate(Ziel.Gallery(it)) },
                                    onDeleted = { ladeZaehler++ },
                                    onMeldung = { scope.launch { snackbar.showSnackbar(it) } },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
