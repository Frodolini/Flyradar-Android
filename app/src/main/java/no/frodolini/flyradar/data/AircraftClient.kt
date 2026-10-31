package no.frodolini.flyradar.data

import java.net.HttpURLConnection
import java.net.URL

/**
 * Hentar fly frå opne ADS-B-API. Prøver adsb.lol først, og adsb.fi viss det feilar.
 * Ein app er ikkje ei nettside, så her er det ikkje noko CORS-problem.
 */
class AircraftClient {

    data class Result(val source: String, val aircraft: List<Aircraft>)

    private val sources = listOf(
        "adsb.lol" to "https://api.adsb.lol/v2/point/%f/%f/%d",
        "adsb.fi" to "https://opendata.adsb.fi/api/v2/lat/%f/lon/%f/dist/%d",
    )

    /** Kallar nettet, så må køyrast utanfor hovudtråden. */
    fun fetch(lat: Double, lon: Double, radiusKm: Int): Result {
        val nm = (radiusKm / KM_PER_NM).toInt().coerceIn(1, 250)
        var lastError: Exception? = null
        for ((name, pattern) in sources) {
            try {
                val url = URL(String.format(java.util.Locale.ROOT, pattern, lat, lon, nm))
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8000
                    readTimeout = 10000
                    setRequestProperty("User-Agent", "Flyradar-Android")
                }
                try {
                    if (conn.responseCode != 200) throw RuntimeException("HTTP ${conn.responseCode}")
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    return Result(name, AircraftParser.parse(body))
                } finally {
                    conn.disconnect()
                }
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: RuntimeException("Ingen kjelder svarte")
    }
}
