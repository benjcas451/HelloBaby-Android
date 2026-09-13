package ch.tschir.hellobaby.data

import okhttp3.Request
import okhttp3.Response

/**
 * Ein Cloudflare-Access-Service-Token: das Gegenstück zum Browser-Login für
 * Maschinen. Cloudflare prüft die beiden Header am Rand und reicht die Anfrage
 * erst danach an den eigentlichen Server weiter – der kann darüber hinaus
 * weiter seinen eigenen `X-API-Key` verlangen.
 */
data class CloudflareServiceToken(
    /** Client-ID des Tokens; endet üblicherweise auf `.access`. */
    val clientId: String,
    /** Client-Secret des Tokens. */
    val clientSecret: String,
) {
    fun anwenden(builder: Request.Builder): Request.Builder = builder
        .header(HEADER_ID, clientId)
        .header(HEADER_SECRET, clientSecret)

    /**
     * Als Map – so brauchen es Coil und ExoPlayer, die keinen
     * `Request.Builder` entgegennehmen.
     */
    val header: Map<String, String>
        get() = mapOf(HEADER_ID to clientId, HEADER_SECRET to clientSecret)

    companion object {
        const val HEADER_ID = "CF-Access-Client-Id"
        const val HEADER_SECRET = "CF-Access-Client-Secret"

        /**
         * Nur ein vollständiges Token ergibt Sinn – mit einer Hälfte weist
         * Cloudflare die Anfrage genauso ab wie ganz ohne.
         */
        fun of(clientId: String?, clientSecret: String?): CloudflareServiceToken? {
            val id = clientId?.trim().orEmpty()
            val secret = clientSecret?.trim().orEmpty()
            if (id.isEmpty() || secret.isEmpty()) return null
            return CloudflareServiceToken(id, secret)
        }

        /**
         * Erkennt, dass Cloudflare Access die Anfrage abgefangen hat, und
         * liefert dafür eine verständliche Meldung (sonst null).
         *
         * Ohne gültiges Token antwortet Access nicht mit einem sauberen
         * Fehler, sondern leitet auf die Login-Seite des Teams um. OkHttp
         * folgt dem automatisch, sodass am Ende eine HTML-Seite mit Status 200
         * ankommt – der Parser meldete dafür nur „kein JSON“, was den
         * eigentlichen Grund verschleiert. Erkennbar ist der Fall am Host der
         * finalen Anfrage: Access leitet immer auf eine Subdomain von
         * `cloudflareaccess.com`.
         */
        fun abweisung(response: Response): String? {
            val host = response.request.url.host.lowercase()
            if (host == LOGIN_HOST || host.endsWith(".$LOGIN_HOST")) {
                return "Cloudflare Access hat die Anfrage abgewiesen. Bitte Service Token " +
                    "in den Einstellungen prüfen – möglicherweise ist es abgelaufen."
            }
            // 403 direkt von Cloudflare: Das Token wird zwar erkannt, die
            // Access-Richtlinie lässt es aber nicht auf diese Anwendung.
            if (response.code == 403 && response.header("cf-ray") != null) {
                return "Cloudflare Access hat den Zugriff verweigert (403). Das Service Token " +
                    "ist dieser Anwendung vermutlich nicht zugewiesen."
            }
            return null
        }

        private const val LOGIN_HOST = "cloudflareaccess.com"
    }
}
