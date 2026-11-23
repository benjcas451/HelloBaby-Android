package ch.tschir.hellobaby.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import ch.tschir.hellobaby.Entry
import ch.tschir.hellobaby.R
import ch.tschir.hellobaby.Reiter
import ch.tschir.hellobaby.StatsResult
import ch.tschir.hellobaby.Ziel
import ch.tschir.hellobaby.data.ApiService
import ch.tschir.hellobaby.data.AppSettings
import ch.tschir.hellobaby.data.DataSourceMode
import ch.tschir.hellobaby.data.OfflineStatus
import ch.tschir.hellobaby.data.Verbindungswache
import ch.tschir.hellobaby.kDiaries
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Hauptansicht wie im Web-HelloBaby: Kopf „Hello <Name>“ mit der einen
 * Hauptaktion „+ Eintrag“, Tagebuch-Wahl als Segmentschalter, darunter die
 * Reiter Start, Tag, Monat, Favoriten und Galerie.
 */
@Composable
fun HauptScreen(
    settings: AppSettings,
    api: ApiService,
    reiter: Reiter,
    tag: String?,
    onNavigate: (Ziel) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var aktivesDiary by remember { mutableStateOf(settings.activeDiary) }
    var ladeZaehler by remember { mutableIntStateOf(0) }
    val offline by OfflineStatus.zustand.collectAsState()
    val meldung: (String) -> Unit = { text -> scope.launch { snackbar.showSnackbar(text) } }

    // Erst das Liegengebliebene loswerden, dann laden: sonst zeigte die
    // Statistik einen Serverstand ohne die eigenen Einträge.
    LaunchedEffect(ladeZaehler) {
        val verworfen = api.nachholen()
        if (verworfen.isNotEmpty()) {
            snackbar.showSnackbar(
                if (verworfen.size == 1) {
                    "Ein wartender Eintrag wurde vom Server abgelehnt: ${verworfen.first()}"
                } else {
                    "${verworfen.size} wartende Einträge wurden vom Server abgelehnt"
                },
            )
        }
    }

    // Meldungen anderer Ansichten (z. B. nach dem Speichern) hier zeigen.
    LaunchedEffect(Unit) {
        AppMeldung.fluss.collect { snackbar.showSnackbar(it) }
    }

    // Sobald wieder ein Netz da ist, neu laden und dabei die Warteschlange
    // abarbeiten – ohne dass der Nutzer etwas antippen muss.
    val kontext = LocalContext.current
    val wache = remember { Verbindungswache(kontext) }
    DisposableEffect(wache) {
        wache.starten()
        onDispose { wache.beenden() }
    }
    LaunchedEffect(wache) {
        wache.wiederVerbunden.collect { ladeZaehler++ }
    }

    val diaries = kDiaries.values.toList()
    Scaffold(
        topBar = {
            Column {
                MhKopf(
                    titel = "Hello ${settings.appName}!",
                    hauptaktion = {
                        MhHauptButton(
                            "Eintrag",
                            { onNavigate(Ziel.Create(initialDate = if (reiter == Reiter.TAG) tag else null)) },
                            symbol = R.drawable.ic_mh_plus,
                            klein = true,
                        )
                    },
                    symbole = {
                        // Immer erreichbar – auch ohne Verbindung.
                        MhSymbolButton(R.drawable.ic_mh_sliders, "Einstellungen", { onNavigate(Ziel.Settings) })
                    },
                )
                Column(Modifier.padding(horizontal = Mh.rand)) {
                    MhSegmente(
                        optionen = diaries.map { it.label },
                        gewaehlt = diaries.indexOfFirst { it.id == aktivesDiary }.coerceAtLeast(0),
                        onWahl = { i ->
                            settings.activeDiary = diaries[i].id
                            aktivesDiary = diaries[i].id
                        },
                    )
                    Spacer(Modifier.height(Mh.abstand2))
                    MhReiter(
                        optionen = Reiter.entries.map { it.titel },
                        gewaehlt = reiter.ordinal,
                        onWahl = { onNavigate(Ziel.Haupt(Reiter.entries[it])) },
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = Mh.farben.bg,
    ) { innen ->
        Column(Modifier.padding(innen).fillMaxSize()) {
            // Offline-Hinweis über allem: weiterarbeiten geht, der Stand ist
            // aber noch nicht beim Server.
            if (offline.grund != null || offline.ausstehend > 0) {
                OfflineBanner(
                    grund = offline.grund,
                    ausstehend = offline.ausstehend,
                    modifier = Modifier.padding(start = Mh.rand, end = Mh.rand, top = Mh.abstand3),
                )
            }
            val schluessel = "$aktivesDiary/$ladeZaehler"
            when (reiter) {
                Reiter.START -> StartInhalt(settings, api, aktivesDiary, ladeZaehler, onNavigate, meldung) { ladeZaehler++ }
                Reiter.TAG -> TagInhalt(api, aktivesDiary, tag, schluessel, onNavigate, meldung)
                Reiter.MONAT -> MonatInhalt(api, aktivesDiary, schluessel, onNavigate, meldung)
                Reiter.FAVORITEN -> FavoritenInhalt(api, aktivesDiary, schluessel, onNavigate, meldung)
                Reiter.GALERIE -> GalerieInhalt(api, aktivesDiary, schluessel, onNavigate)
            }
        }
    }
}

/** Start: Kurzinfo, die letzten Einträge, darunter „Stöbern“. */
@Composable
private fun StartInhalt(
    settings: AppSettings,
    api: ApiService,
    aktivesDiary: String,
    ladeZaehler: Int,
    onNavigate: (Ziel) -> Unit,
    meldung: (String) -> Unit,
    onNeuLaden: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var stats by remember { mutableStateOf<StatsResult?>(null) }
    var zuletzt by remember { mutableStateOf<List<Entry>>(emptyList()) }
    var laedt by remember { mutableStateOf(true) }
    var fehler by remember { mutableStateOf<String?>(null) }
    var zufallLaeuft by remember { mutableStateOf(false) }
    var letzterZufallstag by remember { mutableStateOf<String?>(null) }
    var intern by remember { mutableIntStateOf(0) }

    LaunchedEffect(aktivesDiary, ladeZaehler, intern) {
        laedt = true
        fehler = null
        runCatching {
            val s = api.getStats(aktivesDiary)
            stats = s
            zuletzt = letzteEintraege(api, aktivesDiary, s.last, anzahl = 3)
        }.onFailure { fehler = it.message ?: it.toString() }
        laedt = false
    }

    when {
        laedt -> LadeAnsicht()
        fehler != null -> {
            val lokal = settings.mode == DataSourceMode.LOCAL
            FehlerAnsicht(
                (if (lokal) "Der lokale Speicher ließ sich nicht öffnen. " else "Keine Verbindung zum Server. ") +
                    fehler.orEmpty(),
                onErneut = onNeuLaden,
            )
        }
        else -> Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Mh.rand)
                .padding(bottom = Mh.abstand8),
        ) {
            val diary = kDiaries[aktivesDiary]
            val s = stats
            Text(
                buildList {
                    diary?.title?.let(::add)
                    s?.first?.let(::tagLesen)?.let { add("seit ${anzeigeDatum(it.toString())}") }
                    s?.last?.let(::tagLesen)?.let { add("zuletzt ${anzeigeDatum(it.toString())}") }
                }.joinToString(" · "),
                style = MhTypo.small,
                color = Mh.farben.textSecondary,
                modifier = Modifier.padding(top = Mh.abstand4),
            )

            MhGruppenLabel("Zuletzt")
            if (zuletzt.isEmpty()) {
                MhLeer(
                    "Noch keine Einträge",
                    aktion = { MhTextButton("Ersten Eintrag anlegen", { onNavigate(Ziel.Create()) }) },
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(Mh.abstand3)) {
                    zuletzt.forEach { eintrag ->
                        EntryCard(
                            entry = eintrag,
                            api = api,
                            activeDiary = aktivesDiary,
                            onGalerie = { onNavigate(Ziel.Gallery(it)) },
                            onDeleted = { intern++ },
                            onMeldung = meldung,
                            mitDatum = true,
                        )
                    }
                }
            }

            MhGruppenLabel("Stöbern")
            MhListenZeile(
                "Zufälligen Tag zeigen",
                symbol = R.drawable.ic_mh_shuffle,
                pfeil = true,
                onClick = {
                    if (!zufallLaeuft) {
                        zufallLaeuft = true
                        scope.launch {
                            runCatching { api.getRandomDate(aktivesDiary, exclude = letzterZufallstag) }
                                .onSuccess { datum ->
                                    if (datum == null) {
                                        meldung("Noch keine Einträge vorhanden")
                                    } else {
                                        letzterZufallstag = datum
                                        onNavigate(Ziel.Haupt(Reiter.TAG, tag = datum))
                                    }
                                }
                                .onFailure { meldung("Zufälliger Tag konnte nicht geladen werden: ${it.message}") }
                            zufallLaeuft = false
                        }
                    }
                },
            )
            MhTrennlinie()
            MhListenZeile(
                "Alle Bilder",
                symbol = R.drawable.ic_mh_image,
                pfeil = true,
                onClick = { onNavigate(Ziel.Haupt(Reiter.GALERIE)) },
            )
            MhTrennlinie()
            MhListenZeile(
                "Favoriten",
                symbol = R.drawable.ic_mh_star,
                pfeil = true,
                onClick = { onNavigate(Ziel.Haupt(Reiter.FAVORITEN)) },
            )
            // Nur der Server hebt Gelöschtes auf.
            if (api.hatPapierkorb) {
                MhTrennlinie()
                MhListenZeile(
                    "Papierkorb",
                    symbol = R.drawable.ic_mh_trash,
                    pfeil = true,
                    onClick = { onNavigate(Ziel.Trash) },
                )
            }
        }
    }
}

/**
 * Die letzten [anzahl] Einträge: der Monat des letzten Eintrags, bei Bedarf
 * auch der davor. Nutzt nur vorhandene Abfragen (Monat), damit Server,
 * lokale Ablage und Offline-Zwischenstand gleich behandelt werden.
 */
private suspend fun letzteEintraege(api: ApiService, diary: String, letzter: String, anzahl: Int): List<Entry> {
    // stats.php liefert „15.11.2026“, die lokale Ablage „2026-11-15“.
    val start = tagLesen(letzter) ?: return emptyList()
    val sammlung = mutableListOf<Entry>()
    var monat = start.withDayOfMonth(1)
    repeat(2) {
        sammlung += api.getEntriesByMonth(monat.year, monat.monthValue, diary)
        if (sammlung.size >= anzahl) return@repeat
        monat = monat.minusMonths(1)
    }
    return sammlung
        .sortedWith(compareByDescending<Entry> { it.kalenderDatum }.thenByDescending { it.createdAt })
        .take(anzahl)
}

/**
 * Hinweis über dem Inhalt: Verbindung weg, App weiter benutzbar.
 *
 * [grund] null bedeutet „wieder online, aber es wartet noch etwas auf die
 * Übertragung“.
 */
@Composable
fun OfflineBanner(grund: String?, ausstehend: Int, modifier: Modifier = Modifier) {
    val titel = if (grund == null) "Übertragung läuft" else "Offline – $grund"
    val untertitel = when (ausstehend) {
        0 -> "Angezeigt wird der zuletzt geladene Stand."
        1 -> "Ein Eintrag wartet auf die Übertragung und geht raus, sobald die Verbindung steht."
        else ->
            "$ausstehend Einträge warten auf die Übertragung und gehen raus, " +
                "sobald die Verbindung steht."
    }
    MhHinweis(
        untertitel,
        modifier = modifier.fillMaxWidth(),
        titel = titel,
        art = MhHinweisArt.Warnung,
        symbol = if (grund == null) R.drawable.ic_mh_upload else R.drawable.ic_mh_cloud_off,
    )
}
