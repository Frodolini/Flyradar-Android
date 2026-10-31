package no.frodolini.flyradar.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import no.frodolini.flyradar.data.Stord
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon

/**
 * Kartet. osmdroid er eit vanleg Android-View, så det blir lagt inn i Compose
 * med AndroidView. Markørane blir oppdaterte i staden for å lagast på nytt,
 * så kartet ikkje blinkar ved kvar oppdatering.
 */
@Composable
fun RadarMap(state: RadarState, onSelect: (Selection?) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val icons = remember { MapIcons(context) }
    val holder = remember { MarkerHolder() }

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            isTilesScaledToDpi = true
            minZoomLevel = 4.0
            controller.setZoom(8.5)
            controller.setCenter(GeoPoint(Stord.LAT, Stord.LON))
            Marker(this).apply {
                position = GeoPoint(Stord.LAT, Stord.LON)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                icon = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(0xFFD1242F.toInt())
                    setStroke((2 * resources.displayMetrics.density).toInt(), 0xFFFFFFFF.toInt())
                    val px = (12 * resources.displayMetrics.density).toInt()
                    setSize(px, px)
                }
                title = "Stord"
                infoWindow = null
                setOnMarkerClickListener { _, _ -> true }
                overlays.add(this)
            }
        }
    }

    // osmdroid må få vite når appen blir pausa og starta igjen
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> {}
            }
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs); mapView.onDetach() }
    }

    AndroidView(factory = { mapView }, modifier = modifier) { map ->
        holder.sync(map, icons, state, onSelect)
    }
}

/** Held styr på markørane på kartet og oppdaterer dei. */
private class MarkerHolder {
    private val planes = HashMap<String, Marker>()
    private val boats = HashMap<Int, Marker>()
    private var circle: Polygon? = null
    private var circleKm = -1

    fun sync(map: MapView, icons: MapIcons, s: RadarState, onSelect: (Selection?) -> Unit) {
        updateCircle(map, s.radiusKm)

        val sel = s.selection
        val wantedPlanes = if (s.showPlanes) s.aircraft.associateBy { it.hex } else emptyMap()
        planes.keys.filter { it !in wantedPlanes }.forEach { map.overlays.remove(planes.remove(it)) }
        for ((hex, a) in wantedPlanes) {
            val m = planes.getOrPut(hex) { newMarker(map) { onSelect(Selection.Plane(hex)) } }
            m.position = GeoPoint(a.lat, a.lon)
            m.icon = icons.plane(a, sel == Selection.Plane(hex))
        }

        val wantedBoats = if (s.showBoats) s.visibleVessels().associateBy { it.mmsi } else emptyMap()
        boats.keys.filter { it !in wantedBoats }.forEach { map.overlays.remove(boats.remove(it)) }
        for ((mmsi, v) in wantedBoats) {
            val m = boats.getOrPut(mmsi) { newMarker(map, below = true) { onSelect(Selection.Boat(mmsi)) } }
            m.position = GeoPoint(v.lat, v.lon)
            m.icon = icons.boat(v, sel == Selection.Boat(mmsi))
        }
        map.invalidate()
    }

    private fun newMarker(map: MapView, below: Boolean = false, onClick: () -> Unit) = Marker(map).apply {
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        infoWindow = null
        setOnMarkerClickListener { _, _ -> onClick(); true }
        // Båtar under fly, så fly over sjøen ikkje blir gøymde
        if (below) map.overlays.add(1, this) else map.overlays.add(this)
    }

    private fun updateCircle(map: MapView, km: Int) {
        if (km == circleKm) return
        circle?.let { map.overlays.remove(it) }
        circle = Polygon(map).apply {
            points = Polygon.pointsAsCircle(GeoPoint(Stord.LAT, Stord.LON), km * 1000.0)
            outlinePaint.color = 0xAA0B6BCB.toInt()
            outlinePaint.strokeWidth = 3f
            outlinePaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(12f, 16f), 0f)
            fillPaint.color = 0x00000000
            infoWindow = null
            setOnClickListener { _, _, _ -> false }
        }
        map.overlays.add(0, circle)
        circleKm = km
    }
}
