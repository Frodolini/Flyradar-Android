package no.frodolini.flyradar.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val FrodoGreen = Color(0xFF2E7D32)
private val FrodoGreenDark = Color(0xFF1B5E20)

private val LightColors = lightColorScheme(primary = FrodoGreen)
private val DarkColors = darkColorScheme(primary = Color(0xFF81C784))

/**
 * Fargetema. På Android 12 og nyare hentar appen fargane frå bakgrunnsbiletet
 * på telefonen ("Material You"). Lyst/mørkt tema følgjer innstillinga på telefonen.
 */
@Composable
fun FlyradarTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}

/**
 * Grøn tittellinje, same som i Froditor. Fargen er den same uansett
 * Material You-fargane frå telefonen, men litt mørkare i mørkt tema.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun greenTopBarColors(): TopAppBarColors {
    val green = if (isSystemInDarkTheme()) FrodoGreenDark else FrodoGreen
    return TopAppBarDefaults.topAppBarColors(
        containerColor = green,
        scrolledContainerColor = green,
        navigationIconContentColor = Color.White,
        titleContentColor = Color.White,
        actionIconContentColor = Color.White,
    )
}
