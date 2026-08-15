package ch.tschir.hellobaby.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ch.tschir.hellobaby.Entry
import ch.tschir.hellobaby.StatsResult
import ch.tschir.hellobaby.RandomDay
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Lokale, serverunabhängige Ablage für Tagebucheinträge und Medien.
 *
 * Übernimmt exakt den Bestand der Flutter-App: deren `path_provider`-
 * Documents-Ordner ist auf Android das `app_flutter`-Verzeichnis
 * (`context.getDir("flutter", …)`), darunter `HelloBaby/hello_baby.sqlite`
 * (Version 2 inkl. `remote_imports`) und `HelloBaby/media/<diary>_<id>/`.
 * Die Spalte `bilder` enthält absolute Ordnerpfade — unter Android stabil.
 */
class LocalStorageService private constructor(context: Context) {

    private val appContext = context.applicationContext

    /** `app_flutter/HelloBaby` — identisch zum path_provider-Pfad der Flutter-App. */
    val rootDirectory: File by lazy {
        File(appContext.getDir("flutter", Context.MODE_PRIVATE), "HelloBaby")
            .apply { mkdirs() }
    }

    val mediaDirectory: File
        get() = File(rootDirectory, "media").apply { mkdirs() }

    private val helper by lazy { Helper(appContext, File(rootDirectory, "hello_baby.sqlite").path) }

    private class Helper(context: Context, pfad: String) :
        SQLiteOpenHelper(context, pfad, null, DB_VERSION) {

        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE entries (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  diary TEXT NOT NULL,
                  kalender_datum TEXT NOT NULL,
                  bilder TEXT NOT NULL DEFAULT '',
                  von_name TEXT NOT NULL,
                  favorit INTEGER NOT NULL DEFAULT 0,
                  created_at TEXT NOT NULL,
                  fields_json TEXT NOT NULL DEFAULT '{}'
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX entries_diary_date ON entries(diary, kalender_datum)")
            createRemoteImports(db)
        }

        // Identisch zur sqflite-Migration der Flutter-App (Version 1 -> 2).
        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (oldVersion < 2) createRemoteImports(db)
        }

