package ch.tschir.hellobaby.data

import android.util.Base64
import java.io.ByteArrayInputStream
import java.security.KeyFactory
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/** Das Client-Zertifikat ließ sich nicht verwenden. */
class ClientCertificateException(meldung: String) : Exception(meldung)

/**
 * Baut aus den PEM-Dateien (client.crt / client.key) eine SSLSocketFactory,
 * die das Client-Zertifikat beim TLS-Handshake anbietet. Portiert aus dem
 * :wear-Modul des bisherigen Flutter-Repos.
 *
 * Unterstützt: PEM ohne Passphrase, Schlüssel als PKCS#8
 * (`BEGIN PRIVATE KEY`) oder PKCS#1 (`BEGIN RSA PRIVATE KEY`).
 */
object ClientCertificates {

    /**
     * SocketFactory samt System-TrustManager. OkHttp verlangt beide zusammen,
     * damit es Zertifikatsprüfungen korrekt zuordnen kann; das Server-
     * Zertifikat wird weiterhin normal gegen den System-Trust-Store geprüft.
     */
    fun socketFactoryMitTrust(
        certPem: ByteArray,
        keyPem: ByteArray,
    ): Pair<SSLSocketFactory, X509TrustManager> {
        val kette = zertifikatskette(certPem)
        val schluessel = privatSchluessel(keyPem)

        val keyStore = KeyStore.getInstance("PKCS12")
        keyStore.load(null, null)
        keyStore.setKeyEntry(ALIAS, schluessel, PASSWORT, kette)

        val keyManager = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        keyManager.init(keyStore, PASSWORT)

        val trustFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        trustFactory.init(null as KeyStore?)
        val trustManager = trustFactory.trustManagers.filterIsInstance<X509TrustManager>().first()

        val context = SSLContext.getInstance("TLS")
        context.init(keyManager.keyManagers, arrayOf(trustManager), null)
        return context.socketFactory to trustManager
    }

    private fun zertifikatskette(certPem: ByteArray): Array<Certificate> {
        val zertifikate = runCatching {
            CertificateFactory.getInstance("X.509")
                .generateCertificates(ByteArrayInputStream(certPem))
        }.getOrElse {
            throw ClientCertificateException("client.crt ist kein gültiges PEM-Zertifikat.")
        }
        if (zertifikate.isEmpty()) {
            throw ClientCertificateException("client.crt enthält kein Zertifikat.")
        }
        return zertifikate.toTypedArray()
    }

    private fun privatSchluessel(keyPem: ByteArray): PrivateKey {
        val text = String(keyPem, Charsets.ISO_8859_1)
        val block = pemBlock(text)
            ?: throw ClientCertificateException("client.key ist kein PEM-Schlüssel.")

        if (block.typ.contains("ENCRYPTED")) {
            throw ClientCertificateException(
                "client.key ist mit einer Passphrase geschützt. Bitte einen " +
                    "Schlüssel ohne Passphrase hinterlegen.",
            )
        }

        val der = when (block.typ) {
            "PRIVATE KEY" -> block.inhalt
            "RSA PRIVATE KEY" -> pkcs1AlsPkcs8(block.inhalt)
            "EC PRIVATE KEY" -> throw ClientCertificateException(
                "client.key liegt im SEC1-Format vor. Bitte einmal umwandeln: " +
                    "openssl pkcs8 -topk8 -nocrypt -in client.key -out client.key",
            )

            else -> throw ClientCertificateException(
                "Unbekannter Schlüsseltyp in client.key: ${block.typ}",
            )
        }

        val spec = PKCS8EncodedKeySpec(der)
        for (algorithmus in listOf("RSA", "EC")) {
            runCatching { KeyFactory.getInstance(algorithmus).generatePrivate(spec) }
                .getOrNull()
                ?.let { return it }
        }
        throw ClientCertificateException("client.key konnte nicht gelesen werden.")
    }

    private data class PemBlock(val typ: String, val inhalt: ByteArray)

    /** Liest den ersten `-----BEGIN <typ>-----`-Block aus einer PEM-Datei. */
    private fun pemBlock(text: String): PemBlock? {
        val start = Regex("-----BEGIN ([A-Z0-9 ]+)-----").find(text) ?: return null
        val typ = start.groupValues[1].trim()
        val ende = text.indexOf("-----END", start.range.last)
        if (ende < 0) return null
        val base64 = text.substring(start.range.last + 1, ende)
        val bytes = runCatching { Base64.decode(base64, Base64.DEFAULT) }.getOrNull()
            ?: return null
        return PemBlock(typ, bytes)
    }

    /**
     * Verpackt einen PKCS#1-RSA-Schlüssel in eine PKCS#8-Struktur, weil
     * [PKCS8EncodedKeySpec] nur diese versteht:
     *
     *     SEQUENCE { INTEGER 0, AlgorithmIdentifier(rsaEncryption), OCTET STRING }
     */
    private fun pkcs1AlsPkcs8(pkcs1: ByteArray): ByteArray {
        val version = byteArrayOf(0x02, 0x01, 0x00)
        // SEQUENCE { OID 1.2.840.113549.1.1.1 (rsaEncryption), NULL }
        val algorithmus = byteArrayOf(
            0x30, 0x0D, 0x06, 0x09, 0x2A, 0x86.toByte(), 0x48, 0x86.toByte(),
            0xF7.toByte(), 0x0D, 0x01, 0x01, 0x01, 0x05, 0x00,
        )
        val schluessel = byteArrayOf(0x04) + derLaenge(pkcs1.size) + pkcs1
        val inhalt = version + algorithmus + schluessel
        return byteArrayOf(0x30) + derLaenge(inhalt.size) + inhalt
    }

    /** DER-Längenfeld (kurze Form bis 127, sonst lange Form). */
    private fun derLaenge(laenge: Int): ByteArray = when {
        laenge < 0x80 -> byteArrayOf(laenge.toByte())
        laenge <= 0xFF -> byteArrayOf(0x81.toByte(), laenge.toByte())
        laenge <= 0xFFFF -> byteArrayOf(
            0x82.toByte(),
            (laenge shr 8).toByte(),
            laenge.toByte(),
        )

        else -> byteArrayOf(
            0x83.toByte(),
            (laenge shr 16).toByte(),
            (laenge shr 8).toByte(),
            laenge.toByte(),
        )
    }

    private const val ALIAS = "stillzeit-client"

    /** Nur für den In-Memory-KeyStore; PKCS12 verlangt ein nicht-leeres Passwort. */
    private val PASSWORT = "stillzeit".toCharArray()
}
