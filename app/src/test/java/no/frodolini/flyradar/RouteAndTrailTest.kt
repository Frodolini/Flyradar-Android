package no.frodolini.flyradar

import no.frodolini.flyradar.ais.AisDecoder
import no.frodolini.flyradar.ais.AisEta
import no.frodolini.flyradar.ais.AisMessage
import no.frodolini.flyradar.data.RouteClient
import no.frodolini.flyradar.data.TrailBook
import no.frodolini.flyradar.data.etaInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** Testar flyruter, ETA for båtar og spor. */
class RouteAndTrailTest {

    @Test
    fun les_flyrute_frå_adsbdb() {
        // Forkorta svar frå api.adsbdb.com/v0/callsign/SAS928
        val json = """
            {"response":{"flightroute":{"callsign":"SAS928","callsign_icao":"SAS928","callsign_iata":"SK928",
             "airline":{"name":"Scandinavian Airlines System","icao":"SAS","iata":"SK"},
             "origin":{"iata_code":"BOS","icao_code":"KBOS","latitude":42.3643,"longitude":-71.0052,
                       "municipality":"Boston","name":"Logan International Airport"},
             "destination":{"iata_code":"CPH","icao_code":"EKCH","latitude":55.6179,"longitude":12.656,
                       "municipality":"Copenhagen","name":"Copenhagen Kastrup Airport"}}}}
        """.trimIndent()
        val r = RouteClient.parse(json)!!
        assertEquals("Boston (BOS)", r.origin.label)
        assertEquals("Copenhagen (CPH)", r.destination.label)
        assertEquals("Scandinavian Airlines System", r.airline)
        assertEquals(55.6179, r.destination.lat, 0.0001)
    }

    @Test
    fun ukjent_kallesignal_gjev_null() {
        assertNull(RouteClient.parse("""{"response":"unknown callsign"}"""))
    }

    @Test
    fun eta_frå_ais_type5() {
        val d = AisDecoder()
        d.decode("!AIVDM,2,1,1,A,55?MbV02;H;s<HtKR20EHE:0@T4@Dn2222222216L961O5Gf0NSQEp6ClRp8,0*1C")
        val m = d.decode("!AIVDM,2,2,1,A,88888888880,2*25") as AisMessage.Static
        assertEquals(AisEta(5, 15, 14, 0), m.eta)       // 15. mai kl. 14:00 UTC (fasit frå pyais)
    }

    @Test
    fun eta_utan_verdi_blir_null() {
        assertNull(AisEta.of(0, 0, 24, 60))
    }

    @Test
    fun eta_vel_rett_år() {
        val now = Instant.parse("2026-12-30T12:00:00Z")
        // 2. januar ligg nærast i neste år
        assertEquals(Instant.parse("2027-01-02T08:00:00Z"), etaInstant(AisEta(1, 2, 8, 0), now))
        // 31. desember er i morgon
        assertEquals(Instant.parse("2026-12-31T08:00:00Z"), etaInstant(AisEta(12, 31, 8, 0), now))
        // ein månad gammal ETA blir ikkje vist
        assertNull(etaInstant(AisEta(11, 30, 8, 0), now))
    }

    @Test
    fun spor_hoppar_over_små_rørsler_og_gamle_punkt() {
        val t = TrailBook(maxAgeMs = 10 * 60_000L)
        t.add("b:1", 59.7800, 5.5000, 0, minMeters = 25.0)
        t.add("b:1", 59.7801, 5.5000, 1000, minMeters = 25.0)     // ca. 11 m: hoppa over
        t.add("b:1", 59.7810, 5.5000, 2000, minMeters = 25.0)     // ca. 111 m: med
        assertEquals(2, t.snapshot()["b:1"]!!.size)

        t.add("b:1", 59.7820, 5.5000, 11 * 60_000L, minMeters = 25.0)
        t.prune(11 * 60_000L, setOf("b:1"))
        assertTrue("b:1" !in t.snapshot())                        // berre eitt nytt punkt att
    }

    @Test
    fun spor_for_ting_som_er_borte_blir_fjerna() {
        val t = TrailBook()
        t.add("p:a", 59.0, 5.0, 0, 0.0); t.add("p:a", 59.1, 5.0, 1, 0.0)
        t.add("p:b", 60.0, 5.0, 0, 0.0); t.add("p:b", 60.1, 5.0, 1, 0.0)
        t.prune(2, setOf("p:b"))
        assertEquals(setOf("p:b"), t.snapshot().keys)
    }
}
