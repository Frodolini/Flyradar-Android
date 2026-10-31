package no.frodolini.flyradar.data

data class TrailPoint(val lat: Double, val lon: Double, val time: Long)

/**
 * Hugsar kvar fly og båtar har vore, så kartet kan teikne eit spor bak dei.
 * Berre posisjonar sidan appen vart opna blir med. Veit ingenting om Android.
 */
class TrailBook(
    /** Kor lenge eit punkt blir hugsa. */
    private val maxAgeMs: Long = 20 * 60_000L,
    /** Maks tal punkt per spor, så minnet ikkje veks. */
    private val maxPoints: Int = 300,
) {
    private val trails = HashMap<String, ArrayDeque<TrailPoint>>()

    /**
     * Legg til ein posisjon. Viss [key] ikkje har flytt seg minst [minMeters]
     * sidan førre punkt, blir han ikkje lagra.
     */
    fun add(key: String, lat: Double, lon: Double, time: Long, minMeters: Double) {
        val t = trails.getOrPut(key) { ArrayDeque() }
        val last = t.lastOrNull()
        if (last != null && distanceKm(last.lat, last.lon, lat, lon) * 1000 < minMeters) return
        // Hopp på meir enn 50 km er truleg feil i data: start sporet på nytt
        if (last != null && distanceKm(last.lat, last.lon, lat, lon) > 50) t.clear()
        t.addLast(TrailPoint(lat, lon, time))
        while (t.size > maxPoints) t.removeFirst()
    }

    /** Fjernar gamle punkt og spor for ting som ikkje lenger finst ([alive]). */
    fun prune(now: Long, alive: Set<String>) {
        trails.keys.retainAll(alive)
        for (t in trails.values) while (t.isNotEmpty() && now - t.first().time > maxAgeMs) t.removeFirst()
        trails.values.removeAll { it.isEmpty() }
    }

    /** Kopi av alle spor med minst to punkt. */
    fun snapshot(): Map<String, List<TrailPoint>> =
        trails.filterValues { it.size >= 2 }.mapValues { it.value.toList() }

    companion object {
        fun planeKey(hex: String) = "p:$hex"
        fun boatKey(mmsi: Int) = "b:$mmsi"
    }
}
