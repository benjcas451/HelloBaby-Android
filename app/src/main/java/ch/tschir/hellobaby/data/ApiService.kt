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
 * Authentifizierung: `X-API-Key`-Header in allen Server-Modi (falls Key
 * hinterlegt), bei mTLS zusätzlich das Client-Zertifikat im TLS-Handshake,
 * im Cloudflare-Modus die beiden Service-Token-Header. Bilder, Thumbs und
 * Medien laufen seit 3.1.0 über dieselben Kopfzeilen
 * ([AppSettings.authHeader]) – hinter Cloudflare Access blockiert der Rand
 * sonst auch sie.
 */
class ApiService(context: Context) {

    private val appContext = context.applicationContext
    private val settings = AppSettings(appContext)
    private val certSource = CertSource(appContext, settings)
    private val local = LocalStorageService.getInstance(appContext)

    private var client: OkHttpClient? = null
    private var clientMode: DataSourceMode? = null

    private val isLocal get() = settings.mode == DataSourceMode.LOCAL

    /**
     * Ablage für Zwischenspeicher und Warteschlange des aktuellen Zugangs;
     * null im lokalen Modus, der ohne Server auskommt.
     */
    private val speicher: OfflineSpeicher?
        get() {
            if (isLocal) return null
            val basis = settings.serverBase
            if (basis.isEmpty()) return null
            return OfflineSpeicher(appContext, "${settings.mode.gespeichert}|$basis")
        }

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
        // Medien hängen an denselben Zugangsdaten und am selben Zertifikat.
        MedienClient.reset()
        client?.dispatcher?.executorService?.shutdown()
        client?.connectionPool?.evictAll()
        client = null
        clientMode = null
    }

    private suspend fun httpClient(): OkHttpClient {
        val mode = settings.mode
        client?.let { if (clientMode == mode) return it }
        client?.connectionPool?.evictAll()
        // Großzügige Timeouts: Video-Uploads dauern Minuten, und nach dem
        // Upload braucht der Server Zeit für die Verarbeitung, bevor die
        // Antwort kommt (OkHttp-Default von 10 s bräche dann ab).
        val builder = OkHttpClient.Builder()
            .connectTimeout(java.time.Duration.ofSeconds(20))
            .writeTimeout(java.time.Duration.ofMinutes(5))
            .readTimeout(java.time.Duration.ofMinutes(5))
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
        for ((feld, wert) in settings.authHeader()) header(feld, wert)
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
        // Reihenfolge wahren: Steht schon etwas an, gehört auch das Neue
        // hinten dran, statt es am Stau vorbeizuschicken.
        if (warteschlange().isNotEmpty()) {
            return vormerken(kalenderDatum, fields, vonName, images, diary, OfflineStatus.letzterGrund)
        }
        return try {
            ladeHoch(kalenderDatum, fields, vonName, images, diary, onSendProgress)
        } catch (fehler: Throwable) {
            if (Netzfehler.aus(fehler) != Netzfehler.NIE_GESENDET) throw fehler
            vormerken(kalenderDatum, fields, vonName, images, diary, fehler.meldung())
        }
    }

    /**
     * Lädt einen Eintrag samt Medien hoch. Ohne Offline-Logik — so benutzt ihn
     * auch das Nachholen, das sonst in einer Schleife wieder vormerken würde.
     */
    private suspend fun ladeHoch(
        kalenderDatum: String,
        fields: Map<String, String>,
        vonName: String,
        images: List<File>,
        diary: String,
        onSendProgress: ((sent: Long, total: Long) -> Unit)? = null,
    ): Int {
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
        OfflineStatus.melde(null)
        return data.optInt("id")
    }

    /**
     * Merkt einen Eintrag samt seiner Medien für später vor und liefert die
     * negative lokale Kennung.
     */
    private fun vormerken(
        kalenderDatum: String,
        fields: Map<String, String>,
        vonName: String,
        images: List<File>,
        diary: String,
        grund: String,
    ): Int {
        val ablage = speicher ?: throw ApiException(grund)
        val (ordner, namen) = ablage.uebernehmeMedien(images)
        var id = -1
        schreibeWarteschlange(ablage) { aktionen, naechste ->
            id = naechste
            aktionen.add(
                Warteaktion.Anlegen(
                    lokaleId = naechste,
                    kalenderDatum = kalenderDatum,
                    fields = fields,
                    vonName = vonName,
                    diary = diary,
                    medienOrdner = ordner,
                    medien = namen,
                ),
            )
            naechste - 1
        }
        OfflineStatus.melde(grund)
        return id
    }

    suspend fun deleteEntry(id: Int, diary: String) {
        if (isLocal) return local.deleteEntry(id, diary)
        // Negative IDs kennt nur die App: der Eintrag wartet noch. Und solange
        // etwas ansteht, bleibt die Reihenfolge gewahrt.
        if (id < 0 || warteschlange().isNotEmpty()) {
            loescheVorgemerkt(id, diary)
            return
        }
        try {
            loescheDirekt(id, diary)
            OfflineStatus.melde(null)
        } catch (fehler: Throwable) {
            if (Netzfehler.aus(fehler) != Netzfehler.NIE_GESENDET) throw fehler
            loescheVorgemerkt(id, diary)
            OfflineStatus.melde(fehler.meldung())
        }
    }

    /** Löscht ohne Offline-Logik — so benutzt es auch das Nachholen. */
    private suspend fun loescheDirekt(id: Int, diary: String) {
        val url = "$apiBase/entries.php".toHttpUrlOrNull()!!.newBuilder()
            .addQueryParameter("id", id.toString())
            .addQueryParameter("diary", diary)
            .build()
        ausfuehren(Request.Builder().url(url).auth().delete().build())
    }

    /**
     * Merkt eine Löschung vor. Einen Eintrag, der noch gar nicht beim Server
     * war, wirft sie ersatzlos aus der Warteschlange – samt seiner
     * Favoriten-Umschaltungen und seiner Medien.
     */
    private fun loescheVorgemerkt(id: Int, diary: String) {
        val ablage = speicher ?: return
        var wegzuraeumen: String? = null
        schreibeWarteschlange(ablage) { aktionen, naechste ->
            val index = aktionen.indexOfFirst {
                it is Warteaktion.Anlegen && it.lokaleId == id
            }
            if (index >= 0) {
                wegzuraeumen = (aktionen[index] as Warteaktion.Anlegen).medienOrdner
                aktionen.removeAt(index)
                aktionen.removeAll { it is Warteaktion.Favorit && it.id == id }
            } else {
                aktionen.add(Warteaktion.Loeschen(id, diary))
            }
            naechste
        }
        wegzuraeumen?.let { ablage.raeumeMedien(it) }
    }

    /**
     * Kehrt den Favoriten-Status um und liefert den neuen Wert.
     *
     * [aktuell] braucht es nur für den Offline-Fall: Die API kennt lediglich
     * „umschalten“ und liefert den neuen Wert erst in ihrer Antwort — ohne
     * Verbindung muss die App ihn selbst bilden.
     */
    suspend fun toggleFavorite(id: Int, diary: String, aktuell: Int = 0): Int {
        if (isLocal) return local.toggleFavorite(id, diary)
        if (id < 0 || warteschlange().isNotEmpty()) {
            merkeFavorit(id, diary)
            return if (aktuell == 1) 0 else 1
        }
        return try {
            favoritDirekt(id, diary).also { OfflineStatus.melde(null) }
        } catch (fehler: Throwable) {
            if (Netzfehler.aus(fehler) != Netzfehler.NIE_GESENDET) throw fehler
            merkeFavorit(id, diary)
            OfflineStatus.melde(fehler.meldung())
            if (aktuell == 1) 0 else 1
        }
    }

    /** Schaltet ohne Offline-Logik um — so benutzt es auch das Nachholen. */
    private suspend fun favoritDirekt(id: Int, diary: String): Int {
        val body = JSONObject().put("id", id).put("diary", diary).toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())
        val data = ausfuehren(
            Request.Builder().url("$apiBase/favorite.php").auth().post(body).build(),
        ) as? JSONObject ?: throw ApiException("Unerwartete Favorit-Antwort.")
        return data.optInt("favorit")
    }

    private fun merkeFavorit(id: Int, diary: String) {
        val ablage = speicher ?: return
        schreibeWarteschlange(ablage) { aktionen, naechste ->
            aktionen.add(Warteaktion.Favorit(id, diary))
            naechste
        }
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

    // ── Warteschlange ───────────────────────────────────────────────────────

    /** Die offenen Schreibzugriffe des aktuellen Zugangs. */
    private fun warteschlange(): List<Warteaktion> =
        speicher?.ladeWarteschlange()?.first.orEmpty()

    /**
     * Ändert die Warteschlange und schreibt sie zurück. Der Block bekommt die
     * Aktionen und die nächste lokale Kennung und liefert deren neuen Wert.
     */
    private fun schreibeWarteschlange(
        ablage: OfflineSpeicher,
        aenderung: (MutableList<Warteaktion>, Int) -> Int,
    ) {
        val (geladen, naechste) = ablage.ladeWarteschlange()
        val aktionen = geladen.toMutableList()
        val neueKennung = aenderung(aktionen, naechste)
        ablage.speichere(aktionen, neueKennung)
        OfflineStatus.melde(aktionen.size)
    }

    /**
     * Arbeitet die Warteschlange von vorn ab.
     *
     * Bricht beim ersten Verbindungsfehler ab — der Rest bleibt in der
     * Reihenfolge stehen. Weist der Server eine Aktion inhaltlich zurück (etwa
     * einen längst gelöschten Eintrag), fliegt sie raus und wird gemeldet;
     * sonst blockierte sie die Warteschlange für immer.
     *
     * Liefert die Meldungen zu verworfenen Aktionen.
     */
    suspend fun nachholen(): List<String> {
        val ablage = speicher ?: return emptyList()
        // Medienordner ohne zugehörige Aktion aufräumen – etwa nach einem
        // Absturz zwischen Kopieren und Vormerken.
        ablage.raeumeVerwaisteMedien(
            warteschlange().filterIsInstance<Warteaktion.Anlegen>()
                .mapTo(mutableSetOf()) { it.medienOrdner },
        )

        val verworfen = mutableListOf<String>()
        while (true) {
            val naechste = warteschlange().firstOrNull() ?: break
            try {
                sende(naechste, ablage)
                erledige(naechste, ablage)
            } catch (fehler: Throwable) {
                if (Netzfehler.aus(fehler) != null) {
                    OfflineStatus.melde(fehler.meldung())
                    return verworfen
                }
                erledige(naechste, ablage)
                verworfen.add(fehler.meldung())
            }
        }
        OfflineStatus.melde(null)
        return verworfen
    }

    private suspend fun sende(aktion: Warteaktion, ablage: OfflineSpeicher) {
        when (aktion) {
            is Warteaktion.Anlegen -> ladeHoch(
                kalenderDatum = aktion.kalenderDatum,
                fields = aktion.fields,
                vonName = aktion.vonName,
                images = ablage.medienDateien(aktion.medienOrdner, aktion.medien),
                diary = aktion.diary,
            )

            is Warteaktion.Loeschen -> loescheDirekt(aktion.id, aktion.diary)
            is Warteaktion.Favorit -> favoritDirekt(aktion.id, aktion.diary)
        }
    }

    /**
     * Nimmt die erledigte (oder verworfene) Aktion aus der Warteschlange und
     * räumt ihre Medien weg.
     */
    private fun erledige(aktion: Warteaktion, ablage: OfflineSpeicher) {
        schreibeWarteschlange(ablage) { aktionen, naechste ->
            if (aktionen.isNotEmpty()) aktionen.removeAt(0)
            naechste
        }
        if (aktion is Warteaktion.Anlegen) ablage.raeumeMedien(aktion.medienOrdner)
    }

    // ── Transport ───────────────────────────────────────────────────────────

    /**
     * Lesende Anfrage. Die Antwort landet im Zwischenspeicher und wird bei
     * einem Netzwerkfehler von dort beantwortet — ob die Anfrage ankam,
     * spielt beim Lesen keine Rolle.
     */
    private suspend fun get(url: String): Any? = try {
        val text = ausfuehrenRoh(Request.Builder().url(url).auth().get().build())
        speicher?.speichereAntwort(url, text)
        OfflineStatus.melde(null)
        alsJson(text)
    } catch (fehler: Throwable) {
        val zwischengespeichert = speicher?.ladeAntwort(url)
        if (Netzfehler.aus(fehler) == null || zwischengespeichert == null) throw fehler
        OfflineStatus.melde(fehler.meldung())
        alsJson(zwischengespeichert)
    }

    private fun alsJson(text: String): Any? {
        if (text.isEmpty()) return null
        return runCatching { JSONObject(text) as Any }
            .recoverCatching { JSONArray(text) as Any }
            .getOrNull()
    }

    private suspend fun ausfuehren(request: Request): Any? = alsJson(ausfuehrenRoh(request))

    /** Wie [ausfuehren], liefert aber den Rohtext – den braucht die Ablage. */
    private suspend fun ausfuehrenRoh(request: Request): String = withContext(Dispatchers.IO) {
        httpClient().newCall(request).execute().use { response ->
            CloudflareServiceToken.abweisung(response)?.let {
                throw ApiException(it, statusCode = response.code)
            }
            val text = response.body?.string().orEmpty()
            if (response.code !in 200..299) {
                val meldung = runCatching { JSONObject(text).optString("error") }
                    .getOrNull()?.takeIf { it.isNotEmpty() }
                    ?: "Anfrage fehlgeschlagen (${response.code})"
                throw ApiException(meldung, statusCode = response.code)
            }
            text
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
