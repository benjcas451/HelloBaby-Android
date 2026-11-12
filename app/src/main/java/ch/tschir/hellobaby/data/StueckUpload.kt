package ch.tschir.hellobaby.data

import kotlinx.coroutines.delay
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

/** Antwort des Servers: Status und Rohtext, noch unbewertet. */
internal class Antwort(val code: Int, val text: String) {
    fun json(): JSONObject? = runCatching { JSONObject(text) }.getOrNull()

    /** Die Antwort als Fehler, mit der Meldung aus `{"error": "…"}`. */
    fun alsFehler(): ApiException {
        val meldung = json()?.optString("error")?.takeIf { it.isNotEmpty() }
            ?: if (code in 200..299) "Unerwartete Antwort des Servers." else "Anfrage fehlgeschlagen ($code)"
        return ApiException(meldung, statusCode = code)
    }
}

/**
 * Wiederholt einen Aufruf bei Verbindungsproblemen mit wachsender Pause. Nur
 * für Anfragen, die der Server beliebig oft verträgt.
 */
internal suspend fun <T> mitWiederholung(
    versuche: Int = StueckUpload.VERSUCHE,
    pause: suspend (Long) -> Unit = { delay(it) },
    aufruf: suspend () -> T,
): T {
    var versuch = 1
    while (true) {
        try {
            return aufruf()
        } catch (fehler: IOException) {
            if (versuch >= versuche) throw fehler
            pause(versuch * 2000L)
            versuch++
        }
    }
}

/**
 * Lädt Medien einzeln und in Stücken hoch, bevor der Eintrag angelegt wird.
 *
 * Warum Stücke: Vor dem Server steht meist Cloudflare, das jede Anfrage auf
 * 100 MB begrenzt und nach 100 Sekunden ohne Antwort abbricht. Ein längeres
 * Video passt weder in eine Anfrage, noch darf seine Umwandlung in der
 * Anfrage laufen, die den Eintrag anlegt. Deshalb drei Schritte am Endpunkt
 * `entries.php`:
 *
 * 1. `?upload=init` meldet die Datei an und liefert Kennung und Stückgröße,
 * 2. `?upload=chunk&id=…&offset=…` nimmt die Stücke der Reihe nach entgegen,
 * 3. `?upload=status&id=…` sagt, wann ein Video fertig umgewandelt ist.
 *
 * Erst danach legt der [ApiService] den Eintrag an und nennt dabei die
 * Kennungen in `uploads[]`.
 *
 * @param endpunkt URL von `entries.php`; wird bei jedem Aufruf neu gelesen,
 *   weil sich die Server-URL in den Einstellungen ändern kann.
 * @param sende führt eine Anfrage aus und ergänzt die Anmeldung.
 * @param pause Wartezeit zwischen Versuchen; in Tests ersetzbar.
 */
