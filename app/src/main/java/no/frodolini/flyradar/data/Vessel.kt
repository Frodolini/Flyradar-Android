package no.frodolini.flyradar.data

import no.frodolini.flyradar.ais.AisEta
import no.frodolini.flyradar.ais.AisMessage
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime

/** Ein båt, sett saman av posisjonsmeldingar og meldingar med namn og type. */
data class Vessel(
    val mmsi: Int,
    val lat: Double,
    val lon: Double,
    val speedKnots: Double?,
    val course: Double?,
    val heading: Int?,
    val navStatus: Int,
    val classB: Boolean,
    val name: String?,
    val callsign: String?,
    val shipType: Int?,
    val destination: String?,
    val lengthM: Int?,
    val draughtM: Double?,
    /** Tidspunkt (ms) for siste posisjon. */
    val updatedAt: Long,
    val eta: AisEta? = null,
) {
    val label: String get() = name?.takeIf { it.isNotBlank() } ?: "MMSI $mmsi"
    val category: VesselCategory get() = VesselCategory.of(shipType)
    val isMoving: Boolean get() = (speedKnots ?: 0.0) >= 0.5
    /** Retninga ikonet skal peike: kurs når båten går, elles baugretning. */
    val direction: Double? get() = if (isMoving) course ?: heading?.toDouble() else heading?.toDouble()
}

/** Grove grupper av AIS-skipstypar, brukte til fargar på kartet. */
enum class VesselCategory(val label: String) {
    PASSENGER("Passasjer/ferje"),
    CARGO("Last"),
    TANKER("Tankskip"),
    FISHING("Fiske"),
    SERVICE("Slep/service"),
    LEISURE("Fritid/segl"),
    OTHER("Anna/ukjend");

    companion object {
        fun of(type: Int?): VesselCategory = when (type) {
            null -> OTHER
            in 60..69 -> PASSENGER
            in 70..79 -> CARGO
            in 80..89 -> TANKER
            30 -> FISHING
            31, 32, 33, 50, 51, 52, 53, 54, 55, 58 -> SERVICE
            36, 37 -> LEISURE
            else -> OTHER
        }
    }
}

/**
 * Gjer ETA frå AIS (utan årstal, i UTC) om til eit tidspunkt. Me vel året som
 * gjev tidspunktet nærast [now]. ETA meir enn 3 døgn tilbake i tid er truleg
 * gammal og blir ikkje vist.
 */
fun etaInstant(eta: AisEta, now: Instant): Instant? {
    val year = now.atZone(ZoneOffset.UTC).year
    val candidates = (year - 1..year + 1).mapNotNull { y ->
        runCatching { ZonedDateTime.of(y, eta.month, eta.day, eta.hour, eta.minute, 0, 0, ZoneOffset.UTC).toInstant() }.getOrNull()
    }
    val best = candidates.minByOrNull { kotlin.math.abs(it.epochSecond - now.epochSecond) } ?: return null
    return if (best.isBefore(now.minusSeconds(3 * 24 * 3600))) null else best
}

/** Tekst for navigasjonsstatus i AIS. */
fun navStatusText(status: Int): String? = when (status) {
    0 -> "Går for motor"
    1 -> "Ankra"
    2 -> "Ikkje under kommando"
    3 -> "Avgrensa manøvreringsevne"
    4 -> "Avgrensa av djupgang"
    5 -> "Fortøydd"
    6 -> "På grunn"
    7 -> "Fiskar"
    8 -> "Seglar"
    else -> null
}

/**
 * Held oversikt over båtane. Tek imot dekoda AIS-meldingar og fører saman
 * posisjon og namn for kvar MMSI. Veit ingenting om Android.
 */
class VesselStore(private val now: () -> Long = System::currentTimeMillis) {
    private data class StaticInfo(
        val name: String? = null, val callsign: String? = null, val shipType: Int? = null,
        val destination: String? = null, val lengthM: Int? = null, val draughtM: Double? = null,
        val eta: AisEta? = null,
    )

    private val positions = HashMap<Int, AisMessage.Position>()
    private val posTime = HashMap<Int, Long>()
    private val statics = HashMap<Int, StaticInfo>()

    /**
     * Legg til ei melding. Posisjonar utanfor [maxKm] frå midtpunktet blir ignorerte,
     * så me ikkje held på heile norskekysten i minnet.
     */
    @Synchronized
    fun add(msg: AisMessage, centerLat: Double, centerLon: Double, maxKm: Double) {
        when (msg) {
            is AisMessage.Position -> addPosition(msg, centerLat, centerLon, maxKm)
            is AisMessage.Static -> mergeStatic(msg.mmsi) {
                it.copy(
                    name = msg.name?.ifBlank { null } ?: it.name,
                    callsign = msg.callsign?.ifBlank { null } ?: it.callsign,
                    shipType = msg.shipType?.takeIf { t -> t > 0 } ?: it.shipType,
                    destination = msg.destination?.ifBlank { null } ?: it.destination,
                    lengthM = msg.lengthM?.takeIf { l -> l > 0 } ?: it.lengthM,
                    draughtM = msg.draughtM?.takeIf { d -> d > 0 } ?: it.draughtM,
                    eta = msg.eta ?: it.eta,
                )
            }
            is AisMessage.ExtendedB -> {
                addPosition(msg.position, centerLat, centerLon, maxKm)
                mergeStatic(msg.mmsi) {
                    it.copy(
                        name = msg.name.ifBlank { null } ?: it.name,
                        shipType = msg.shipType.takeIf { t -> t > 0 } ?: it.shipType,
                    )
                }
            }
        }
    }

    private fun addPosition(p: AisMessage.Position, cLat: Double, cLon: Double, maxKm: Double) {
        val lat = p.lat ?: return
        val lon = p.lon ?: return
        if (distanceKm(cLat, cLon, lat, lon) > maxKm) {
            positions.remove(p.mmsi)   // båten har gått ut av området
            posTime.remove(p.mmsi)
            return
        }
        positions[p.mmsi] = p
        posTime[p.mmsi] = now()
    }

    private fun mergeStatic(mmsi: Int, change: (StaticInfo) -> StaticInfo) {
        if (statics.size > 20000) statics.clear()
        statics[mmsi] = change(statics[mmsi] ?: StaticInfo())
    }

    /** Alle båtar med posisjon som er nyare enn [maxAgeMs]. Eldre blir fjerna. */
    @Synchronized
    fun snapshot(maxAgeMs: Long = 15 * 60_000L): List<Vessel> {
        val t = now()
        val stale = posTime.filterValues { t - it > maxAgeMs }.keys
        stale.forEach { positions.remove(it); posTime.remove(it) }
        return positions.values.map { p ->
            val s = statics[p.mmsi] ?: StaticInfo()
            Vessel(
                mmsi = p.mmsi, lat = p.lat!!, lon = p.lon!!, speedKnots = p.speedKnots,
                course = p.course, heading = p.heading, navStatus = p.navStatus, classB = p.classB,
                name = s.name, callsign = s.callsign, shipType = s.shipType,
                destination = s.destination, lengthM = s.lengthM, draughtM = s.draughtM,
                updatedAt = posTime[p.mmsi] ?: t, eta = s.eta,
            )
        }
    }

    @Synchronized
    fun clear() {
        positions.clear(); posTime.clear()
    }
}
