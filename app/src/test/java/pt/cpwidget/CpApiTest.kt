package pt.cpwidget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class CpApiTest {
    private val date = LocalDate.of(2026, 11, 23)
    private fun sample() = javaClass.getResource("/amadora_sample.json")!!.readText()

    @Test
    fun parsesStationCodeFromUrlOrBareCode() {
        assertEquals("94-60087", parseStationCode("https://www.cp.pt/pt/pesquisa-estacao-detalhe/94-60087"))
        assertEquals("94-60087", parseStationCode("  94-60087 "))
        assertEquals("94-31039", parseStationCode("https://www.cp.pt/en/pesquisa-estacao-detalhe/94-31039?x=1"))
        assertNull(parseStationCode("https://www.cp.pt/pt/pesquisa-estacao"))
    }

    @Test
    fun parsesOnTimeDelayedAndCancelled() {
        val list = CpApi.parseDay(sample(), "94-60087", date)
        // The stop with no departure time is skipped.
        assertEquals(4, list.size)

        val onTime = list[0]
        assertEquals(LocalDateTime.of(2026, 11, 23, 0, 0), onTime.scheduled)
        assertFalse(onTime.isDelayed())
        assertFalse(onTime.cancelled)
        assertEquals("Sintra", onTime.destination)

        val delayed = list[1]
        assertTrue(delayed.isDelayed())
        assertEquals(4, delayed.delayMinutes)
        assertEquals(LocalDateTime.of(2026, 11, 23, 5, 58), delayed.effectiveTime())

        val cancelled = list[3]
        assertTrue(cancelled.cancelled)
        // CP sends delay -1 for cancelled trains; must not count as delayed.
        assertFalse(cancelled.isDelayed())
        assertEquals("Lisboa Oriente", cancelled.destination)
    }

    @Test
    fun dropsTrainsTerminatingAtStation() {
        // Lisboa Oriente is the destination of two sample trains.
        val list = CpApi.parseDay(sample(), "94-31039", date)
        assertTrue(list.none { it.destination == "Lisboa Oriente" })
        assertEquals(2, list.size)
    }

    @Test
    fun estimatedTimeRollsPastMidnight() {
        val d = Departure(LocalDateTime.of(2026, 11, 23, 23, 55), "00:07", 12, false, "Sintra")
        assertEquals(LocalDateTime.of(2026, 11, 24, 0, 7), d.effectiveTime())
    }

    @Test
    fun delayWithoutEtdFallsBackToScheduledPlusDelay() {
        val d = Departure(LocalDateTime.of(2026, 11, 23, 8, 0), null, 5, false, "Sintra")
        assertEquals(LocalDateTime.of(2026, 11, 23, 8, 5), d.effectiveTime())
    }

    /** Live check against CP. Run with: ./gradlew test -Dcp.live=true */
    @Test
    fun liveDepartures() {
        assumeTrue(System.getProperty("cp.live") == "true")
        assertEquals("Amadora", CpApi.stationName("94-60087"))
        val list = CpApi.upcomingDepartures("94-60087")
        println("Live: ${list.size} departures; first 5:")
        list.take(5).forEach { println("  ${it.scheduled} -> ${it.destination} delay=${it.delayMinutes} etd=${it.estimated} cancelled=${it.cancelled}") }
        assertTrue(list.isNotEmpty())
        assertTrue(list.first().effectiveTime() >= LocalDateTime.now(CpApi.LISBON).minusMinutes(2))
    }
}
