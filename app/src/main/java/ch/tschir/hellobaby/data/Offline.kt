package ch.tschir.hellobaby.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkRequest
import java.io.File
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Lesbare Meldung einer Exception (ApiException liefert den Statuscode mit). */
fun Throwable.meldung(): String = when (this) {
    is ApiException -> toString()
    else -> message?.takeIf { it.isNotBlank() } ?: toString()
}

/** Ein Schreibzugriff, der offline erfasst wurde und noch zum Server muss. */
sealed interface Warteaktion {

    /** Ein offline erfasster neuer Eintrag samt seiner Medien. */
    data class Anlegen(
        /** Negative Kennung, unter der der Eintrag bis zum Hochladen läuft. */
        val lokaleId: Int,
        val kalenderDatum: String,
        val fields: Map<String, String>,
        val vonName: String,
        val diary: String,
        /** Unterordner in der Medien-Ablage der Warteschlange. */
        val medienOrdner: String,
        /**
         * Dateinamen darin, in der Reihenfolge der Auswahl. Bewusst nicht die
         * ursprünglichen Pfade: Eine Aufnahme aus der Galerie liegt je nach
         * Quelle in einem Ordner, den das System jederzeit räumen darf.
         */
        val medien: List<String>,
    ) : Warteaktion

    data class Loeschen(val id: Int, val diary: String) : Warteaktion

    /** Favoriten-Status umschalten (die API kennt keinen Zielwert). */
    data class Favorit(val id: Int, val diary: String) : Warteaktion
}

/** Der Offline-Zustand, den die Oberfläche anzeigt. */
data class OfflineZustand(
    /** Grund der letzten gescheiterten Verbindung; null heisst „online“. */
    val grund: String? = null,
    /** Anzahl der Schreibzugriffe, die noch auf Übertragung warten. */
    val ausstehend: Int = 0,
)

/**
 * Der Offline-Zustand als app-weite Quelle – der [ApiService] wird an vielen
 * Stellen direkt benutzt, ein durchgereichter Zustand wäre hier mehr Aufwand
 * als Nutzen.
 */
object OfflineStatus {

    private val zustandFlow = MutableStateFlow(OfflineZustand())
    val zustand: StateFlow<OfflineZustand> = zustandFlow

    /**
     * Der zuletzt gemeldete Grund. Der Client braucht ihn, wenn er einen
     * Eintrag wegen der Reihenfolge vormerkt, ohne selbst auf einen Fehler
     * gelaufen zu sein.
     */
    @Volatile
    var letzterGrund: String = "Keine Verbindung zum Server"
        private set

    fun melde(grund: String?) {
        if (grund != null) letzterGrund = grund
        if (zustandFlow.value.grund != grund) {
            zustandFlow.value = zustandFlow.value.copy(grund = grund)
        }
    }

    fun melde(ausstehend: Int) {
        if (zustandFlow.value.ausstehend != ausstehend) {
            zustandFlow.value = zustandFlow.value.copy(ausstehend = ausstehend)
        }
    }

    /** Setzt alles zurück – beim Wechsel der Datenquelle. */
    fun zuruecksetzen() {
        zustandFlow.value = OfflineZustand()
    }
}

/**
 * Legt Antwort-Zwischenspeicher, Warteschlange und deren Medien je Zugang im
 * app-privaten Verzeichnis ab.
 *
 * Der Schlüssel ist Modus plus Server-URL: Wer zwischen zwei Servern wechselt,
 * bekommt nicht die Einträge des anderen zu sehen und lädt auch keine
 * Warteschlange dorthin hoch, wo sie nicht hingehört.
 */
class OfflineSpeicher(context: Context, zugang: String) {

    private val schluessel = zugang.map { if (it.isLetterOrDigit()) it else '_' }.joinToString("")
    private val basis = File(context.applicationContext.filesDir, "offline").apply { mkdirs() }
    private val antworten = File(basis, "antworten_$schluessel").apply { mkdirs() }

    /**
     * Basis der Medien wartender Einträge. Diese Dateien sind die einzigen
     * Kopien, bis der Upload durch ist – deshalb nicht im Cache-Verzeichnis.
     */
    private val medienBasis = File(basis, "medien_$schluessel").apply { mkdirs() }

    private val warteschlangeDatei get() = File(basis, "warteschlange_$schluessel.json")

    // ── Antworten ───────────────────────────────────────────────────────────

    private fun antwortDatei(url: String): File {
        val name = url.map { if (it.isLetterOrDigit()) it else '_' }.joinToString("")
        // Lange URLs würden den Dateinamen sprengen; der Hash hält ihn kurz
        // und bleibt für dieselbe Abfrage gleich.
        return File(antworten, "${name.takeLast(80)}_${url.hashCode()}.json")
    }

    fun ladeAntwort(url: String): String? =
        runCatching { antwortDatei(url).readText() }.getOrNull()

    fun speichereAntwort(url: String, text: String) {
        runCatching { antwortDatei(url).writeText(text) }
    }

    // ── Warteschlange ───────────────────────────────────────────────────────

