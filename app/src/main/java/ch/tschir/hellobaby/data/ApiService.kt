package ch.tschir.hellobaby.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ch.tschir.hellobaby.Entry
import ch.tschir.hellobaby.RandomDay
import ch.tschir.hellobaby.StatsResult
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import okio.buffer
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Fehler einer API-Anfrage (Statuscode + Meldung). */
class ApiException(message: String, val statusCode: Int? = null) : Exception(message) {
    override fun toString(): String =
        if (statusCode != null) "Fehler $statusCode: $message" else message.orEmpty()
}

/**
 * Zentrale Datenquelle: je nach Modus die lokale Ablage ([LocalStorageService])
 * oder die Server-API unter `<serverBase>/api/…`.
 *
 * Authentifizierung: `X-API-Key`-Header in beiden Server-Modi (falls Key
 * hinterlegt), bei mTLS zusätzlich das Client-Zertifikat im TLS-Handshake.
 * Bilder/Thumbs/Medien liefert der Server offen aus (kein Auth-Header nötig).
 */
class ApiService(context: Context) {

    private val appContext = context.applicationContext
    private val settings = AppSettings(appContext)
    private val certSource = CertSource(appContext, settings)
    private val local = LocalStorageService.getInstance(appContext)

    private var client: OkHttpClient? = null
    private var clientMode: DataSourceMode? = null

    private val isLocal get() = settings.mode == DataSourceMode.LOCAL

    /** Basis-URL der REST-API (`<serverBase>/api`). */
    private val apiBase: String
        get() {
            val base = settings.serverBase
            if (base.isEmpty()) {
                throw ApiException(
                    "Keine Server-URL konfiguriert. Bitte in den Einstellungen eintragen.",
                )
            }
            return "$base/api"
        }

    /** Volle URL einer Mediendatei zum Streamen (offen, ohne Auth). */
    fun mediaUrl(relPath: String, download: Boolean = false): String =
        "${settings.serverBase}/api/media.php?file=${android.net.Uri.encode(relPath)}" +
            if (download) "&download=1" else ""

    /** URL zum Vorschaubild bzw. Video-Poster. */
    fun thumbUrl(relPath: String, width: Int = 400): String =
        "${settings.serverBase}/api/thumb.php?file=${android.net.Uri.encode(relPath)}&w=$width"

    /** Verwirft den gecachten HTTP-Client (nach Einstellungsänderungen). */
    fun reset() {
        client?.dispatcher?.executorService?.shutdown()
        client?.connectionPool?.evictAll()
        client = null
        clientMode = null
    }

    private suspend fun httpClient(): OkHttpClient {
        val mode = settings.mode
        client?.let { if (clientMode == mode) return it }
        client?.connectionPool?.evictAll()
        val builder = OkHttpClient.Builder()
        if (mode == DataSourceMode.MTLS) {
            val (cert, key) = certSource.readCredentials()
            val (factory, trust) = ClientCertificates.socketFactoryMitTrust(cert, key)
            builder.sslSocketFactory(factory, trust)
        }
        return builder.build().also {
            client = it
            clientMode = mode
        }
    }

    private fun Request.Builder.auth(): Request.Builder {
        val key = settings.apiKey
        if (key.isNotEmpty()) header("X-API-Key", key)
        return this
    }

    // ── Stats / Zufallstag ───────────────────────────────────────────────────

    suspend fun getStats(diary: String, excludeRandomDate: String? = null): StatsResult {
        if (isLocal) return local.getStats(diary)
        val url = "$apiBase/stats.php".toHttpUrlOrNull()!!.newBuilder()
            .addQueryParameter("diary", diary)
            .apply { excludeRandomDate?.let { addQueryParameter("exclude_date", it) } }
            .build()
        val data = get(url.toString()) as? JSONObject
            ?: throw ApiException("Unerwartete Stats-Antwort.")
        return StatsResult(
            first = data.optString("first"),
            last = data.optString("last"),
            randomDate = data.optString("random_date"),
        )
    }

    /** Frischer Zufallstag; meidet [exclude], wenn mehrere Tage existieren. */
    suspend fun getRandomDate(diary: String, exclude: String?): String? {
        if (isLocal) {
            return RandomDay.pick(local.distinctDates(diary), exclude = exclude)
        }
        repeat(5) {
            val stats = getStats(diary, excludeRandomDate = exclude)
            val date = stats.randomDate
            if (date.isEmpty()) return null
            if (stats.first == stats.last) return date
            if (date != exclude) return date
        }
        return null
    }

    // ── Einträge ────────────────────────────────────────────────────────────

    suspend fun getEntriesByDate(date: String, diary: String): List<Entry> {
        if (isLocal) return local.entriesByDate(date, diary)
        return fetchEntries("date" to date, "diary" to diary)
    }

    suspend fun getEntriesByMonth(year: Int, month: Int, diary: String): List<Entry> {
        if (isLocal) return local.entriesByMonth(year, month, diary)
        return fetchEntries("year" to year.toString(), "month" to month.toString(), "diary" to diary)
    }

    suspend fun getFavorites(diary: String): List<Entry> {
        if (isLocal) return local.favorites(diary)
        return fetchEntries("favorites" to "1", "diary" to diary)
    }

