package ch.tschir.hellobaby.ui

import ch.tschir.hellobaby.PapierkorbEintrag
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Prüft, wie die App die Papierkorb-Liste des Servers liest und beschriftet. */
class PapierkorbTest {

    /** So antwortet `entries.php?papierkorb=liste` für einen gelöschten Eintrag. */
    private val eintrag = PapierkorbEintrag.fromJson(
        JSONObject(
            """
            {"kennung":"20261112_135210_edata_42","diary":"entwicklung","diary_vorschlag":"entwicklung",
             "geloescht":"2026-11-12 13:52:10","verfaellt":"2026-12-12","tage_uebrig":30,
             "wiederherstellbar":true,"medien":2,"dateien":["6af5b2250bc04.jpg","6af5b2250bc72.mp4"],"bytes":21076582,
             "eintrag":{"id":42,"kalender_datum":"2026-11-03","bilder":"uploads/20261103_101500_abc",
                        "von_name":"Celine","favorit":1,"created_at":"2026-11-03 10:15:00",
                        "gewicht":"5,4","groesse":"","kopfumfang":"0","notiz":"Erstes Lächeln","sonstiges":""}}
            """,
        ),
    )

    /** Medienordner, dessen Text schon verloren war, bevor es den Papierkorb gab. */
    private val ohneEintrag = PapierkorbEintrag.fromJson(
        JSONObject(
            """
            {"kennung":"20261112_134431_altlast_20250727_134229_68862cc5dc2a1","diary":null,
             "diary_vorschlag":"schwangerschaft",
             "geloescht":"2026-11-12 13:44:31","verfaellt":"2026-12-12","tage_uebrig":1,
             "wiederherstellbar":true,"medien":1,"bytes":1677721,
             "eintrag":{"id":0,"kalender_datum":"2025-07-27","bilder":"uploads/20250727_134229_68862cc5dc2a1",
                        "von_name":"","favorit":0,"created_at":""}}
            """,
        ),
    )

    @Test
    fun `liest einen geloeschten Eintrag samt Feldern`() {
        assertEquals("20261112_135210_edata_42", eintrag.kennung)
        assertEquals("entwicklung", eintrag.diary)
        assertTrue(eintrag.wiederherstellbar)
        assertEquals(2, eintrag.medien)
        assertEquals(listOf("6af5b2250bc04.jpg", "6af5b2250bc72.mp4"), eintrag.dateien)
        // Ein Server ohne Vorschaubilder nennt keine Dateien.
        assertTrue(ohneEintrag.dateien.isEmpty())
        assertEquals(21076582L, eintrag.bytes)
        assertEquals(42, eintrag.eintrag.id)
        assertEquals("entwicklung", eintrag.eintrag.diary)
        assertEquals("Erstes Lächeln", eintrag.eintrag.field("notiz"))
        assertEquals("5,4", eintrag.eintrag.field("gewicht"))
        assertEquals(1, eintrag.eintrag.favorit)
    }

    @Test
    fun `liest Medien ohne Eintrag samt Vorschlag fuer das Tagebuch`() {
        // Ein JSON-null darf nicht als Text "null" ankommen.
        assertEquals("", ohneEintrag.diary)
        assertEquals("schwangerschaft", ohneEintrag.diaryVorschlag)
        assertTrue(ohneEintrag.wiederherstellbar)
        assertEquals("entwicklung", eintrag.diaryVorschlag)
        assertEquals(
            "Zu diesen Medien gibt es keinen Text mehr. Wiederherstellen legt einen Eintrag ohne Text an.",
            papierkorbOhneEintrag(ohneEintrag),
        )
        assertEquals(
            "Der Ordner ist leer; hier gibt es nichts wiederherzustellen.",
            papierkorbOhneEintrag(ohneEintrag.copy(wiederherstellbar = false, medien = 0)),
        )
    }

    @Test
    fun `kommt mit einem Server ohne Vorschlag zurecht`() {
        val alt = PapierkorbEintrag.fromJson(
            JSONObject("""{"kennung":"k","diary":"entwicklung","wiederherstellbar":true,"eintrag":{"id":1}}"""),
        )
        assertEquals("entwicklung", alt.diaryVorschlag)
        assertFalse(alt.eintrag.diary.isEmpty())
    }

    @Test
    fun `beschriftet die Karten`() {
        assertEquals("Entwicklung: Eintrag vom 03.11.2026", papierkorbTitel(eintrag))
        assertEquals("Medien ohne Eintrag vom 27.07.2025", papierkorbTitel(ohneEintrag))
        assertEquals("2 Fotos/Videos (20,1 MB)", papierkorbMedien(eintrag.medien, eintrag.bytes))
        assertEquals("1 Foto/Video (1,6 MB)", papierkorbMedien(ohneEintrag.medien, ohneEintrag.bytes))
        assertEquals(
            "Gelöscht am 12.11.2026 13:52 · wird am 12.12.2026 endgültig entfernt (noch 30 Tage)",
            papierkorbFrist(eintrag),
        )
        assertEquals(
            "Gelöscht am 12.11.2026 13:44 · wird am 12.12.2026 endgültig entfernt (noch 1 Tag)",
            papierkorbFrist(ohneEintrag),
        )
    }
}
