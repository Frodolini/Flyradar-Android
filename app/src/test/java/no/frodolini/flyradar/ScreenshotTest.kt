package no.frodolini.flyradar

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import no.frodolini.flyradar.ais.AisStream
import no.frodolini.flyradar.data.Aircraft
import no.frodolini.flyradar.data.Vessel
import no.frodolini.flyradar.ui.RadarScreen
import no.frodolini.flyradar.ui.RadarState
import no.frodolini.flyradar.ui.Selection
import no.frodolini.flyradar.ui.theme.FlyradarTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import androidx.compose.foundation.background

/**
 * Teiknar skjermen med oppdikta fly og båtar, og lagrar skjermbilete i
 * app/build/skjermbilete/. Kartbitane blir ikkje lasta ned i testen.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotTest {

    /** Grå flate i staden for kartet. */
    @androidx.compose.runtime.Composable
    private fun FakeMap(m: androidx.compose.ui.Modifier) {
        androidx.compose.foundation.layout.Box(m.background(androidx.compose.ui.graphics.Color(0xFFE8E4DC)))
    }

    @get:Rule
    val compose = androidx.compose.ui.test.junit4.createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private val fly = listOf(
        Aircraft("478f1d", "SAS928", "LN-RKT", "A333", "AIRBUS A-330-300", 38000, false, 486.0, 212.0, 0, "2741", 59.95, 5.30),
        Aircraft("4ac9e1", "WIF47K", "LN-WDI", "DH8D", null, 6200, false, 250.0, 330.0, -1024, "7034", 59.70, 5.60),
        Aircraft("478a11", null, "LN-OXA", "AS32", null, 1200, false, 120.0, 90.0, 0, null, 59.82, 5.40),
    )
    private val baatar = listOf(
        Vessel(257001000, 59.77, 5.45, 12.5, 40.0, 41, 0, false, "STORD FERJA", "LFTX", 60, "HALHJEM", 110, 4.2, 0L),
        Vessel(257002000, 59.86, 5.38, 0.0, null, 200, 5, false, "KVINNHERAD", null, 70, null, 90, null, 0L),
        Vessel(257003000, 59.73, 5.58, 7.0, 180.0, 179, 7, false, "HAVFISK", null, 30, null, 25, null, 0L),
    )

    // captureToImage() heng i Robolectric, så me teiknar vindauget sjølve
    private fun lagre(namn: String, dialog: Boolean = false) {
        compose.waitForIdle()
        val view = if (dialog) org.robolectric.shadows.ShadowDialog.getLatestDialog().window!!.decorView
                   else compose.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bmp))
        val dir = File("build/skjermbilete").apply { mkdirs() }
        File(dir, namn).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun kart_med_fly_og_baatar() {
        val state = RadarState(
            aircraft = fly, vessels = baatar, planeSource = "adsb.lol",
            planeUpdated = 1_793_430_000_000L, aisStatus = AisStream.Status.CONNECTED,
        )
        compose.setContent { FlyradarTheme { RadarScreen(state, {}, {}, {}, {}, map = { FakeMap(it) }) } }
        compose.onNodeWithText("Fly (3)").assertExists()
        compose.onNodeWithText("Båtar (3)").assertExists()
        lagre("1-kart.png")
    }

    @Test
    fun infokort_for_fly_og_baat() {
        val state = RadarState(aircraft = fly, vessels = baatar, selection = Selection.Plane("478f1d"))
        compose.setContent { FlyradarTheme { RadarScreen(state, {}, {}, {}, {}, map = { FakeMap(it) }) } }
        compose.onNodeWithText("SAS928").assertExists()
        compose.onNodeWithText("38 000 ft (11 582 m)").assertExists()
        lagre("2-fly.png")
    }

    @Test
    fun infokort_for_baat() {
        val state = RadarState(aircraft = fly, vessels = baatar, selection = Selection.Boat(257001000))
        compose.setContent { FlyradarTheme { RadarScreen(state, {}, {}, {}, {}, map = { FakeMap(it) }) } }
        compose.onNodeWithText("STORD FERJA").assertExists()
        compose.onNodeWithText("Passasjer/ferje").assertExists()
        lagre("3-baat.png")
    }

    @Test
    fun forklaring() {
        compose.setContent { FlyradarTheme { RadarScreen(RadarState(), {}, {}, {}, {}, map = { FakeMap(it) }) } }
        compose.onNodeWithContentDescriptionCompat("Forklaring")
        compose.onNodeWithText("Fly (farge etter høgd)").assertExists()
        lagre("4-forklaring.png", dialog = true)
    }

    @Test
    fun ikon_for_kartet() {
        val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val icons = no.frodolini.flyradar.ui.MapIcons(ctx)
        val all = fly.map { icons.plane(it, it.hex == "478f1d") } + baatar.map { icons.boat(it, false) } + icons.boat(baatar[1], true)
        val size = 120
        val bmp = Bitmap.createBitmap(size * all.size, size, Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(bmp)
        c.drawColor(0xFFAAD3DF.toInt())   // sjøfarge som på OSM-kartet
        all.forEachIndexed { i, d ->
            val w = d.intrinsicWidth; val h = d.intrinsicHeight
            val x = i * size + (size - w) / 2; val y = (size - h) / 2
            d.setBounds(x, y, x + w, y + h); d.draw(c)
        }
        val dir = File("build/skjermbilete").apply { mkdirs() }
        File(dir, "5-ikon.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onNodeWithContentDescriptionCompat(d: String) {
        onNode(androidx.compose.ui.test.hasContentDescription(d)).performClick()
    }
}
