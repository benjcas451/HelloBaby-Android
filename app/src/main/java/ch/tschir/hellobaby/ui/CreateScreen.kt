package ch.tschir.hellobaby.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import ch.tschir.hellobaby.R
import ch.tschir.hellobaby.data.ApiService
import ch.tschir.hellobaby.data.AppSettings
import ch.tschir.hellobaby.isVideoFile
import ch.tschir.hellobaby.kDiaries
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

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
    // Anzahl Medien, die gerade übernommen werden – große Videos brauchen
    // dafür mehrere Sekunden (Kopie aus dem MediaStore).
    var medienLadenAnzahl by remember { mutableStateOf(0) }
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

    /**
     * Kopiert eine Picker-Uri in den Cache, damit Name+Inhalt stabil bleiben.
     * Läuft auf dem IO-Dispatcher – große Videos würden den UI-Thread sonst
     * sekundenlang blockieren.
     */
    suspend fun uebernehmeUri(uri: Uri) {
        runCatching {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
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
                GewaehltesMedium(ziel, mime.startsWith("video") || isVideoFile(name))
            }
        }.fold(
            onSuccess = { medien.add(it) },
            onFailure = { snackbar.showSnackbar("Das Medium ließ sich nicht übernehmen: ${it.message}") },
        )
    }

    val galerieWahl = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(),
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        medienLadenAnzahl += uris.size
        scope.launch {
            uris.forEach { uri ->
                uebernehmeUri(uri)
                medienLadenAnzahl = (medienLadenAnzahl - 1).coerceAtLeast(0)
            }
        }
    }

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
        if (medienLadenAnzahl > 0) {
            scope.launch { snackbar.showSnackbar("Bitte kurz warten, die Medien werden noch übernommen") }
            return
        }
        val fehlend = diary.fields.filter { it.required && werte.value[it.key].orEmpty().isBlank() }
        if (fehlend.isNotEmpty()) {
            scope.launch {
                snackbar.showSnackbar("Bitte ${fehlend.joinToString(" und ") { "„${it.label}“" }} ausfüllen")
            }
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
                    // Die Zwischenkopien unter cacheDir/captures haben ihren
                    // Zweck erfuellt: lokal hat createEntry sie in den
                    // media-Ordner kopiert, im Server-Modus sind sie
                    // hochgeladen. Ohne das Aufraeumen bliebe jedes Foto und
                    // jedes Video dauerhaft ein zweites Mal auf dem Geraet.
                    val temporaer = medien.map { it.datei }
                    medien.clear()
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        temporaer.forEach { runCatching { it.delete() } }
                    }
                    speichert = false
                    uploadFortschritt = null
                    AppMeldung.senden("Eintrag gespeichert")
                    onZurueck()
                },
                onFailure = {
                    speichert = false
                    uploadFortschritt = null
                    snackbar.showSnackbar("Speichern fehlgeschlagen: ${it.message}")
                },
            )
        }
    }

    Scaffold(
        topBar = { MhKopf(titel = "Neuer Eintrag", untertitel = diary.title, onZurueck = onZurueck) },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = Mh.farben.bg,
    ) { innen ->
        Column(
            modifier = Modifier
                .padding(innen)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Mh.rand)
                .padding(top = Mh.abstand2, bottom = Mh.abstand8),
            verticalArrangement = Arrangement.spacedBy(Mh.abstand4),
        ) {
            val f = Mh.farben
            // Datum: sieht aus wie ein Feld, öffnet die Datumsauswahl.
            MhFeld("Datum") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = Mh.touch)
                        .clip(MhFeldForm)
                        .background(f.fieldBg)
                        .border(1.dp, f.borderStrong, MhFeldForm)
                        .clickable(role = Role.Button, onClickLabel = "Datum ändern") { zeigeDatumswahl = true }
                        .padding(horizontal = Mh.abstand4),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        datum.format(DateTimeFormatter.ofPattern("EEEE, dd.MM.yyyy", Locale.GERMAN)),
                        style = MhTypo.body,
                        color = f.text,
                        modifier = Modifier.weight(1f),
                    )
                    MhSymbol(R.drawable.ic_mh_calendar, null, farbe = f.textSecondary)
                }
            }

            // Felder des Tagebuchs: Pflichtfeld ohne Zusatz, alles andere „optional“.
            for (feld in diary.fields) {
                MhFeld(feld.label, zusatz = if (feld.required) null else "optional") {
                    OutlinedTextField(
                        value = werte.value[feld.key].orEmpty(),
                        onValueChange = { neu ->
                            werte.value = werte.value.toMutableMap().apply { put(feld.key, neu) }
                        },
                        suffix = if (feld.unit.isNotEmpty()) {
                            { Text(feld.unit, style = MhTypo.body, color = f.textSecondary) }
                        } else null,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = if (feld.numeric) KeyboardType.Decimal else KeyboardType.Text,
                            capitalization = if (feld.numeric) KeyboardCapitalization.None else KeyboardCapitalization.Sentences,
                        ),
                        textStyle = MhTypo.body,
                        colors = mhFeldFarben(),
                        shape = MhFeldForm,
                        minLines = if (feld.textarea) 4 else 1,
                        maxLines = if (feld.textarea) 8 else 1,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // Ersteller als Auswahl
            MhFeld(
                "Von",
                hinweis = if (nutzer.isEmpty()) "Personen legst du in den Einstellungen an." else null,
            ) {
                if (nutzer.isEmpty()) {
                    MhTextButton(
                        "Person anlegen",
                        { zeigeErstellerDialog = true },
                        symbol = R.drawable.ic_mh_user_plus,
                    )
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        nutzer.forEach { name ->
                            MhAuswahl(name, gewaehlt = vonName == name, onClick = { vonName = name })
                        }
                    }
                }
            }

            // Medien
            MhFeld("Fotos und Videos", zusatz = "optional") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Mh.abstand2),
                    verticalArrangement = Arrangement.spacedBy(Mh.abstand2),
                ) {
                    MhZweitButton("Auswählen", {
                        galerieWahl.launch(
                            androidx.activity.result.PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageAndVideo,
                            ),
                        )
                    }, symbol = R.drawable.ic_mh_image)
                    MhZweitButton("Foto", {
                        val ziel = kameraDatei("jpg")
                        kameraZiel = ziel
                        fotoAufnahme.launch(ziel.second)
                    }, symbol = R.drawable.ic_mh_camera)
                    MhZweitButton("Video", {
                        val ziel = kameraDatei("mp4")
                        kameraZiel = ziel
                        videoAufnahme.launch(ziel.second)
                    }, symbol = R.drawable.ic_mh_video)
                }

                if (medien.isNotEmpty() || medienLadenAnzahl > 0) {
                    Spacer(Modifier.height(Mh.abstand1))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        // Platzhalter für Medien, die noch übernommen werden.
                        items(medienLadenAnzahl) {
                            Box(
                                Modifier.size(88.dp).clip(RoundedCornerShape(Mh.radiusSm)).background(f.surfaceSubtle),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(Modifier.size(22.dp), color = f.primary, strokeWidth = 2.dp)
                            }
                        }
                        items(medien.size) { index ->
                            val medium = medien[index]
                            Box(Modifier.size(88.dp).clip(RoundedCornerShape(Mh.radiusSm)).background(f.surfaceSubtle)) {
                                if (!medium.istVideo) {
                                    AsyncImage(
                                        model = medium.datei,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.size(88.dp),
                                    )
                                } else {
                                    VideoMarke(Modifier.align(Alignment.Center))
                                }
                                Box(
                                    Modifier
                                        .align(Alignment.TopEnd)
                                        .size(Mh.touch)
                                        .clickable(role = Role.Button, onClickLabel = "Entfernen") { medien.removeAt(index) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Box(
                                        Modifier.size(24.dp).clip(CircleShape).background(Color(0x99000000)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        MhSymbol(R.drawable.ic_mh_close, "Entfernen", farbe = Color.White, groesse = 14.dp)
                                    }
                                }
                            }
                        }
                    }
                }
                if (medienLadenAnzahl > 0) {
                    Text("Medien werden übernommen, bitte kurz warten.", style = MhTypo.small, color = f.textSecondary)
                }
            }

            // Upload-Fortschritt
            if (speichert && medien.isNotEmpty()) {
                val fortschritt = uploadFortschritt
                val laeuftNoch = fortschritt == null || fortschritt < 1f
                Column(verticalArrangement = Arrangement.spacedBy(Mh.abstand2)) {
                    Text(
                        when {
                            !laeuftNoch -> "Medien werden verarbeitet …"
                            fortschritt == null -> "Upload wird vorbereitet …"
                            else -> "Medien werden hochgeladen · ${(fortschritt * 100).toInt()} %"
                        },
                        style = MhTypo.small,
                        color = f.textSecondary,
                    )
                    if (laeuftNoch && fortschritt != null) {
                        LinearProgressIndicator(
                            progress = { fortschritt },
                            modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                            color = f.primary,
                            trackColor = f.dataTrack,
                            drawStopIndicator = {},
                        )
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                            color = f.primary,
                            trackColor = f.dataTrack,
                        )
                    }
                }
            }

            // Die eine Hauptaktion
            MhHauptButton(
                text = when {
                    medienLadenAnzahl > 0 -> "Medien werden übernommen …"
                    speichert -> "Wird gespeichert …"
                    else -> "Eintrag speichern"
                },
                onClick = { if (!speichert) speichern() },
                enabled = !speichert && medienLadenAnzahl == 0,
                modifier = Modifier.fillMaxWidth().padding(top = Mh.abstand2),
            )
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

    if (zeigeErstellerDialog) {
        AlertDialog(
            onDismissRequest = { zeigeErstellerDialog = false },
            title = { Text("Wer schreibt den Eintrag", style = MhTypo.h2) },
            text = {
                Text(
                    if (nutzer.isEmpty()) {
                        "Es ist noch keine Person angelegt. Lege in den Einstellungen " +
                            "mindestens eine Person an, dann kannst du sie hier auswählen."
                    } else {
                        "Bitte wähle aus, von wem der Eintrag ist, oder lege in den " +
                            "Einstellungen eine weitere Person an."
                    },
                    style = MhTypo.body,
                )
            },
            confirmButton = {
                MhTextButton("Zu den Einstellungen", {
                    zeigeErstellerDialog = false
                    onEinstellungen()
                })
            },
            dismissButton = { MhTextButton("Abbrechen", { zeigeErstellerDialog = false }) },
        )
    }
}
