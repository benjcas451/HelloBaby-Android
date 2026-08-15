package ch.tschir.hellobaby

import org.json.JSONObject

/** Ein einzelnes Eingabefeld eines Tagebuchs (gespiegelt aus der Web-diaries.php). */
data class DiaryField(
    val key: String,
    val label: String,
    val unit: String = "",
    val textarea: Boolean = false,
    val required: Boolean = false,
    val numeric: Boolean = false,
)

/** Ein Tagebuch-Modus (z. B. Schwangerschaft oder Entwicklung). */
data class Diary(
    val id: String,
    val label: String,
    val title: String,
    val fields: List<DiaryField>,
)

/** Verfügbare Tagebücher – Felder identisch zur Flutter-/Web-App. */
val kDiaries: Map<String, Diary> = linkedMapOf(
    "schwangerschaft" to Diary(
        id = "schwangerschaft",
        label = "Schwangerschaft",
        title = "Schwangerschaftstagebuch",
        fields = listOf(
            DiaryField("gewicht", "Gewicht", unit = "kg", numeric = true),
            DiaryField("bauchumfang", "Bauchumfang", unit = "cm", numeric = true),
            DiaryField("empfinden", "Empfinden / Gefühle", textarea = true, required = true),
            DiaryField("gelueste", "Gelüste", textarea = true),
            DiaryField("sonstiges", "Sonstiges", textarea = true),
        ),
    ),
    "entwicklung" to Diary(
        id = "entwicklung",
        label = "Entwicklung",
        title = "Entwicklungstagebuch",
        fields = listOf(
            DiaryField("gewicht", "Gewicht", unit = "kg", numeric = true),
            DiaryField("groesse", "Größe", unit = "cm", numeric = true),
            DiaryField("kopfumfang", "Kopfumfang", unit = "cm", numeric = true),
            DiaryField("notiz", "Meilenstein / Notiz", textarea = true, required = true),
            DiaryField("sonstiges", "Sonstiges", textarea = true),
        ),
    ),
)

const val DEFAULT_DIARY = "schwangerschaft"

/** Ein Tagebucheintrag (lokal oder vom Server). */
data class Entry(
    val id: Int,
    val diary: String = "",
    val kalenderDatum: String,
    /** Galerie-Ordner: lokal ein absoluter Pfad, serverseitig `uploads/...`. */
    val bilder: String,
    val vonName: String,
    var favorit: Int,
    val createdAt: String,
    /** Tagebuch-spezifische Felder; Keys entsprechen den DB-Spalten. */
    val fields: Map<String, String>,
    /** Mediendateien des Galerie-Ordners (nur beim images-Filter gefüllt). */
    val bilderFiles: List<String> = emptyList(),
) {
    fun field(key: String): String = fields[key].orEmpty()

    companion object {
        private val commonKeys = setOf(
            "id", "diary", "kalender_datum", "bilder", "von_name",
            "favorit", "created_at", "bilder_files",
        )

        fun fromJson(json: JSONObject): Entry {
            val fields = mutableMapOf<String, String>()
            json.keys().forEach { key ->
                if (key !in commonKeys) {
                    fields[key] = if (json.isNull(key)) "" else json.opt(key)?.toString().orEmpty()
                }
            }
            val files = json.optJSONArray("bilder_files")?.let { array ->
                List(array.length()) { array.opt(it)?.toString().orEmpty() }
            } ?: emptyList()
            return Entry(
                id = json.optInt("id"),
                diary = json.optString("diary"),
                kalenderDatum = json.optString("kalender_datum"),
                bilder = json.optString("bilder"),
                vonName = json.optString("von_name"),
                favorit = json.optInt("favorit", 0),
                createdAt = json.optString("created_at"),
                fields = fields,
                bilderFiles = files,
            )
        }
    }
}

/** Erster/letzter Eintrag und ein zufälliges Datum (`stats.php`). */
data class StatsResult(
    val first: String,
    val last: String,
    val randomDate: String,
)

// ── Medien-Helfer (identisch zur Flutter-config.dart) ─────────────────────────

private val imageExtensions = setOf("jpg", "jpeg", "png", "gif", "webp")
private val videoExtensions = setOf("mp4", "mov", "m4v", "webm")

private fun ext(name: String): String =
    if ("." in name) name.substringAfterLast('.').lowercase() else ""

fun isImageFile(name: String) = ext(name) in imageExtensions
fun isVideoFile(name: String) = ext(name) in videoExtensions

/** Lokale absolute Pfade bleiben unverändert, Serverpfade werden zu URLs. */
fun isLocalMediaSource(path: String) = path.startsWith("/")

/** Auswahl eines zufälligen Tages (lokaler Modus). */
object RandomDay {
    fun pick(dates: List<String>, exclude: String? = null): String? {
        if (dates.isEmpty()) return null
        val distinct = dates.distinct()
        if (distinct.size == 1) return distinct.first()
        val candidates = distinct.filter { it != exclude }
        val pool = candidates.ifEmpty { distinct }
        return pool.random()
    }
}
