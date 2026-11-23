package ch.tschir.hellobaby.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import ch.tschir.hellobaby.R
import ch.tschir.hellobaby.data.ApiService
import ch.tschir.hellobaby.data.AppSettings
import ch.tschir.hellobaby.data.CertSource
import ch.tschir.hellobaby.data.DataSourceMode
import ch.tschir.hellobaby.data.LocalApiImportService
import ch.tschir.hellobaby.data.LocalBackupService
import kotlinx.coroutines.launch

/** Beschreibung der REST-API (für den Dialog "Aufbau API"). */
private const val API_INFO_TEXT = """
Die App spricht die Baby-Tagebuch-REST-API unter der eingestellten Basis-URL an (Pfad /api). Alle Antworten sind JSON.

Wichtige Endpunkte:

• GET <Basis-URL>/api/stats.php?diary=<id>
  Erster/letzter Eintrag und ein zufälliges Datum.

• GET <Basis-URL>/api/entries.php?date=YYYY-MM-DD&diary=<id>
  Einträge eines Tages. Ebenso ?year=&month= oder ?favorites=1 oder ?images=1.

• POST <Basis-URL>/api/entries.php  (multipart/form-data)
  Neuen Eintrag anlegen: Felder je Tagebuch + kalender_datum, von_name, diary
  sowie optional images[] (Fotos/Videos).

• DELETE <Basis-URL>/api/entries.php?id=<id>&diary=<id>
  Eintrag löschen.

• POST <Basis-URL>/api/favorite.php   Body {"id":.., "diary":".."}
  Favorit umschalten.

• GET <Basis-URL>/api/gallery.php?folder=uploads/<ordner>
  Dateien einer Galerie.

Bilder/Video-Poster liefert /api/thumb.php, die Medien selbst
/api/media.php?file=uploads/<ordner>/<datei> (&download=1 erzwingt
Content-Disposition: attachment). Die App ruft beide mit denselben Kopfzeilen
ab wie die übrigen Endpunkte – hinter Cloudflare Access ist das zwingend,
sonst blockiert der Rand die Anfrage.

Authentifizierung je nach Modus:
• API-Key:    HTTP-Header  X-API-Key: <Key>
• mTLS:       Client-Zertifikat (client.crt + client.key), API-Key optional
• Cloudflare: Header CF-Access-Client-Id und CF-Access-Client-Secret,
              API-Key optional

Fehler kommen als {"error": "..."} mit passendem HTTP-Statuscode.
"""

