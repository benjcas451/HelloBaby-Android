package ch.tschir.hellobaby.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Portables ZIP-Backup der lokalen Ablage (entries.json + media/…).
 * Format identisch zur Flutter-App (format=1) – alte Backups bleiben
 * wiederherstellbar und umgekehrt.
 */
class LocalBackupService(context: Context) {

    private val storage = LocalStorageService.getInstance(context)

    fun dateiname(): String =
        "hello_baby_backup_${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"))}.zip"

    /** Schreibt das komplette Backup in [ziel] (SAF-OutputStream). */
    suspend fun export(ziel: OutputStream) = withContext(Dispatchers.IO) {
        val rows = storage.exportRows()
        val media = storage.mediaDirectory

        ZipOutputStream(ziel.buffered()).use { zip ->
            val portable = JSONArray()
            for (row in rows) {
                val kopie = JSONObject()
                row.forEach { (key, value) -> kopie.put(key, value) }
                val folder = row["bilder"] as String
                if (folder.isNotEmpty()) {
                    kopie.put("bilder", "media/${File(folder).name}")
                }
                portable.put(kopie)
            }
            zip.putNextEntry(ZipEntry("entries.json"))
            zip.write(
                JSONObject()
                    .put("format", 1)
                    .put("exported_at", LocalDateTime.now().toString())
                    .put("entries", portable)
                    .toString(2)
                    .toByteArray(Charsets.UTF_8),
            )
            zip.closeEntry()

            if (media.isDirectory) {
                media.walkTopDown().filter { it.isFile }.forEach { file ->
                    val relativ = file.relativeTo(media).invariantSeparatorsPath
                    zip.putNextEntry(ZipEntry("media/$relativ"))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
    }

    /**
     * Liest, prüft und übernimmt ein Backup aus [quelle]. Liefert die Anzahl
     * wiederhergestellter Einträge. Ersetzt den kompletten lokalen Bestand.
     */
    suspend fun restore(context: Context, quelle: Uri): Int = withContext(Dispatchers.IO) {
        val root = storage.rootDirectory
        val staging = File(root, "restore_staging_${System.nanoTime()}")
        val stagedMedia = File(staging, "media").apply { mkdirs() }

        try {
            var manifest: JSONObject? = null
            oeffne(context, quelle).use { input ->
                ZipInputStream(input.buffered()).use { zip ->
                    var eintrag: ZipEntry? = zip.nextEntry
                    while (eintrag != null) {
                        val name = eintrag.name.replace('\\', '/')
                        when {
                            eintrag.isDirectory -> Unit

                            name == "entries.json" -> {
                                if (manifest != null) {
                                    throw IllegalArgumentException("entries.json ist mehrfach vorhanden.")
                                }
                                manifest = JSONObject(zip.readBytes().toString(Charsets.UTF_8))
                            }

                            name.startsWith("media/") -> {
                                val relativ = name.removePrefix("media/")
                                if (relativ.isEmpty() || ".." in relativ.split('/') ||
                                    relativ.startsWith("/")
                                ) {
                                    throw IllegalArgumentException("Ungültiger Pfad im Backup: $name")
                                }
                                val ziel = File(stagedMedia, relativ)
                                // Zip-Slip-Schutz: Ziel muss unter stagedMedia bleiben.
                                if (!ziel.canonicalPath.startsWith(stagedMedia.canonicalPath + File.separator)) {
                                    throw IllegalArgumentException("Ungültiger Pfad im Backup: $name")
                                }
                                ziel.parentFile?.mkdirs()
                                ziel.outputStream().use { zip.copyTo(it) }
                            }

                            else -> throw IllegalArgumentException("Unerwartete Datei im Backup: $name")
                        }
                        eintrag = zip.nextEntry
                    }
                }
            }

            val geleseneManifest = manifest
                ?: throw IllegalArgumentException("entries.json fehlt im Backup.")
            if (geleseneManifest.optInt("format", -1) != 1) {
                throw IllegalArgumentException("Nicht unterstütztes Backup-Format.")
            }
            val rows = validiere(
                geleseneManifest.optJSONArray("entries")
                    ?: throw IllegalArgumentException("Eintragsliste fehlt im Backup."),
            )

            for (row in rows) {
                val folder = row["bilder"] as String
                if (folder.isEmpty()) continue
                if (!File(stagedMedia, folder.substringAfterLast('/')).isDirectory) {
                    throw IllegalArgumentException("Medienordner fehlt: $folder")
                }
            }

            storage.restoreRows(rows, stagedMedia)
            rows.size
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun oeffne(context: Context, uri: Uri): InputStream =
        context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Datei ließ sich nicht lesen.")

    private fun validiere(rawEntries: JSONArray): List<Map<String, Any?>> {
        val result = mutableListOf<Map<String, Any?>>()
        val ids = mutableSetOf<Int>()
        val datum = Regex("""^\d{4}-\d{2}-\d{2}$""")
        for (index in 0 until rawEntries.length()) {
            val raw = rawEntries.optJSONObject(index)
                ?: throw IllegalArgumentException("Ungültiger Eintrag im Backup.")
            val id = raw.optInt("id", -1)
            if (id <= 0 || !ids.add(id)) {
                throw IllegalArgumentException("Ungültige oder doppelte Eintrags-ID.")
            }
            val diary = raw.optString("diary")
            val date = raw.optString("kalender_datum")
            val folder = raw.optString("bilder")
            val favorit = raw.optInt("favorit", -1)
            val fieldsJson = raw.optString("fields_json")
            if (diary.isEmpty() || !datum.matches(date) ||
                (favorit != 0 && favorit != 1) ||
                runCatching { JSONObject(fieldsJson) }.isFailure
            ) {
                throw IllegalArgumentException("Eintrag $id enthält ungültige Daten.")
            }
            if (folder.isNotEmpty() &&
                (!folder.startsWith("media/") || folder.removePrefix("media/").let { it.isEmpty() || "/" in it })
            ) {
                throw IllegalArgumentException("Eintrag $id enthält einen ungültigen Medienpfad.")
            }
            result.add(
                mapOf(
                    "id" to id,
                    "diary" to diary,
                    "kalender_datum" to date,
                    "bilder" to folder,
                    "von_name" to raw.optString("von_name"),
                    "favorit" to favorit,
                    "created_at" to raw.optString("created_at"),
                    "fields_json" to fieldsJson,
                ),
            )
        }
        return result
    }
}
