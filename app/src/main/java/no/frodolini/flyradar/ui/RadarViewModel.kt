package no.frodolini.flyradar.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import no.frodolini.flyradar.ais.AisStream
import no.frodolini.flyradar.data.Aircraft
import no.frodolini.flyradar.data.AircraftClient
import no.frodolini.flyradar.data.FlightRoute
import no.frodolini.flyradar.data.RouteClient
import no.frodolini.flyradar.data.Stord
import no.frodolini.flyradar.data.TrailBook
import no.frodolini.flyradar.data.TrailPoint
import no.frodolini.flyradar.data.Vessel
import no.frodolini.flyradar.data.VesselStore

/** Kva som er valt på kartet (det som blir vist i infokortet). */
sealed interface Selection {
    data class Plane(val hex: String) : Selection
    data class Boat(val mmsi: Int) : Selection
}

/** Oppslag av flyrute for eitt kallesignal. */
sealed interface RouteLookup {
    data object Loading : RouteLookup
    data class Found(val route: FlightRoute) : RouteLookup
    data object Unknown : RouteLookup
    data object Failed : RouteLookup
}

data class RadarState(
    val showPlanes: Boolean = true,
    val showBoats: Boolean = true,
    val radiusKm: Int = 100,
    val aircraft: List<Aircraft> = emptyList(),
    val vessels: List<Vessel> = emptyList(),
    val planeSource: String? = null,
    val planeError: String? = null,
    val planeUpdated: Long? = null,
    val aisStatus: AisStream.Status? = null,
    val aisError: String? = null,
    val selection: Selection? = null,
    /** Flyruter, med kallesignal som nøkkel. */
    val routes: Map<String, RouteLookup> = emptyMap(),
    /** Spor bak fly og båtar, sjå TrailBook for nøklane. */
    val trails: Map<String, List<TrailPoint>> = emptyMap(),
    val showTrails: Boolean = true,
)

/**
 * Held på tilstanden til appen: kva fly og båtar som finst, kva som er slått på,
 * og kva som er valt. Hentar data berre medan appen er synleg (start/stop).
 */
class RadarViewModel : ViewModel() {

    private val _state = MutableStateFlow(RadarState())
    val state: StateFlow<RadarState> = _state.asStateFlow()

    private val planes = AircraftClient()
    private val ais = AisStream()
    private val store = VesselStore()
    private val routeClient = RouteClient()
    private val trailBook = TrailBook()

    private var planeJob: Job? = null
    private var aisJob: Job? = null
    private var boatTickJob: Job? = null

    /** Kalla når appen blir synleg. */
    fun start() {
        if (_state.value.showPlanes) startPlanes()
        if (_state.value.showBoats) startBoats()
    }

    /** Kalla når appen går i bakgrunnen, så han ikkje brukar data og straum. */
    fun stop() {
        planeJob?.cancel(); planeJob = null
        aisJob?.cancel(); aisJob = null
        boatTickJob?.cancel(); boatTickJob = null
    }

    fun setShowPlanes(on: Boolean) {
        _state.update { it.copy(showPlanes = on, selection = if (!on && it.selection is Selection.Plane) null else it.selection) }
        if (on) startPlanes() else { planeJob?.cancel(); planeJob = null }
    }

    fun setShowBoats(on: Boolean) {
        _state.update { it.copy(showBoats = on, selection = if (!on && it.selection is Selection.Boat) null else it.selection) }
        if (on) startBoats() else {
            aisJob?.cancel(); aisJob = null
            boatTickJob?.cancel(); boatTickJob = null
            _state.update { it.copy(aisStatus = null) }
        }
    }

    fun setRadius(km: Int) {
        _state.update { it.copy(radiusKm = km) }
        if (_state.value.showPlanes) { planeJob?.cancel(); planeJob = null; startPlanes() }
    }

    fun select(sel: Selection?) {
        _state.update { it.copy(selection = sel) }
        if (sel is Selection.Plane) {
            _state.value.aircraft.find { it.hex == sel.hex }?.callsign?.let(::lookupRoute)
        }
    }

    fun setShowTrails(on: Boolean) = _state.update { it.copy(showTrails = on) }

    /** Hentar ruta for eit kallesignal, berre éin gong (mislukka oppslag blir prøvde på nytt). */
    private fun lookupRoute(callsign: String) {
        val prev = _state.value.routes[callsign]
        if (prev != null && prev != RouteLookup.Failed) return
        _state.update { it.copy(routes = it.routes + (callsign to RouteLookup.Loading)) }
        viewModelScope.launch {
            val result = try {
                withContext(Dispatchers.IO) { routeClient.fetch(callsign) }
                    ?.let { RouteLookup.Found(it) } ?: RouteLookup.Unknown
            } catch (e: Exception) {
                RouteLookup.Failed
            }
            _state.update { it.copy(routes = it.routes + (callsign to result)) }
        }
    }

    /** Oppdaterer spora med siste posisjonar. Kalla etter kvar oppdatering. */
    @Synchronized
    private fun updateTrails(): Map<String, List<TrailPoint>> {
        val st = _state.value
        val now = System.currentTimeMillis()
        val alive = HashSet<String>()
        for (a in st.aircraft) {
            val k = TrailBook.planeKey(a.hex); alive += k
            trailBook.add(k, a.lat, a.lon, now, minMeters = 150.0)
        }
        for (v in st.vessels) {
            val k = TrailBook.boatKey(v.mmsi); alive += k
            trailBook.add(k, v.lat, v.lon, now, minMeters = 25.0)
        }
        trailBook.prune(now, alive)
        return trailBook.snapshot()
    }

    private fun startPlanes() {
        if (planeJob?.isActive == true) return
        planeJob = viewModelScope.launch {
            while (isActive) {
                val r = _state.value.radiusKm
                try {
                    val res = withContext(Dispatchers.IO) { planes.fetch(Stord.LAT, Stord.LON, r) }
                    _state.update {
                        it.copy(
                            aircraft = res.aircraft, planeSource = res.source,
                            planeError = null, planeUpdated = System.currentTimeMillis(),
                        )
                    }
                    _state.update { it.copy(trails = updateTrails()) }
                } catch (e: Exception) {
                    _state.update { it.copy(planeError = e.message ?: "Nettverksfeil") }
                }
                delay(5000)
            }
        }
    }

    private fun startBoats() {
        if (aisJob?.isActive != true) {
            aisJob = viewModelScope.launch {
                ais.run(
                    onStatus = { s, err -> _state.update { it.copy(aisStatus = s, aisError = err) } },
                    // Tek med båtar litt utanfor radius, så dei ikkje blinkar inn og ut i kanten
                    onMessage = { store.add(it, Stord.LAT, Stord.LON, _state.value.radiusKm * 1.2) },
                )
            }
        }
        if (boatTickJob?.isActive != true) {
            // Oppdaterer kartet kvart 2. sekund i staden for for kvar melding
            boatTickJob = viewModelScope.launch {
                while (isActive) {
                    val list = withContext(Dispatchers.Default) { store.snapshot() }
                    _state.update { it.copy(vessels = list) }
                    _state.update { it.copy(trails = updateTrails()) }
                    delay(2000)
                }
            }
        }
    }

    override fun onCleared() = stop()
}
