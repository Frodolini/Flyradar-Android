package no.frodolini.flyradar.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

const val REPO_URL = "https://github.com/Frodolini/Flyradar-Android"
const val GPL_URL = "https://www.gnu.org/licenses/gpl-3.0.html"

/** Versjonsnamnet frå byggjeoppsettet (app/build.gradle.kts), så det aldri blir feil. */
@Composable
fun appVersion(): String {
    val ctx = LocalContext.current
    return remember {
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "?"
    }
}

/** «Om Flyradar»: versjon, kven som har laga appen, lisens, datakjelder og bibliotek. */
@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    val uri = LocalUriHandler.current
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
        dismissButton = { TextButton(onClick = { uri.openUri(REPO_URL) }) { Text("Kjeldekode") } },
        title = { Text("Om Flyradar") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Versjon ${appVersion()}", fontWeight = FontWeight.Bold)
                Text("Laga av Frode Fjellanger")
                Text("github.com/Frodolini/Flyradar-Android", style = MaterialTheme.typography.bodySmall)

                Heading("Lisens")
                Body(
                    "Flyradar er fri programvare under GNU General Public License, versjon 3 (GPL-3.0). " +
                        "Du kan bruke, dele og endre han, men det du deler vidare, må òg vere GPL-3.0 med open kjeldekode. " +
                        "Appen kjem utan nokon form for garanti."
                )
                TextButton(onClick = { uri.openUri(GPL_URL) }) { Text("Les lisensen") }

                Heading("Datakjelder")
                Body(
                    "• Kart: © OpenStreetMap-bidragsytarar, ODbL\n" +
                        "• Fly: adsb.lol (ODbL) og adsb.fi\n" +
                        "• Flyruter: adsbdb.com\n" +
                        "• Båtar: Kystverket, open AIS-straum, Norsk lisens for offentlege data (NLOD)"
                )
                Body("Data kan vere forseinka, mangelfulle eller feil. Bruk ikkje appen til navigasjon.")

                Heading("Bibliotek")
                Body(
                    "• osmdroid, Apache License 2.0\n" +
                        "• Jetpack Compose og AndroidX, Apache License 2.0\n" +
                        "• Kotlin, Apache License 2.0"
                )
            }
        },
    )
}

@Composable
private fun Heading(text: String) {
    Spacer(Modifier.height(14.dp))
    Text(text, fontWeight = FontWeight.Bold)
}

@Composable
private fun Body(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall)
}
