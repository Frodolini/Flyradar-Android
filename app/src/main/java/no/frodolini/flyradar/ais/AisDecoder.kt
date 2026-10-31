package no.frodolini.flyradar.ais

/**
 * Dekodar AIS-meldingar (NMEA-linjer som "!AIVDM,1,1,,B,177KQJ5000G?tO`K>RA1wUbN0TKH,0*5C").
 *
 * AIS sender data som ein lang rekkje bitar. I NMEA-linja er bitane pakka
 * som tekst, der kvart teikn ber 6 bitar ("six-bit ASCII"). Dekodaren
 * pakkar teikna ut att til bitar, og les felta på faste bitposisjonar.
 *
 * Veit ingenting om Android, så han kan testast rett på PC-en.
 */
class AisDecoder {

    /** Delar av meldingar som er sende over fleire linjer, til alle delane er komne. */
    private val fragment = HashMap<String, Array<String?>>()

    /**
     * Tek imot éi linje. Returnerer ei dekoda melding når ho er komplett,
     * elles null (ukjend type, øydelagd linje eller berre første del av ei fleirlinje-melding).
     */
    fun decode(line: String): AisMessage? {
        val start = line.indexOf('!')
        if (start < 0) return null
        val sentence = line.substring(start).trim()
        if (!checksumOk(sentence)) return null

        val body = sentence.substringBefore('*')
        val f = body.split(',')
        if (f.size < 7) return null
        if (!(f[0].endsWith("VDM") || f[0].endsWith("VDO"))) return null

        val total = f[1].toIntOrNull() ?: return null
        val num = f[2].toIntOrNull() ?: return null
        val seq = f[3]
        val channel = f[4]
        val payload = f[5]
        val fill = f[6].toIntOrNull() ?: 0

        val fullPayload = if (total == 1) {
            payload
        } else {
            if (num !in 1..total) return null
            val key = "$seq|$channel|$total"
            val parts = fragment.getOrPut(key) { arrayOfNulls(total) }
            if (parts.size != total) return null
            parts[num - 1] = payload
            if (parts.any { it == null }) {
                if (fragment.size > 200) fragment.clear()   // hindrar at gamle bitar hopar seg opp
                return null
            }
            fragment.remove(key)
            parts.joinToString("")
        }

        return try {
            parse(Bits(fullPayload, fill))
        } catch (e: IndexOutOfBoundsException) {
            null    // for kort melding
        }
    }

    private fun parse(b: Bits): AisMessage? {
        val type = b.uint(0, 6)
        val mmsi = b.uint(8, 30)
        return when (type) {
            1, 2, 3 -> AisMessage.Position(
                mmsi = mmsi,
                navStatus = b.uint(38, 4),
                speedKnots = speed(b.uint(50, 10)),
                lon = lon(b.int(61, 28)),
                lat = lat(b.int(89, 27)),
                course = course(b.uint(116, 12)),
                heading = heading(b.uint(128, 9)),
                classB = false,
            )
            18 -> AisMessage.Position(
                mmsi = mmsi,
                navStatus = 15,
                speedKnots = speed(b.uint(46, 10)),
                lon = lon(b.int(57, 28)),
                lat = lat(b.int(85, 27)),
                course = course(b.uint(112, 12)),
                heading = heading(b.uint(124, 9)),
                classB = true,
            )
            19 -> AisMessage.ExtendedB(
                position = AisMessage.Position(
                    mmsi = mmsi,
                    navStatus = 15,
                    speedKnots = speed(b.uint(46, 10)),
                    lon = lon(b.int(57, 28)),
                    lat = lat(b.int(85, 27)),
                    course = course(b.uint(112, 12)),
                    heading = heading(b.uint(124, 9)),
                    classB = true,
                ),
                name = b.text(143, 20),
                shipType = b.uint(263, 8),
            )
            5 -> AisMessage.Static(
                mmsi = mmsi,
                name = b.text(112, 20),
                callsign = b.text(70, 7),
                shipType = b.uint(232, 8),
                destination = b.text(302, 20),
                lengthM = b.uint(240, 9) + b.uint(249, 9),
                draughtM = b.uint(294, 8) / 10.0,
            )
            24 -> when (b.uint(38, 2)) {
                0 -> AisMessage.Static(mmsi = mmsi, name = b.text(40, 20))
                1 -> AisMessage.Static(
                    mmsi = mmsi,
                    shipType = b.uint(40, 8),
                    callsign = b.text(90, 7),
                    lengthM = b.uint(132, 9) + b.uint(141, 9),
                )
                else -> null
            }
            else -> null
        }
    }

