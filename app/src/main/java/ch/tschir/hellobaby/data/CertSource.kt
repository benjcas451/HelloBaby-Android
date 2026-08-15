package ch.tschir.hellobaby.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Fehler beim Laden der Zertifikate (Dateien fehlen o. Ä.). */
class CertException(message: String) : Exception(message)

/**
 * Quelle für client.crt / client.key: ein per Storage Access Framework
 * dauerhaft freigegebener Ordner. Die gespeicherte Ordner-URI wird beim
 * Umstieg von der Flutter-App übernommen ([AppSettings]) – die persistierte
 * SAF-Berechtigung überlebt App-Updates unter derselben applicationId.
 */
class CertSource(context: Context, private val settings: AppSettings) {

    private val context = context.applicationContext

    /** true, sobald ein freigegebener Ordner bereitsteht. */
    val isReady: Boolean
        get() = folderUri() != null

    /** Für die UI lesbarer Ort der Zertifikate (Ordnername). */
    val locationLabel: String?
        get() = folderUri()?.let { uri ->
            Uri.decode(uri.toString()).substringAfterLast('/')
        }

    private fun folderUri(): Uri? {
        val gespeichert = settings.certFolderUri ?: return null
        val uri = Uri.parse(gespeichert)
        val berechtigt = context.contentResolver.persistedUriPermissions.any {
            it.uri == uri && it.isReadPermission
        }
        return if (berechtigt) uri else null
    }

    /**
     * Übernimmt einen frisch per `OpenDocumentTree` gewählten Ordner und
     * persistiert die Leseberechtigung dauerhaft.
     */
    fun uebernehmeOrdner(uri: Uri) {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
        settings.certFolderUri = uri.toString()
    }

    /** Liest die Bytes von Zertifikat und privatem Schlüssel. */
    suspend fun readCredentials(): Pair<ByteArray, ByteArray> = withContext(Dispatchers.IO) {
        val uri = folderUri()
            ?: throw CertException("Kein Zertifikats-Ordner ausgewählt.")
        val ordner = DocumentFile.fromTreeUri(context, uri)
            ?: throw CertException("Der Zertifikats-Ordner ist nicht (mehr) erreichbar.")
        val certDoc = ordner.findFile(CERT_FILE_NAME)
        val keyDoc = ordner.findFile(KEY_FILE_NAME)
        if (certDoc == null || keyDoc == null) {
            throw CertException(
                "$CERT_FILE_NAME oder $KEY_FILE_NAME nicht im gewählten Ordner gefunden.",
            )
        }
        leseBytes(certDoc.uri) to leseBytes(keyDoc.uri)
    }

    private fun leseBytes(uri: Uri): ByteArray =
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw CertException("Datei ließ sich nicht lesen: $uri")

    companion object {
        const val CERT_FILE_NAME = "client.crt"
        const val KEY_FILE_NAME = "client.key"
    }
}
