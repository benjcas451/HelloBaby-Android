package ch.tschir.hellobaby.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import ch.tschir.hellobaby.data.ApiService
import ch.tschir.hellobaby.data.AppSettings
import ch.tschir.hellobaby.isVideoFile
import ch.tschir.hellobaby.kDiaries
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Ein fürs Formular ausgewähltes Medium (immer als lokale Datei gepuffert). */
private data class GewaehltesMedium(val datei: File, val istVideo: Boolean)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CreateScreen(
    settings: AppSettings,
    api: ApiService,
    initialDate: String?,
    onZurueck: () -> Unit,
    onEinstellungen: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val diary = remember { kDiaries[settings.activeDiary] ?: kDiaries.values.first() }
    val werte = remember { mutableStateOf(diary.fields.associate { it.key to "" }) }
    var datum by remember {
        mutableStateOf(initialDate?.let(LocalDate::parse) ?: LocalDate.now())
    }
    var vonName by remember { mutableStateOf("") }
    var nutzer by remember { mutableStateOf<List<String>>(emptyList()) }
    val medien = remember { mutableStateListOf<GewaehltesMedium>() }
    var speichert by remember { mutableStateOf(false) }
    var uploadFortschritt by remember { mutableStateOf<Float?>(null) }
    var zeigeDatumswahl by remember { mutableStateOf(false) }
    var zeigeErstellerDialog by remember { mutableStateOf(false) }
    var kameraZiel by remember { mutableStateOf<Pair<File, Uri>?>(null) }

    LaunchedEffect(Unit) {
        nutzer = settings.users
        val standard = settings.defaultUser
        val zuletzt = settings.selectedUser
        vonName = when {
            standard in nutzer -> standard
            zuletzt in nutzer -> zuletzt
            else -> ""
        }
    }

    /** Kopiert eine Picker-Uri in den Cache, damit Name+Inhalt stabil bleiben. */
    fun uebernehmeUri(uri: Uri) {
        runCatching {
            val name = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val spalte = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && spalte >= 0) cursor.getString(spalte) else null
            } ?: uri.lastPathSegment?.substringAfterLast('/') ?: "medium"
            val ziel = File(context.cacheDir, "captures").apply { mkdirs() }
                .resolve("${System.nanoTime()}_$name")
            context.contentResolver.openInputStream(uri)!!.use { input ->
                ziel.outputStream().use { input.copyTo(it) }
            }
            val mime = context.contentResolver.getType(uri).orEmpty()
            medien.add(GewaehltesMedium(ziel, mime.startsWith("video") || isVideoFile(name)))
        }.onFailure {
            scope.launch { snackbar.showSnackbar("Medium konnte nicht übernommen werden: ${it.message}") }
        }
    }

    val galerieWahl = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(),
    ) { uris -> uris.forEach(::uebernehmeUri) }

    val fotoAufnahme = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { ok ->
        kameraZiel?.let { (datei, _) ->
            if (ok) medien.add(GewaehltesMedium(datei, istVideo = false)) else datei.delete()
        }
        kameraZiel = null
    }

    val videoAufnahme = rememberLauncherForActivityResult(
        ActivityResultContracts.CaptureVideo(),
    ) { ok ->
        kameraZiel?.let { (datei, _) ->
            if (ok) medien.add(GewaehltesMedium(datei, istVideo = true)) else datei.delete()
        }
        kameraZiel = null
    }

    fun kameraDatei(endung: String): Pair<File, Uri> {
        val datei = File(context.cacheDir, "captures").apply { mkdirs() }
            .resolve("${System.nanoTime()}.$endung")
        val uri = FileProvider.getUriForFile(context, "ch.tschir.HelloBaby.fileprovider", datei)
        return datei to uri
    }

    fun speichern() {
        val pflichtFehlt = diary.fields.any { it.required && werte.value[it.key].orEmpty().isBlank() }
        if (pflichtFehlt) {
            scope.launch { snackbar.showSnackbar("Bitte alle Pflichtfelder (*) ausfüllen.") }
            return
        }
        if (vonName.isBlank()) {
            zeigeErstellerDialog = true
            return
        }
        speichert = true
        uploadFortschritt = null
        scope.launch {
            runCatching {
                api.createEntry(
                    kalenderDatum = datum.toString(),
                    fields = diary.fields.associate { it.key to werte.value[it.key].orEmpty().trim() },
                    vonName = vonName,
                    images = medien.map { it.datei },
                    diary = diary.id,
                    onSendProgress = if (medien.isNotEmpty()) {
                        { gesendet, gesamt ->
                            if (gesamt > 0) uploadFortschritt = gesendet.toFloat() / gesamt
                        }
                    } else null,
                )
                settings.selectedUser = vonName
            }.fold(
                onSuccess = {
                    speichert = false
                    uploadFortschritt = null
                    onZurueck()
                },
                onFailure = {
                    speichert = false
                    uploadFortschritt = null
                    snackbar.showSnackbar("Fehler: ${it.message}")
                },
            )
        }
    }

    Scaffold(
        topBar = { HbTopBar(titel = "${diary.title}: Eintrag", onZurueck = onZurueck) },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innen ->
        Column(
            modifier = Modifier
                .padding(innen)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            // Datum
            ListItem(
                leadingContent = { Icon(Icons.Filled.CalendarToday, contentDescription = null) },
                headlineContent = { Text("Datum") },
                supportingContent = {
                    Text(
                        datum.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                },
                trailingContent = { Icon(Icons.Filled.Edit, contentDescription = "Datum ändern") },
                modifier = Modifier.clickable { zeigeDatumswahl = true },
            )
            Spacer(Modifier.height(8.dp))

            // Dynamische Felder
            for (feld in diary.fields) {
                OutlinedTextField(
                    value = werte.value[feld.key].orEmpty(),
                    onValueChange = { neu ->
                        werte.value = werte.value.toMutableMap().apply { put(feld.key, neu) }
                    },
                    label = { Text(if (feld.required) "${feld.label} *" else feld.label) },
                    suffix = if (feld.unit.isNotEmpty()) {
                        { Text(feld.unit) }
                    } else null,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (feld.numeric) KeyboardType.Decimal else KeyboardType.Text,
                        capitalization = if (feld.numeric) KeyboardCapitalization.None else KeyboardCapitalization.Sentences,
                    ),
                    minLines = if (feld.textarea) 4 else 1,
                    maxLines = if (feld.textarea) 8 else 1,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                )
            }

            // Ersteller
            var dropdownOffen by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = dropdownOffen,
                onExpandedChange = { if (nutzer.isNotEmpty()) dropdownOffen = it },
            ) {
                OutlinedTextField(
                    value = vonName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Ersteller *") },
                    placeholder = if (nutzer.isEmpty()) {
                        { Text("Keine Person angelegt") }
                    } else null,
                    supportingText = if (nutzer.isEmpty()) {
                        { Text("In den Einstellungen unter „Personen“ anlegen") }
                    } else null,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownOffen) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(
                    expanded = dropdownOffen,
                    onDismissRequest = { dropdownOffen = false },
                ) {
                    nutzer.forEach { name ->
                        DropdownMenuItem(
                            text = { Text(name) },
                            onClick = {
                                vonName = name
                                dropdownOffen = false
                            },
                        )
                    }
                }
            }
            if (nutzer.isEmpty()) {
                TextButton(onClick = { zeigeErstellerDialog = true }) {
                    Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Person anlegen")
                }
            }
            Spacer(Modifier.height(20.dp))

            // Medien
            Text("Medien", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = {
                    galerieWahl.launch(
                        androidx.activity.result.PickVisualMediaRequest(
                            ActivityResultContracts.PickVisualMedia.ImageAndVideo,
                        ),
                    )
                }, shape = MaterialTheme.shapes.medium) {
                    Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Galerie")
                }
                Button(onClick = {
                    val ziel = kameraDatei("jpg")
                    kameraZiel = ziel
                    fotoAufnahme.launch(ziel.second)
                }, shape = MaterialTheme.shapes.medium) {
                    Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Kamera Foto")
                }
                Button(onClick = {
                    val ziel = kameraDatei("mp4")
                    kameraZiel = ziel
                    videoAufnahme.launch(ziel.second)
                }, shape = MaterialTheme.shapes.medium) {
                    Icon(Icons.Filled.Videocam, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Kamera Video")
                }
            }

            if (medien.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(medien.size) { index ->
                        val medium = medien[index]
                        Box {
                            if (medium.istVideo) {
                                Box(
                                    modifier = Modifier
                                        .size(100.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xDD000000)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Filled.PlayCircle,
                                        contentDescription = "Video",
                                        tint = Color.White,
                                        modifier = Modifier.size(48.dp),
                                    )
                                }
                            } else {
                                AsyncImage(
                                    model = medium.datei,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(100.dp).clip(RoundedCornerShape(8.dp)),
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(2.dp)
                                    .size(24.dp)
                                    .background(MaterialTheme.colorScheme.error, CircleShape)
                                    .clickable { medien.removeAt(index) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "Entfernen",
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(28.dp))

            // Upload-Fortschritt
            if (speichert && medien.isNotEmpty()) {
                val fortschritt = uploadFortschritt
                val laeuftNoch = fortschritt == null || fortschritt < 1f
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when {
                            !laeuftNoch -> "Medien werden verarbeitet…"
                            fortschritt == null -> "Upload wird vorbereitet…"
                            else -> "Medien werden hochgeladen… ${(fortschritt * 100).toInt()}%"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Spacer(Modifier.height(8.dp))
                if (laeuftNoch && fortschritt != null) {
                    LinearProgressIndicator(
                        progress = { fortschritt },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                    )
                }
                Spacer(Modifier.height(16.dp))
            }

            // Speichern
            Button(
                onClick = { if (!speichert) speichern() },
                enabled = !speichert,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                if (speichert) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text(if (speichert) "Speichern..." else "Speichern")
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

    if (zeigeErstellerDialog) {
        AlertDialog(
            onDismissRequest = { zeigeErstellerDialog = false },
            title = { Text("Kein Ersteller ausgewählt") },
            text = {
                Text(
                    if (nutzer.isEmpty()) {
                        "Es ist noch keine Person angelegt. Lege in den Einstellungen " +
                            "mindestens eine Person an, um sie hier als Ersteller " +
                            "auswählen zu können."
                    } else {
                        "Bitte wähle einen Ersteller aus oder lege in den " +
                            "Einstellungen eine weitere Person an."
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    zeigeErstellerDialog = false
                    onEinstellungen()
                }) { Text("Zu den Einstellungen") }
            },
            dismissButton = {
                TextButton(onClick = { zeigeErstellerDialog = false }) { Text("Abbrechen") }
            },
        )
    }
}
