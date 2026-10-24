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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
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
    val officer = Store.rank.value in setOf("section", "dro", "range")
    val rrtOfficer = officer && Store.inRrt.value
    val limited = !officer && !Store.receives.value   // a watcher or beat officer posted at a station or section: not in the RRT unit
    val dash = rememberDash(officer)
    BackHandler(enabled = open != null) { open = null }
    if (open != null) { ReportDetail(open!!) { open = null }; return }
    if (Store.serverStatus.value == "pending") { WaitingScreen(); return }
    Page(header = false) {
        HomeBar(officer, dash)
        Box(Modifier.weight(1f)) {
            when {
                limited -> Me()
                officer -> when (dash.tab) {
                    "Dashboard" -> DashboardTab(dash, rrtOfficer)
                    "Nodes" -> NodesTab(dash)
                    "Reports" -> ReportsTab(dash)
                    "On duty" -> DutyTab(dash)
                    "Alerts" -> Alerts(onReport, onGo)
                    else -> Me()
                }
                tab == 0 -> Alerts(onReport, onGo)
                tab == 1 -> Reports { open = it }
                else -> Me()
            }
        }
        if (!limited) Row(Modifier.fillMaxWidth().background(pal.card).padding(vertical = 6.dp)) {
            if (officer) officerTabs(rrtOfficer).forEach { l ->
                NavItem(l, when (l) { "Dashboard" -> Icons.Rounded.Dashboard; "Nodes" -> Icons.Rounded.Sensors; "Reports" -> Icons.Rounded.Description; "On duty" -> Icons.Rounded.Groups; "Alerts" -> Icons.Rounded.NotificationsActive; else -> Icons.Rounded.Person }, dash.tab == l, Modifier.weight(1f)) { dash.tab = l }
            }
            else listOf(Triple("Alerts", Icons.Rounded.NotificationsActive, 0), Triple("Reports", Icons.Rounded.Description, 1), Triple("Me", Icons.Rounded.Person, 2)).forEach { (l, ic, i) ->
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

class LevelColors(val main: Color, val bg: Color, val text: Color, val on: Color)

/** Same colours as the advisory on the OCAANA board: safe green, low yellow, medium orange, high red. */
@Composable
fun levelColors(level: String): LevelColors = when (level) {
    "LOW" -> if (pal.dark) LevelColors(Color(0xFFD9A73A), Color(0xFF3A2C10), Color(0xFFD9A73A), Color(0xFF0F1713)) else LevelColors(Color(0xFFA86500), Color(0xFFFBECC8), Color(0xFF7A4A00), Color.White)
    "MEDIUM" -> if (pal.dark) LevelColors(Color(0xFFF08A3C), Color(0xFF3D2412), Color(0xFFF08A3C), Color(0xFF0F1713)) else LevelColors(Color(0xFFC2570F), Color(0xFFFCE5D2), Color(0xFF9A430B), Color.White)
    "SAFE" -> LevelColors(pal.grn, pal.grn2, pal.grn, if (pal.dark) Color(0xFF09241B) else Color.White)
    else -> LevelColors(pal.red, pal.red2, pal.redText, pal.onRed)
}

/** Tells the member when Android would stop alerts reaching a locked phone, with a button to fix each cause. */
@Composable
private fun AlertHealth() {
    val ctx = LocalContext.current
    Store.resumeTick.value
    val notifOk = androidx.core.app.NotificationManagerCompat.from(ctx).areNotificationsEnabled()
    val batteryOk = (ctx.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager).isIgnoringBatteryOptimizations(ctx.packageName)
    if (notifOk && batteryOk) return
    Card(Modifier.fillMaxWidth(), bg = pal.amb2, border = pal.amb, pad = 16.dp) {
        T("Alerts may not reach you", 17, FontWeight.ExtraBold, pal.amb)
        Spacer(Modifier.height(4.dp))
        if (!notifOk) {
            T("Notifications are switched off for DharmIS, so there will be no siren.", 14, FontWeight.Medium, pal.ink2)
            Spacer(Modifier.height(8.dp))
            BigButton("Allow notifications", Icons.Rounded.NotificationsActive, modifier = Modifier.fillMaxWidth()) {
                ctx.startActivity(Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, ctx.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            Spacer(Modifier.height(10.dp))
        }
        if (!batteryOk) {
            T("Android puts DharmIS to sleep when the phone is locked for a while. Allow it to run in the background.", 14, FontWeight.Medium, pal.ink2)
            Spacer(Modifier.height(8.dp))
            BigButton("Allow in background", Icons.Rounded.Check, modifier = Modifier.fillMaxWidth()) {
                try { ctx.startActivity(Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + ctx.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                catch (_: Exception) { ctx.startActivity(Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
        }
    }
}

@Composable
private fun Alerts(onReport: (Alert) -> Unit, onGo: (Alert) -> Unit) {
    val duty by Store.onDuty
    val p = Store.profile.value
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (!duty) {
            Card(Modifier.fillMaxWidth(), pad = 24.dp) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(80.dp).clip(androidx.compose.foundation.shape.CircleShape).background(pal.card2), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.NotificationsOff, null, tint = pal.ink2, modifier = Modifier.size(38.dp)) }
                    T("You are off duty", 26, FontWeight.ExtraBold, align = androidx.compose.ui.text.style.TextAlign.Center)
                    T("No alerts, sounds or notifications will reach this phone. Your control room can see that you are off duty.", 15, FontWeight.Medium, pal.ink2, align = androidx.compose.ui.text.style.TextAlign.Center, lh = 22)
                    BigButton("Go on duty", Icons.Rounded.PowerSettingsNew, modifier = Modifier.fillMaxWidth()) { Store.setDuty(true) }
                }
            }
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(pal.amb2).padding(16.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Rounded.NotificationsActive, null, tint = pal.amb, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                T("Alerts raised while you are off duty are not sent to you later. Go on duty before your shift.", 14, FontWeight.Medium, pal.amb, lh = 20)
            }
        } else {
            AlertHealth()
            val status by Store.serverStatus
            val online by Store.online
            val alerts = Store.alerts
            when {
                status == "pending" || status == "" && Store.token.isBlank() && online -> Card(Modifier.fillMaxWidth(), pad = 24.dp) {
                    Icon(Icons.Rounded.HourglassTop, null, tint = pal.ink3, modifier = Modifier.size(36.dp))
                    Spacer(Modifier.height(10.dp))
                    T("Waiting for approval", 20, FontWeight.ExtraBold)
                    T("Your control room must approve you before alerts start. Ask them to check the RRT members list on the OCAANA control site.", 14, FontWeight.Medium, pal.ink2)
                }
                status == "blocked" -> Card(Modifier.fillMaxWidth(), pad = 24.dp) {
                    T("Your access is switched off", 20, FontWeight.ExtraBold)
                    T("Please call your range office.", 14, FontWeight.Medium, pal.ink2)
                }
                alerts.isEmpty() -> Card(Modifier.fillMaxWidth(), pad = 24.dp) {
                    Icon(if (online) Icons.Rounded.CheckCircle else Icons.Rounded.CloudOff, null, tint = pal.ink3, modifier = Modifier.size(36.dp))
                    Spacer(Modifier.height(10.dp))
                    T(if (online) "No alerts" else "Cannot reach the control room", 20, FontWeight.ExtraBold)
                    T(if (online) "All your nodes are quiet. You are alerted when a node reaches High." else "Check your signal. This page checks again every few seconds.", 14, FontWeight.Medium, pal.ink2)
                    val ok = Store.lastOk.value
                    if (ok > 0) T("Last checked " + SimpleDateFormat("h:mm:ss a", Locale.getDefault()).format(Date(ok)), 12, FontWeight.Bold, pal.ink3)
                }
                else -> alerts.forEach { a ->
                    val lc = levelColors(a.level)
                    Card(Modifier.fillMaxWidth(), bg = lc.main, border = null, pad = 20.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.clip(RoundedCornerShape(8.dp)).background(lc.on).padding(horizontal = 10.dp, vertical = 4.dp)) { T(a.level + " ALERT", 12, FontWeight.ExtraBold, lc.main) }
                            Spacer(Modifier.width(10.dp)); T("since " + SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(a.since)), 13, FontWeight.SemiBold, lc.on)
                        }
                        Spacer(Modifier.height(10.dp))
                        T(a.node, 34, FontWeight.ExtraBold, lc.on)
                        if (a.place.isNotBlank()) T(a.place, 15, FontWeight.Medium, lc.on, lh = 22)
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (a.hasPos) Row(Modifier.height(56.dp).clip(RoundedCornerShape(28.dp)).background(lc.on).clickable { onGo(a) }.padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Navigation, null, tint = lc.main, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(6.dp)); T("Go", 17, FontWeight.Bold, lc.main)
                            }
                            Row(Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(28.dp)).background(Color(0xFF11201A)).clickable { onReport(a) }, horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Edit, null, tint = Color.White, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(6.dp)); T("Report now", 17, FontWeight.Bold, Color.White)
                            }
                        }
                    }
                }
            }
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
                    if (r.others.isNotBlank()) "Others" to r.others else null,
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
    Card(Modifier.fillMaxWidth(), pad = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { T("On duty", 17, FontWeight.Bold); T("Switch off to stop all alerts", 13, FontWeight.Medium, pal.ink3) }
            Switch(duty, { Store.setDuty(it) }, colors = SwitchDefaults.colors(checkedTrackColor = pal.grn, checkedThumbColor = Color.White))
        }
    }
}

