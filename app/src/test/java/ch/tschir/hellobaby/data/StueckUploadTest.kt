package ch.tschir.hellobaby.data

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import kotlin.random.Random

/**
 * Prüft [StueckUpload] gegen einen nachgestellten Server, der sich wie
 * `upload_chunks.php` verhält: Stücke nur am passenden Offset, 409 mit dem
 * echten Stand sonst, Videos erst nach einigen Statusabfragen „bereit“.
 */
class StueckUploadTest {

    @get:Rule
    val ordner = TemporaryFolder()

    private val server = MockWebServer()
    private val uploads = linkedMapOf<String, Upload>()
    private val pausen = mutableListOf<Long>()

    /** Wird vor jeder Anfrage gefragt; liefert eine Antwort, um sie zu stören. */
    private var stoerung: (RecordedRequest) -> MockResponse? = { null }

    /** So viele Statusabfragen meldet ein Video noch „nicht bereit“. */
    private var abfragenBisBereit = 2

    private class Upload(val name: String, val groesse: Long) {
        val daten = ByteArrayOutputStream()
        var abfragen = 0
    }

    private val upload = StueckUpload(
        endpunkt = { server.url("/api/entries.php").toString() },
        sende = { anfrage ->
            OkHttpClient().newCall(anfrage.build()).execute().use {
                Antwort(it.code, it.body?.string().orEmpty())
            }
        },
        pause = { pausen += it },
    )