    companion object {
        /** NMEA-sjekksum: XOR av alle teikn mellom '!' og '*'. Linjer utan sjekksum blir godtekne. */
        fun checksumOk(sentence: String): Boolean {
            val star = sentence.indexOf('*')
            if (star < 0) return true
            val expected = sentence.substring(star + 1).take(2).toIntOrNull(16) ?: return false
            var x = 0
            for (i in 1 until star) x = x xor sentence[i].code
            return x == expected
        }

        private fun speed(raw: Int) = if (raw == 1023) null else raw / 10.0
        private fun course(raw: Int) = if (raw >= 3600) null else raw / 10.0
        private fun heading(raw: Int) = if (raw >= 360) null else raw
        private fun lon(raw: Int) = (raw / 600000.0).takeIf { it in -180.0..180.0 }
        private fun lat(raw: Int) = (raw / 600000.0).takeIf { it in -90.0..90.0 }
    }
}

/** Bitane i ei AIS-melding, pakka ut frå six-bit-teikna. */
internal class Bits(payload: String, fill: Int) {
    private val bits: BooleanArray

    init {
        val n = payload.length * 6 - fill.coerceIn(0, 5)
        bits = BooleanArray(maxOf(n, 0))
        var i = 0
        for (c in payload) {
            var v = c.code - 48
            if (v > 40) v -= 8
            for (k in 5 downTo 0) {
                if (i < bits.size) bits[i] = (v shr k) and 1 == 1
                i++
            }
        }
    }

    /** Usignert tal frå bit [start], [len] bitar langt. */
    fun uint(start: Int, len: Int): Int {
        var v = 0L
        for (i in start until start + len) v = (v shl 1) or (if (bits[i]) 1L else 0L)
        return v.toInt()
    }

    /** Signert tal (toarskomplement). */
    fun int(start: Int, len: Int): Int {
        val v = uint(start, len).toLong() and ((1L shl len) - 1)
        return if (v and (1L shl (len - 1)) != 0L) (v - (1L shl len)).toInt() else v.toInt()
    }

    /** Tekst: 6 bitar per teikn, '@' betyr tomt. Er meldinga for kort, blir resten tom. */
    fun text(start: Int, chars: Int): String {
        val sb = StringBuilder()
        for (c in 0 until chars) {
            val pos = start + c * 6
            if (pos + 6 > bits.size) break
            var v = uint(pos, 6)
            if (v < 32) v += 64
            sb.append(v.toChar())
        }
        return sb.toString().replace('@', ' ').trim()
    }
}

/** Det dekodaren kan gje tilbake. */
sealed interface AisMessage {
    val mmsi: Int

    data class Position(
        override val mmsi: Int,
        val navStatus: Int,
        val speedKnots: Double?,
        val lat: Double?,
        val lon: Double?,
        val course: Double?,
        val heading: Int?,
        val classB: Boolean,
    ) : AisMessage

    /** Namn, type og liknande. Felt som ikkje er med i meldinga er null. */
    data class Static(
        override val mmsi: Int,
        val name: String? = null,
        val callsign: String? = null,
        val shipType: Int? = null,
        val destination: String? = null,
        val lengthM: Int? = null,
        val draughtM: Double? = null,
    ) : AisMessage

    /** Klasse B-melding med både posisjon og namn (type 19). */
    data class ExtendedB(val position: Position, val name: String, val shipType: Int) : AisMessage {
        override val mmsi get() = position.mmsi
    }
}
