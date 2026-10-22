package co.ocaana.dharmis

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun ago(t: Long): String {
    val m = (System.currentTimeMillis() - t) / 60_000
    return if (m < 1) "just now" else if (m < 60) "$m min ago" else if (m < 1440) "${m / 60} h ago" else SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(t))
}

fun stamp(t: Long): String = SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault()).format(Date(t))

@Composable
fun Home(onReport: (Alert) -> Unit, onGo: (Alert) -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    var open by remember { mutableStateOf<String?>(null) }
    BackHandler(enabled = open != null) { open = null }
    if (open != null) { ReportDetail(open!!) { open = null }; return }
    Page {
        Box(Modifier.weight(1f)) {
            when (tab) {
                0 -> Alerts(onReport, onGo)
                1 -> Reports { open = it }
                else -> Me()
            }
        }
        Row(Modifier.fillMaxWidth().background(pal.card).padding(vertical = 6.dp)) {
            listOf(Triple("Alerts", Icons.Rounded.NotificationsActive, 0), Triple("Reports", Icons.Rounded.Description, 1), Triple("Me", Icons.Rounded.Person, 2)).forEach { (l, ic, i) ->
                NavItem(l, ic, tab == i, Modifier.weight(1f)) { tab = i }
            }
        }
    }
}

@Composable
private fun NavItem(label: String, icon: ImageVector, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.heightIn(min = 56.dp).clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(icon, null, tint = if (on) pal.pri else pal.ink3, modifier = Modifier.size(26.dp))
        T(label, 12, FontWeight.Bold, if (on) pal.pri else pal.ink3)
    }
}

@Composable
private fun Alerts(onReport: (Alert) -> Unit, onGo: (Alert) -> Unit) {
    val duty by Store.onDuty
    val p = Store.profile.value
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { T("Alerts", 26, FontWeight.ExtraBold); T(p?.station ?: "", 13, FontWeight.Medium, pal.ink3) }
            Pill(if (duty) "On duty" else "Off duty", if (duty) pal.grn2 else pal.card2, if (duty) pal.grn else pal.ink3)
        }
        DutyRow()
        if (!duty) {
            Card(Modifier.fillMaxWidth(), pad = 24.dp) {
                Icon(Icons.Rounded.NotificationsOff, null, tint = pal.ink3, modifier = Modifier.size(36.dp))
                Spacer(Modifier.height(10.dp))
                T("You are off duty", 20, FontWeight.ExtraBold)
                T("You get no alerts, and nothing is saved up to show you later. Switch on duty to receive alerts again.", 14, FontWeight.Medium, pal.ink2)
                Spacer(Modifier.height(14.dp))
                BigButton("Go on duty", Icons.Rounded.Check, modifier = Modifier.fillMaxWidth()) { Store.setDuty(true) }
            }
        } else {
            val a = remember { demoAlert() }
            Card(Modifier.fillMaxWidth(), bg = pal.red2, border = pal.red, pad = 18.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(pal.red).padding(horizontal = 8.dp, vertical = 2.dp)) { T(a.level, 11, FontWeight.ExtraBold, pal.onRed) }
                    Spacer(Modifier.width(8.dp)); T(ago(a.since), 13, FontWeight.Bold, pal.redText)
                }
                Spacer(Modifier.height(8.dp))
                T(a.node, 24, FontWeight.ExtraBold, pal.redText)
                T(a.place, 15, FontWeight.Medium, pal.ink2)
                Spacer(Modifier.height(6.dp))
                T("Example alert. Live alerts start once your office connects DharmIS to the control room.", 12, FontWeight.Bold, pal.ink3)
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BigButton("Go", Icons.Rounded.Navigation, 1, Modifier.weight(1f)) { onGo(a) }
                    BigButton("Report now", Icons.Rounded.Edit, 2, Modifier.weight(2f)) { onReport(a) }
                }
            }
            T("No other alerts", 14, FontWeight.Medium, pal.ink3)
        }
    }
}

@Composable
private fun Reports(onOpen: (String) -> Unit) {
    Store.version.value
    val list = Store.reports
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        T("My reports", 26, FontWeight.ExtraBold)
        if (list.isEmpty()) T("Nothing reported yet. Reports you make appear here and stay here.", 15, FontWeight.Medium, pal.ink3)
        list.forEach { r ->
            OptionRow(
                "${r.nodeId} · ${if (r.herd > 0) "${r.herd} elephants" else "No elephants"}",
                "${ago(r.createdAt)} · ${if (r.sent) "Sent" else "Waiting to send"}",
                if (r.sent) Icons.Rounded.CloudDone else Icons.Rounded.CloudOff, chevron = true,
            ) { onOpen(r.id) }
        }
    }
}

