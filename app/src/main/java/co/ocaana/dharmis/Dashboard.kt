package co.ocaana.dharmis

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

private fun JSONArray?.items(): List<JSONObject> = if (this == null) emptyList() else List(length()) { getJSONObject(it) }

private fun postName(p: String) = when (p) {
    "watcher" -> "Forest Watcher"; "rrt" -> "RRT Member"; "beat" -> "Beat Forest Officer"; "section" -> "Section Forest Officer"
    "dro" -> "Deputy Range Forest Officer"; "range" -> "Range Officer"; "office" -> "Office Phone"; else -> p
}

private fun kindName(k: String) = when (k) { "division" -> "Division"; "range" -> "Range"; "section" -> "Section office"; "station" -> "Forest station"; "rrt" -> "RRT unit"; else -> k }

private fun levelWord(l: String) = when (l) { "high" -> "High"; "medium" -> "Medium"; "low" -> "Low"; "safe" -> "Safe"; else -> "No signal" }

@Composable
private fun levelColor(l: String): Color = when (l) { "high" -> pal.red; "medium" -> pal.amb; "low" -> pal.amb; "safe" -> pal.grn; else -> pal.ink3 }

@Composable
private fun Head(title: String, sub: String) { Column { T(title, 28, FontWeight.ExtraBold); T(sub, 13, FontWeight.Medium, pal.ink3) } }

@Composable
private fun Sect(t: String) = T(t, 14, FontWeight.Bold, pal.ink2)

@Composable
private fun Stat(label: String, num: Int, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(pal.card).border(BorderStroke(1.dp, pal.line), RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(8.dp).clip(CircleShape).background(color)); Spacer(Modifier.width(6.dp)); T(label, 12, FontWeight.SemiBold, pal.ink2) }
        T("$num", 30, FontWeight.ExtraBold, if (color == pal.ink3) pal.ink else color)
    }
}

/** What the officer tabs share: the office being looked at and the tab or filter the cards jumped to. */
class DashState {
    var data by mutableStateOf<JSONObject?>(null)
    var err by mutableStateOf<String?>(null)
    var stack by mutableStateOf(listOf<Int>())   // offices opened below the officer's own
    var tab by mutableStateOf("Dashboard")
    var filter by mutableStateOf("All")
}

fun officerTabs(rrtOfficer: Boolean) = listOf("Dashboard", "Nodes", "Reports", if (rrtOfficer) "Alerts" else "On duty", "Me")

@Composable
fun rememberDash(enabled: Boolean): DashState {
    val s = remember { DashState() }
    BackHandler(enabled = enabled && s.tab == "Dashboard" && s.stack.isNotEmpty()) { s.stack = s.stack.dropLast(1) }
    LaunchedEffect(enabled, s.stack.lastOrNull(), Store.resumeTick.value) {
        if (!enabled) return@LaunchedEffect
        while (true) {
            Api.dash(s.stack.lastOrNull() ?: 0, s.data?.optString("hash") ?: "") { d, e -> if (d != null) { if (!d.optBoolean("same")) s.data = d; s.err = null } else s.err = e }
            delay(30_000)
        }
    }
    return s
}

@Composable
private fun TabPage(s: DashState, content: @Composable ColumnScope.(JSONObject) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val d = s.data
        if (d == null) {
            T(if (s.err != null) "Cannot load the dashboard" else "Loading…", 22, FontWeight.ExtraBold)
            if (s.err != null) T(s.err!!, 14, FontWeight.Medium, pal.ink2)
        } else content(d)
    }
}

/** Cards for the office; a Deputy Range Forest Officer or SFO also sees live nodes and the team below. Drilled into a station or section it shows only the live nodes and the team. */
@Composable
fun DashboardTab(s: DashState, rrtOfficer: Boolean) = TabPage(s) { d ->
    val u = d.getJSONObject("unit"); val sm = d.getJSONObject("summary")
    val nodes = d.getJSONArray("nodes").items()
    val people = d.getJSONArray("people").items()
    val drilled = s.stack.isNotEmpty()
    val officeView = u.getString("kind") in setOf("station", "section")
    if (s.err != null) T("Not updated: ${s.err}", 12, FontWeight.Bold, pal.amb)

    fun open(tab: String, filter: String = "All") { s.filter = filter; s.tab = tab }
    if (!(drilled && officeView)) {
        val cards = listOf(
            Triple("Nodes", sm.getInt("nodes"), pal.ink) to { open("Nodes") },
            Triple("High", sm.getInt("high"), pal.red) to { open("Nodes", "High") },
            Triple("Medium", sm.getInt("medium"), pal.amb) to { open("Nodes", "Medium") },
            Triple("No signal", sm.getInt("offline"), pal.ink3) to { open("Nodes", "No signal") },
            Triple("Reports today", sm.getInt("reports24"), pal.pri) to { open("Reports") },
            Triple("On duty", sm.getInt("on_duty"), pal.grn) to { if (!rrtOfficer) open("On duty") },
        )
        cards.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { row.forEach { (c, go) -> Stat(c.first, c.second, c.third, Modifier.weight(1f), go) } }
        }
    }

    val kids = d.getJSONArray("children").items()
    if (kids.isNotEmpty()) {
        Sect("Offices")
        kids.forEach { k ->
            val sub = if (k.getInt("high") > 0) "${k.getInt("nodes")} nodes · ${k.getInt("high")} High" else if (k.getInt("medium") > 0) "${k.getInt("nodes")} nodes · ${k.getInt("medium")} Medium" else "${k.getInt("nodes")} nodes · all clear"
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(pal.card).border(BorderStroke(1.dp, pal.line), RoundedCornerShape(16.dp)).clickable { s.stack = s.stack + k.getInt("id") }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { T(k.getString("name"), 15, FontWeight.Bold); T(sub, 12, FontWeight.Medium, pal.ink3) }
                Icon(Icons.Rounded.ChevronRight, null, tint = pal.ink3)
            }
        }
    }

    if (officeView) {
        Sect("Live nodes")
        if (nodes.isEmpty()) T("No nodes are set for this office yet.", 14, FontWeight.Medium, pal.ink3)
        nodes.forEach { NodeCard(it) }
        Sect("Team")
        if (people.isEmpty()) T("No one is registered under this office yet.", 14, FontWeight.Medium, pal.ink3)
        people.forEach { PersonRow(it, u.getString("name")) }
    }
}

