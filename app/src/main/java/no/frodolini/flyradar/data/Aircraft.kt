package no.frodolini.flyradar.data

import org.json.JSONObject

/** Eitt fly, slik det kjem frå ADS-B-nettverket. Felt som manglar er null. */
data class Aircraft(
    val hex: String,
    val callsign: String?,
    val registration: String?,
    val type: String?,
    val description: String?,
    /** Høgd i fot. null når ukjend. */
    val altitudeFt: Int?,
    val onGround: Boolean,
    val speedKnots: Double?,
    val track: Double?,
    val verticalRate: Int?,
    val squawk: String?,
    val lat: Double,
    val lon: Double,
) {
    /** Det som blir vist som namn: kallesignal, registrering eller ICAO-kode. */
    val label: String get() = callsign ?: registration ?: hex.uppercase()
}

/**
 * Les JSON frå adsb.lol (feltet "ac") eller adsb.fi (feltet "aircraft").
 * Fly utan posisjon blir hoppa over.
 */
object AircraftParser {
    fun parse(json: String): List<Aircraft> {
        val root = JSONObject(json)
        val arr = root.optJSONArray("ac") ?: root.optJSONArray("aircraft") ?: return emptyList()
        val out = ArrayList<Aircraft>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (!o.has("lat") || !o.has("lon")) continue
            val altRaw = o.opt("alt_baro")
            val onGround = altRaw == "ground"
            out += Aircraft(
                hex = o.optString("hex"),
                callsign = o.str("flight"),
                registration = o.str("r"),
                type = o.str("t"),
                description = o.str("desc"),
                altitudeFt = (altRaw as? Number)?.toInt() ?: o.num("alt_geom")?.toInt(),
                onGround = onGround,
                speedKnots = o.num("gs"),
                track = o.num("track") ?: o.num("true_heading"),
                verticalRate = (o.num("baro_rate") ?: o.num("geom_rate"))?.toInt(),
                squawk = o.str("squawk"),
                lat = o.getDouble("lat"),
                lon = o.getDouble("lon"),
            )
        }
        return out
    }

    private fun JSONObject.str(key: String): String? =
        optString(key, "").trim().takeIf { it.isNotEmpty() && has(key) }

    private fun JSONObject.num(key: String): Double? =
        (opt(key) as? Number)?.toDouble()
}
