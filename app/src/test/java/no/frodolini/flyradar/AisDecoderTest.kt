package no.frodolini.flyradar

import no.frodolini.flyradar.ais.AisDecoder
import no.frodolini.flyradar.ais.AisMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Testar AIS-dekodaren med kjende eksempelmeldingar. Fasiten er kontrollert
 * mot Python-biblioteket pyais.
 */
class AisDecoderTest {

    @Test
    fun posisjonsmelding_type1() {
        val m = AisDecoder().decode("!AIVDM,1,1,,B,177KQJ5000G?tO`K>RA1wUbN0TKH,0*5C") as AisMessage.Position
        assertEquals(477553000, m.mmsi)
        assertEquals(5, m.navStatus)           // fortøydd
        assertEquals(0.0, m.speedKnots!!, 0.001)
        assertEquals(47.582833, m.lat!!, 0.00001)
        assertEquals(-122.345833, m.lon!!, 0.00001)
        assertEquals(51.0, m.course!!, 0.001)
        assertEquals(181, m.heading)
        assertFalse(m.classB)
    }

    @Test
    fun klasseB_type18_utan_kjend_baugretning() {
        val m = AisDecoder().decode("!AIVDM,1,1,,A,B52K>;h00Fc>jpUlNV@ikwpUoP06,0*4C") as AisMessage.Position
        assertEquals(338087471, m.mmsi)
        assertEquals(0.1, m.speedKnots!!, 0.001)
        assertEquals(40.68454, m.lat!!, 0.00001)
        assertEquals(-74.072132, m.lon!!, 0.00001)
        assertEquals(79.6, m.course!!, 0.001)
        assertNull(m.heading)                  // 511 = ikkje tilgjengeleg
        assertTrue(m.classB)
    }

    @Test
    fun skipsdata_over_to_linjer_type5() {
        val d = AisDecoder()
        assertNull(d.decode("!AIVDM,2,1,1,A,55?MbV02;H;s<HtKR20EHE:0@T4@Dn2222222216L961O5Gf0NSQEp6ClRp8,0*1C"))
        val m = d.decode("!AIVDM,2,2,1,A,88888888880,2*25") as AisMessage.Static
        assertEquals(351759000, m.mmsi)
        assertEquals("EVER DIADEM", m.name)
        assertEquals("3FOF8", m.callsign)
        assertEquals(70, m.shipType)
        assertEquals("NEW YORK", m.destination)
        assertEquals(295, m.lengthM)           // 225 + 70
        assertEquals(12.2, m.draughtM!!, 0.001)
    }

    @Test
    fun kystverket_format_med_tag_block_foran() {
        val line = "\\s:2573535,c:1793422283*0A\\!AIVDM,1,1,,B,177KQJ5000G?tO`K>RA1wUbN0TKH,0*5C"
        val m = AisDecoder().decode(line)
        assertEquals(477553000, m!!.mmsi)
    }

    @Test
    fun feil_sjekksum_blir_avvist() {
        assertNull(AisDecoder().decode("!AIVDM,1,1,,B,177KQJ5000G?tO`K>RA1wUbN0TKH,0*5D"))
    }

    @Test
    fun boss_og_ukjende_linjer_gjev_null() {
        val d = AisDecoder()
        assertNull(d.decode(""))
        assertNull(d.decode("hei"))
        assertNull(d.decode("\$GPGGA,123519,4807.038,N,01131.000,E,1,08,0.9,545.4,M,46.9,M,,*47"))
        assertNull(d.decode("!AIVDM,1,1,,B,1,0"))
    }
}
