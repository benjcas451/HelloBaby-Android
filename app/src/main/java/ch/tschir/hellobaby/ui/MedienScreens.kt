package ch.tschir.hellobaby.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.PermMedia
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import ch.tschir.hellobaby.Entry
import ch.tschir.hellobaby.Ziel
import ch.tschir.hellobaby.data.ApiService
import ch.tschir.hellobaby.data.AppSettings
import ch.tschir.hellobaby.isImageFile
import ch.tschir.hellobaby.isLocalMediaSource
import ch.tschir.hellobaby.isVideoFile

// ── Medien-Feed (alle Einträge mit Medien, 3er-Grid nach Tagen) ─────────────

@Composable
fun ImageFeedScreen(
    settings: AppSettings,
    api: ApiService,
    onZurueck: () -> Unit,
    onNavigate: (Ziel) -> Unit,
) {
    var eintraege by remember { mutableStateOf<List<Entry>>(emptyList()) }
    var laedt by remember { mutableStateOf(true) }
    var fehler by remember { mutableStateOf<String?>(null) }
    var ladeZaehler by remember { mutableIntStateOf(0) }
    var vollbild by remember { mutableStateOf<Vollbild?>(null) }

    LaunchedEffect(ladeZaehler) {
        laedt = true
        fehler = null
        runCatching { api.getEntriesWithImages(settings.activeDiary) }
            .onSuccess { eintraege = it }
            .onFailure { fehler = it.message ?: it.toString() }
        laedt = false
    }

    vollbild?.let { aktiv ->
        FullscreenGallery(
            urls = aktiv.urls,
            initialIndex = aktiv.index,
            onSchliessen = { vollbild = null },
        )
        return
    }

    Scaffold(
        topBar = {
            HbTopBar(titel = "Galerie", onZurueck = onZurueck) {
                IconButton(onClick = { ladeZaehler++ }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Neu laden")
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innen ->
        Column(modifier = Modifier.padding(innen).fillMaxSize()) {
            when {
                laedt -> LadeAnsicht()
                fehler != null -> FehlerAnsicht("Fehler: $fehler") { ladeZaehler++ }
                else -> {
                    // Kacheln je Tag: (Eintrag, Datei)-Paare.
                    val gruppen = linkedMapOf<String, MutableList<Pair<Entry, String>>>()
                    for (eintrag in eintraege) {
                        val medien = eintrag.bilderFiles.filter { isImageFile(it) || isVideoFile(it) }
                        for (datei in medien) {
                            gruppen.getOrPut(eintrag.kalenderDatum) { mutableListOf() }
                                .add(eintrag to datei)
                        }
                    }
                    if (gruppen.isEmpty()) {
                        LeerAnsicht(Icons.Filled.PermMedia, "Noch keine Beiträge mit Medien vorhanden")
                        return@Column
                    }
                    val tage = gruppen.keys.sortedDescending()

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
                    ) {
                        tage.forEach { tag ->
                            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
                                Text(
                                    wochentagsDatum(tag),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
                                )
                            }
                            items(gruppen[tag]!!) { (eintrag, datei) ->
                                val relPath = "${eintrag.bilder}/$datei"
                                val istVideo = isVideoFile(datei)
                                MedienKachel(
                                    api = api,
                                    relPath = relPath,
                                    istVideo = istVideo,
                                    onTap = {
                                        if (istVideo) {
                                            onNavigate(Ziel.Video(quelleAlsString(api, relPath)))
                                        } else {
                                            val bilder = eintrag.bilderFiles.filter(::isImageFile)
                                            vollbild = Vollbild(
                                                urls = bilder.map { quelleAlsString(api, "${eintrag.bilder}/$it") },
                                                index = bilder.indexOf(datei).coerceAtLeast(0),
                                            )
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class Vollbild(val urls: List<String>, val index: Int)

/** Medienquelle als String (Dateipfad oder URL) fürs Weiterreichen. */
private fun quelleAlsString(api: ApiService, relPath: String): String =
    if (isLocalMediaSource(relPath)) relPath else api.mediaUrl(relPath)

@Composable
private fun MedienKachel(
    api: ApiService,
    relPath: String,
    istVideo: Boolean,
    onTap: () -> Unit,
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onTap),
    ) {
        if (istVideo && isLocalMediaSource(relPath)) {
            // Lokale Videos: Coil-Video liefert den Frame; dunkler Grund als Fallback.
            Box(modifier = Modifier.fillMaxSize().background(Color(0xDD000000)))
            AsyncImage(
                model = java.io.File(relPath),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            AsyncImage(
                model = api.thumbnailSource(relPath),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                error = androidx.compose.ui.res.painterResource(android.R.drawable.ic_menu_report_image),
            )
        }
        if (istVideo) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color(0x8A000000), CircleShape)
                    .padding(8.dp),
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = "Video", tint = Color.White)
            }
        }
    }
}

// ── Galerie eines Eintrags ──────────────────────────────────────────────────

@Composable
fun GalleryScreen(
    api: ApiService,
    folder: String,
    onZurueck: () -> Unit,
    onNavigate: (Ziel) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var dateien by remember { mutableStateOf<List<String>>(emptyList()) }
    var laedt by remember { mutableStateOf(true) }
    var fehler by remember { mutableStateOf<String?>(null) }
    var ladeZaehler by remember { mutableIntStateOf(0) }
    var vollbild by remember { mutableStateOf<Vollbild?>(null) }

    val downloadTypen = remember { setOf("heic", "psd") }
    fun ext(name: String) = if ("." in name) name.substringAfterLast('.').lowercase() else ""

    LaunchedEffect(ladeZaehler) {
        laedt = true
        fehler = null
        runCatching { api.getGalleryFiles(folder) }
            .onSuccess { dateien = it }
            .onFailure { fehler = it.message ?: it.toString() }
        laedt = false
    }

    vollbild?.let { aktiv ->
        FullscreenGallery(
            urls = aktiv.urls,
            initialIndex = aktiv.index,
            onSchliessen = { vollbild = null },
        )
        return
    }

    Scaffold(
        topBar = {
            HbTopBar(titel = "Galerie", onZurueck = onZurueck) {
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
                dateien.isEmpty() -> LeerAnsicht(Icons.Filled.PermMedia, "Keine Dateien in dieser Galerie")
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                ) {
                    items(dateien.size) { index ->
                        val datei = dateien[index]
                        val relPath = "$folder/$datei"
                        when {
                            isImageFile(datei) || isVideoFile(datei) -> {
                                val istVideo = isVideoFile(datei)
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 12.dp)
                                        .clickable {
                                            if (istVideo) {
                                                onNavigate(Ziel.Video(quelleAlsString(api, relPath)))
                                            } else {
                                                val bilder = dateien.filter(::isImageFile)
                                                vollbild = Vollbild(
                                                    urls = bilder.map { quelleAlsString(api, "$folder/$it") },
                                                    index = bilder.indexOf(datei).coerceAtLeast(0),
                                                )
                                            }
                                        },
                                    shape = MaterialTheme.shapes.large,
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                                ) {
                                    Box(modifier = Modifier.fillMaxWidth().height(240.dp)) {
                                        AsyncImage(
                                            model = api.thumbnailSource(relPath),
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                        if (istVideo) {
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.Center)
                                                    .background(Color(0x8A000000), CircleShape)
                                                    .padding(10.dp),
                                            ) {
                                                Icon(
                                                    Icons.Filled.PlayArrow,
                                                    contentDescription = "Video",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(40.dp),
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        datei,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(8.dp),
                                    )
                                }
                            }

                            ext(datei) in downloadTypen -> ListItem(
                                leadingContent = { Icon(Icons.Filled.FileDownload, contentDescription = null) },
                                headlineContent = { Text(datei) },
                                supportingContent = { Text(ext(datei).uppercase()) },
                                modifier = Modifier.clickable {
                                    scope.launch {
                                        snackbar.showSnackbar("URL: ${api.mediaUrl(relPath, download = true)}")
                                    }
                                },
                            )

                            else -> ListItem(
                                leadingContent = { Icon(Icons.Filled.InsertDriveFile, contentDescription = null) },
                                headlineContent = { Text(datei) },
                                supportingContent = { Text("Nicht unterstützter Typ: ${ext(datei)}") },
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Vollbild-Galerie mit Wisch-Navigation und Zoom ──────────────────────────

@Composable
fun FullscreenGallery(
    urls: List<String>,
    initialIndex: Int,
    onSchliessen: () -> Unit,
) {
    androidx.activity.compose.BackHandler(onBack = onSchliessen)
    val pager = rememberPagerState(initialPage = initialIndex) { urls.size }

    Scaffold(
        topBar = {
            HbTopBar(titel = "${pager.currentPage + 1} / ${urls.size}", onZurueck = onSchliessen)
        },
        containerColor = Color.Black,
    ) { innen ->
        HorizontalPager(
            state = pager,
            modifier = Modifier.padding(innen).fillMaxSize(),
        ) { seite ->
            var zoom by remember { mutableFloatStateOf(1f) }
            var versatzX by remember { mutableFloatStateOf(0f) }
            var versatzY by remember { mutableFloatStateOf(0f) }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoomDelta, _ ->
                            zoom = (zoom * zoomDelta).coerceIn(1f, 5f)
                            if (zoom > 1f) {
                                versatzX += pan.x
                                versatzY += pan.y
                            } else {
                                versatzX = 0f
                                versatzY = 0f
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                val quelle = urls[seite]
                AsyncImage(
                    model = if (isLocalMediaSource(quelle)) java.io.File(quelle) else quelle,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = zoom, scaleY = zoom,
                            translationX = versatzX, translationY = versatzY,
                        ),
                    error = androidx.compose.ui.res.painterResource(android.R.drawable.ic_menu_report_image),
                )
            }
        }
    }
}