    suspend fun getEntriesWithImages(diary: String): List<Entry> {
        if (isLocal) return local.entriesWithImages(diary)
        return fetchEntries("images" to "1", "diary" to diary)
    }

    private suspend fun fetchEntries(vararg query: Pair<String, String>): List<Entry> {
        val url = "$apiBase/entries.php".toHttpUrlOrNull()!!.newBuilder()
            .apply { query.forEach { (k, v) -> addQueryParameter(k, v) } }
            .build()
        val data = get(url.toString()) as? JSONArray
            ?: throw ApiException("Unerwartete Antwort der API.")
        return List(data.length()) { Entry.fromJson(data.getJSONObject(it)) }
    }

    /**
     * Legt einen Eintrag an. [onSendProgress] meldet gesendete/gesamte Bytes
     * des Uploads (nur im Server-Modus mit Medien).
     */
    suspend fun createEntry(
        kalenderDatum: String,
        fields: Map<String, String>,
        vonName: String,
        images: List<File>,
        diary: String,
        onSendProgress: ((sent: Long, total: Long) -> Unit)? = null,
    ): Int {
        if (isLocal) {
            return local.createEntry(kalenderDatum, fields, vonName, images, diary)
        }
        val multipart = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("diary", diary)
            .addFormDataPart("kalender_datum", kalenderDatum)
            .addFormDataPart("von_name", vonName)
            .apply {
                fields.forEach { (key, value) -> addFormDataPart(key, value) }
                images.forEach { file ->
                    addFormDataPart(
                        "images[]", file.name,
                        file.asRequestBody("application/octet-stream".toMediaType()),
                    )
                }
            }
            .build()
        val body: RequestBody = if (onSendProgress != null && images.isNotEmpty()) {
            FortschrittBody(multipart, onSendProgress)
        } else {
            multipart
        }
        val data = ausfuehren(
            Request.Builder().url("$apiBase/entries.php").auth().post(body).build(),
        ) as? JSONObject ?: throw ApiException("Unerwartete Antwort beim Erstellen.")
        return data.optInt("id")
    }

    suspend fun deleteEntry(id: Int, diary: String) {
        if (isLocal) return local.deleteEntry(id, diary)
        val url = "$apiBase/entries.php".toHttpUrlOrNull()!!.newBuilder()
            .addQueryParameter("id", id.toString())
            .addQueryParameter("diary", diary)
            .build()
        ausfuehren(Request.Builder().url(url).auth().delete().build())
    }

    suspend fun toggleFavorite(id: Int, diary: String): Int {
        if (isLocal) return local.toggleFavorite(id, diary)
        val body = JSONObject().put("id", id).put("diary", diary).toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())
        val data = ausfuehren(
            Request.Builder().url("$apiBase/favorite.php").auth().post(body).build(),
        ) as? JSONObject ?: throw ApiException("Unerwartete Favorit-Antwort.")
        return data.optInt("favorit")
    }

    suspend fun getGalleryFiles(folder: String): List<String> {
        if (isLocal) return local.galleryFiles(folder)
        val url = "$apiBase/gallery.php".toHttpUrlOrNull()!!.newBuilder()
            .addQueryParameter("folder", folder)
            .build()
        val data = get(url.toString()) as? JSONObject
            ?: throw ApiException("Unerwartete Galerie-Antwort.")
        val files = data.optJSONArray("files") ?: JSONArray()
        // Versteckte Server-Hilfsordner (.orig, .thumbs) ausblenden.
        return List(files.length()) { files.optString(it) }.filterNot { it.startsWith(".") }
    }

    // ── Transport ───────────────────────────────────────────────────────────

    private suspend fun get(url: String): Any? =
        ausfuehren(Request.Builder().url(url).auth().get().build())

    private suspend fun ausfuehren(request: Request): Any? = withContext(Dispatchers.IO) {
        httpClient().newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (response.code !in 200..299) {
                val meldung = runCatching { JSONObject(text).optString("error") }
                    .getOrNull()?.takeIf { it.isNotEmpty() }
                    ?: "Anfrage fehlgeschlagen (${response.code})"
                throw ApiException(meldung, statusCode = response.code)
            }
            if (text.isEmpty()) return@use null
            runCatching { JSONObject(text) as Any }
                .recoverCatching { JSONArray(text) as Any }
                .getOrNull()
        }
    }

    /** RequestBody-Hülle, die den Sende-Fortschritt pro Chunk meldet. */
    private class FortschrittBody(
        private val delegat: RequestBody,
        private val onProgress: (Long, Long) -> Unit,
    ) : RequestBody() {
        override fun contentType() = delegat.contentType()
        override fun contentLength() = delegat.contentLength()

        override fun writeTo(sink: BufferedSink) {
            val total = contentLength()
            var gesendet = 0L
            val zaehler = object : okio.ForwardingSink(sink) {
                override fun write(source: okio.Buffer, byteCount: Long) {
                    super.write(source, byteCount)
                    gesendet += byteCount
                    onProgress(gesendet, total)
                }
            }
            val gepuffert = zaehler.buffer()
            delegat.writeTo(gepuffert)
            gepuffert.flush()
        }
    }
}