/** Beschreibung der lokalen Ablage (für den Dialog "Aufbau Datenbank"). */
private const val DB_INFO_TEXT = """
Im Modus "Lokal" speichert die App alles auf dem Gerät: die Einträge in der Datenbank hello_baby.sqlite, die Fotos und Videos daneben im Ordner media. Beides liegt im app-privaten Bereich, andere Apps haben keinen Zugriff, und es findet keine Synchronisation statt.

Tabelle "entries":

• id
  INTEGER, Primärschlüssel (Auto-Increment)

• diary
  TEXT, welches Tagebuch: schwangerschaft oder entwicklung

• kalender_datum
  TEXT, Tag des Eintrags als YYYY-MM-DD

• bilder
  TEXT, absoluter Pfad zum Medienordner des Eintrags (…/media/<tagebuch>_<id>) – leer, wenn der Eintrag keine Medien hat. Liegt der Ordner nach einer Wiederherstellung woanders, sucht die App ihn anhand des Ordnernamens im aktuellen media-Verzeichnis.

• von_name
  TEXT, wer den Eintrag angelegt hat

• favorit
  INTEGER (0/1)

• created_at
  TEXT, Zeitpunkt der Erstellung

• fields_json
  TEXT, die tagebuchspezifischen Felder als JSON-Objekt. So kommen neue Felder ohne Schemaänderung dazu.

Dazu die Tabelle "remote_imports": sie merkt sich, welcher lokale Eintrag bereits zu welchem Server hochgeladen wurde, damit ein zweiter Import keine Dubletten anlegt.

Sicherung & Gerätewechsel

Android sichert die App automatisch. Was dabei mitgeht, legt die App bewusst unterschiedlich fest:

• Cloud-Backup (über das Google-Konto)
  Nur die Datenbank, also die Einträge samt Texten. Fotos und Videos bleiben außen vor – sie sind um ein Vielfaches größer als das, was ein automatisches Backup aufnehmen darf, und würden es nicht vergrößern, sondern ganz scheitern lassen. Ebenfalls nicht mit in die Cloud gehen die Einstellungen, weil dort der API-Key und das Cloudflare Service Token stehen.

• Direkter Gerätewechsel (altes Gerät → neues Gerät)
  Hier gibt es diese Grenze nicht: Medien und Einstellungen kommen mit. Die Übertragung läuft Ende-zu-Ende-verschlüsselt unmittelbar zwischen den beiden Geräten.

Nach einer Wiederherstellung aus der Cloud sind die Einträge also vollständig da, die zugehörigen Fotos und Videos aber nicht – für die ist das ZIP-Backup weiter unten der richtige Weg. Server-Adresse, API-Key und ein etwaiges Cloudflare Service Token müssen in dem Fall ebenfalls neu eingetragen werden, den Zertifikats-Ordner für mTLS muss man in beiden Fällen neu auswählen.
"""

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    api: ApiService,
    onZurueck: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val certSource = remember { CertSource(context, settings) }
    val backup = remember { LocalBackupService(context) }
    val importService = remember { LocalApiImportService(context) }

    var mode by remember { mutableStateOf(settings.mode) }
    var serverUrl by remember { mutableStateOf(settings.serverBase) }
    var apiKey by remember { mutableStateOf(settings.apiKey) }
    var cfClientId by remember { mutableStateOf(settings.cfAccessClientId) }
    var cfClientSecret by remember { mutableStateOf(settings.cfAccessClientSecret) }
    var cfSichtbar by remember { mutableStateOf(false) }
    var apiKeySichtbar by remember { mutableStateOf(false) }
    var appName by remember { mutableStateOf(settings.appName) }
    var nutzer by remember { mutableStateOf(settings.users) }
    var standardNutzer by remember { mutableStateOf(settings.defaultUser) }
    var certsOk by remember { mutableStateOf(false) }
    var exportiert by remember { mutableStateOf(false) }
    var restauriert by remember { mutableStateOf(false) }
    var importiert by remember { mutableStateOf(false) }
    var importStand by remember { mutableStateOf(0 to 0) }
    var infoDialog by remember { mutableStateOf<Pair<String, String>?>(null) }
    var personDialog by remember { mutableStateOf(false) }
    var restoreNachfrage by remember { mutableStateOf(false) }
    var importNachfrage by remember { mutableStateOf(false) }
    var certPruefung by remember { mutableIntStateOf(0) }

    fun zeige(text: String) {
        scope.launch { snackbar.showSnackbar(text) }
    }

    LaunchedEffect(certPruefung) {
        certsOk = runCatching { certSource.readCredentials() }.isSuccess
    }

    val ordnerWahl = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            runCatching { certSource.uebernehmeOrdner(uri) }
                .onFailure { zeige("Der Ordner ließ sich nicht übernehmen: ${it.message}") }
            certPruefung++
        }
    }

    val backupSpeichern = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        if (uri != null) {
            exportiert = true
            scope.launch {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { ziel ->
                        backup.export(ziel)
                    } ?: error("Datei ließ sich nicht schreiben.")
                }.fold(
                    onSuccess = { zeige("Backup gespeichert") },
                    onFailure = { zeige("Backup fehlgeschlagen: ${it.message}") },
                )
                exportiert = false
            }
        }
    }

    val backupOeffnen = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            restauriert = true
            scope.launch {
                runCatching { backup.restore(context, uri) }.fold(
                    onSuccess = { zeige(if (it == 1) "1 Eintrag wiederhergestellt" else "$it Einträge wiederhergestellt") },
                    onFailure = { zeige("Wiederherstellung fehlgeschlagen: ${it.message}") },
                )
                restauriert = false
            }
        }
    }

    Scaffold(
        topBar = { MhKopf(titel = "Einstellungen", onZurueck = onZurueck) },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = Mh.farben.bg,
    ) { innen ->
        val f = Mh.farben
        Column(
            modifier = Modifier
                .padding(innen)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Mh.rand)
                .padding(bottom = Mh.abstand8),
        ) {
            MhGruppenLabel("Personen", Modifier.padding(top = 0.dp))
            if (nutzer.isEmpty()) {
                Text(
                    "Noch keine Person angelegt. Lege mindestens eine an, " +
                        "dann kannst du sie beim Eintrag auswählen.",
                    style = MhTypo.small,
                    color = f.textSecondary,
                )
            } else {
                MhListe(nutzer) { name ->
                    MhListenZeile(
                        name,
                        symbol = R.drawable.ic_mh_user,
                        untertitel = if (name == standardNutzer) "Wird beim Eintrag vorgewählt" else null,
                        rechts = {
                            MhAuswahl(
                                "Standard",
                                gewaehlt = name == standardNutzer,
                                onClick = {
                                    standardNutzer = if (standardNutzer == name) "" else name
                                    settings.defaultUser = standardNutzer
                                },
                            )
                            MhSymbolButton(R.drawable.ic_mh_trash, "$name entfernen", {
                                nutzer = nutzer - name
                                settings.users = nutzer
                                if (settings.selectedUser == name) settings.selectedUser = ""
                                if (standardNutzer == name) {
                                    standardNutzer = ""
                                    settings.defaultUser = ""
                                }
                            })
                        },
                    )
                }
            }
            Spacer(Modifier.height(Mh.abstand2))
            MhZweitButton("Person hinzufügen", { personDialog = true }, symbol = R.drawable.ic_mh_user_plus)

            MhGruppenLabel("Name im Titel")
            MhFeld("Name", hinweis = "Die App heißt dann „Hello ${appName.trim().ifEmpty { AppSettings.DEFAULT_APP_NAME }}!“.") {
                OutlinedTextField(
                    value = appName,
                    onValueChange = {
                        appName = it
                        settings.appName = it
                    },
                    placeholder = { Text(AppSettings.DEFAULT_APP_NAME, style = MhTypo.body) },
                    singleLine = true,
                    textStyle = MhTypo.body,
                    colors = mhFeldFarben(),
                    shape = MhFeldForm,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            MhGruppenLabel("Datenspeicher")
            ModusZeile(
                gewaehlt = mode == DataSourceMode.LOCAL,
                titel = "Lokal auf diesem Gerät",
                untertitel = "SQLite-Datenbank und lokaler Medienordner",
            ) {
                mode = DataSourceMode.LOCAL
                settings.mode = mode
                api.reset()
            }
            MhTrennlinie()
            ModusZeile(
                gewaehlt = mode != DataSourceMode.LOCAL,
                titel = "Server",
                untertitel = "API mit API-Key, Client-Zertifikat oder Cloudflare Service Token",
            ) {
                if (mode == DataSourceMode.LOCAL) {
                    mode = DataSourceMode.API_KEY
                    settings.mode = mode
                    api.reset()
                }
            }

            if (mode == DataSourceMode.LOCAL) {
                MhGruppenLabel("Sicherung")
                Column(verticalArrangement = Arrangement.spacedBy(Mh.abstand2)) {
                    MhZweitButton(
                        if (exportiert) "Backup wird erstellt …" else "Backup speichern",
                        { backupSpeichern.launch(backup.dateiname()) },
                        symbol = R.drawable.ic_mh_save,
                        enabled = !exportiert && !restauriert,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    MhZweitButton(
                        if (restauriert) "Backup wird wiederhergestellt …" else "Backup wiederherstellen",
                        { restoreNachfrage = true },
                        symbol = R.drawable.ic_mh_undo,
                        enabled = !exportiert && !restauriert,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "Als Ziel im Dateidialog zum Beispiel Google Drive wählen.",
                        style = MhTypo.small,
                        color = f.textSecondary,
                    )
                }
            } else {
                MhGruppenLabel("Anmeldung")
                ModusZeile(
                    gewaehlt = mode == DataSourceMode.API_KEY,
                    titel = "API-Key",
                    untertitel = "Empfohlen, Key als X-API-Key-Header",
                ) {
                    mode = DataSourceMode.API_KEY
                    settings.mode = mode
                    api.reset()
                }
                MhTrennlinie()
                ModusZeile(
                    gewaehlt = mode == DataSourceMode.MTLS,
                    titel = "Client-Zertifikat (mTLS)",
                    untertitel = "Anmeldung per client.crt und client.key",
                ) {
                    mode = DataSourceMode.MTLS
                    settings.mode = mode
                    api.reset()
                }
                MhTrennlinie()
                ModusZeile(
                    gewaehlt = mode == DataSourceMode.CLOUDFLARE,
                    titel = "Cloudflare Access",
                    untertitel = "Service Token, API-Key optional zusätzlich",
                ) {
                    mode = DataSourceMode.CLOUDFLARE
                    settings.mode = mode
                    api.reset()
                }

                MhGruppenLabel("Server")
                Column(verticalArrangement = Arrangement.spacedBy(Mh.abstand4)) {
                    MhFeld("Server-Adresse", hinweis = "z. B. https://baby.example.org (ohne /api)") {
                        OutlinedTextField(
                            value = serverUrl,
                            onValueChange = {
                                serverUrl = it
                                settings.serverBase = it
                                api.reset()
                            },
                            singleLine = true,
                            textStyle = MhTypo.body,
                            colors = mhFeldFarben(),
                            shape = MhFeldForm,
                            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    if (mode == DataSourceMode.MTLS) {
                        MhHinweis(
                            certSource.locationLabel ?: "Kein Ordner ausgewählt",
                            titel = if (certsOk) "Zertifikate gefunden" else "Keine Zertifikate gefunden",
                            art = if (certsOk) MhHinweisArt.Erfolg else MhHinweisArt.Fehler,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(Mh.abstand2),
                            verticalArrangement = Arrangement.spacedBy(Mh.abstand2),
                        ) {
                            MhZweitButton("Ordner wählen", { ordnerWahl.launch(null) }, symbol = R.drawable.ic_mh_folder)
                            MhTextButton("Erneut prüfen", {
                                certPruefung++
                                scope.launch {
                                    val ok = runCatching { certSource.readCredentials() }.isSuccess
                                    zeige(if (ok) "Zertifikate gefunden" else "Keine Zertifikate gefunden")
                                }
                            }, symbol = R.drawable.ic_mh_refresh)
                        }
                    }

                    // Cloudflare Access prüft das Service Token am Rand und reicht
                    // die Anfrage erst danach an den Server weiter. Beide Hälften
                    // sind nötig: mit einer weist Cloudflare genauso ab wie ohne.
                    if (mode == DataSourceMode.CLOUDFLARE) {
                        CfFeld(
                            wert = cfClientId,
                            titel = "Client-ID",
                            hinweis = "Client-ID des Service Tokens, endet üblicherweise auf „.access“.",
                            sichtbar = cfSichtbar,
                            onSichtbarkeit = { cfSichtbar = !cfSichtbar },
                        ) {
                            cfClientId = it
                            settings.cfAccessClientId = it
                            api.reset()
                        }
                        CfFeld(
                            wert = cfClientSecret,
                            titel = "Client-Secret",
                            hinweis = "Service Tokens laufen ab, standardmäßig nach einem Jahr.",
                            sichtbar = cfSichtbar,
                            onSichtbarkeit = { cfSichtbar = !cfSichtbar },
                        ) {
                            cfClientSecret = it
                            settings.cfAccessClientSecret = it
                            api.reset()
                        }
                    }

                    MhFeld(
                        "API-Key",
                        zusatz = if (mode == DataSourceMode.API_KEY) null else "optional",
                        hinweis = when (mode) {
                            DataSourceMode.MTLS ->
                                "Zusätzlich zum Client-Zertifikat, falls der Server beides verlangt."
                            DataSourceMode.CLOUDFLARE ->
                                "Zusätzlich zum Service Token, falls der Server dahinter einen Key verlangt."
                            else -> "Pro Gerät ein eigener Key, vom Server ausgestellt."
                        },
                    ) {
                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = {
                                apiKey = it
                                settings.apiKey = it
                                api.reset()
                            },
                            singleLine = true,
                            textStyle = MhTypo.body,
                            colors = mhFeldFarben(),
                            shape = MhFeldForm,
                            visualTransformation = if (apiKeySichtbar) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                            trailingIcon = {
                                MhSymbolButton(
                                    if (apiKeySichtbar) R.drawable.ic_mh_eye_off else R.drawable.ic_mh_eye,
                                    if (apiKeySichtbar) "Verbergen" else "Anzeigen",
                                    { apiKeySichtbar = !apiKeySichtbar },
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                MhGruppenLabel("Übernahme")
                MhZweitButton(
                    if (importiert) "Import ${importStand.first} / ${importStand.second} …" else "Lokale Daten importieren",
                    { importNachfrage = true },
                    symbol = R.drawable.ic_mh_upload,
                    enabled = !importiert,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(Mh.abstand2))
                Text(
                    "Überträgt lokale Einträge einmalig zum eingestellten Server.",
                    style = MhTypo.small,
                    color = f.textSecondary,
                )

                MhGruppenLabel("Erklärung")
                MhListenZeile(
                    "Aufbau der API",
                    symbol = R.drawable.ic_mh_cloud,
                    pfeil = true,
                    onClick = { infoDialog = "Aufbau der API" to API_INFO_TEXT.trim() },
                )
                MhTrennlinie()
                MhListenZeile(
                    "Aufbau der Datenbank",
                    symbol = R.drawable.ic_mh_info,
                    pfeil = true,
                    onClick = { infoDialog = "Aufbau der Datenbank" to DB_INFO_TEXT.trim() },
                )
            }
        }
    }

    if (personDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { personDialog = false },
            title = { Text("Person hinzufügen", style = MhTypo.h2) },
            text = {
                MhFeld("Name") {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        singleLine = true,
                        textStyle = MhTypo.body,
                        colors = mhFeldFarben(),
                        shape = MhFeldForm,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                MhTextButton("Hinzufügen", {
                    val bereinigt = name.trim()
                    personDialog = false
                    if (bereinigt.isEmpty()) return@MhTextButton
                    if (nutzer.any { it.equals(bereinigt, ignoreCase = true) }) {
                        zeige("„$bereinigt“ ist schon angelegt")
                        return@MhTextButton
                    }
                    nutzer = nutzer + bereinigt
                    settings.users = nutzer
                })
            },
            dismissButton = { MhTextButton("Abbrechen", { personDialog = false }) },
        )
    }

    if (restoreNachfrage) {
        AlertDialog(
            onDismissRequest = { restoreNachfrage = false },
            title = { Text("Backup wiederherstellen", style = MhTypo.h2) },
            text = {
                Text(
                    "Alle derzeit lokal gespeicherten Einträge und Medien werden durch " +
                        "den Inhalt des Backups ersetzt. Das lässt sich nicht rückgängig machen.",
                    style = MhTypo.body,
                )
            },
            confirmButton = {
                MhTextButton("Backup auswählen", {
                    restoreNachfrage = false
                    backupOeffnen.launch(arrayOf("application/zip"))
                })
            },
            dismissButton = { MhTextButton("Abbrechen", { restoreNachfrage = false }) },
        )
    }

    if (importNachfrage) {
        AlertDialog(
            onDismissRequest = { importNachfrage = false },
            title = { Text("Lokale Daten importieren", style = MhTypo.h2) },
            text = {
                Text(
                    "Noch nicht übertragene lokale Einträge werden samt Medien und " +
                        "Favoriten an den eingestellten Server gesendet. Bereits für " +
                        "diesen Server importierte Einträge werden übersprungen.",
                    style = MhTypo.body,
                )
            },
            confirmButton = {
                MhTextButton("Import starten", {
                    importNachfrage = false
                    importiert = true
                    importStand = 0 to 0
                    scope.launch {
                        runCatching {
                            settings.serverBase = serverUrl
                            settings.apiKey = apiKey
                            api.reset()
                            importService.import(api, settings.serverBase) { aktuell, gesamt ->
                                importStand = aktuell to gesamt
                            }
                        }.fold(
                            onSuccess = {
                                zeige("${it.imported} Einträge importiert, ${it.skipped} schon vorhanden")
                            },
                            onFailure = { zeige("Import fehlgeschlagen: ${it.message}") },
                        )
                        importiert = false
                    }
                })
            },
            dismissButton = { MhTextButton("Abbrechen", { importNachfrage = false }) },
        )
    }

    infoDialog?.let { (titel, inhalt) ->
        AlertDialog(
            onDismissRequest = { infoDialog = null },
            title = { Text(titel, style = MhTypo.h2) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(inhalt, style = MhTypo.small)
                }
            },
            confirmButton = { MhTextButton("Schließen", { infoDialog = null }) },
        )
    }
}

/**
 * Eingabefeld für eine Hälfte des Cloudflare Service Tokens: verdeckt, mit
 * Auge zum Aufdecken. Beide Hälften teilen sich den Schalter, weil sie
 * ohnehin zusammen eingetragen werden.
 */
@Composable
private fun CfFeld(
    wert: String,
    titel: String,
    hinweis: String,
    sichtbar: Boolean,
    onSichtbarkeit: () -> Unit,
    onAenderung: (String) -> Unit,
) {
    MhFeld(titel, hinweis = hinweis) {
        OutlinedTextField(
            value = wert,
            onValueChange = onAenderung,
            singleLine = true,
            textStyle = MhTypo.body,
            colors = mhFeldFarben(),
            shape = MhFeldForm,
            visualTransformation = if (sichtbar) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
            trailingIcon = {
                MhSymbolButton(
                    if (sichtbar) R.drawable.ic_mh_eye_off else R.drawable.ic_mh_eye,
                    if (sichtbar) "Verbergen" else "Anzeigen",
                    onSichtbarkeit,
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Auswahlzeile mit Optionsfeld: Titel, Nebentext, ganze Zeile tippbar. */
@Composable
private fun ModusZeile(
    gewaehlt: Boolean,
    titel: String,
    untertitel: String,
    onWahl: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .selectable(selected = gewaehlt, role = Role.RadioButton, onClick = onWahl)
            .padding(vertical = Mh.abstand2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = gewaehlt,
            onClick = null,
            colors = RadioButtonDefaults.colors(
                selectedColor = Mh.farben.primary,
                unselectedColor = Mh.farben.borderStrong,
            ),
        )
        Spacer(Modifier.padding(start = Mh.abstand3))
        Column {
            Text(titel, style = MhTypo.body.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium), color = Mh.farben.textStrong)
            Text(untertitel, style = MhTypo.small, color = Mh.farben.textSecondary)
        }
    }
}
