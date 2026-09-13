package ch.tschir.hellobaby.data

import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.PortUnreachableException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Wie ein fehlgeschlagener Netzwerkzugriff zu bewerten ist.
 *
 * Der Unterschied entscheidet über Duplikate: Nur wenn die Anfrage den Server
 * nachweislich nie erreicht hat, darf die App sie in die Warteschlange legen
 * und später erneut senden. Bei allem Mehrdeutigen könnte der Server sie
 * längst ausgeführt haben — ein zweiter Versuch legte dann einen zweiten
 * Eintrag an.
 */
enum class Netzfehler {
    /**
     * DNS, Verbindungsaufbau oder TLS schlugen fehl, oder das Gerät hat gar
     * keine Verbindung. Die Anfrage ist nie hinausgegangen.
     */
    NIE_GESENDET,

    /**
     * Zeitüberschreitung oder Abbruch mitten in der Übertragung. Ob der Server
     * die Anfrage gesehen hat, ist nicht feststellbar.
     */
    MEHRDEUTIG,
    ;

    companion object {
        /**
         * Ordnet einen Fehler ein; null bei allem, was kein Netzwerkproblem
         * ist (etwa einer [ApiException] mit HTTP-Statuscode).
         */
        fun aus(fehler: Throwable): Netzfehler? = when (fehler) {
            is UnknownHostException,
            is ConnectException,
            is NoRouteToHostException,
            is PortUnreachableException,
            is SSLException,
            -> NIE_GESENDET

            is SocketTimeoutException,
            is InterruptedIOException,
            -> MEHRDEUTIG

            // Jede andere IOException bleibt bewusst mehrdeutig: lieber eine
            // Fehlermeldung zu viel als ein doppelter Eintrag.
            is IOException -> MEHRDEUTIG

            else -> null
        }
    }
}
