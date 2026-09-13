package ch.tschir.hellobaby.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import ch.tschir.hellobaby.data.MedienClient
import ch.tschir.hellobaby.isLocalMediaSource

/**
 * Vollbild-Videoplayer (Media3/ExoPlayer) für lokale Dateien und Server-URLs.
 *
 * Server-URLs laufen über denselben OkHttp-Client wie der Rest der App. Ohne
 * dessen Kopfzeilen blockiert Cloudflare Access die Anfrage am Rand, und der
 * Player zeigte nur einen schwarzen Bildschirm.
 */
@Composable
fun VideoPlayerScreen(url: String, onZurueck: () -> Unit) {
    val context = LocalContext.current
    val player = remember {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(
                    OkHttpDataSource.Factory(MedienClient.callFactory(context)),
                ),
            )
            .build()
            .apply {
                val uri = if (isLocalMediaSource(url)) {
                    android.net.Uri.fromFile(java.io.File(url))
                } else {
                    android.net.Uri.parse(url)
                }
                setMediaItem(MediaItem.fromUri(uri))
                prepare()
                playWhenReady = true
            }
    }

    DisposableEffect(Unit) {
        onDispose { player.release() }
    }

    Scaffold(
        topBar = { HbTopBar(titel = "Video", onZurueck = onZurueck) },
        containerColor = Color.Black,
    ) { innen ->
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    setShowNextButton(false)
                    setShowPreviousButton(false)
                }
            },
            modifier = Modifier.padding(innen).fillMaxSize(),
        )
    }
}
