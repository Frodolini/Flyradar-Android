package no.frodolini.flyradar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import no.frodolini.flyradar.ais.AisStream
import no.frodolini.flyradar.data.Aircraft
import no.frodolini.flyradar.data.Stord
import no.frodolini.flyradar.data.Vessel
import no.frodolini.flyradar.data.VesselCategory
import no.frodolini.flyradar.data.distanceKm
import no.frodolini.flyradar.data.etaInstant
import no.frodolini.flyradar.data.navStatusText
import no.frodolini.flyradar.ui.theme.greenTopBarColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Båtar innanfor valt radius (straumen kan gje nokre litt utanfor). */
fun RadarState.visibleVessels(): List<Vessel> =
    vessels.filter { distanceKm(Stord.LAT, Stord.LON, it.lat, it.lon) <= radiusKm }

val RADIUS_VAL = listOf(25, 50, 100, 200)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadarScreen(
    state: RadarState,
    onShowPlanes: (Boolean) -> Unit,
    onShowBoats: (Boolean) -> Unit,
    onRadius: (Int) -> Unit,
    onSelect: (Selection?) -> Unit,
    onShowTrails: (Boolean) -> Unit = {},
    // Kartet kan bytast ut i testar, der osmdroid ikkje kan køyre
    map: @Composable (Modifier) -> Unit = { m -> RadarMap(state, onSelect, m) },
) {
    var showLegend by remember { mutableStateOf(false) }
    val boats = state.visibleVessels()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Flyradar") },
                colors = greenTopBarColors(),
                actions = {
                    IconButton(onClick = { showLegend = true }) {
                        Icon(Icons.Filled.Info, contentDescription = "Forklaring")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Controls(state, boats.size, onShowPlanes, onShowBoats, onRadius, onShowTrails)
            StatusLine(state)
            Box(Modifier.fillMaxSize()) {
                map(Modifier.fillMaxSize())
                val sel = state.selection
                val card: (@Composable () -> Unit)? = when (sel) {
                    is Selection.Plane -> state.aircraft.find { it.hex == sel.hex }?.let { a -> { PlaneCard(a, a.callsign?.let { state.routes[it] }) } }
                    is Selection.Boat -> state.vessels.find { it.mmsi == sel.mmsi }?.let { v -> { BoatCard(v) } }
                    null -> null
                }
                if (card != null) {
                    InfoCard(onClose = { onSelect(null) }, content = card,
                        modifier = Modifier.align(Alignment.BottomCenter))
                }
                Text(
                    "© OpenStreetMap-bidragsytarar",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF333333),
                    modifier = Modifier.align(Alignment.TopEnd)
                        .background(Color(0xCCFFFFFF))
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                )
            }
        }
    }

    if (showLegend) LegendDialog(onDismiss = { showLegend = false })
}

@Composable
private fun Controls(
    state: RadarState, boatCount: Int,
    onShowPlanes: (Boolean) -> Unit, onShowBoats: (Boolean) -> Unit, onRadius: (Int) -> Unit,
    onShowTrails: (Boolean) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(
            selected = state.showPlanes,
            onClick = { onShowPlanes(!state.showPlanes) },
            label = { Text(if (state.showPlanes) "Fly (${state.aircraft.size})" else "Fly") },
            leadingIcon = if (state.showPlanes) {
                { Icon(Icons.Filled.Check, null, Modifier.size(FilterChipDefaults.IconSize)) }
            } else null,
        )
        FilterChip(
            selected = state.showBoats,
            onClick = { onShowBoats(!state.showBoats) },
            label = { Text(if (state.showBoats) "Båtar ($boatCount)" else "Båtar") },
            leadingIcon = if (state.showBoats) {
                { Icon(Icons.Filled.Check, null, Modifier.size(FilterChipDefaults.IconSize)) }
            } else null,
        )
        Spacer(Modifier.weight(1f))
        Box {
            TextButton(onClick = { menu = true }) { Text("${state.radiusKm} km ▾") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                RADIUS_VAL.forEach { km ->
                    DropdownMenuItem(
                        text = { Text("Radius $km km") },
                        onClick = { menu = false; onRadius(km) },
                        trailingIcon = if (km == state.radiusKm) { { Icon(Icons.Filled.Check, null) } } else null,
                    )
                }
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("Vis spor bak alle") },
                    onClick = { menu = false; onShowTrails(!state.showTrails) },
                    trailingIcon = if (state.showTrails) { { Icon(Icons.Filled.Check, null) } } else null,
                )
            }
        }
    }
}

@Composable
private fun StatusLine(state: RadarState) {
    val parts = buildList {
        if (state.showPlanes) {
            add(
                when {
                    state.planeError != null -> "Fly: feil (${state.planeError})"
                    state.planeUpdated != null -> "Fly ${klokke(state.planeUpdated)} · ${state.planeSource}"
                    else -> "Fly: hentar …"
                }
            )
        }
        if (state.showBoats) {
            add(
                when (state.aisStatus) {
                    AisStream.Status.CONNECTED -> "AIS: tilkopla"
                    AisStream.Status.ERROR -> "AIS: feil (${state.aisError}), prøver igjen"
                    else -> "AIS: koplar til …"
                }
            )
        }
    }
    Text(
        parts.joinToString("   "),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun InfoCard(onClose: () -> Unit, content: @Composable () -> Unit, modifier: Modifier) {
    Card(
        modifier = modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
    ) {
        Box {
            Column(Modifier.padding(16.dp)) { content() }
            IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd)) {
                Icon(Icons.Filled.Close, contentDescription = "Lukk")
            }
        }
    }
}