@Composable
private fun ReportDetail(id: String, onBack: () -> Unit) {
    Store.version.value
    val r = Store.reports.firstOrNull { it.id == id } ?: return onBack()
    var adding by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    Page {
        BackBar(r.nodeId, stamp(r.createdAt), onBack = onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(Modifier.fillMaxWidth(), pad = 18.dp) {
                listOfNotNull(
                    "Seen" to r.seen,
                    if (r.herd > 0) "Herd" to "${r.herd}: ${r.adults} adults, ${r.calves} calves, ${r.tuskers} tuskers" else null,
                    "Moving" to r.direction,
                    r.distanceM?.let { "Distance" to kmText(it.toFloat()) },
                    if (r.activity.isNotEmpty()) "Doing" to r.activity.joinToString(", ") else null,
                    if (r.photo != null) "Photo" to "Saved on this phone" else null,
                    if (r.note.isNotBlank()) "Note" to r.note else null,
                    "Status" to if (r.sent) "Sent to the control room" else "Waiting to send",
                ).forEach { (k, v) -> Row(Modifier.padding(vertical = 5.dp)) { T(k, 13, FontWeight.Bold, pal.ink3, Modifier.width(70.dp)); T(v, 15, FontWeight.Bold, pal.ink, Modifier.weight(1f)) } }
            }
            r.corrections.forEach { c ->
                Card(Modifier.fillMaxWidth(), bg = pal.amb2, border = null) {
                    T("Correction · ${stamp(c.at)}", 12, FontWeight.Bold, pal.amb)
                    T(c.text, 15, FontWeight.Bold)
                }
            }
            if (adding) {
                TextBox(text, "What needs correcting?") { text = it }
                BigButton("Save correction", Icons.Rounded.Check, modifier = Modifier.fillMaxWidth(), enabled = text.isNotBlank()) { Store.addCorrection(r.id, text.trim()); text = ""; adding = false }
            } else BigButton("Add a correction", Icons.Rounded.Edit, 1, Modifier.fillMaxWidth()) { adding = true }
            T("A saved report cannot be deleted or changed. Corrections are added under it with the date.", 12, FontWeight.Medium, pal.ink3)
        }
    }
}

@Composable
private fun DutyRow() {
    val duty by Store.onDuty
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(pal.card).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { T("On duty", 17, FontWeight.Bold); T(if (duty) "You receive alerts" else "No alerts at all", 13, FontWeight.Medium, pal.ink3) }
        Switch(duty, { Store.setDuty(it) }, colors = SwitchDefaults.colors(checkedTrackColor = pal.pri))
    }
}

@Composable
private fun Me() {
    val ctx = LocalContext.current
    val p = Store.profile.value ?: return
    val mode by Store.themeMode
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        T("Me", 26, FontWeight.ExtraBold)
        Card(Modifier.fillMaxWidth(), pad = 18.dp) {
            T(p.name, 20, FontWeight.ExtraBold)
            T(p.role, 14, FontWeight.Medium, pal.ink2)
            Spacer(Modifier.height(8.dp))
            T("${p.station} · ${p.range.name}", 14, FontWeight.Bold)
            T("${p.range.division} · ${p.range.circle}", 13, FontWeight.Medium, pal.ink3)
            T("+91 ${p.phone}", 13, FontWeight.Medium, pal.ink3)
        }
        if (p.range.phone.isNotBlank()) BigButton("Call the range office", Icons.Rounded.Call, 1, Modifier.fillMaxWidth()) {
            ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + p.range.phone)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        DutyRow()
        T("Appearance", 14, FontWeight.Bold, pal.ink2)
        Chips(listOf("Auto", "Light", "Dark"), setOf(listOf("Auto", "Light", "Dark")[mode])) { Store.setTheme(listOf("Auto", "Light", "Dark").indexOf(it)) }
        T("Auto turns dark at sunset.", 12, FontWeight.Medium, pal.ink3)
        T("DharmIS 0.1 · OCAANA", 12, FontWeight.Medium, pal.ink3)
    }
}
