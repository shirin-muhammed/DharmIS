package co.ocaana.dharmis

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private val SENT_GREEN = Color(0xFF1E5B44)

/** Shown after Save and send: the report is stored; it is either already with the control room or waiting for signal. */
@Composable
fun Sent(id: String, onDone: () -> Unit) {
    BackHandler(onBack = onDone)
    Store.version.value
    val sent = Store.reports.firstOrNull { it.id == id }?.sent == true
    Column(Modifier.fillMaxSize().background(SENT_GREEN).statusBarsPadding().navigationBarsPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(Modifier.size(76.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Check, null, tint = SENT_GREEN, modifier = Modifier.size(48.dp)) }
            Spacer(Modifier.height(20.dp))
            T("Report sent", 36, FontWeight.ExtraBold, Color.White, align = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            T(
                if (sent) "Thank you. The control room can see it, and it shows under \"Report elephants\" on OCAANA."
                else "Thank you. It is saved on this phone and goes to the control room as soon as there is signal.",
                17, FontWeight.Medium, Color.White.copy(alpha = .92f), Modifier.widthIn(max = 300.dp), TextAlign.Center,
            )
        }
        Box(Modifier.fillMaxWidth().height(60.dp).clip(RoundedCornerShape(30.dp)).background(Color.White).clickable(onClick = onDone), contentAlignment = Alignment.Center) {
            T("Back to alerts", 17, FontWeight.ExtraBold, SENT_GREEN)
        }
    }
}