    @Before
    fun start() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                synchronized(uploads) { stoerung(request) ?: antworte(request) }
        }
        server.start()
    }

    @After
    fun stopp() = server.shutdown()

    // ── Nachgestellter Server ───────────────────────────────────────────────

    private fun json(code: Int, vararg felder: Pair<String, Any>) =
        MockResponse().setResponseCode(code).setBody(JSONObject(mapOf(*felder)).toString())

    private fun stand(upload: Upload, bereit: Boolean) = arrayOf(
        "received" to upload.daten.size().toLong(),
        "size" to upload.groesse,
        "complete" to (upload.daten.size().toLong() == upload.groesse),
        "bereit" to bereit,
    )

    private fun istVideo(upload: Upload) = upload.name.endsWith(".mov")

    private fun antworte(request: RecordedRequest): MockResponse {
        val url = request.requestUrl!!
        val kennung = url.queryParameter("id")
        return when (url.queryParameter("upload")) {
            "init" -> {
                val felder = request.body.readUtf8().split("&").associate {
                    val (k, v) = it.split("=")
                    k to java.net.URLDecoder.decode(v, "UTF-8")
                }
                val neu = "id${uploads.size}"
                uploads[neu] = Upload(felder.getValue("name"), felder.getValue("size").toLong())
                json(200, "upload_id" to neu, "chunk_size" to STUECK, "received" to 0)
            }

            "chunk" -> {
                val upload = uploads[kennung] ?: return json(404, "error" to "weg", "upload_fehler" to true)
                val offset = url.queryParameter("offset")!!.toLong()
                if (offset != upload.daten.size().toLong()) {
                    return json(409, "error" to "Stand", "upload_fehler" to true, *stand(upload, false))
                }
                assertEquals("application/octet-stream", request.getHeader("Content-Type"))
                upload.daten.write(request.body.readByteArray())
                json(200, *stand(upload, !istVideo(upload)))
            }

            "status" -> {
                val upload = uploads[kennung] ?: return json(404, "error" to "weg", "upload_fehler" to true)
                val komplett = upload.daten.size().toLong() == upload.groesse
                val bereit = komplett && (!istVideo(upload) || ++upload.abfragen > abfragenBisBereit)
                json(200, *stand(upload, bereit))
            }

            else -> json(400, "error" to "Required fields missing: kalender_datum")
        }
    }

    private fun datei(name: String, groesse: Int): File =
        ordner.newFile(name).apply { writeBytes(Random(groesse).nextBytes(groesse)) }

    private fun istStueck(request: RecordedRequest) =
        request.requestUrl?.queryParameter("upload") == "chunk"

    // ── Tests ───────────────────────────────────────────────────────────────

    @Test
    fun `laedt mehrere Dateien in Stuecken und wartet auf das Video`() = runBlocking {
        val foto = datei("foto.jpg", 1000)
        val video = datei("video.mov", 3 * STUECK + 123)
        val fortschritt = mutableListOf<Pair<Long, Long>>()

        val kennungen = upload.ladeHoch(listOf(foto, video)) { gesendet, gesamt ->
            synchronized(fortschritt) { fortschritt += gesendet to gesamt }
        }

        assertEquals(listOf("id0", "id1"), kennungen)
        assertArrayEquals(foto.readBytes(), uploads.getValue("id0").daten.toByteArray())
        assertArrayEquals(video.readBytes(), uploads.getValue("id1").daten.toByteArray())
        // Foto: gar keine Statusabfrage. Video: so lange, bis es bereit ist.
        assertEquals(0, uploads.getValue("id0").abfragen)
        assertEquals(abfragenBisBereit + 1, uploads.getValue("id1").abfragen)

        val gesamt = foto.length() + video.length()
        assertTrue(fortschritt.all { it.second == gesamt && it.first in 0..gesamt })
        assertEquals(fortschritt.map { it.first }.sorted(), fortschritt.map { it.first })
        assertEquals(gesamt, fortschritt.last().first)
    }

    @Test
    fun `setzt nach verlorener Antwort dort fort wo der Server steht`() = runBlocking {
        val bild = datei("gross.jpg", 4 * STUECK)
        var gestoert = false
        // Das zweite Stück kommt an, die Antwort darauf geht verloren.
        stoerung = { request ->
            if (!gestoert && istStueck(request) && request.requestUrl!!.queryParameter("offset") == "$STUECK") {
                gestoert = true
                antworte(request)
                MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)
            } else {
                null
            }
        }

        val kennungen = upload.ladeHoch(listOf(bild))

        assertEquals(listOf("id0"), kennungen)
        assertArrayEquals(bild.readBytes(), uploads.getValue("id0").daten.toByteArray())
        assertEquals("genau eine Pause vor dem neuen Anlauf", listOf(2000L), pausen)
        // Kein Stück ging doppelt raus: 4 Stücke, davon keines wiederholt.
        val offsets = generateSequence { server.takeRequest(0, java.util.concurrent.TimeUnit.SECONDS) }
            .filter(::istStueck).map { it.requestUrl!!.queryParameter("offset") }.toList()
        assertEquals(listOf("0", "$STUECK", "${2 * STUECK}", "${3 * STUECK}"), offsets)
    }

    @Test
    fun `nimmt den Stand aus einem 409 wenn auch die Statusabfrage scheitert`() = runBlocking {
        val bild = datei("gross.jpg", 3 * STUECK)
        var stoerungen = 0
        // Antwort auf das erste Stück verloren, die Statusabfrage danach auch:
        // Die App schickt das Stück noch einmal und bekommt 409 samt Stand.
        stoerung = { request ->
            when {
                stoerungen == 0 && istStueck(request) -> {
                    stoerungen++
                    antworte(request)
                    MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)
                }

                stoerungen == 1 && request.requestUrl!!.queryParameter("upload") == "status" -> {
                    stoerungen++
                    MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)
                }

                else -> null
            }
        }

        upload.ladeHoch(listOf(bild))

        assertEquals(2, stoerungen)
        assertArrayEquals(bild.readBytes(), uploads.getValue("id0").daten.toByteArray())
    }

    @Test
    fun `liefert null wenn der Server das Verfahren nicht kennt`() = runBlocking {
        // Ein älterer Server hält die Anmeldung für einen Eintrag ohne Pflichtfelder.
        stoerung = { json(400, "error" to "Required fields missing: kalender_datum") }

        assertNull(upload.ladeHoch(listOf(datei("foto.jpg", 10))))
    }

    @Test
    fun `meldet eine Ablehnung des Servers als Fehler statt als unbekanntes Verfahren`() = runBlocking {
        stoerung = { json(415, "error" to "Dateityp 'exe' nicht erlaubt.", "upload_fehler" to true) }

        try {
            upload.ladeHoch(listOf(datei("foto.jpg", 10)))
            fail("ApiException erwartet")
        } catch (fehler: ApiException) {
            assertEquals(415, fehler.statusCode)
            assertEquals("Dateityp 'exe' nicht erlaubt.", fehler.message)
        }
    }

    @Test
    fun `gibt ohne Verbindung sofort auf damit der Eintrag in die Warteschlange kann`() = runBlocking {
        stoerung = { MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST) }

        try {
            upload.ladeHoch(listOf(datei("foto.jpg", 10)))
            fail("UploadUnterbrochen erwartet")
        } catch (fehler: UploadUnterbrochen) {
            assertEquals(Netzfehler.NIE_GESENDET, Netzfehler.aus(fehler))
            assertTrue("keine Wartezeit vor dem Aufgeben", pausen.isEmpty())
        }
    }

    @Test
    fun `gibt nach mehreren Anlaeufen auf wenn die Verbindung mitten im Upload wegbleibt`() = runBlocking {
        val bild = datei("gross.jpg", 2 * STUECK)
        stoerung = { request ->
            if (request.requestUrl!!.queryParameter("upload") == "init") {
                null
            } else {
                MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)
            }
        }

        try {
            upload.ladeHoch(listOf(bild))
            fail("UploadUnterbrochen erwartet")
        } catch (fehler: UploadUnterbrochen) {
            assertTrue(fehler.cause is IOException)
            assertEquals(Netzfehler.NIE_GESENDET, Netzfehler.aus(fehler))
            assertEquals(StueckUpload.VERSUCHE - 1, pausen.size)
        }
    }

    private companion object {
        /** Kleine Stücke, damit die Tests schnell bleiben. */
        const val STUECK = 300 * 1024
    }
}
