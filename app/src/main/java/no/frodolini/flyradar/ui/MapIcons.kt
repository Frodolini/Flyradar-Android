package no.frodolini.flyradar.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.core.graphics.PathParser
import no.frodolini.flyradar.data.Aircraft
import no.frodolini.flyradar.data.Vessel
import no.frodolini.flyradar.data.VesselCategory
import kotlin.math.roundToInt

/** Fargar, delte mellom kartet og forklaringa i appen. */
object Palette {
    fun altitude(a: Aircraft): Int = when {
        a.onGround || a.altitudeFt == null -> 0xFF8C959F.toInt()
        a.altitudeFt < 3000 -> 0xFF2EA043.toInt()
        a.altitudeFt < 10000 -> 0xFFD29922.toInt()
        a.altitudeFt < 25000 -> 0xFFDB6D28.toInt()
        else -> 0xFF0B6BCB.toInt()
    }

    fun vessel(c: VesselCategory): Int = when (c) {
        VesselCategory.PASSENGER -> 0xFF1F6FEB.toInt()
        VesselCategory.CARGO -> 0xFF1A7F37.toInt()
        VesselCategory.TANKER -> 0xFFCF222E.toInt()
        VesselCategory.FISHING -> 0xFFE16F24.toInt()
        VesselCategory.SERVICE -> 0xFF8250DF.toInt()
        VesselCategory.LEISURE -> 0xFFBF3989.toInt()
        VesselCategory.OTHER -> 0xFF6E7781.toInt()
    }

    val altitudeLegend = listOf(
        "Bakken" to 0xFF8C959F.toInt(), "< 3 000 ft" to 0xFF2EA043.toInt(),
        "< 10 000 ft" to 0xFFD29922.toInt(), "< 25 000 ft" to 0xFFDB6D28.toInt(),
        "≥ 25 000 ft" to 0xFF0B6BCB.toInt(),
    )
}

/**
 * Teiknar ikon for kartet: eit fly eller ein båt i rett farge, rotert etter kurs.
 * Ikona blir lagra, så kvar variant berre blir teikna éin gong.
 */
class MapIcons(private val context: Context) {
    private val cache = HashMap<String, Drawable>()
    private val density = context.resources.displayMetrics.density

    // Same flyform som på nettsida, teikna i ein 24x24-boks med nasen opp
    private val planePath: Path = PathParser.createPathFromPathData(
        "M12 2c.7 0 1.2.6 1.2 1.4v6l7.8 4.6v2l-7.8-2.4v4.6l2.2 1.7v1.6L12 20.6l-3.4.9v-1.6l2.2-1.7v-4.6L3 16v-2l7.8-4.6v-6C10.8 2.6 11.3 2 12 2z"
    )
    // Båt: spiss baug opp
    private val boatPath: Path = PathParser.createPathFromPathData("M12 3L17 10V21H7V10Z")

    fun plane(a: Aircraft, selected: Boolean): Drawable {
        val heading = bucket(a.track ?: 0.0)
        return get("p|${Palette.altitude(a)}|$heading|$selected") {
            draw(planePath, Palette.altitude(a), heading, if (selected) 40 else 30, selected, circle = false)
        }
    }

    fun boat(v: Vessel, selected: Boolean): Drawable {
        val color = Palette.vessel(v.category)
        val dir = v.direction
        // Båtar som ligg i ro (eller utan kjend retning) blir vist som ein prikk
        if (!v.isMoving || dir == null) {
            return get("b0|$color|$selected") { draw(boatPath, color, 0, if (selected) 26 else 16, selected, circle = true) }
        }
        val heading = bucket(dir)
        return get("b|$color|$heading|$selected") { draw(boatPath, color, heading, if (selected) 34 else 24, selected, circle = false) }
    }

    private fun bucket(deg: Double) = ((deg / 10.0).roundToInt() * 10) % 360

    private fun get(key: String, make: () -> Drawable) = cache.getOrPut(key, make)

    private fun draw(shape: Path, color: Int, rotation: Int, sizeDp: Int, selected: Boolean, circle: Boolean): Drawable {
        val px = (sizeDp * density).roundToInt()
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.FILL }
        val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = 0x99000000.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 1.0f * density
        }
        if (selected) {
            // Valt: kvit ring rundt ikonet
            val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = 0xFFFFFFFF.toInt(); style = Paint.Style.STROKE; strokeWidth = 3f * density
            }
            val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = 0x55FFFFFF }
            c.drawCircle(px / 2f, px / 2f, px / 2f - 2 * density, halo)
            c.drawCircle(px / 2f, px / 2f, px / 2f - 2 * density, ring)
        }
        if (circle) {
            val r = (if (selected) px * 0.3f else px / 2f - 2 * density)
            c.drawCircle(px / 2f, px / 2f, r, fill)
            c.drawCircle(px / 2f, px / 2f, r, edge)
        } else {
            val s = px / 24f * (if (selected) 0.72f else 1f)
            c.save()
            c.rotate(rotation.toFloat(), px / 2f, px / 2f)
            c.translate((px - 24 * s) / 2f, (px - 24 * s) / 2f)
            c.scale(s, s)
            edge.strokeWidth /= s
            c.drawPath(shape, fill)
            c.drawPath(shape, edge)
            c.restore()
        }
        return BitmapDrawable(context.resources, bmp)
    }
}
