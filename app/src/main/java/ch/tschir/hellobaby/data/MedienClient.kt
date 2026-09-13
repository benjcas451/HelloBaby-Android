package ch.tschir.hellobaby.data

import android.content.Context
import coil3.ImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.video.VideoFrameDecoder
import java.time.Duration
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.OkHttpClient

/**
 * Der HTTP-Client für Medien: Vorschaubilder und Vollbilder (Coil) sowie
 * Videos (ExoPlayer).
 *
 * Diese Ladewege gingen bisher an [ApiService] vorbei und damit an der
 * Authentifizierung: Coil und ExoPlayer bauen ihre Verbindung selbst auf,
 * ohne `X-API-Key` und ohne Client-Zertifikat. Solange der Server Medien offen
 * auslieferte, fiel das nicht auf — hinter Cloudflare Access blockiert der
 * Rand jede dieser Anfragen, und zwar bevor sie den Server erreicht.
 *
 * Die Kopfzeilen setzt ein Interceptor bei jeder Anfrage neu, damit ein
 * geänderter Key oder ein neues Service Token sofort greift. Nur das
 * Client-Zertifikat hängt am Client selbst und zwingt deshalb zum Neubau,
 * wenn der Modus wechselt — dieselbe Bedingung wie in [ApiService].
 *
 * [get] baut den Client beim ersten Zugriff und liest dabei blockierend das
 * Zertifikat. Die Aufrufer (Coils Fetcher-Factory, ExoPlayers
 * DataSource-Factory) laufen beide auf einem Hintergrund-Thread; auf dem
 * Main-Thread hat diese Methode nichts verloren.
 */
object MedienClient {

    private var client: OkHttpClient? = null
    private var clientMode: DataSourceMode? = null

    @Synchronized
    fun get(context: Context): OkHttpClient {
        val appContext = context.applicationContext
        // Eine Instanz genügt: AppSettings liest jede Eigenschaft frisch aus
        // den SharedPreferences, der Interceptor sieht also stets den
        // aktuellen Stand.
        val settings = AppSettings(appContext)
        val mode = settings.mode
        client?.let { if (clientMode == mode) return it }
        client?.connectionPool?.evictAll()

        val builder = OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(20))
            .readTimeout(Duration.ofMinutes(2))
            .addInterceptor { kette ->
                val anfrage = kette.request().newBuilder().apply {
                    // Bei jeder Anfrage frisch gelesen: so wirkt ein geänderter
                    // Key sofort, ohne den Client neu zu bauen.
                    for ((feld, wert) in settings.authHeader()) {
                        header(feld, wert)
                    }
                }.build()
                kette.proceed(anfrage)
            }

        if (mode == DataSourceMode.MTLS) {
            // Schlaegt das Lesen fehl (Ordner weg, Datei geloescht), bleibt es
            // beim Client ohne Zertifikat: die Medien laden dann nicht, was
            // aber besser ist als ein Absturz beim Aufbau der Oberflaeche.
            runCatching {
                val (cert, key) = runBlocking { CertSource(appContext, settings).readCredentials() }
                ClientCertificates.socketFactoryMitTrust(cert, key)
            }.onSuccess { (factory, trust) ->
                builder.sslSocketFactory(factory, trust)
            }
        }

        return builder.build().also {
            client = it
            clientMode = mode
        }
    }

    /**
     * Eine `Call.Factory`, die den Client erst beim tatsächlichen Aufruf holt.
     * Coil und ExoPlayer werden beim Aufbau der Oberfläche konfiguriert, also
     * auf dem Main-Thread — das blockierende Lesen des Zertifikats in [get]
     * wandert damit auf den jeweiligen Lade-Thread.
     */
    fun callFactory(context: Context): Call.Factory {
        val appContext = context.applicationContext
        return Call.Factory { request -> get(appContext).newCall(request) }
    }

    /**
     * Der Bild-Lader für Coil. Ersetzt dessen Standard-Netzwerkzugriff durch
     * [callFactory]; alles andere bleibt beim Standard. `VideoFrameDecoder`
     * ist ausdrücklich dabei, damit die Vorschaubilder lokaler Videos wie
     * bisher funktionieren.
     */
    fun bildLader(context: Context): ImageLoader {
        val appContext = context.applicationContext
        return ImageLoader.Builder(appContext)
            .components {
                add(VideoFrameDecoder.Factory())
                add(OkHttpNetworkFetcherFactory(callFactory = { callFactory(appContext) }))
            }
            .build()
    }

    /** Verwirft den gecachten Client (nach Einstellungsänderungen). */
    @Synchronized
    fun reset() {
        client?.connectionPool?.evictAll()
        client = null
        clientMode = null
    }
}
