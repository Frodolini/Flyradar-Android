package no.frodolini.flyradar.data

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Stord, midtpunktet for kartet. */
object Stord {
    const val LAT = 59.78
    const val LON = 5.50
}

/** Avstand i km mellom to punkt på jorda (haversine-formelen). */
fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2).pow(2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
    return 2 * r * asin(sqrt(a))
}

const val KM_PER_NM = 1.852
