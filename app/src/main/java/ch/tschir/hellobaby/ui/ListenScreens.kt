package ch.tschir.hellobaby.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import ch.tschir.hellobaby.Entry
import ch.tschir.hellobaby.R
import ch.tschir.hellobaby.Ziel
import ch.tschir.hellobaby.data.ApiService
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

// ── Reiter „Tag“ ────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagInhalt(
    api: ApiService,
    aktivesDiary: String,
    initialDate: String?,
    schluessel: String,
    onNavigate: (Ziel) -> Unit,
    meldung: (String) -> Unit,
) {
    var datum by remember(initialDate) {
        mutableStateOf(initialDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now())
    }
    var eintraege by remember { mutableStateOf<List<Entry>>(emptyList()) }
    var laedt by remember { mutableStateOf(true) }
    var fehler by remember { mutableStateOf<String?>(null) }
    var ladeZaehler by remember { mutableIntStateOf(0) }
    var zeigeDatumswahl by remember { mutableStateOf(false) }

    LaunchedEffect(datum, ladeZaehler, schluessel) {
        laedt = true
        fehler = null
        runCatching { api.getEntriesByDate(datum.toString(), aktivesDiary) }
            .onSuccess { eintraege = it }
            .onFailure { fehler = it.message ?: it.toString() }
        laedt = false
    }

    Column(Modifier.fillMaxSize()) {
        Blaetterer(
            titel = datum.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")),
            untertitel = datum.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.GERMAN),
            zurueckText = "Vortag",
            weiterText = "Folgetag",
            onZurueck = { datum = datum.minusDays(1) },
            onWeiter = { datum = datum.plusDays(1) },
            onTitel = { zeigeDatumswahl = true },
        )
        when {
            laedt -> LadeAnsicht()
            fehler != null -> FehlerAnsicht("Der Tag konnte nicht geladen werden. $fehler") { ladeZaehler++ }
            eintraege.isEmpty() -> LeerAnsicht("Keine Einträge an diesem Tag")
            else -> EintragsListe {
                items(eintraege, key = { it.id }) { eintrag ->
                    EntryCard(
                        entry = eintrag,
                        api = api,
                        activeDiary = aktivesDiary,
                        onGalerie = { onNavigate(Ziel.Gallery(it)) },
                        onDeleted = { ladeZaehler++ },
                        onMeldung = meldung,
                    )
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
                MhTextButton("Übernehmen", {
                    state.selectedDateMillis?.let {
                        datum = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    zeigeDatumswahl = false
                })
            },
            dismissButton = { MhTextButton("Abbrechen", { zeigeDatumswahl = false }) },
            colors = DatePickerDefaults.colors(containerColor = Mh.farben.surfaceRaised),
        ) { DatePicker(state = state, showModeToggle = false) }
    }
}

// ── Reiter „Monat“ ──────────────────────────────────────────────────────────

@Composable
fun MonatInhalt(
    api: ApiService,
    aktivesDiary: String,
    schluessel: String,
    onNavigate: (Ziel) -> Unit,
    meldung: (String) -> Unit,
) {
    var jahrMonat by remember { mutableStateOf(LocalDate.now().withDayOfMonth(1)) }
    var eintraege by remember { mutableStateOf<List<Entry>>(emptyList()) }
    var laedt by remember { mutableStateOf(true) }
    var fehler by remember { mutableStateOf<String?>(null) }
    var ladeZaehler by remember { mutableIntStateOf(0) }

    LaunchedEffect(jahrMonat, ladeZaehler, schluessel) {
        laedt = true
        fehler = null
        runCatching { api.getEntriesByMonth(jahrMonat.year, jahrMonat.monthValue, aktivesDiary) }
            .onSuccess { eintraege = it }
            .onFailure { fehler = it.message ?: it.toString() }
        laedt = false
    }

    Column(Modifier.fillMaxSize()) {
        Blaetterer(
            titel = jahrMonat.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.GERMAN)),
            untertitel = if (!laedt && fehler == null) {
                if (eintraege.size == 1) "1 Eintrag" else "${eintraege.size} Einträge"
            } else {
                null
            },
            zurueckText = "Vormonat",
            weiterText = "Folgemonat",
            onZurueck = { jahrMonat = jahrMonat.minusMonths(1) },
            onWeiter = { jahrMonat = jahrMonat.plusMonths(1) },
        )
        when {
            laedt -> LadeAnsicht()
            fehler != null -> FehlerAnsicht("Der Monat konnte nicht geladen werden. $fehler") { ladeZaehler++ }
            eintraege.isEmpty() -> LeerAnsicht("Keine Einträge in diesem Monat")
            else -> {
                val gruppen = remember(eintraege) { eintraege.groupBy { it.kalenderDatum }.toSortedMap() }
                EintragsListe {
                    nachTagen(gruppen, api, aktivesDiary, onNavigate, meldung) { ladeZaehler++ }
                }
            }
        }
    }
}