@Composable
private fun Me() {
    val ctx = LocalContext.current
    val p = Store.profile.value ?: return
    val mode by Store.themeMode
    var confirm by remember { mutableStateOf(false) }
    val sun = remember { sunTimes(10.5, 76.5, System.currentTimeMillis()) }
    fun clock(t: Long) = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(t)).lowercase()
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            T("Me", 26, FontWeight.ExtraBold)
            Card(Modifier.fillMaxWidth(), pad = 16.dp) {
                T(p.name, 18, FontWeight.ExtraBold)
                T("+91 ${p.phone}", 14, FontWeight.Medium)
                T("${p.station} · ${p.range.name}", 13, FontWeight.Medium, pal.ink3)
                T("${p.range.division} · ${p.range.circle}", 13, FontWeight.Medium, pal.ink3)
            }
            if (!Store.receives.value && Store.rank.value !in setOf("section", "dro", "range")) Card(Modifier.fillMaxWidth(), pad = 18.dp) {
                T("Alerts are not for your post", 17, FontWeight.ExtraBold)
                T("The alert screen is for the RRT unit and for station and section office phones. Your control room can add you to the RRT unit.", 14, FontWeight.Medium, pal.ink2)
            }
            if (Store.alertUser && Store.receives.value) DutyRow()
            Card(Modifier.fillMaxWidth(), pad = 16.dp) {
                T("Appearance", 13, FontWeight.SemiBold, pal.ink3)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(pal.card2).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("Auto" to Icons.Rounded.BrightnessAuto, "Light" to Icons.Rounded.LightMode, "Dark" to Icons.Rounded.DarkMode).forEachIndexed { i, (l, ic) ->
                        Row(Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(12.dp)).background(if (mode == i) pal.pri else Color.Transparent).clickable { Store.setTheme(i) }, horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            Icon(ic, null, tint = if (mode == i) pal.onPri else pal.ink2, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); T(l, 14, FontWeight.Bold, if (mode == i) pal.onPri else pal.ink2)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                T(if (sun != null) "Auto turns dark at sunset (${clock(sun.second)} today) and light at sunrise (${clock(sun.first)}) for your station. Worked out on the phone, so it needs no signal." else "Auto turns dark at sunset and light at sunrise.", 13, FontWeight.Medium, pal.ink2, lh = 19)
            }
            if (p.range.phone.isNotBlank()) Card(Modifier.fillMaxWidth().clickable { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + p.range.phone)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }, pad = 16.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Call, null, tint = pal.pri, modifier = Modifier.size(24.dp)); Spacer(Modifier.width(12.dp))
                    Column { T("Call your range office", 17, FontWeight.Bold); T("${p.range.name} · ${p.range.phone}", 13, FontWeight.Medium, pal.ink3) }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        BigButton("Sign out", Icons.Rounded.Logout, 1, Modifier.fillMaxWidth()) { confirm = true }
        if (confirm) {
            val unsent = Store.reports.count { !it.sent }
            AlertDialog(
                onDismissRequest = { confirm = false },
                title = { Text("Sign out of DharmIS?") },
                text = { Text("You will get no alerts on this phone until you log in again with your mobile number and PIN." + if (unsent > 0) " $unsent report${if (unsent > 1) "s" else ""} not yet sent stay on this phone and send after you log back in." else " Your saved reports stay on this phone.") },
                confirmButton = { TextButton(onClick = { confirm = false; Store.logout(); AlertService.sync(ctx.applicationContext) }) { Text("Sign out") } },
                dismissButton = { TextButton(onClick = { confirm = false }) { Text("Stay logged in") } },
            )
        }
    }
}

