package co.ocaana.dharmis

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Four dots that fill as the PIN is typed. */
@Composable
fun PinDots(pin: String, wrong: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        for (i in 0 until 4) {
            val filled = i < pin.length
            Box(
                Modifier.padding(horizontal = 10.dp).size(22.dp).clip(CircleShape)
                    .background(if (filled) (if (wrong) pal.red else pal.pri) else pal.card)
                    .border(2.dp, if (wrong) pal.red else if (filled) pal.pri else pal.line, CircleShape),
            )
        }
    }
}

/** Big number keys, easy to hit with gloves or one thumb. */
@Composable
fun NumPad(onDigit: (Char) -> Unit, onBack: () -> Unit) {
    val rows = listOf("123", "456", "789")
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEach { r ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { r.forEach { d -> Key(d.toString(), Modifier.weight(1f)) { onDigit(d) } } }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Spacer(Modifier.weight(1f))
            Key("0", Modifier.weight(1f)) { onDigit('0') }
            Box(Modifier.weight(1f).height(64.dp).clip(RoundedCornerShape(18.dp)).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Rounded.Backspace, "Delete", tint = pal.ink2, modifier = Modifier.size(26.dp))
            }
        }
    }
}

@Composable
private fun Key(label: String, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.height(64.dp).clip(RoundedCornerShape(18.dp)).background(pal.card).border(1.dp, pal.line, RoundedCornerShape(18.dp)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        T(label, 26, FontWeight.ExtraBold)
    }
}
