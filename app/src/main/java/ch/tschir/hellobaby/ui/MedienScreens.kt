package ch.tschir.hellobaby.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ch.tschir.hellobaby.Entry
import ch.tschir.hellobaby.R
import ch.tschir.hellobaby.Ziel
import ch.tschir.hellobaby.data.ApiService
import ch.tschir.hellobaby.isImageFile
import ch.tschir.hellobaby.isLocalMediaSource
import ch.tschir.hellobaby.isVideoFile
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch

// ── Reiter „Galerie“ (alle Einträge mit Medien, 3er-Raster nach Tagen) ──────

@Composable
fun GalerieInhalt(
    api: ApiService,
    aktivesDiary: String,
    schluessel: String,
    onNavigate: (Ziel) -> Unit,
) {
    var eintraege by remember { mutableStateOf<List<Entry>>(emptyList()) }
    var laedt by remember { mutableStateOf(true) }
    var fehler by remember { mutableStateOf<String?>(null) }
    var ladeZaehler by remember { mutableIntStateOf(0) }
    var vollbild by remember { mutableStateOf<Vollbild?>(null) }

    LaunchedEffect(ladeZaehler, schluessel) {
        laedt = true
        fehler = null
        runCatching { api.getEntriesWithImages(aktivesDiary) }
            .onSuccess { eintraege = it }
            .onFailure { fehler = it.message ?: it.toString() }
        laedt = false
    }

    vollbild?.let { aktiv ->
        FullscreenGallery(urls = aktiv.urls, initialIndex = aktiv.index, onSchliessen = { vollbild = null })
    }

    when {
        laedt -> LadeAnsicht()
        fehler != null -> FehlerAnsicht("Die Galerie konnte nicht geladen werden. $fehler") { ladeZaehler++ }
        else -> {
            // Kacheln je Tag: (Eintrag, Datei)-Paare. An [eintraege] gebunden,
            // sonst baute jede Recomposition die Map neu auf.
            val gruppen = remember(eintraege) {
                linkedMapOf<String, MutableList<Pair<Entry, String>>>().apply {
                    for (eintrag in eintraege) {
                        for (datei in eintrag.bilderFiles.filter { isImageFile(it) || isVideoFile(it) }) {
                            getOrPut(eintrag.kalenderDatum) { mutableListOf() }.add(eintrag to datei)
                        }
                    }
                }
            }
            if (gruppen.isEmpty()) {
                LeerAnsicht("Noch keine Einträge mit Fotos oder Videos")
                return
            }
            val tage = remember(gruppen) { gruppen.keys.sortedDescending() }
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = Mh.rand, end = Mh.rand, bottom = Mh.abstand8),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                tage.forEach { tag ->
                    item(span = { GridItemSpan(3) }) { MhGruppenLabel(wochentagsDatum(tag)) }
                    items(gruppen[tag]!!) { (eintrag, datei) ->
                        val relPath = "${eintrag.bilder}/$datei"
                        val istVideo = isVideoFile(datei)
                        MedienKachel(api, relPath, istVideo) {
                            if (istVideo) {
                                onNavigate(Ziel.Video(quelleAlsString(api, relPath)))
                            } else {
                                val bilder = eintrag.bilderFiles.filter(::isImageFile)
                                vollbild = Vollbild(
                                    urls = bilder.map { quelleAlsString(api, "${eintrag.bilder}/$it") },
                                    index = bilder.indexOf(datei).coerceAtLeast(0),
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
private fun MedienKachel(api: ApiService, relPath: String, istVideo: Boolean, onTap: () -> Unit) {
    Box(
        Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(Mh.radiusSm))
            .background(Mh.farben.surfaceSubtle)
            .clickable(onClick = onTap),
    ) {
        AsyncImage(
            model = if (istVideo && isLocalMediaSource(relPath)) java.io.File(relPath) else api.thumbnailSource(relPath),
            contentDescription = if (istVideo) "Video" else "Foto",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (istVideo) VideoMarke(Modifier.align(Alignment.Center))
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
        FullscreenGallery(urls = aktiv.urls, initialIndex = aktiv.index, onSchliessen = { vollbild = null })
    }

    Scaffold(
        topBar = {
            MhKopf(
                titel = "Fotos",
                onZurueck = onZurueck,
                symbole = { MhSymbolButton(R.drawable.ic_mh_refresh, "Neu laden", { ladeZaehler++ }) },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = Mh.farben.bg,
    ) { innen ->
        Box(Modifier.padding(innen).fillMaxSize()) {
            when {
                laedt -> LadeAnsicht()
                fehler != null -> FehlerAnsicht("Die Fotos konnten nicht geladen werden. $fehler") { ladeZaehler++ }
                dateien.isEmpty() -> LeerAnsicht("Keine Dateien zu diesem Eintrag")
                else -> {
                    val medien = dateien.filter { isImageFile(it) || isVideoFile(it) }
                    val andere = dateien - medien.toSet()
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = Mh.rand, end = Mh.rand, bottom = Mh.abstand8),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(medien) { datei ->
                            val relPath = "$folder/$datei"
                            val istVideo = isVideoFile(datei)
                            MedienKachel(api, relPath, istVideo) {
                                if (istVideo) {
                                    onNavigate(Ziel.Video(quelleAlsString(api, relPath)))
                                } else {
                                    val bilder = dateien.filter(::isImageFile)
                                    vollbild = Vollbild(
                                        urls = bilder.map { quelleAlsString(api, "$folder/$it") },
                                        index = bilder.indexOf(datei).coerceAtLeast(0),
                                    )
                                }
                            }
                        }
                        if (andere.isNotEmpty()) {
                            item(span = { GridItemSpan(2) }) { MhGruppenLabel("Weitere Dateien") }
                            items(andere, span = { GridItemSpan(2) }) { datei ->
                                val relPath = "$folder/$datei"
                                if (ext(datei) in downloadTypen) {
                                    MhListenZeile(
                                        datei,
                                        untertitel = ext(datei).uppercase(),
                                        symbol = R.drawable.ic_mh_download,
                                        onClick = {
                                            scope.launch {
                                                snackbar.showSnackbar("Adresse: ${api.mediaUrl(relPath, download = true)}")
                                            }
                                        },
                                    )
                                } else {
                                    MhListenZeile(
                                        datei,
                                        untertitel = "Dateityp ${ext(datei)} wird nicht angezeigt",
                                        symbol = R.drawable.ic_mh_file,
                                    )
                                }
                            }
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
    Dialog(
        onDismissRequest = onSchliessen,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val pager = rememberPagerState(initialPage = initialIndex) { urls.size }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { seite ->
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
                        contentDescription = "Foto ${seite + 1} von ${urls.size}",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(scaleX = zoom, scaleY = zoom, translationX = versatzX, translationY = versatzY),
                    )
                }
            }
            // Leiste oben: Schließen und Zähler, hell auf dem schwarzen Grund.
            Row(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = Mh.abstand2)
                    .height(56.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MhSymbolButton(R.drawable.ic_mh_close, "Schließen", onSchliessen, farbe = Color.White)
                Text(
                    "${pager.currentPage + 1} / ${urls.size}",
                    style = MhTypo.label.copy(fontFeatureSettings = "tnum"),
                    color = Color.White,
                )
            }
        }
    }
}