/** Figma app bar: logo avatar, name and place, and the tap-to-toggle duty pill for anyone who gets alerts. */
@Composable
private fun HomeBar(officer: Boolean, dash: DashState) {
    val ctx = LocalContext.current
    val logo = remember { android.graphics.BitmapFactory.decodeStream(ctx.assets.open("logo.png"), null, android.graphics.BitmapFactory.Options().apply { inSampleSize = 4 })!!.asImageBitmap() }
    val p = Store.profile.value
    val duty by Store.onDuty
    val open = if (officer && dash.tab == "Dashboard") dash.openOffice() else null
    val title = if (open != null) open.first else if (officer) p?.name ?: "" else p?.let { it.station.ifBlank { it.range.name } } ?: ""
    val sub = if (open != null) open.second else if (officer) listOf(p?.role ?: "", (p?.station ?: "").ifBlank { p?.range?.name ?: "" }).filter { it.isNotBlank() }.joinToString(" · ") else p?.range?.name ?: ""
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (open != null) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = pal.ink, modifier = Modifier.size(28.dp).clickable { dash.stack = dash.stack.dropLast(1) }); Spacer(Modifier.width(8.dp)) }
        Box(Modifier.size(40.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Color(0xFF11201A)), contentAlignment = Alignment.Center) {
            androidx.compose.foundation.Image(logo, "OCAANA", Modifier.size(32.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) { T(title, 18, FontWeight.ExtraBold); T(sub, 12, FontWeight.Medium, pal.ink3) }
        if (Store.alertUser && Store.receives.value) Row(
            Modifier.clip(RoundedCornerShape(18.dp)).background(if (duty) pal.grn2 else pal.card2).clickable { Store.setDuty(!duty) }.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(8.dp).clip(androidx.compose.foundation.shape.CircleShape).background(if (duty) pal.grn else pal.ink3))
            Spacer(Modifier.width(6.dp))
            T(if (duty) "On duty" else "Off duty", 13, FontWeight.Bold, if (duty) pal.grn else pal.ink3)
        }
    }
}

