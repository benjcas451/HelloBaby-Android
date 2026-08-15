package ch.tschir.hellobaby.data

import android.content.Context
import java.io.File

data class LocalApiImportResult(val imported: Int, val skipped: Int)

/** Überträgt noch nicht importierte lokale Einträge zur gewählten Server-API. */
class LocalApiImportService(context: Context) {

    private val storage = LocalStorageService.getInstance(context)

    suspend fun import(
        api: ApiService,
        server: String,
        onProgress: ((current: Int, total: Int) -> Unit)? = null,
    ): LocalApiImportResult {
        val normalizedServer = server.trim().trimEnd('/')
        if (normalizedServer.isEmpty()) {
            throw IllegalArgumentException("Bitte zuerst eine Server-URL eintragen.")
        }

        val entries = storage.allEntries()
        val importedIds = storage.importedLocalIds(normalizedServer)
        val pending = entries.filter { it.id !in importedIds }

        var imported = 0
        for (entry in pending) {
            onProgress?.invoke(imported, pending.size)
            val mediaFiles = entry.bilderFiles
                .map { File(entry.bilder, it) }
                .filter { it.exists() }
            val remoteId = api.createEntry(
                kalenderDatum = entry.kalenderDatum,
                fields = entry.fields,
                vonName = entry.vonName,
                images = mediaFiles,
                diary = entry.diary,
            )
            try {
                if (entry.favorit == 1) {
                    api.toggleFavorite(remoteId, entry.diary)
                }
                storage.markImported(
                    localId = entry.id,
                    diary = entry.diary,
                    server = normalizedServer,
                    remoteId = remoteId,
                )
            } catch (fehler: Exception) {
                // Ein unvollständiger Remote-Eintrag würde beim Fortsetzen ein
                // Duplikat erzeugen — nach Möglichkeit serverseitig zurückrollen.
                runCatching { api.deleteEntry(remoteId, entry.diary) }
                throw fehler
            }
            imported++
            onProgress?.invoke(imported, pending.size)
        }

        return LocalApiImportResult(
            imported = imported,
            skipped = entries.size - pending.size,
        )
    }
}