internal class StueckUpload(
    private val endpunkt: () -> String,
    private val sende: suspend (Request.Builder) -> Antwort,
    private val pause: suspend (Long) -> Unit = { delay(it) },
) {

    /**
     * Lädt [dateien] hoch und liefert ihre Upload-Kennungen in derselben
     * Reihenfolge — oder null, wenn der Server das Verfahren nicht kennt.
     *
     * [onSendProgress] meldet gesendete und gesamte Bytes über alle Dateien.
     * Sind alle Bytes draußen, wartet der Aufruf noch auf die Umwandlung der
     * Videos.
     *
     * Bis hierhin existiert der Eintrag nachweislich nicht. Ein
     * Verbindungsabbruch wird deshalb als [UploadUnterbrochen] gemeldet — das
     * darf in die Warteschlange, ohne ein Duplikat zu riskieren.
     */
    suspend fun ladeHoch(
        dateien: List<File>,
        onSendProgress: ((sent: Long, total: Long) -> Unit)? = null,
    ): List<String>? = try {
        val gesamt = dateien.sumOf { it.length() }
        var erledigt = 0L
        val kennungen = mutableListOf<String>()
        val videos = mutableListOf<String>()
        for ((index, datei) in dateien.withIndex()) {
            val groesse = datei.length()
            val sitzung = meldeAn(datei.name, groesse, erste = index == 0) ?: return null
            val kennung = sitzung.optString("upload_id")
            val stueck = sitzung.optLong("chunk_size", STUECK).coerceIn(STUECK_MIN, STUECK_MAX)
            val basis = erledigt
            sendeStuecke(datei, kennung, groesse, stueck) { stand ->
                onSendProgress?.invoke(basis + stand, gesamt)
            }
            erledigt += groesse
            kennungen += kennung
            if (datei.extension.lowercase() in VIDEO_ENDUNGEN) videos += kennung
        }
        // Ab hier zeigt die Oberfläche „Medien werden verarbeitet…“.
        onSendProgress?.invoke(gesamt, gesamt)
        videos.forEach { warteAufVerarbeitung(it) }
        kennungen
    } catch (fehler: IOException) {
        throw UploadUnterbrochen(fehler)
    }

    /**
     * Meldet eine Datei an. Liefert null, wenn der Server das Verfahren nicht
     * kennt: Ein älterer Stand hält die Anfrage für einen Eintrag ohne
     * Pflichtfelder und antwortet mit 400 — ohne das Kennzeichen
     * `upload_fehler`, das der neue Server jeder Ablehnung mitgibt.
     *
     * Die [erste] Anmeldung wird bei einem Verbindungsfehler nicht wiederholt:
     * Ist das Gerät schlicht offline, soll der Eintrag sofort in die
     * Warteschlange und nicht erst nach mehreren Anläufen.
     */
    private suspend fun meldeAn(name: String, groesse: Long, erste: Boolean): JSONObject? {
        val anfrage = suspend {
            val body = FormBody.Builder().add("name", name).add("size", groesse.toString()).build()
            sende(Request.Builder().url(url("init")).post(body))
        }
        val antwort = if (erste) anfrage() else mitWiederholung(pause = pause, aufruf = anfrage)
        val json = antwort.json()
        if (antwort.code in 200..299 && json?.optString("upload_id").orEmpty().isNotEmpty()) return json
        val unbekannt = antwort.code in setOf(400, 404, 405) && json?.optBoolean("upload_fehler") != true
        if (unbekannt && erste) return null
        throw antwort.alsFehler()
    }

    /**
     * Sendet die Stücke einer Datei. Nach einem Verbindungsabbruch geht es an
     * der Stelle weiter, die der Server zuletzt bestätigt hat — nicht von
     * vorn.
     */
    private suspend fun sendeStuecke(
        datei: File,
        kennung: String,
        groesse: Long,
        stueck: Long,
        melde: (stand: Long) -> Unit,
    ) {
        var stand = 0L
        var fehlversuche = 0
        while (stand < groesse) {
            val von = stand
            val body = StueckBody(datei, von, minOf(stueck, groesse - von)) { melde(von + it) }
            try {
                val antwort = sende(
                    Request.Builder()
                        .url(url("chunk", "id" to kennung, "offset" to von.toString()))
                        .post(body),
                )
                val bestaetigt = antwort.json()?.takeIf { it.has("received") }?.optLong("received")
                stand = when {
                    antwort.code in 200..299 && bestaetigt != null && bestaetigt > von -> bestaetigt
                    // 409: Der Server ist schon weiter, weil die Antwort auf
                    // ein Stück verloren ging — dort weitermachen.
                    antwort.code == 409 && bestaetigt != null && bestaetigt != von -> bestaetigt
                    else -> throw antwort.alsFehler()
                }
                fehlversuche = 0
            } catch (fehler: IOException) {
                if (++fehlversuche >= VERSUCHE) throw fehler
                pause(fehlversuche * 2000L)
                // Ob das Stück noch ankam, weiß nur der Server.
                stand = try {
                    status(kennung).optLong("received", von)
                } catch (weiterOffline: IOException) {
                    von
                }
            }
            melde(stand)
        }
    }

    /**
     * Wartet, bis der Server ein Video umgewandelt hat. Das läuft dort nach
     * dem letzten Stück im Hintergrund weiter — bei langen Videos dauert es
     * länger, als eine einzelne Anfrage offen bleiben darf.
     */
    private suspend fun warteAufVerarbeitung(kennung: String) {
        val ende = System.nanoTime() + VERARBEITUNG_MAX_MINUTEN * 60_000_000_000L
        while (true) {
            if (mitWiederholung(pause = pause) { status(kennung) }.optBoolean("bereit")) return
            if (System.nanoTime() > ende) {
                throw ApiException("Der Server braucht ungewöhnlich lange für die Umwandlung des Videos.")
            }
            pause(2000)
        }
    }

    private suspend fun status(kennung: String): JSONObject {
        val antwort = sende(Request.Builder().url(url("status", "id" to kennung)).get())
        if (antwort.code !in 200..299) throw antwort.alsFehler()
        return antwort.json() ?: throw ApiException("Unerwartete Antwort des Servers.")
    }

    private fun url(aktion: String, vararg query: Pair<String, String>): HttpUrl =
        endpunkt().toHttpUrl().newBuilder()
            .addQueryParameter("upload", aktion)
            .apply { query.forEach { (k, v) -> addQueryParameter(k, v) } }
            .build()

    /** Ein Ausschnitt einer Datei als Anfragekörper — gestreamt, nicht im Speicher. */
    private class StueckBody(
        private val datei: File,
        private val offset: Long,
        private val laenge: Long,
        private val onProgress: (gesendet: Long) -> Unit,
    ) : RequestBody() {
        override fun contentType() = "application/octet-stream".toMediaType()
        override fun contentLength() = laenge

        override fun writeTo(sink: BufferedSink) {
            RandomAccessFile(datei, "r").use { quelle ->
                quelle.seek(offset)
                val puffer = ByteArray(64 * 1024)
                var rest = laenge
                while (rest > 0) {
                    val gelesen = quelle.read(puffer, 0, minOf(puffer.size.toLong(), rest).toInt())
                    if (gelesen < 0) throw IOException("Die Datei ist kürzer als erwartet.")
                    sink.write(puffer, 0, gelesen)
                    rest -= gelesen
                    onProgress(laenge - rest)
                }
            }
        }
    }

    companion object {
        /** Stückgröße, falls der Server keine nennt. */
        const val STUECK = 8L * 1024 * 1024
        const val STUECK_MIN = 256L * 1024

        /** Obergrenze des Servers je Stück; weit unter Cloudflares 100 MB. */
        const val STUECK_MAX = 64L * 1024 * 1024

        /** Versuche je Anfrage, bevor ein Verbindungsfehler durchschlägt. */
        const val VERSUCHE = 5
        const val VERARBEITUNG_MAX_MINUTEN = 120L

        /** Was der Server als Video umwandelt (wie `upload_chunks.php`). */
        val VIDEO_ENDUNGEN = setOf("mov", "mp4", "m4v", "webm")
    }
}
