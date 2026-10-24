package co.ocaana.dharmis

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
    "dro" -> "Deputy Range Officer"; "range" -> "Range Officer"; "office" -> "Office Phone"; else -> p
}

private fun kindName(k: String) = when (k) { "division" -> "Division"; "range" -> "Range"; "section" -> "Section office"; "station" -> "Forest station"; "rrt" -> "RRT unit"; else -> k }

/** Level colours for a node; offline or silent nodes are grey. */
@Composable
private fun nodeColors(level: String): LevelColors =
    if (level in setOf("high", "medium", "low", "safe")) levelColors(level.uppercase()) else LevelColors(pal.ink3, pal.card2, pal.ink3, pal.bg)

@Composable
private fun Tile(value: String, label: String, lc: LevelColors?, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(lc?.bg ?: pal.card).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp)) {
        T(value, 26, FontWeight.ExtraBold, lc?.text ?: pal.ink)
        T(label, 12, FontWeight.Bold, lc?.text ?: pal.ink3)
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
            Api.dash(s.stack.lastOrNull() ?: 0) { d, e -> if (d != null) { s.data = d; s.err = null } else s.err = e }
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

/** Cards for the office; a Deputy Range Officer or SFO also sees the team below. Drilled into a station or section it shows only the live nodes and the team. */
@Composable
fun DashboardTab(s: DashState, rrtOfficer: Boolean) = TabPage(s) { d ->
    val u = d.getJSONObject("unit"); val sm = d.getJSONObject("summary")
    val crumbs = d.getJSONArray("crumbs").items()
    val nodes = d.getJSONArray("nodes").items()
    val people = d.getJSONArray("people").items()
    val drilled = s.stack.isNotEmpty()
    val officeView = u.getString("kind") in setOf("station", "section")
    if (drilled) T("‹ Back to " + (crumbs.getOrNull(crumbs.size - 2)?.optString("name") ?: "your office"), 14, FontWeight.Bold, pal.pri, Modifier.clickable { s.stack = s.stack.dropLast(1) }.padding(vertical = 4.dp))
    else Store.profile.value?.let { T(it.name + " · " + postName(Store.rank.value), 13, FontWeight.Bold, pal.ink2) }
    T(u.getString("name"), 26, FontWeight.ExtraBold)
    T(kindName(u.getString("kind")) + if (crumbs.size > 1) " · " + crumbs.dropLast(1).joinToString(" › ") { it.optString("name") } else "", 13, FontWeight.Medium, pal.ink3)
    if (s.err != null) T("Not updated: ${s.err}", 12, FontWeight.Bold, pal.amb)

    fun open(tab: String, filter: String = "All") { s.filter = filter; s.tab = tab }
    if (!(drilled && officeView)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile("${sm.getInt("nodes")}", "Nodes", null, Modifier.weight(1f)) { open("Nodes") }
            Tile("${sm.getInt("high")}", "High", if (sm.getInt("high") > 0) levelColors("HIGH") else null, Modifier.weight(1f)) { open("Nodes", "High") }
            Tile("${sm.getInt("medium")}", "Medium", if (sm.getInt("medium") > 0) levelColors("MEDIUM") else null, Modifier.weight(1f)) { open("Nodes", "Medium") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile("${sm.getInt("on_duty")} / ${sm.getInt("people")}", "On duty", null, Modifier.weight(1f)) { if (!rrtOfficer) open("On duty") }
            Tile("${sm.getInt("reports24")}", "Reports", null, Modifier.weight(1f)) { open("Reports") }
            Tile("${sm.getInt("offline")}", "No signal", null, Modifier.weight(1f)) { open("Nodes", "No signal") }
        }
    }

    val kids = d.getJSONArray("children").items()
    if (kids.isNotEmpty()) {
        T("Offices", 16, FontWeight.ExtraBold)
        kids.forEach { k ->
            val worst = if (k.getInt("high") > 0) "high" else if (k.getInt("medium") > 0) "medium" else if (k.getInt("low") > 0) "low" else if (k.getInt("safe") > 0) "safe" else "offline"
            val lc = nodeColors(worst)
            Card(Modifier.fillMaxWidth().clickable { s.stack = s.stack + k.getInt("id") }, pad = 14.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(12.dp).clip(CircleShape).background(lc.main))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        T(k.getString("name"), 17, FontWeight.ExtraBold)
                        T(kindName(k.getString("kind")), 12, FontWeight.Medium, pal.ink3)
                        T("${k.getInt("nodes")} nodes · ${k.getInt("high")} high · ${k.getInt("medium")} medium · ${k.getInt("on_duty")}/${k.getInt("people")} on duty · ${k.getInt("reports24")} reports today", 13, FontWeight.Medium, pal.ink2)
                    }
                    Icon(Icons.Rounded.ChevronRight, null, tint = pal.ink3)
                }
            }
        }
    }

    if (drilled && officeView) {
        T("Live nodes", 16, FontWeight.ExtraBold)
        if (nodes.isEmpty()) T("No nodes are set for this office yet.", 14, FontWeight.Medium, pal.ink3)
        nodes.forEach { NodeCard(it) }
    }
    if (officeView) {
        T("Team", 16, FontWeight.ExtraBold)
        if (people.isEmpty()) T("No one is registered under this office yet.", 14, FontWeight.Medium, pal.ink3)
        people.forEach { PersonRow(it) }
    }
}

