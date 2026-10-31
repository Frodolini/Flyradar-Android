package no.frodolini.flyradar

import no.frodolini.flyradar.ais.AisMessage
import no.frodolini.flyradar.data.AircraftParser
import no.frodolini.flyradar.data.VesselCategory
import no.frodolini.flyradar.data.VesselStore
import no.frodolini.flyradar.data.distanceKm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Testar flydata frå JSON, avstandsrekning og samanslåing av båtdata. */
class DataTest {

    @Test
    fun les_fly_frå_adsb_lol() {
        val json = """
            {"ac":[
              {"hex":"478f1d","flight":"SAS928  ","r":"LN-RKT","t":"A333","alt_baro":40000,
               "gs":506.3,"track":212.5,"baro_rate":-64,"squawk":"2741","lat":59.9,"lon":5.1},
              {"hex":"4aca5b","alt_baro":"ground","gs":0,"lat":59.79,"lon":5.34},
              {"hex":"ab12cd","flight":"UTANPOS"}
            ],"msg":"No error","total":3}
        """.trimIndent()
        val list = AircraftParser.parse(json)
        assertEquals(2, list.size)                          // flyet utan posisjon er hoppa over
        val sas = list[0]
        assertEquals("SAS928", sas.label)                   // mellomrom fjerna
        assertEquals(40000, sas.altitudeFt)
        assertEquals("A333", sas.type)
        assertEquals(212.5, sas.track!!, 0.001)
        val bakke = list[1]
        assertTrue(bakke.onGround)
        assertNull(bakke.altitudeFt)
        assertEquals("4ACA5B", bakke.label)                 // ingen kallesignal: ICAO-kode
    }

    @Test
    fun les_fly_frå_adsb_fi() {
        val json = """{"aircraft":[{"hex":"abc123","lat":60.0,"lon":5.0,"alt_baro":3000}],"resultCount":1}"""
        assertEquals(3000, AircraftParser.parse(json).single().altitudeFt)
    }

    @Test
    fun avstand_stord_bergen() {
        // Stord til Bergen sentrum er om lag 70 km i luftlinje
        val km = distanceKm(59.78, 5.50, 60.39, 5.32)
        assertTrue("var $km", km in 65.0..72.0)
    }

    @Test
    fun båt_får_namn_og_type_frå_eiga_melding() {
        val store = VesselStore { 1000L }
        store.add(pos(257000001, 59.80, 5.45), 59.78, 5.50, 100.0)
        store.add(AisMessage.Static(257000001, name = "FJORDFERJE", shipType = 60), 59.78, 5.50, 100.0)
        val v = store.snapshot().single()
        assertEquals("FJORDFERJE", v.label)
        assertEquals(VesselCategory.PASSENGER, v.category)
    }

    @Test
    fun båtar_langt_unna_blir_ikkje_med() {
        val store = VesselStore { 1000L }
        store.add(pos(1, 59.80, 5.45), 59.78, 5.50, 100.0)    // nær Stord
        store.add(pos(2, 69.65, 18.95), 59.78, 5.50, 100.0)   // Tromsø
        assertEquals(listOf(1), store.snapshot().map { it.mmsi })
    }

    @Test
    fun gamle_båtar_forsvinn() {
        var t = 0L
        val store = VesselStore { t }
        store.add(pos(1, 59.80, 5.45), 59.78, 5.50, 100.0)
        t = 20 * 60_000L
        assertTrue(store.snapshot().isEmpty())
    }

    private fun pos(mmsi: Int, lat: Double, lon: Double) =
        AisMessage.Position(mmsi, 0, 10.0, lat, lon, 90.0, 90, false)
}