// ── Reiter „Favoriten“ ──────────────────────────────────────────────────────

@Composable
fun FavoritenInhalt(
    api: ApiService,
    aktivesDiary: String,
    schluessel: String,
    onNavigate: (Ziel) -> Unit,
    meldung: (String) -> Unit,
) {
    var eintraege by remember { mutableStateOf<List<Entry>>(emptyList()) }
    var laedt by remember { mutableStateOf(true) }
    var fehler by remember { mutableStateOf<String?>(null) }
    var ladeZaehler by remember { mutableIntStateOf(0) }

    LaunchedEffect(ladeZaehler, schluessel) {
        laedt = true
        fehler = null
        runCatching { api.getFavorites(aktivesDiary) }
            .onSuccess { eintraege = it }
            .onFailure { fehler = it.message ?: it.toString() }
        laedt = false
    }

    when {
        laedt -> LadeAnsicht()
        fehler != null -> FehlerAnsicht("Die Favoriten konnten nicht geladen werden. $fehler") { ladeZaehler++ }
        eintraege.isEmpty() -> LeerAnsicht(
            "Noch keine Favoriten",
            "Markiere Einträge mit dem Stern, um sie hier zu sammeln.",
        )
        else -> {
            val gruppen = remember(eintraege) {
                eintraege.groupBy { it.kalenderDatum }.toSortedMap(compareByDescending { it })
            }
            EintragsListe {
                nachTagen(gruppen, api, aktivesDiary, onNavigate, meldung) { ladeZaehler++ }
            }
        }
    }
}

// ── Gemeinsames ─────────────────────────────────────────────────────────────

/** Liste mit Seitenrand und Abstand zwischen den Karten. */
@Composable
private fun EintragsListe(inhalt: LazyListScope.() -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Mh.rand, end = Mh.rand, top = Mh.abstand2, bottom = Mh.abstand8),
        verticalArrangement = Arrangement.spacedBy(Mh.abstand3),
        content = inhalt,
    )
}

/** Einträge unter einem Gruppenlabel je Tag. */
private fun LazyListScope.nachTagen(
    gruppen: Map<String, List<Entry>>,
    api: ApiService,
    aktivesDiary: String,
    onNavigate: (Ziel) -> Unit,
    meldung: (String) -> Unit,
    onGeloescht: () -> Unit,
) {
    gruppen.forEach { (tag, tagesEintraege) ->
        item(key = "tag-$tag") { MhGruppenLabel(wochentagsDatum(tag)) }
        items(tagesEintraege, key = { it.id }) { eintrag ->
            EntryCard(
                entry = eintrag,
                api = api,
                activeDiary = aktivesDiary,
                onGalerie = { onNavigate(Ziel.Gallery(it)) },
                onDeleted = onGeloescht,
                onMeldung = meldung,
            )
        }
    }
}

/**
 * Blätterzeile: ‹ Titel › mit Symbol-Knöpfen; der Titel öffnet optional
 * eine Auswahl (Datum).
 */
@Composable
private fun Blaetterer(
    titel: String,
    untertitel: String?,
    zurueckText: String,
    weiterText: String,
    onZurueck: () -> Unit,
    onWeiter: () -> Unit,
    onTitel: (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Mh.abstand2, vertical = Mh.abstand1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MhSymbolButton(R.drawable.ic_mh_back, zurueckText, onZurueck)
        Column(
            Modifier
                .weight(1f)
                .heightIn(min = Mh.touch)
                .then(if (onTitel != null) Modifier.clickable(role = Role.Button, onClick = onTitel) else Modifier),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(titel, style = MhTypo.h3, color = Mh.farben.textStrong, textAlign = TextAlign.Center)
            if (untertitel != null) {
                Text(untertitel, style = MhTypo.small, color = Mh.farben.textSecondary)
            }
        }
        MhSymbolButton(R.drawable.ic_mh_chevron_right, weiterText, onWeiter)
    }
}
