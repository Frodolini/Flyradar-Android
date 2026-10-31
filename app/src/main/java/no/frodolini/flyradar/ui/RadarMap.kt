package no.frodolini.flyradar.ui

import android.graphics.DashPathEffect
import android.graphics.drawable.GradientDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import no.frodolini.flyradar.data.Airport
import no.frodolini.flyradar.data.Stord
import no.frodolini.flyradar.data.TrailBook
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.FolderOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline

/**
 * Kartet. osmdroid er eit vanleg Android-View, så det blir lagt inn i Compose
 * med AndroidView. Markørane blir oppdaterte i staden for å lagast på nytt,
 * så kartet ikkje blinkar ved kvar oppdatering.
 */
@Composable
fun RadarMap(state: RadarState, onSelect: (Selection?) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val icons = remember { MapIcons(context) }

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            isTilesScaledToDpi = true
            minZoomLevel = 3.0
            controller.setZoom(8.5)
            controller.setCenter(GeoPoint(Stord.LAT, Stord.LON))
        }
    }
    val holder = remember { MapLayers(mapView) }

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

/**
 * Laga på kartet, frå botn til topp: radiussirkel, spor, flyrute, Stord, båtar, fly.
 * Kvart lag er ein eigen mappe (FolderOverlay), så rekkjefølgja alltid blir rett.
 */
internal class MapLayers(map: MapView) {
    private val density = map.resources.displayMetrics.density
    private val circleLayer = FolderOverlay()
    private val trailLayer = FolderOverlay()
    private val routeLayer = FolderOverlay()
    private val homeLayer = FolderOverlay()
    private val boatLayer = FolderOverlay()
    private val planeLayer = FolderOverlay()

    private val planes = HashMap<String, Marker>()
    private val boats = HashMap<Int, Marker>()
    private val trails = HashMap<String, Polyline>()
    private var circleKm = -1
    private var routeKey: String? = null

    init {
        listOf(circleLayer, trailLayer, routeLayer, homeLayer, boatLayer, planeLayer).forEach { map.overlays.add(it) }
        homeLayer.add(Marker(map).apply {
            position = GeoPoint(Stord.LAT, Stord.LON)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            icon = dot(0xFFD1242F.toInt(), 12)
            infoWindow = null
            setOnMarkerClickListener { _, _ -> true }
        })
    }

    fun sync(map: MapView, icons: MapIcons, s: RadarState, onSelect: (Selection?) -> Unit) {
        updateCircle(map, s.radiusKm)
        val sel = s.selection

        // Fly
        val wantedPlanes = if (s.showPlanes) s.aircraft.associateBy { it.hex } else emptyMap()
        planes.keys.filter { it !in wantedPlanes }.forEach { planeLayer.remove(planes.remove(it)) }
        for ((hex, a) in wantedPlanes) {
            val m = planes.getOrPut(hex) { newMarker(map, planeLayer) { onSelect(Selection.Plane(hex)) } }
            m.position = GeoPoint(a.lat, a.lon)
            m.icon = icons.plane(a, sel == Selection.Plane(hex))
        }

        // Båtar
        val visibleBoats = if (s.showBoats) s.visibleVessels() else emptyList()
        val wantedBoats = visibleBoats.associateBy { it.mmsi }
        boats.keys.filter { it !in wantedBoats }.forEach { boatLayer.remove(boats.remove(it)) }
        for ((mmsi, v) in wantedBoats) {
            val m = boats.getOrPut(mmsi) { newMarker(map, boatLayer) { onSelect(Selection.Boat(mmsi)) } }
            m.position = GeoPoint(v.lat, v.lon)
            m.icon = icons.boat(v, sel == Selection.Boat(mmsi))
        }

        // Spor: farge som ikonet, tydelegare for det valde
        val selKey = when (sel) {
            is Selection.Plane -> TrailBook.planeKey(sel.hex)
            is Selection.Boat -> TrailBook.boatKey(sel.mmsi)
            null -> null
        }
        val colors = HashMap<String, Int>()
        wantedPlanes.values.forEach { colors[TrailBook.planeKey(it.hex)] = Palette.altitude(it) }
        visibleBoats.forEach { colors[TrailBook.boatKey(it.mmsi)] = Palette.vessel(it.category) }
        val wantedTrails = if (s.showTrails) s.trails.filterKeys { it in colors } else
            s.trails.filterKeys { it == selKey }    // spor av: vis berre sporet til det valde
        trails.keys.filter { it !in wantedTrails }.forEach { trailLayer.remove(trails.remove(it)) }
        for ((key, pts) in wantedTrails) {
            val line = trails.getOrPut(key) {
                Polyline(map).apply {
                    infoWindow = null
                    setOnClickListener { _, _, _ -> false }
                    outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
                    trailLayer.add(this)
                }
            }
            val base = colors[key] ?: 0xFF6E7781.toInt()
            val selected = key == selKey
            line.setPoints(pts.map { GeoPoint(it.lat, it.lon) })
            line.outlinePaint.color = (base and 0x00FFFFFF) or (if (selected) 0xEE shl 24 else 0x77 shl 24)
            line.outlinePaint.strokeWidth = (if (selected) 4f else 2f) * density
        }

        updateRoute(map, s)
        map.invalidate()
    }

