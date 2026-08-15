package ch.tschir.hellobaby.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ch.tschir.hellobaby.data.ApiService
import ch.tschir.hellobaby.data.AppSettings
import ch.tschir.hellobaby.data.CertSource
import ch.tschir.hellobaby.data.DataSourceMode
import ch.tschir.hellobaby.data.LocalApiImportService
import ch.tschir.hellobaby.data.LocalBackupService

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

Bilder/Video-Poster liefert /api/thumb.php (offen, ohne Auth), die Medien selbst
/api/media.php?file=uploads/<ordner>/<datei> (offen; &download=1 erzwingt
Content-Disposition: attachment).

Authentifizierung der geschützten Endpunkte je nach Modus:
• API-Key:  HTTP-Header  X-API-Key: <Key>
• mTLS:     Client-Zertifikat (client.crt + client.key)

Fehler kommen als {"error": "..."} mit passendem HTTP-Statuscode.
"""

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
    var apiKeySichtbar by remember { mutableStateOf(false) }
    var appName by remember { mutableStateOf(settings.appName) }
    var nutzer by remember { mutableStateOf(settings.users) }
    var standardNutzer by remember { mutableStateOf(settings.defaultUser) }
    var certsOk by remember { mutableStateOf(false) }
    var exportiert by remember { mutableStateOf(false) }
    var restauriert by remember { mutableStateOf(false) }
    var importiert by remember { mutableStateOf(false) }
    var importStand by remember { mutableStateOf(0 to 0) }
    var infoDialog by remember { mutableStateOf(false) }
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
                .onFailure { zeige("Fehler bei der Ordnerauswahl: ${it.message}") }
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
                    onSuccess = { zeige("Backup gespeichert.") },
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
                    onSuccess = { zeige("$it Einträge wurden wiederhergestellt.") },
                    onFailure = { zeige("Wiederherstellung fehlgeschlagen: ${it.message}") },
                )
                restauriert = false
            }
        }
    }

    Scaffold(
        topBar = { HbTopBar(titel = "Einstellungen", onZurueck = onZurueck) },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innen ->
        Column(
            modifier = Modifier
                .padding(innen)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            Abschnitt("Personen")
            if (nutzer.isEmpty()) {
                Text(
                    "Noch keine Person angelegt. Lege mindestens eine Person an, " +
                        "um sie beim Erstellen eines Eintrags als Ersteller auswählen zu können.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            } else {
                nutzer.forEach { name ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Person, contentDescription = null)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(name)
                            if (name == standardNutzer) {
                                Text(
                                    "Standard",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Checkbox(
                            checked = name == standardNutzer,
                            onCheckedChange = { gewaehlt ->
                                standardNutzer = if (gewaehlt) name else ""
                                settings.defaultUser = standardNutzer
                            },
                        )
                        IconButton(onClick = {
                            nutzer = nutzer - name
                            settings.users = nutzer
                            if (settings.selectedUser == name) settings.selectedUser = ""
                            if (standardNutzer == name) {
                                standardNutzer = ""
                                settings.defaultUser = ""
                            }
                        }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Entfernen")
                        }
                    }
                }
            }
            FilledTonalButton(
                onClick = { personDialog = true },
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Person hinzufügen")
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Abschnitt("App-Titel")
            OutlinedTextField(
                value = appName,
                onValueChange = {
                    appName = it
                    settings.appName = it
                },
                label = { Text("Name") },
                placeholder = { Text(AppSettings.DEFAULT_APP_NAME) },
                supportingText = { Text("Die App heißt dann „Hello NAME!\".") },
                leadingIcon = { Icon(Icons.Filled.ChildCare, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Abschnitt("Datenspeicher")
            ModusZeile(
                gewaehlt = mode == DataSourceMode.LOCAL,
                titel = "Lokal auf diesem Gerät",
                untertitel = "SQLite-Datenbank und lokaler Medienordner.",
            ) {
                mode = DataSourceMode.LOCAL
                settings.mode = mode
                api.reset()
            }
            ModusZeile(
                gewaehlt = mode != DataSourceMode.LOCAL,
                titel = "API",
                untertitel = "Server-API mit API-Key oder Client-Zertifikat.",
            ) {
                if (mode == DataSourceMode.LOCAL) {
                    mode = DataSourceMode.API_KEY
                    settings.mode = mode
                    api.reset()
                }
            }

            if (mode == DataSourceMode.LOCAL) {
                FilledTonalButton(
                    onClick = { backupSpeichern.launch(backup.dateiname()) },
                    enabled = !exportiert && !restauriert,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                ) {
                    if (exportiert) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Filled.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(if (exportiert) "Backup wird erstellt …" else "Zu Google Drive speichern")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { restoreNachfrage = true },
                    enabled = !exportiert && !restauriert,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                ) {
                    if (restauriert) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Filled.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(if (restauriert) "Backup wird wiederhergestellt …" else "Backup wiederherstellen")
                }
                Text(
                    "Im Dateidialog Google Drive als Ziel wählen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp, 8.dp, 16.dp, 0.dp),
                )
            } else {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Abschnitt("Authentifizierung")
                ModusZeile(
                    gewaehlt = mode == DataSourceMode.API_KEY,
                    titel = "API-Key",
                    untertitel = "Empfohlen. Key als X-API-Key-Header.",
                ) {
                    mode = DataSourceMode.API_KEY
                    settings.mode = mode
                    api.reset()
                }
                ModusZeile(
                    gewaehlt = mode == DataSourceMode.MTLS,
                    titel = "Client-Zertifikat (mTLS)",
                    untertitel = "Authentifizierung per client.crt/client.key",
                ) {
                    mode = DataSourceMode.MTLS
                    settings.mode = mode
                    api.reset()
                }

                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Abschnitt("Server")
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = {
                        serverUrl = it
                        settings.serverBase = it
                        api.reset()
                    },
                    label = { Text("Server-URL") },
                    supportingText = { Text("z. B. https://baby.example.org (ohne /api)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )

                if (mode == DataSourceMode.MTLS) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (certsOk) Icons.Filled.CheckCircle else Icons.Filled.Cancel,
                            contentDescription = null,
                            tint = if (certsOk) Hb.accentDeep else MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(if (certsOk) "Zertifikate gefunden" else "Keine Zertifikate gefunden")
                            Text(
                                certSource.locationLabel ?: "Kein Ordner ausgewählt",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Row(modifier = Modifier.padding(horizontal = 16.dp)) {
                        Button(onClick = { ordnerWahl.launch(null) }, shape = MaterialTheme.shapes.medium) {
                            Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Zertifikats-Ordner wählen")
                        }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = {
                            certPruefung++
                            scope.launch {
                                val ok = runCatching { certSource.readCredentials() }.isSuccess
                                zeige(if (ok) "Zertifikate gefunden." else "Keine Zertifikate gefunden.")
                            }
                        }, shape = MaterialTheme.shapes.medium) {
                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Erneut prüfen")
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = {
                        apiKey = it
                        settings.apiKey = it
                        api.reset()
                    },
                    label = { Text(if (mode == DataSourceMode.MTLS) "API-Key (optional)" else "API-Key") },
                    supportingText = {
                        Text(
                            if (mode == DataSourceMode.MTLS) {
                                "Optional – zusätzlich zum Client-Zertifikat, falls der Server beides verlangt."
                            } else {
                                "Pro Gerät ein eigener Key (vom Server ausgestellt)."
                            },
                        )
                    },
                    singleLine = true,
                    visualTransformation = if (apiKeySichtbar) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                    trailingIcon = {
                        IconButton(onClick = { apiKeySichtbar = !apiKeySichtbar }) {
                            Icon(
                                if (apiKeySichtbar) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (apiKeySichtbar) "Verbergen" else "Anzeigen",
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )

                Spacer(Modifier.height(16.dp))
                FilledTonalButton(
                    onClick = { importNachfrage = true },
                    enabled = !importiert,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                ) {
                    if (importiert) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Filled.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (importiert) "Import ${importStand.first} / ${importStand.second} …"
                        else "Lokale Daten importieren",
                    )
                }
                Text(
                    "Überträgt lokale Einträge einmalig zur eingestellten API.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp, 8.dp, 16.dp, 0.dp),
                )

                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Abschnitt("Erklärung")
                OutlinedButton(
                    onClick = { infoDialog = true },
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) {
                    Icon(Icons.Outlined.Cloud, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Aufbau API")
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (personDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { personDialog = false },
            title = { Text("Person hinzufügen") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val bereinigt = name.trim()
                    personDialog = false
                    if (bereinigt.isEmpty()) return@TextButton
                    if (nutzer.any { it.equals(bereinigt, ignoreCase = true) }) {
                        zeige("„$bereinigt“ ist bereits angelegt.")
                        return@TextButton
                    }
                    nutzer = nutzer + bereinigt
                    settings.users = nutzer
                }) { Text("Hinzufügen") }
            },
            dismissButton = {
                TextButton(onClick = { personDialog = false }) { Text("Abbrechen") }
            },
        )
    }

    if (restoreNachfrage) {
        AlertDialog(
            onDismissRequest = { restoreNachfrage = false },
            title = { Text("Backup wiederherstellen?") },
            text = {
                Text(
                    "Alle derzeit lokal gespeicherten Einträge und Medien werden durch " +
                        "den Inhalt des Backups ersetzt. Dieser Vorgang kann nicht " +
                        "rückgängig gemacht werden.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    restoreNachfrage = false
                    backupOeffnen.launch(arrayOf("application/zip"))
                }) { Text("Backup auswählen") }
            },
            dismissButton = {
                TextButton(onClick = { restoreNachfrage = false }) { Text("Abbrechen") }
            },
        )
    }

    if (importNachfrage) {
        AlertDialog(
            onDismissRequest = { importNachfrage = false },
            title = { Text("Lokale Daten importieren?") },
            text = {
                Text(
                    "Noch nicht übertragene lokale Einträge werden inklusive Medien und " +
                        "Favoriten an die aktuell eingestellte API gesendet. Bereits für " +
                        "diesen Server importierte Einträge werden übersprungen.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
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
                                zeige("${it.imported} Einträge importiert, ${it.skipped} bereits vorhanden.")
                            },
                            onFailure = { zeige("Import fehlgeschlagen: ${it.message}") },
                        )
                        importiert = false
                    }
                }) { Text("Import starten") }
            },
            dismissButton = {
                TextButton(onClick = { importNachfrage = false }) { Text("Abbrechen") }
            },
        )
    }

    if (infoDialog) {
        AlertDialog(
            onDismissRequest = { infoDialog = false },
            title = { Text("Aufbau API") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(API_INFO_TEXT.trim())
                }
            },
            confirmButton = {
                TextButton(onClick = { infoDialog = false }) { Text("Schließen") }
            },
        )
    }
}

@Composable
private fun Abschnitt(titel: String) {
    Text(
        titel,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 4.dp),
    )
}

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
            .clickable(onClick = onWahl)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = gewaehlt, onClick = onWahl)
        Spacer(Modifier.width(8.dp))
        Column {
            Text(titel)
            Text(
                untertitel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