    fun ladeWarteschlange(): Pair<List<Warteaktion>, Int> {
        val json = runCatching { JSONObject(warteschlangeDatei.readText()) }.getOrNull()
            ?: return emptyList<Warteaktion>() to -1
        val liste = json.optJSONArray("aktionen") ?: JSONArray()
        val aktionen = (0 until liste.length()).mapNotNull { i ->
            val o = liste.optJSONObject(i) ?: return@mapNotNull null
            when (o.optString("art")) {
                "anlegen" -> {
                    val felder = mutableMapOf<String, String>()
                    val f = o.optJSONObject("fields") ?: JSONObject()
                    for (key in f.keys()) felder[key] = f.optString(key)
                    val medien = o.optJSONArray("medien") ?: JSONArray()
                    Warteaktion.Anlegen(
                        lokaleId = o.optInt("lokale_id"),
                        kalenderDatum = o.optString("kalender_datum"),
                        fields = felder,
                        vonName = o.optString("von_name"),
                        diary = o.optString("diary"),
                        medienOrdner = o.optString("medien_ordner"),
                        medien = List(medien.length()) { medien.optString(it) },
                    )
                }

                "loeschen" -> Warteaktion.Loeschen(o.optInt("id"), o.optString("diary"))
                "favorit" -> Warteaktion.Favorit(o.optInt("id"), o.optString("diary"))
                else -> null
            }
        }
        val naechste = if (json.has("naechste_lokale_id")) json.optInt("naechste_lokale_id") else -1
        return aktionen to naechste
    }

    fun speichere(aktionen: List<Warteaktion>, naechsteLokaleId: Int) {
        val liste = JSONArray()
        for (aktion in aktionen) {
            val o = JSONObject()
            when (aktion) {
                is Warteaktion.Anlegen -> {
                    val felder = JSONObject()
                    aktion.fields.forEach { (k, v) -> felder.put(k, v) }
                    val medien = JSONArray()
                    aktion.medien.forEach { medien.put(it) }
                    o.put("art", "anlegen")
                        .put("lokale_id", aktion.lokaleId)
                        .put("kalender_datum", aktion.kalenderDatum)
                        .put("fields", felder)
                        .put("von_name", aktion.vonName)
                        .put("diary", aktion.diary)
                        .put("medien_ordner", aktion.medienOrdner)
                        .put("medien", medien)
                }

                is Warteaktion.Loeschen ->
                    o.put("art", "loeschen").put("id", aktion.id).put("diary", aktion.diary)

                is Warteaktion.Favorit ->
                    o.put("art", "favorit").put("id", aktion.id).put("diary", aktion.diary)
            }
            liste.put(o)
        }
        val json = JSONObject()
            .put("naechste_lokale_id", naechsteLokaleId)
            .put("aktionen", liste)
        // Erst in eine Nebendatei, dann umbenennen: ein Absturz mitten im
        // Schreiben hinterlässt sonst eine halbe Warteschlange.
        runCatching {
            val temp = File(basis, "${warteschlangeDatei.name}.tmp")
            temp.writeText(json.toString())
            if (!temp.renameTo(warteschlangeDatei)) {
                warteschlangeDatei.writeText(json.toString())
                temp.delete()
            }
        }
    }

    // ── Medien ──────────────────────────────────────────────────────────────

    /**
     * Kopiert die gewählten Dateien in einen eigenen Ordner der Warteschlange
     * und liefert dessen Namen samt der Dateinamen darin.
     */
    fun uebernehmeMedien(dateien: List<File>): Pair<String, List<String>> {
        val name = java.util.UUID.randomUUID().toString()
        val ziel = File(medienBasis, name).apply { mkdirs() }
        val namen = mutableListOf<String>()
        for ((index, quelle) in dateien.withIndex()) {
            // Nummeriert, damit die Reihenfolge erhalten bleibt und gleiche
            // Dateinamen sich nicht überschreiben.
            val dateiname = "${index}_${quelle.name}"
            quelle.copyTo(File(ziel, dateiname), overwrite = true)
            namen.add(dateiname)
        }
        return name to namen
    }

    fun medienDateien(ordner: String, namen: List<String>): List<File> {
        val basisOrdner = File(medienBasis, ordner)
        return namen.map { File(basisOrdner, it) }
    }

    /** Räumt den Medienordner einer erledigten oder verworfenen Aktion weg. */
    fun raeumeMedien(ordner: String) {
        runCatching { File(medienBasis, ordner).deleteRecursively() }
    }

    /**
     * Entfernt Medienordner, zu denen keine Aktion mehr existiert – etwa nach
     * einem Absturz zwischen Kopieren und Vormerken.
     */
    fun raeumeVerwaisteMedien(behalte: Set<String>) {
        medienBasis.listFiles()?.forEach { ordner ->
            if (ordner.name !in behalte) runCatching { ordner.deleteRecursively() }
        }
    }
}

/**
 * Meldet, sobald wieder ein Netzwerk da ist — damit die Warteschlange nicht
 * erst beim nächsten Antippen abgearbeitet wird.
 */
class Verbindungswache(context: Context) {

    private val wiederVerbundenFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Feuert bei jedem Wechsel von „kein Netz“ zu „Netz da“. */
    val wiederVerbunden: SharedFlow<Unit> = wiederVerbundenFlow

    private val manager =
        context.applicationContext.getSystemService(ConnectivityManager::class.java)
    private var warOffline = false

    private val rueckruf = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            if (warOffline) wiederVerbundenFlow.tryEmit(Unit)
            warOffline = false
        }

        override fun onLost(network: Network) {
            warOffline = true
        }
    }

    fun starten() {
        runCatching {
            manager?.registerNetworkCallback(NetworkRequest.Builder().build(), rueckruf)
        }
    }

    fun beenden() {
        runCatching { manager?.unregisterNetworkCallback(rueckruf) }
    }
}