@Composable
private fun PlaneCard(a: Aircraft, route: RouteLookup?) {
    Title(a.label, Color(Palette.altitude(a)))
    Sub(listOfNotNull(a.type, a.description).joinToString(" · ").ifEmpty { "Ukjend flytype" })
    Spacer(Modifier.height(6.dp))
    when (route) {
        is RouteLookup.Found -> {
            Text(
                "${route.route.origin.label} → ${route.route.destination.label}",
                style = MaterialTheme.typography.titleMedium,
            )
            route.route.airline?.let { Sub(it) }
        }
        RouteLookup.Loading -> Sub("Hentar rute …")
        RouteLookup.Failed -> Sub("Fekk ikkje henta ruta")
        RouteLookup.Unknown -> Sub("Ukjend rute")
        null -> Sub(if (a.callsign == null) "Ingen rute (manglar kallesignal)" else "Ukjend rute")
    }
    Spacer(Modifier.height(8.dp))
    Field("Høgd", if (a.onGround) "På bakken" else a.altitudeFt?.let { "${fmtInt(it)} ft (${fmtInt((it * 0.3048).roundToInt())} m)" })
    Field("Stig/søkk", a.verticalRate?.takeIf { abs(it) >= 64 }?.let { "${if (it > 0) "↑" else "↓"} ${fmtInt(abs(it))} ft/min" } ?: "Flatt")
    Field("Fart", a.speedKnots?.let { "${it.roundToInt()} kt (${(it * 1.852).roundToInt()} km/t)" })
    Field("Kurs", a.track?.let { "${it.roundToInt()}°" })
    Field("Registrering", a.registration)
    Field("ICAO", a.hex.uppercase())
    Field("Squawk", a.squawk)
    Field("Avstand", "${distanceKm(Stord.LAT, Stord.LON, a.lat, a.lon).roundToInt()} km frå Stord")
    (route as? RouteLookup.Found)?.route?.destination?.let { d ->
        Field("Att til mål", "${fmtInt(distanceKm(a.lat, a.lon, d.lat, d.lon).roundToInt())} km")
    }
}

@Composable
private fun BoatCard(v: Vessel) {
    Title(v.label, Color(Palette.vessel(v.category)))
    Sub(v.category.label + if (v.classB) " · klasse B" else "")
    Spacer(Modifier.height(8.dp))
    Field("Status", navStatusText(v.navStatus))
    Field("Fart", v.speedKnots?.let { "${"%.1f".format(Locale.ROOT, it)} knop (${(it * 1.852).roundToInt()} km/t)" })
    Field("Kurs", v.course?.let { "${it.roundToInt()}°" })
    Field("Destinasjon", v.destination)
    Field("Forventa fram", v.eta?.let { etaInstant(it, java.time.Instant.now()) }?.let(::fmtEta))
    Field("Lengd", v.lengthM?.let { "$it m" })
    Field("Djupgang", v.draughtM?.let { "${"%.1f".format(Locale.ROOT, it)} m" })
    Field("Kallesignal", v.callsign)
    Field("MMSI", v.mmsi.toString())
    Field("Avstand", "${distanceKm(Stord.LAT, Stord.LON, v.lat, v.lon).roundToInt()} km frå Stord")
    Field("Sist høyrd", klokke(v.updatedAt))
}

@Composable
private fun Title(text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 40.dp)) {
        Box(Modifier.size(12.dp).background(color, CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Sub(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Field(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Row(Modifier.padding(vertical = 1.dp)) {
        Text(label, Modifier.width(110.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun LegendDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
        title = { Text("Forklaring") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Fly (farge etter høgd)", fontWeight = FontWeight.Bold)
                Palette.altitudeLegend.forEach { (t, c) -> LegendRow(t, Color(c)) }
                Spacer(Modifier.height(12.dp))
                Text("Båtar (farge etter type)", fontWeight = FontWeight.Bold)
                VesselCategory.entries.forEach { LegendRow(it.label, Color(Palette.vessel(it))) }
                Spacer(Modifier.height(8.dp))
                Text("Båtar som ligg i ro, blir viste som prikkar.", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(12.dp))
                Text("Spor og ruter", fontWeight = FontWeight.Bold)
                Text(
                    "Streken bak eit fly eller ein båt viser kvar det har vore sidan appen vart opna (opp til 20 minutt). " +
                        "Trykk på eit rutefly for å sjå heile ruta: heil strek frå avgangsflyplassen, stipla strek til målet. " +
                        "Ruta blir slått opp på kallesignalet, så ekstraflygingar kan få feil rute.\n\n" +
                        "For båtar er destinasjon og forventa ankomst lagt inn av mannskapet, og kan vere forkorta eller gamle.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Flydata: adsb.lol / adsb.fi (ADS-B). Ruter: adsbdb.com.\nBåtdata: Kystverket, open AIS-straum (NLOD).\nKart: © OpenStreetMap-bidragsytarar.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
    )
}

@Composable
private fun LegendRow(text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Surface(Modifier.size(12.dp), shape = CircleShape, color = color) {}
        Spacer(Modifier.width(8.dp))
        Text(text)
    }
}

private val etaFormat = java.time.format.DateTimeFormatter
    .ofPattern("EEE d. MMM 'kl.' HH:mm", Locale.forLanguageTag("nn-NO"))
    .withZone(java.time.ZoneId.systemDefault())

private fun fmtEta(t: java.time.Instant): String = etaFormat.format(t)

private fun klokke(ms: Long): String = SimpleDateFormat("HH:mm:ss", Locale.ROOT).format(Date(ms))
private fun fmtInt(n: Int): String = "%,d".format(Locale.ROOT, n).replace(',', ' ')