/** All nodes, with a sliding strip to narrow them to High, Medium or No signal. */
@Composable
fun NodesTab(s: DashState) {
    Column(Modifier.fillMaxSize()) {
        val filters = listOf("All", "High", "Medium", "No signal")
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            filters.forEach { f ->
                val on = s.filter == f
                Box(Modifier.height(40.dp).clip(RoundedCornerShape(20.dp)).background(if (on) pal.pri else pal.card).clickable { s.filter = f }.padding(horizontal = 18.dp), contentAlignment = Alignment.Center) {
                    T(f, 14, FontWeight.Bold, if (on) pal.bg else pal.ink2)
                }
            }
        }
        Box(Modifier.weight(1f)) {
            TabPage(s) { d ->
                val want = when (s.filter) { "High" -> "high"; "Medium" -> "medium"; "No signal" -> "offline"; else -> null }
                val list = d.getJSONArray("nodes").items().filter { want == null || it.getString("level") == want }
                if (list.isEmpty()) T("Nothing here.", 14, FontWeight.Medium, pal.ink3)
                list.forEach { NodeCard(it) }
            }
        }
    }
}

@Composable
fun ReportsTab(s: DashState) = TabPage(s) { d ->
    val list = d.getJSONArray("reports").items()
    if (list.isEmpty()) T("No reports yet.", 14, FontWeight.Medium, pal.ink3)
    list.forEach { ReportCard(it) }
}

@Composable
fun DutyTab(s: DashState) = TabPage(s) { d ->
    val on = d.getJSONArray("people").items().filter { it.getBoolean("on_duty") }
    if (on.isEmpty()) T("No one is on duty.", 14, FontWeight.Medium, pal.ink3)
    on.forEach { PersonRow(it) }
}

@Composable
private fun NodeCard(n: JSONObject) {
    val level = n.getString("level"); val lc = nodeColors(level)
    Card(Modifier.fillMaxWidth(), bg = lc.bg, border = lc.main, pad = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                T(n.getString("id"), 16, FontWeight.ExtraBold, lc.text)
                val place = n.optString("place"); if (place.isNotBlank()) T(place, 13, FontWeight.Medium, pal.ink2)
                if (!n.isNull("age")) T("Last signal " + ago(System.currentTimeMillis() - n.getLong("age") * 1000), 12, FontWeight.Medium, pal.ink3)
            }
            Box(Modifier.clip(RoundedCornerShape(6.dp)).background(lc.main).padding(horizontal = 8.dp, vertical = 2.dp)) { T(if (level == "offline") "NO SIGNAL" else level.uppercase(), 11, FontWeight.ExtraBold, lc.on) }
        }
    }
}

@Composable
private fun ReportCard(r: JSONObject) {
    Card(Modifier.fillMaxWidth(), pad = 12.dp) {
        T("${r.getString("node")} · ${if (r.getInt("herd") > 0) "${r.getInt("herd")} elephants" else "No elephants"}", 15, FontWeight.ExtraBold)
        T(ago(r.getLong("at") * 1000) + if (r.optString("by").isNotBlank() && r.optString("by") != "null") " · ${r.optString("by")}" else "", 12, FontWeight.Medium, pal.ink3)
        if (r.optString("note").isNotBlank()) T(r.optString("note"), 13, FontWeight.Medium, pal.ink2)
    }
}

@Composable
private fun PersonRow(p: JSONObject) {
    val ctx = LocalContext.current
    val duty = p.optBoolean("duty", true)   // officers have no duty status
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(pal.card).clickable {
        ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + p.getString("phone"))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (duty) { Box(Modifier.size(10.dp).clip(CircleShape).background(if (p.getBoolean("on_duty")) pal.grn else pal.line)); Spacer(Modifier.width(10.dp)) }
        Column(Modifier.weight(1f)) { T(p.getString("name"), 15, FontWeight.Bold); T(postName(p.getString("post")) + if (!duty) "" else if (p.getBoolean("on_duty")) " · on duty" else " · off duty", 12, FontWeight.Medium, pal.ink3) }
        Icon(Icons.Rounded.Call, null, tint = pal.pri)
    }
}