        private fun createRemoteImports(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS remote_imports (
                  local_id INTEGER NOT NULL,
                  diary TEXT NOT NULL,
                  server_base TEXT NOT NULL,
                  remote_id INTEGER NOT NULL,
                  imported_at TEXT NOT NULL,
                  PRIMARY KEY (local_id, server_base)
                )
                """.trimIndent(),
            )
        }
    }

    // ── Abfragen ────────────────────────────────────────────────────────────

    suspend fun getStats(diary: String): StatsResult = withContext(Dispatchers.IO) {
        val db = helper.readableDatabase
        var first = ""
        var last = ""
        db.rawQuery(
            "SELECT MIN(kalender_datum), MAX(kalender_datum) FROM entries WHERE diary = ?",
            arrayOf(diary),
        ).use { cursor ->
            if (cursor.moveToNext()) {
                first = cursor.getString(0).orEmpty()
                last = cursor.getString(1).orEmpty()
            }
        }
        val random = RandomDay.pick(distinctDatesSync(db, diary))
            ?: LocalDate.now().toString()
        StatsResult(first = first, last = last, randomDate = random)
    }

    suspend fun distinctDates(diary: String): List<String> = withContext(Dispatchers.IO) {
        distinctDatesSync(helper.readableDatabase, diary)
    }

    private fun distinctDatesSync(db: SQLiteDatabase, diary: String): List<String> =
        db.query(true, "entries", arrayOf("kalender_datum"), "diary = ?", arrayOf(diary), null, null, null, null)
            .use { cursor ->
                buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
            }

    suspend fun entriesByDate(date: String, diary: String) =
        query("diary = ? AND kalender_datum = ?", arrayOf(diary, date))

    suspend fun entriesByMonth(year: Int, month: Int, diary: String) =
        query(
            "diary = ? AND kalender_datum LIKE ?",
            arrayOf(diary, "%04d-%02d-%%".format(year, month)),
        )

    suspend fun favorites(diary: String) =
        query("diary = ? AND favorit = 1", arrayOf(diary))

    suspend fun entriesWithImages(diary: String) =
        query("diary = ? AND bilder <> ''", arrayOf(diary))

    suspend fun allEntries(): List<Entry> = withContext(Dispatchers.IO) {
        helper.readableDatabase.query("entries", null, null, null, null, null, "kalender_datum, id")
            .use { cursor ->
                buildList { while (cursor.moveToNext()) add(entryFromCursor(cursor)) }
            }
    }

    private suspend fun query(where: String, args: Array<String>): List<Entry> =
        withContext(Dispatchers.IO) {
            helper.readableDatabase.query(
                "entries", null, where, args, null, null, "kalender_datum DESC, id DESC",
            ).use { cursor ->
                buildList { while (cursor.moveToNext()) add(entryFromCursor(cursor)) }
            }
        }

    private fun entryFromCursor(cursor: android.database.Cursor): Entry {
        fun text(name: String) = cursor.getString(cursor.getColumnIndexOrThrow(name)).orEmpty()
        val folder = text("bilder")
        val decoded = runCatching { JSONObject(text("fields_json")) }.getOrElse { JSONObject() }
        val fields = mutableMapOf<String, String>()
        decoded.keys().forEach { key ->
            fields[key] = if (decoded.isNull(key)) "" else decoded.opt(key)?.toString().orEmpty()
        }
        return Entry(
            id = cursor.getInt(cursor.getColumnIndexOrThrow("id")),
            diary = text("diary"),
            kalenderDatum = text("kalender_datum"),
            bilder = folder,
            vonName = text("von_name"),
            favorit = cursor.getInt(cursor.getColumnIndexOrThrow("favorit")),
            createdAt = text("created_at"),
            fields = fields,
            bilderFiles = if (folder.isEmpty()) emptyList() else galleryFilesSync(folder),
        )
    }

    // ── Schreiben ───────────────────────────────────────────────────────────

    suspend fun createEntry(
        kalenderDatum: String,
        fields: Map<String, String>,
        vonName: String,
        images: List<File>,
        diary: String,
    ): Int = withContext(Dispatchers.IO) {
        val db = helper.writableDatabase
        val id = db.insertOrThrow("entries", null, ContentValues().apply {
            put("diary", diary)
            put("kalender_datum", kalenderDatum)
            put("von_name", vonName)
            put("created_at", LocalDateTime.now().toString())
            put("fields_json", JSONObject(fields as Map<*, *>).toString())
        }).toInt()

        if (images.isEmpty()) return@withContext id
        val folder = File(mediaDirectory, "${diary}_$id")
        try {
            folder.mkdirs()
            images.forEachIndexed { index, source ->
                val safeName = source.name.replace(Regex("[^A-Za-z0-9._-]"), "_")
                source.copyTo(File(folder, "%03d_%s".format(index, safeName)), overwrite = true)
            }
            db.update(
                "entries",
                ContentValues().apply { put("bilder", folder.path) },
                "id = ?", arrayOf(id.toString()),
            )
            id
        } catch (fehler: Exception) {
            folder.deleteRecursively()
            db.delete("entries", "id = ?", arrayOf(id.toString()))
            throw fehler
        }
    }

    suspend fun deleteEntry(id: Int, diary: String) = withContext(Dispatchers.IO) {
        val db = helper.writableDatabase
        val folder = db.query(
            "entries", arrayOf("bilder"), "id = ? AND diary = ?",
            arrayOf(id.toString(), diary), null, null, null, "1",
        ).use { cursor -> if (cursor.moveToNext()) cursor.getString(0).orEmpty() else return@withContext }
        db.delete("entries", "id = ? AND diary = ?", arrayOf(id.toString(), diary))
        db.delete("remote_imports", "local_id = ?", arrayOf(id.toString()))
        if (folder.isNotEmpty()) File(folder).deleteRecursively()
    }

    suspend fun toggleFavorite(id: Int, diary: String): Int = withContext(Dispatchers.IO) {
        val db = helper.writableDatabase
        val aktuell = db.query(
            "entries", arrayOf("favorit"), "id = ? AND diary = ?",
            arrayOf(id.toString(), diary), null, null, null, "1",
        ).use { cursor ->
            if (cursor.moveToNext()) cursor.getInt(0) else throw IllegalStateException("Eintrag nicht gefunden.")
        }
        val neu = if (aktuell == 1) 0 else 1
        db.update(
            "entries",
            ContentValues().apply { put("favorit", neu) },
            "id = ? AND diary = ?", arrayOf(id.toString(), diary),
        )
        neu
    }

    suspend fun galleryFiles(folder: String): List<String> = withContext(Dispatchers.IO) {
        galleryFilesSync(folder)
    }

    private fun galleryFilesSync(folder: String): List<String> {
        val dir = File(folder)
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles { file -> file.isFile }
            ?.map { it.name }
            ?.sorted()
            ?: emptyList()
    }

    // ── Import-Tracking / Backup ────────────────────────────────────────────

    suspend fun importedLocalIds(server: String): Set<Int> = withContext(Dispatchers.IO) {
        helper.readableDatabase.query(
            "remote_imports", arrayOf("local_id"), "server_base = ?", arrayOf(server),
            null, null, null,
        ).use { cursor ->
            buildSet { while (cursor.moveToNext()) add(cursor.getInt(0)) }
        }
    }

    suspend fun markImported(localId: Int, diary: String, server: String, remoteId: Int) =
        withContext(Dispatchers.IO) {
            helper.writableDatabase.insertWithOnConflict(
                "remote_imports", null,
                ContentValues().apply {
                    put("local_id", localId)
                    put("diary", diary)
                    put("server_base", server)
                    put("remote_id", remoteId)
                    put("imported_at", LocalDateTime.now().toString())
                },
                SQLiteDatabase.CONFLICT_REPLACE,
            )
            Unit
        }

    /** Alle Roh-Zeilen der Tabelle `entries` (für den Backup-Export). */
    suspend fun exportRows(): List<Map<String, Any?>> = withContext(Dispatchers.IO) {
        helper.readableDatabase.query("entries", null, null, null, null, null, "id").use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        mapOf(
                            "id" to cursor.getInt(0),
                            "diary" to cursor.getString(1),
                            "kalender_datum" to cursor.getString(2),
                            "bilder" to cursor.getString(3),
                            "von_name" to cursor.getString(4),
                            "favorit" to cursor.getInt(5),
                            "created_at" to cursor.getString(6),
                            "fields_json" to cursor.getString(7),
                        ),
                    )
                }
            }
        }
    }

    /**
     * Ersetzt Datenbank und Medien durch ein zuvor vollständig geprüftes
     * Backup. [stagedMedia] wird zum neuen media-Ordner; bei einem
     * Datenbankfehler werden die bisherigen Medien zurückgelegt.
     */
    suspend fun restoreRows(rows: List<Map<String, Any?>>, stagedMedia: File) =
        withContext(Dispatchers.IO) {
            val db = helper.writableDatabase
            val media = File(rootDirectory, "media")
            val previous = File(rootDirectory, "media_before_restore_${System.nanoTime()}")

            var previousMoved = false
            try {
                if (media.exists()) {
                    check(media.renameTo(previous)) { "Medienordner ließ sich nicht sichern." }
                    previousMoved = true
                }
                check(stagedMedia.renameTo(media)) { "Neue Medien ließen sich nicht übernehmen." }

                db.beginTransaction()
                try {
                    db.delete("entries", null, null)
                    db.delete("remote_imports", null, null)
                    for (row in rows) {
                        val portableFolder = row["bilder"] as String
                        db.insertOrThrow("entries", null, ContentValues().apply {
                            put("id", row["id"] as Int)
                            put("diary", row["diary"] as String)
                            put("kalender_datum", row["kalender_datum"] as String)
                            put(
                                "bilder",
                                if (portableFolder.isEmpty()) ""
                                else File(media, portableFolder.substringAfterLast('/')).path,
                            )
                            put("von_name", row["von_name"] as String)
                            put("favorit", row["favorit"] as Int)
                            put("created_at", row["created_at"] as String)
                            put("fields_json", row["fields_json"] as String)
                        })
                    }
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
            } catch (fehler: Exception) {
                media.deleteRecursively()
                if (previousMoved && previous.exists()) previous.renameTo(media)
                throw fehler
            }
            // Erfolgreich – alter Ordner darf weg (Scheitern ist harmlos).
            if (previousMoved) previous.deleteRecursively()
        }

    companion object {
        private const val DB_VERSION = 2

        @Volatile private var instanz: LocalStorageService? = null

        fun getInstance(context: Context): LocalStorageService =
            instanz ?: synchronized(this) {
                instanz ?: LocalStorageService(context).also { instanz = it }
            }
    }
}
