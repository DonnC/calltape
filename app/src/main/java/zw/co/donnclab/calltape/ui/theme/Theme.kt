package zw.co.donnclab.calltape.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = POSAccent,
    secondary = POSLightBlue,
    tertiary = POSSilver,
    background = DarkGray,
    surface = SurfaceGray,
    onPrimary = Color.White,
    onSecondary = POSNavy,
    onBackground = POSSilver,
    onSurface = POSSilver,
    outline = BorderGray
)

private val LightColorScheme = lightColorScheme(
    primary = POSAccent,
    secondary = POSBlue,
    tertiary = POSNavy,
    background = POSSilver,
    surface = Color.White,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = POSNavy,
    onSurface = POSNavy,
    outline = POSLightBlue
)

@Composable
fun CallTapeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
