package ch.tschir.hellobaby.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray

/** Authentifizierungs-/Datenquellen-Modus der App. Namen identisch zur Flutter-App. */
enum class DataSourceMode(val gespeichert: String) {
    /** Vollständig lokale Speicherung in SQLite und im App-Medienordner. */
    LOCAL("local"),

    /** Server-API mit Client-Zertifikat (mTLS). */
    MTLS("mtls"),

    /** Server-API mit API-Key (`X-API-Key`-Header). */
    API_KEY("apiKey");

    companion object {
        fun fromGespeichert(value: String?): DataSourceMode =
            entries.firstOrNull { it.gespeichert == value } ?: LOCAL
    }
}

/**
 * Lädt und speichert App-Einstellungen (SharedPreferences).
 *
 * Beim ersten Start nach dem Umstieg von der Flutter-App werden deren Werte
 * übernommen: Flutters shared_preferences speichert unter
 * `FlutterSharedPreferences` mit dem Präfix `flutter.`. String-Listen legt es
 * dort als String mit einem festen Präfix ab, gefolgt je nach Plugin-API von
 * Base64-kodierter Java-Serialisierung (Legacy) oder von JSON (Async-API).
 */
class AppSettings(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("hellobaby_settings", Context.MODE_PRIVATE)

    init {
        migriereVonFlutter(context.applicationContext)
    }

    var mode: DataSourceMode
        get() = DataSourceMode.fromGespeichert(prefs.getString(KEY_MODE, null))
        set(value) = prefs.edit().putString(KEY_MODE, value.gespeichert).apply()

    var apiKey: String
        get() = prefs.getString(KEY_API_KEY, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_API_KEY, value.trim()).apply()

    /** Basis-URL des Servers ohne abschließenden Slash; leer = nicht gesetzt. */
    var serverBase: String
        get() = prefs.getString(KEY_SERVER_BASE, "").orEmpty().trim().trimEnd('/')
        set(value) = prefs.edit().putString(KEY_SERVER_BASE, value.trim()).apply()

    /** Der Name im Titel „Hello NAME!“. */
    var appName: String
        get() = prefs.getString(KEY_APP_NAME, "").orEmpty().trim().ifEmpty { DEFAULT_APP_NAME }
        set(value) {
            val trimmed = value.trim()
            if (trimmed.isEmpty()) {
                prefs.edit().remove(KEY_APP_NAME).apply()
            } else {
                prefs.edit().putString(KEY_APP_NAME, trimmed).apply()
            }
        }

    /** Aktiver Tagebuch-Modus (schwangerschaft/entwicklung). */
    var activeDiary: String
        get() = prefs.getString(KEY_ACTIVE_DIARY, null) ?: "schwangerschaft"
        set(value) = prefs.edit().putString(KEY_ACTIVE_DIARY, value).apply()

    /** Liste der auswählbaren Ersteller („von_name“), als JSON gespeichert. */
    var users: List<String>
        get() = ladeListe(KEY_USERS)
        set(value) = prefs.edit().putString(KEY_USERS, JSONArray(value).toString()).apply()

    /** Zuletzt gewählter Ersteller (Vorauswahl beim Anlegen). */
    var selectedUser: String
        get() = prefs.getString(KEY_SELECTED_USER, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_SELECTED_USER, value).apply()

    /** Fest hinterlegter Standard-Ersteller (leer = keiner). */
    var defaultUser: String
        get() = prefs.getString(KEY_DEFAULT_USER, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_DEFAULT_USER, value).apply()

    /** SAF-Ordner-URI der Zertifikate; null, solange keiner gewählt wurde. */
    var certFolderUri: String?
        get() = prefs.getString(KEY_CERT_FOLDER_URI, null)
        set(value) = prefs.edit().putString(KEY_CERT_FOLDER_URI, value).apply()

    private fun ladeListe(key: String): List<String> {
        val raw = prefs.getString(key, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            List(array.length()) { array.optString(it) }
        }.getOrElse { emptyList() }
    }

    private fun migriereVonFlutter(context: Context) {
        if (prefs.getBoolean(KEY_MIGRIERT, false)) return

        val flutter = context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        for (key in listOf(
            KEY_MODE, KEY_API_KEY, KEY_SERVER_BASE, KEY_APP_NAME,
            KEY_ACTIVE_DIARY, KEY_SELECTED_USER, KEY_DEFAULT_USER, KEY_CERT_FOLDER_URI,
        )) {
            val wert = flutter.getString("flutter.$key", null)
            if (wert != null && !prefs.contains(key)) {
                editor.putString(key, wert)
            }
        }
        // String-Listen speichert shared_preferences als String mit festem
        // Präfix („This is the prefix for a list.“ als Base64). Danach folgt
        // bei der Legacy-API Base64-kodierte Java-Serialisierung einer
        // ArrayList<String>, bei der neueren Async-API direkt JSON.
        val rohListe = flutter.getString("flutter.$KEY_USERS", null)
        if (rohListe != null && !prefs.contains(KEY_USERS)) {
            dekodiereFlutterListe(rohListe)?.let {
                editor.putString(KEY_USERS, JSONArray(it).toString())
            }
        }
        editor.putBoolean(KEY_MIGRIERT, true).apply()
    }

    private fun dekodiereFlutterListe(roh: String): List<String>? {
        val rest = roh.removePrefix(FLUTTER_LIST_PREFIX)
        if (rest.trimStart().startsWith("[")) {
            return runCatching {
                val array = JSONArray(rest)
                List(array.length()) { array.getString(it) }
            }.getOrNull()
        }
        return runCatching {
            val bytes = android.util.Base64.decode(rest, android.util.Base64.DEFAULT)
            val stream = object : java.io.ObjectInputStream(bytes.inputStream()) {
                override fun resolveClass(desc: java.io.ObjectStreamClass): Class<*> {
                    if (desc.name != "java.util.ArrayList") {
                        throw java.io.InvalidClassException("Unerwartete Klasse: ${desc.name}")
                    }
                    return super.resolveClass(desc)
                }
            }
            @Suppress("UNCHECKED_CAST")
            (stream.readObject() as List<Any?>).map { it as String }
        }.getOrNull()
    }

    companion object {
        const val DEFAULT_APP_NAME = "Baby"

        private const val KEY_MODE = "data_source_mode"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_SERVER_BASE = "server_base_url"
        private const val KEY_APP_NAME = "app_display_name"
        private const val KEY_ACTIVE_DIARY = "active_diary"
        private const val KEY_USERS = "creator_users"
        private const val KEY_SELECTED_USER = "creator_selected"
        private const val KEY_DEFAULT_USER = "creator_default"
        private const val KEY_CERT_FOLDER_URI = "cert_folder_uri"
        private const val KEY_MIGRIERT = "migriert_von_flutter"

        /** Base64 von "This is the prefix for a list." */
        private const val FLUTTER_LIST_PREFIX = "VGhpcyBpcyB0aGUgcHJlZml4IGZvciBhIGxpc3Qu"
    }
}
