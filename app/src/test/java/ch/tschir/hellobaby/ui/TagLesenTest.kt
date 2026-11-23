package ch.tschir.hellobaby.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * stats.php des Servers liefert „15.11.2026“ bzw. „Keine Einträge“, die
 * lokale Ablage ISO. Beides muss die Startseite lesen, sonst bleibt
 * „Zuletzt“ im Server-Modus leer.
 */
class TagLesenTest {
    @Test
    fun `liest ISO und deutsche Schreibweise`() {
        assertEquals(LocalDate.of(2026, 11, 15), tagLesen("2026-11-15"))
        assertEquals(LocalDate.of(2026, 11, 15), tagLesen("15.11.2026"))
        assertEquals(LocalDate.of(2026, 4, 22), tagLesen(" 22.04.2026 "))
    }

    @Test
    fun `kein Datum ergibt null`() {
        assertNull(tagLesen("Keine Einträge"))
        assertNull(tagLesen(""))
    }

    @Test
    fun `Anzeige ist immer deutsch`() {
        assertEquals("15.11.2026", anzeigeDatum("2026-11-15"))
        assertEquals("15.11.2026", anzeigeDatum("15.11.2026"))
    }
}
