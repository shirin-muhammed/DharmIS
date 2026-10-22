package co.ocaana.dharmis

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

class Pal(
    val dark: Boolean,
    val bg: Color, val card: Color, val card2: Color, val line: Color,
    val ink: Color, val ink2: Color, val ink3: Color,
    val pri: Color, val onPri: Color, val pri2: Color,
    val red: Color, val onRed: Color, val red2: Color, val redText: Color,
    val amb: Color, val amb2: Color, val grn: Color, val grn2: Color,
)

val LightPal = Pal(
    false, Color(0xFFF5F1E8), Color(0xFFFFFFFF), Color(0xFFECE6D8), Color(0xFFDDD5C3),
    Color(0xFF17261E), Color(0xFF4A5A50), Color(0xFF6C7A72),
    Color(0xFF1D5A43), Color(0xFFFFFFFF), Color(0xFFDCEBE3),
    Color(0xFFC4301F), Color(0xFFFFFFFF), Color(0xFFFBE3DD), Color(0xFFC4301F),
    Color(0xFFA86500), Color(0xFFFBECC8), Color(0xFF1D7A3E), Color(0xFFDCF1E2),
)

val DarkPal = Pal(
    true, Color(0xFF0F1713), Color(0xFF18231E), Color(0xFF223029), Color(0xFF2F3D36),
    Color(0xFFE8EFE9), Color(0xFFB3C0B8), Color(0xFF8D9C93),
    Color(0xFF4CC2A8), Color(0xFF0F1713), Color(0xFF1B3A30),
    Color(0xFFD9402D), Color(0xFFFFFFFF), Color(0xFF401A17), Color(0xFFFF8A7C),
    Color(0xFFF0B54A), Color(0xFF3A2C10), Color(0xFF5FD08A), Color(0xFF163423),
)

val LocalPal = staticCompositionLocalOf { LightPal }

@Composable
fun DharmisTheme(dark: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalPal provides if (dark) DarkPal else LightPal, content = content)
}

@Composable
fun systemDark() = isSystemInDarkTheme()