    /** Stipla linje frå avgangsflyplass, via flyet, til målet, for det valde flyet. */
    private fun updateRoute(map: MapView, s: RadarState) {
        val sel = s.selection as? Selection.Plane
        val plane = sel?.let { p -> s.aircraft.find { it.hex == p.hex } }
        val route = (plane?.callsign?.let { s.routes[it] } as? RouteLookup.Found)?.route
        if (plane == null || route == null || !s.showPlanes) {
            if (routeKey != null) { routeLayer.items.clear(); routeKey = null }
            return
        }
        // Teikn på nytt kvar gong flyet flyttar seg
        routeKey = plane.hex
        routeLayer.items.clear()
        val here = GeoPoint(plane.lat, plane.lon)
        routeLayer.add(routeLine(map, airportPoint(route.origin), here, 0xAA555555.toInt(), dashed = false))
        routeLayer.add(routeLine(map, here, airportPoint(route.destination), 0xDD0B6BCB.toInt(), dashed = true))
        routeLayer.add(airportMarker(map, route.origin, 0xFF555555.toInt()))
        routeLayer.add(airportMarker(map, route.destination, 0xFF0B6BCB.toInt()))
    }

    private fun airportPoint(a: Airport) = GeoPoint(a.lat, a.lon)

    private fun routeLine(map: MapView, from: GeoPoint, to: GeoPoint, color: Int, dashed: Boolean) =
        Polyline(map).apply {
            setPoints(listOf(from, to))
            isGeodesic = true        // følgjer jordkrumminga, som flyruter gjer
            infoWindow = null
            setOnClickListener { _, _, _ -> false }
            outlinePaint.color = color
            outlinePaint.strokeWidth = 3f * density
            if (dashed) outlinePaint.pathEffect = DashPathEffect(floatArrayOf(10 * density, 8 * density), 0f)
        }

    private fun airportMarker(map: MapView, a: Airport, color: Int) = Marker(map).apply {
        position = airportPoint(a)
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        icon = dot(color, 10)
        infoWindow = null
        setOnMarkerClickListener { _, _ -> true }
    }

    private fun newMarker(map: MapView, layer: FolderOverlay, onClick: () -> Unit) = Marker(map).apply {
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        infoWindow = null
        setOnMarkerClickListener { _, _ -> onClick(); true }
        layer.add(this)
    }

    private fun dot(color: Int, sizeDp: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
        setStroke((2 * density).toInt(), 0xFFFFFFFF.toInt())
        val px = (sizeDp * density).toInt()
        setSize(px, px)
    }

    private fun updateCircle(map: MapView, km: Int) {
        if (km == circleKm) return
        circleLayer.items.clear()
        circleLayer.add(Polygon(map).apply {
            points = Polygon.pointsAsCircle(GeoPoint(Stord.LAT, Stord.LON), km * 1000.0)
            outlinePaint.color = 0xAA0B6BCB.toInt()
            outlinePaint.strokeWidth = 1.5f * density
            outlinePaint.pathEffect = DashPathEffect(floatArrayOf(6 * density, 8 * density), 0f)
            fillPaint.color = 0x00000000
            infoWindow = null
            setOnClickListener { _, _, _ -> false }
        })
        circleKm = km
    }
}
