package co.ocaana.dharmis

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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

/** Station, section, DRO and RO phones: who is working and what is going on in their office, with a drill down. Never alerts. */
@Composable
fun Dashboard() {
    var stack by remember { mutableStateOf(listOf<Int>()) }   // offices opened below the officer's own
    var data by remember { mutableStateOf<JSONObject?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    var detail by remember { mutableStateOf<String?>(null) }
    val unit = stack.lastOrNull() ?: 0
    BackHandler(enabled = stack.isNotEmpty()) { stack = stack.dropLast(1) }
    LaunchedEffect(unit, Store.resumeTick.value) {
        while (true) {
            Api.dash(unit) { d, e -> if (d != null) { data = d; err = null } else err = e }
            delay(30_000)
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val d = data
        if (d == null) {
            T(if (err != null) "Cannot load the dashboard" else "Loading…", 22, FontWeight.ExtraBold)
            if (err != null) T(err!!, 14, FontWeight.Medium, pal.ink2)
            return@Column
        }
        val u = d.getJSONObject("unit"); val s = d.getJSONObject("summary")
        val crumbs = d.getJSONArray("crumbs").items()
        if (stack.isNotEmpty()) T("‹ Back to " + (crumbs.getOrNull(crumbs.size - 2)?.optString("name") ?: "your office"), 14, FontWeight.Bold, pal.pri, Modifier.clickable { stack = stack.dropLast(1) }.padding(vertical = 4.dp))
        if (stack.isEmpty()) Store.profile.value?.let { T(it.name + " · " + postName(Store.rank.value), 13, FontWeight.Bold, pal.ink2) }
        T(u.getString("name"), 26, FontWeight.ExtraBold)
        T(kindName(u.getString("kind")) + if (crumbs.size > 1) " · " + crumbs.dropLast(1).joinToString(" › ") { it.optString("name") } else "", 13, FontWeight.Medium, pal.ink3)
        if (err != null) T("Not updated: $err", 12, FontWeight.Bold, pal.amb)

        val nodes = d.getJSONArray("nodes").items()
        val reports = d.getJSONArray("reports").items()
        val people = d.getJSONArray("people").items()
        // A station or section shows only its live nodes and, below them, its team. Higher offices show cards that open lists.
        val office = stack.isNotEmpty() && u.getString("kind") in setOf("station", "section")
        if (!office) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Tile("${s.getInt("nodes")}", "Nodes", null, Modifier.weight(1f)) { detail = "nodes" }
                Tile("${s.getInt("high")}", "High", if (s.getInt("high") > 0) levelColors("HIGH") else null, Modifier.weight(1f)) { detail = "high" }
                Tile("${s.getInt("medium")}", "Medium", if (s.getInt("medium") > 0) levelColors("MEDIUM") else null, Modifier.weight(1f)) { detail = "medium" }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Tile("${s.getInt("on_duty")} / ${s.getInt("people")}", "On duty", null, Modifier.weight(1f)) { detail = "duty" }
                Tile("${s.getInt("reports24")}", "Reports", null, Modifier.weight(1f)) { detail = "reports" }
                Tile("${s.getInt("offline")}", "No signal", null, Modifier.weight(1f)) { detail = "offline" }
            }
        }

        val kids = d.getJSONArray("children").items()
        if (kids.isNotEmpty()) {
            T("Offices", 16, FontWeight.ExtraBold)
            kids.forEach { k ->
                val worst = if (k.getInt("high") > 0) "high" else if (k.getInt("medium") > 0) "medium" else if (k.getInt("low") > 0) "low" else if (k.getInt("safe") > 0) "safe" else "offline"
                val lc = nodeColors(worst)
                Card(Modifier.fillMaxWidth().clickable { stack = stack + k.getInt("id") }, pad = 14.dp) {
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

        if (office) {
            T("Live nodes", 16, FontWeight.ExtraBold)
            if (nodes.isEmpty()) T("No nodes are set for this office yet.", 14, FontWeight.Medium, pal.ink3)
            nodes.forEach { NodeCard(it) }
            T("Team", 16, FontWeight.ExtraBold)
            if (people.isEmpty()) T("No one is registered under this office yet.", 14, FontWeight.Medium, pal.ink3)
            people.forEach { PersonRow(it) }
        }

        when (detail) {
            "nodes", "high", "medium", "offline" -> {
                val list = nodes.filter { detail == "nodes" || it.getString("level") == detail }
                ListDialog(when (detail) { "high" -> "High"; "medium" -> "Medium"; "offline" -> "No signal"; else -> "All nodes" }, list.isEmpty(), "Nothing here.", { detail = null }) { list.forEach { NodeCard(it) } }
            }
            "reports" -> ListDialog("Reports", reports.isEmpty(), "No reports yet.", { detail = null }) { reports.forEach { ReportCard(it) } }
            "duty" -> {
                val on = people.filter { it.getBoolean("on_duty") }
                ListDialog("On duty", on.isEmpty(), "No one is on duty.", { detail = null }) { on.forEach { PersonRow(it) } }
            }
        }
    }
}

@Composable
private fun ListDialog(title: String, empty: Boolean, emptyText: String, onClose: () -> Unit, body: @Composable ColumnScope.() -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onClose,
        title = { androidx.compose.material3.Text(title) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) { if (empty) T(emptyText, 14, FontWeight.Medium, pal.ink3) else body() } },
        confirmButton = { androidx.compose.material3.TextButton(onClick = onClose) { androidx.compose.material3.Text("Close") } },
    )
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
