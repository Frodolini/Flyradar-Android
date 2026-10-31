package no.frodolini.flyradar.data

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class Airport(
    val iata: String?,
    val icao: String?,
    val city: String?,
    val name: String?,
    val lat: Double,
    val lon: Double,
) {
    /** Til dømes "Boston (BOS)". */
    val label: String get() {
        val code = iata ?: icao
        val place = city ?: name ?: code ?: "?"
        return if (code != null && code != place) "$place ($code)" else place
    }
}

data class FlightRoute(val callsign: String, val airline: String?, val origin: Airport, val destination: Airport)

/**
 * Slår opp kvar eit fly kjem frå og skal, ut frå kallesignalet.
 * ADS-B-signalet inneheld ikkje dette, så me brukar den opne databasen adsbdb.com.
 * Ruta er den vanlege for kallesignalet, så ekstraflygingar kan få feil rute.
 */
class RouteClient {

    /** Returnerer null når ruta er ukjend. Kastar unntak ved nettverksfeil. */
    fun fetch(callsign: String): FlightRoute? {
        val url = URL("https://api.adsbdb.com/v0/callsign/" + URLEncoder.encode(callsign.trim(), "UTF-8"))
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 10000
            setRequestProperty("User-Agent", "Flyradar-Android")
        }
        try {
            val code = conn.responseCode
            if (code == 404) return null
            if (code != 200) throw RuntimeException("HTTP $code")
            return parse(conn.inputStream.bufferedReader().use { it.readText() })
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        fun parse(json: String): FlightRoute? {
            val response = JSONObject(json).opt("response") as? JSONObject ?: return null
            val r = response.optJSONObject("flightroute") ?: return null
            val origin = airport(r.optJSONObject("origin")) ?: return null
            val dest = airport(r.optJSONObject("destination")) ?: return null
            return FlightRoute(
                callsign = r.optString("callsign_icao").ifBlank { r.optString("callsign") },
                airline = r.optJSONObject("airline")?.str("name"),
                origin = origin,
                destination = dest,
            )
        }

        private fun airport(o: JSONObject?): Airport? {
            if (o == null || !o.has("latitude") || !o.has("longitude")) return null
            return Airport(
                iata = o.str("iata_code"), icao = o.str("icao_code"),
                city = o.str("municipality"), name = o.str("name"),
                lat = o.getDouble("latitude"), lon = o.getDouble("longitude"),
            )
        }

        private fun JSONObject.str(key: String): String? =
            optString(key, "").trim().takeIf { it.isNotEmpty() }
    }
}