/** All nodes, with a sliding strip to narrow them to High, Medium or No signal. */
@Composable
fun NodesTab(s: DashState) {
    Column(Modifier.fillMaxSize()) {
        val d = s.data
        val all = d?.getJSONArray("nodes").items()
        fun count(l: String?) = all.count { l == null || it.getString("level") == l }
        val filters = listOf("All" to count(null), "High" to count("high"), "Medium" to count("medium"), "No signal" to count("offline"))
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Head("Nodes", "Across " + (d?.getJSONObject("unit")?.getString("name") ?: ""))
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            filters.forEach { (f, n) ->
                val on = s.filter == f
                Row(Modifier.clip(RoundedCornerShape(18.dp)).background(if (on) pal.pri else pal.card2).clickable { s.filter = f }.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    T(f, 13, FontWeight.Bold, if (on) pal.onPri else pal.ink2); Spacer(Modifier.width(6.dp)); T("$n", 13, FontWeight.Medium, if (on) pal.onPri else pal.ink3)
                }
            }
        }
        Box(Modifier.weight(1f)) {
            TabPage(s) { dd ->
                val want = when (s.filter) { "High" -> "high"; "Medium" -> "medium"; "No signal" -> "offline"; else -> null }
                val list = dd.getJSONArray("nodes").items().filter { want == null || it.getString("level") == want }
                if (list.isEmpty()) T("Nothing here.", 14, FontWeight.Medium, pal.ink3)
                list.forEach { NodeCard(it) }
            }
        }
    }
}

@Composable
fun ReportsTab(s: DashState) = TabPage(s) { d ->
    val list = d.getJSONArray("reports").items()
    Head("Reports", "Today · ${d.getJSONObject("summary").getInt("reports24")} reports")
    if (list.isEmpty()) T("No reports yet.", 14, FontWeight.Medium, pal.ink3)
    list.forEach { ReportCard(it) }
}

@Composable
fun DutyTab(s: DashState) = TabPage(s) { d ->
    val on = d.getJSONArray("people").items().filter { it.getBoolean("on_duty") }
    Head("On duty", "${on.size} people on duty now")
    if (on.isEmpty()) T("No one is on duty.", 14, FontWeight.Medium, pal.ink3)
    on.forEach { PersonRow(it, d.getJSONObject("unit").getString("name")) }
}

/** What the app bar shows while an office is open below the officer's own: its name, and the office above it. */
fun DashState.openOffice(): Pair<String, String>? {
    val d = data ?: return null
    if (stack.isEmpty()) return null
    val crumbs = d.getJSONArray("crumbs").items()
    return d.getJSONObject("unit").getString("name") to (crumbs.getOrNull(crumbs.size - 2)?.optString("name") ?: "")
}

@Composable
private fun NodeCard(n: JSONObject) {
    val level = n.getString("level"); val c = levelColor(level)
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(pal.card).border(BorderStroke(1.dp, pal.line), RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(c)); Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            T(n.getString("id"), 15, FontWeight.Bold)
            val place = n.optString("place"); if (place.isNotBlank()) T(place, 12, FontWeight.Medium, pal.ink3)
        }
        Column(horizontalAlignment = Alignment.End) {
            T(levelWord(level), 13, FontWeight.Bold, c)
            if (!n.isNull("age")) T(java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(System.currentTimeMillis() - n.getLong("age") * 1000)), 12, FontWeight.Medium, pal.ink3)
        }
    }
}

@Composable
private fun ReportCard(r: JSONObject) {
    Card(Modifier.fillMaxWidth(), pad = 14.dp) {
        Row { T(r.getString("node"), 15, FontWeight.Bold, modifier = Modifier.weight(1f)); T(java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(r.getLong("at") * 1000)), 12, FontWeight.Medium, pal.ink3) }
        Spacer(Modifier.height(4.dp))
        T((if (r.getInt("herd") > 0) "${r.getInt("herd")} elephants" else "No elephants") + if (r.optString("note").isNotBlank() && r.optString("note") != "null") ". " + r.optString("note") else "", 13, FontWeight.Medium, lh = 19)
        if (r.optString("by").isNotBlank() && r.optString("by") != "null") T(r.optString("by"), 12, FontWeight.Medium, pal.ink3)
    }
}

@Composable
private fun PersonRow(p: JSONObject, place: String) {
    val ctx = LocalContext.current
    val duty = p.optBoolean("duty", true)   // officers have no duty status
    val name = p.getString("name")
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(pal.card).border(BorderStroke(1.dp, pal.line), RoundedCornerShape(16.dp)).clickable {
        ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + p.getString("phone"))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(pal.pri2), contentAlignment = Alignment.Center) { T(name.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1).uppercase() }, 13, FontWeight.Bold, pal.pri) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) { T(name, 15, FontWeight.Bold); T(postName(p.getString("post")) + " · " + place, 12, FontWeight.Medium, pal.ink3) }
        if (duty && p.getBoolean("on_duty")) Box(Modifier.size(10.dp).clip(CircleShape).background(pal.grn))
    }
}