/** Shown until the control room approves this phone. */
@Composable
private fun WaitingScreen() {
    val ctx = LocalContext.current
    val p = Store.profile.value
    Page(header = false) {
        Column(Modifier.weight(1f).padding(horizontal = 28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically)) {
            Box(Modifier.size(100.dp).clip(androidx.compose.foundation.shape.CircleShape).background(pal.card2), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Schedule, null, tint = pal.ink2, modifier = Modifier.size(48.dp)) }
            T("Waiting for approval", 26, FontWeight.ExtraBold, align = androidx.compose.ui.text.style.TextAlign.Center)
            T("Waiting for your control room to approve you. Alerts start once you are approved.", 15, FontWeight.Medium, pal.ink2, align = androidx.compose.ui.text.style.TextAlign.Center, lh = 22)
            if (p != null) Card(Modifier.fillMaxWidth(), bg = pal.pri2, border = null, pad = 14.dp) {
                T("Registered as", 12, FontWeight.Bold, pal.pri)
                T("${p.name} · ${p.role}", 15, FontWeight.Bold)
                T(listOf(p.station, if (Store.inRrt.value) "RRT unit" else "").filter { it.isNotBlank() }.joinToString(" · "), 13, FontWeight.Medium, pal.ink2)
            }
        }
        Column(Modifier.padding(20.dp)) { BigButton("Sign out", null, 1, Modifier.fillMaxWidth()) { Store.logout(); AlertService.sync(ctx.applicationContext) } }
    }
}
