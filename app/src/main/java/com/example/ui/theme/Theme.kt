package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = BleuInstitutionnel,
    onPrimary = BlancPur,
    primaryContainer = BleuClairSurface,
    onPrimaryContainer = BleuSurClair,
    secondary = TurquoiseAccent,
    onSecondary = BlancPur,
    secondaryContainer = TurquoiseClair,
    onSecondaryContainer = TurquoiseSurClair,
    tertiary = BleuFonce,
    onTertiary = BlancPur,
    background = FondPorcelaine,
    onBackground = TextePrincipal,
    surface = SurfaceCarte,
    onSurface = TextePrincipal,
    surfaceVariant = SurfaceVariante,
    onSurfaceVariant = TexteSecondaire,
    outline = BordureSubtile,
    error = ErreurRouge,
    onError = BlancPur,
    errorContainer = ErreurFond
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkBleuPrimaire,
    onPrimary = BleuFonce,
    primaryContainer = BleuInstitutionnel,
    onPrimaryContainer = BlancPur,
    secondary = DarkTurquoise,
    onSecondary = TurquoiseSurClair,
    secondaryContainer = TurquoiseAccent,
    onSecondaryContainer = BlancPur,
    background = DarkFond,
    onBackground = BlancPur,
    surface = DarkSurface,
    onSurface = BlancPur,
    surfaceVariant = DarkSurfaceVariante,
    onSurfaceVariant = BleuClairSurface,
    outline = DarkSurfaceVariante,
    error = ErreurFond,
    onError = ErreurRouge
)

val EcoleShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun EcoleConnecteeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = EcoleTypography,
        shapes = EcoleShapes,
        content = content
    )
}
