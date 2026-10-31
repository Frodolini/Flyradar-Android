package no.frodolini.flyradar

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import no.frodolini.flyradar.ui.RadarScreen
import no.frodolini.flyradar.ui.RadarViewModel
import no.frodolini.flyradar.ui.theme.FlyradarTheme
import org.osmdroid.config.Configuration

/** Inngangen til appen. */
class MainActivity : ComponentActivity() {

    private val vm: RadarViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // OpenStreetMap krev at appen seier kven ho er når kartbitar blir lasta ned
        Configuration.getInstance().apply {
            userAgentValue = packageName
            osmdroidBasePath = cacheDir
            osmdroidTileCache = cacheDir.resolve("tiles")
        }
        enableEdgeToEdge()
        setContent {
            FlyradarTheme {
                val state by vm.state.collectAsStateWithLifecycle()
                RadarScreen(
                    state = state,
                    onShowPlanes = vm::setShowPlanes,
                    onShowBoats = vm::setShowBoats,
                    onRadius = vm::setRadius,
                    onSelect = vm::select,
                    onShowTrails = vm::setShowTrails,
                    onShowMoored = vm::setShowMoored,
                )
            }
        }
    }

    // Hentar data berre når appen er synleg
    override fun onStart() { super.onStart(); vm.start() }
    override fun onStop() { vm.stop(); super.onStop() }
}
