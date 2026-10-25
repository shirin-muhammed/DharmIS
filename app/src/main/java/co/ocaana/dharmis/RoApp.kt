package co.ocaana.dharmis

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** The Range Officer app, drawn exactly as the Figma page "v4 · RO command". Follows the Appearance choice in Me (Auto, Light or Dark). */
private object V4 {
    val bg @Composable get() = if (pal.dark) Color(0xFF09241B) else Color(0xFFF5F1E8)
    val surf @Composable get() = if (pal.dark) Color(0xFF0F3125) else Color(0xFFFFFFFF)
    val surf2 @Composable get() = if (pal.dark) Color(0xFF16402F) else Color(0xFFECE6D8)
    val line @Composable get() = if (pal.dark) Color(0xFF21503C) else Color(0xFFDDD5C3)
    val text @Composable get() = if (pal.dark) Color(0xFFEAF6F0) else Color(0xFF17261E)
    val mute @Composable get() = if (pal.dark) Color(0xFF8FB5A4) else Color(0xFF6C7A72)
    val acc @Composable get() = if (pal.dark) Color(0xFF2EE6C5) else Color(0xFF1D5A43)
    val onAcc @Composable get() = if (pal.dark) Color(0xFF09241B) else Color.White
    val high @Composable get() = if (pal.dark) Color(0xFFFF6B5B) else Color(0xFFC4301F)
    val med @Composable get() = if (pal.dark) Color(0xFFFFB347) else Color(0xFFA86500)
    val ok @Composable get() = if (pal.dark) Color(0xFF4ADE80) else Color(0xFF1D7A3E)
    val off @Composable get() = if (pal.dark) Color(0xFF7F968C) else Color(0xFF6C7A72)
    val heroRed @Composable get() = if (pal.dark) Color(0xFF3A1A17) else Color(0xFFFBE3DD)
    val heroText @Composable get() = if (pal.dark) Color(0xFFF3CFC9) else Color(0xFF7A2418)
    val heroAmber @Composable get() = if (pal.dark) Color(0xFF33260F) else Color(0xFFFBECC8)
}

private sealed class Sc {
    class Nodes(val f: String) : Sc()
    object Reports : Sc()
    class Station(val name: String) : Sc()
    class Alert(val node: String) : Sc()
    class Person(val name: String) : Sc()
    class Report(val i: Int) : Sc()
}

private class Ro(val d: JSONObject, val push: (Sc) -> Unit, val tab: (String) -> Unit) {
    val nodes = d.getJSONArray("nodes").l()
    val kids = d.getJSONArray("children").l()
    val people = d.getJSONArray("people").l()
    val reports = d.getJSONArray("reports").l()
    val resp = d.optJSONArray("resp")?.l() ?: emptyList()
    val sum: JSONObject = d.getJSONObject("summary")
    val rangeName: String = d.getJSONObject("unit").getString("name")
    val now: Long = d.optLong("now", System.currentTimeMillis() / 1000)
    fun kid(office: String) = kids.firstOrNull { it.s("name") == office }
    fun sec(n: JSONObject): Long? = if (n.isNull("age")) null else now - n.getLong("age")
}

private fun JSONArray.l(): List<JSONObject> = List(length()) { getJSONObject(it) }
private fun JSONObject.s(k: String): String = if (isNull(k)) "" else optString(k).let { if (it == "null") "" else it }
@Composable
private fun lc(l: String) = when (l) { "high" -> V4.high; "medium" -> V4.med; "low", "safe" -> V4.ok; else -> V4.off }
private fun lw(l: String) = when (l) { "high" -> "High"; "medium" -> "Medium"; "low" -> "Low"; "safe" -> "Safe"; else -> "No signal" }
private fun hhmm(sec: Long) = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(sec * 1000))
private fun herdText(r: JSONObject) = when (val h = r.getInt("herd")) { 0 -> "no elephants"; 1 -> "1 elephant"; else -> "$h elephants" }
private fun reportLine(r: JSONObject): String = listOf(r.s("seen"), herdText(r), r.s("note")).filter { it.isNotBlank() }.joinToString(", ")
private fun stepWord(s: String) = when (s) { "accepted" -> "Accepted"; "on_the_way" -> "On the way"; "reached" -> "Reached"; else -> s }
private fun openMap(ctx: Context, lat: Double, lon: Double, label: String) { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lon?q=$lat,$lon(${Uri.encode(label)})")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
private fun postWord(p: String) = when (p) {
    "watcher" -> "Forest Watcher"; "rrt" -> "RRT Member"; "beat" -> "Beat Forest Officer"; "section" -> "Section Forest Officer"
    "dro" -> "Deputy Range Officer"; "range" -> "Range Officer"; "office" -> "Office Phone"; else -> p
}
private fun dial(ctx: Context, phone: String) { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }

@Composable
private fun X(s: String, size: Int = 14, w: FontWeight = FontWeight.Medium, c: Color = V4.text, m: Modifier = Modifier, al: TextAlign? = null) = T(s, size, w, c, m, al)

@Composable
private fun Surf(m: Modifier = Modifier, r: Int = 18, border: Color = V4.line, fill: Color = V4.surf, click: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(r.dp)
    Column(m.clip(shape).background(fill).border(BorderStroke(1.dp, border), shape).let { if (click != null) it.clickable(onClick = click) else it }.padding(horizontal = 16.dp, vertical = 14.dp), content = content)
}

@Composable
private fun Av(name: String, size: Int, ring: Color? = null) {
    val ini = name.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1).uppercase() }
    Box(Modifier.size(size.dp).clip(CircleShape).background(V4.surf2).let { if (ring != null) it.border(BorderStroke(2.dp, ring), CircleShape) else it }, contentAlignment = Alignment.Center) { X(ini, (size * 0.36).toInt(), FontWeight.Bold) }
}

@Composable
private fun Chip4(label: String, c: Color) {
    Row(Modifier.clip(RoundedCornerShape(20.dp)).background(c.copy(alpha = .16f)).padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(c)); Spacer(Modifier.width(6.dp)); X(label, 12, FontWeight.SemiBold, c)
    }
}

@Composable
private fun CallBtn(phone: String, on: Boolean) {
    val ctx = LocalContext.current
    Box(Modifier.size(36.dp).clip(CircleShape).background(if (on) V4.acc else V4.surf2).clickable { dial(ctx, phone) }, contentAlignment = Alignment.Center) {
        Icon(Icons.Rounded.Call, "Call", tint = if (on) V4.onAcc else V4.mute, modifier = Modifier.size(18.dp))
    }
}

/** fill null = outlined */
@Composable
private fun PBtn(label: String, fill: Color?, fg: Color, m: Modifier = Modifier, icon: ImageVector? = null, h: Int = 50, click: () -> Unit) {
    val shape = RoundedCornerShape(30.dp)
    Row(m.height(h.dp).clip(shape).let { if (fill != null) it.background(fill) else it.border(BorderStroke(1.5.dp, V4.line), shape) }.clickable(onClick = click).padding(horizontal = 18.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) { Icon(icon, null, tint = fg, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
        X(label, 14, FontWeight.Bold, fg)
    }
}

@Composable
private fun Stat(value: String, label: String, cap: String, vc: Color, m: Modifier, border: Color = V4.line, click: () -> Unit) {
    Surf(m, r = 14, border = border, click = click) { X(value, 24, FontWeight.ExtraBold, vc); X(label, 11, FontWeight.SemiBold, V4.mute); X(cap, 10, FontWeight.Medium, if (vc == V4.text) V4.mute else vc) }
}

@Composable
private fun FilterRow(items: List<String>, sel: String, onSel: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { f -> val on = f == sel
            Box(Modifier.clip(RoundedCornerShape(20.dp)).background(if (on) V4.acc else V4.surf).clickable { onSel(f) }.padding(horizontal = 14.dp, vertical = 8.dp)) { X(f, 12, FontWeight.SemiBold, if (on) V4.onAcc else V4.mute) }
        }
    }
}

@Composable
private fun Segmented(items: List<String>, sel: Int, onSel: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(V4.surf).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        items.forEachIndexed { i, s ->
            Box(Modifier.weight(1f).height(34.dp).clip(RoundedCornerShape(11.dp)).background(if (i == sel) V4.acc else Color.Transparent).clickable { onSel(i) }, contentAlignment = Alignment.Center) { X(s, 13, FontWeight.SemiBold, if (i == sel) V4.onAcc else V4.mute) }
        }
    }
}

@Composable
private fun Body(m: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(m.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
}

@Composable
private fun TopBrand() {
    val ctx = LocalContext.current
    val logo = remember { android.graphics.BitmapFactory.decodeStream(ctx.assets.open("logo.png"), null, android.graphics.BitmapFactory.Options().apply { inSampleSize = 4 })!!.asImageBitmap() }
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF11201A)).border(BorderStroke(1.5.dp, V4.acc), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
            androidx.compose.foundation.Image(logo, "OCAANA", Modifier.size(24.dp))
        }
        Spacer(Modifier.width(10.dp))
        X("DharmIS", 17, FontWeight.ExtraBold)
    }
}

@Composable
private fun BackTop(title: String, sub: String, right: (@Composable () -> Unit)? = null, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(V4.surf).clickable(onClick = onBack), contentAlignment = Alignment.Center) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = V4.text, modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) { X(title, 18, FontWeight.ExtraBold); if (sub.isNotBlank()) X(sub, 12, FontWeight.Medium, V4.mute) }
        right?.invoke()
    }
}

@Composable
private fun Nav(tab: String, onTab: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().background(V4.surf).border(BorderStroke(1.dp, V4.line)).padding(top = 8.dp, bottom = 8.dp, start = 8.dp, end = 8.dp)) {
        listOf("Command" to Icons.Rounded.Dashboard, "Stations" to Icons.Rounded.Place, "Today" to Icons.Rounded.Schedule, "People" to Icons.Rounded.Groups, "Me" to Icons.Rounded.Person).forEach { (l, ic) ->
            val on = tab == l
            Column(Modifier.weight(1f).heightIn(min = 52.dp).clickable { onTab(l) }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(ic, null, tint = if (on) V4.acc else V4.mute, modifier = Modifier.size(22.dp))
                X(l, 11, if (on) FontWeight.Bold else FontWeight.Medium, if (on) V4.acc else V4.mute)
            }
        }
    }
}

@Composable
fun RoApp(dash: DashState) {
    var tab by remember { mutableStateOf("Command") }
    val stack = remember { mutableStateListOf<Sc>() }
    BackHandler(enabled = stack.isNotEmpty()) { stack.removeAt(stack.lastIndex) }
    val d = dash.data
    Column(Modifier.fillMaxSize().background(V4.bg).statusBarsPadding().navigationBarsPadding()) {
        Box(Modifier.weight(1f)) {
            if (d == null) {
                Column(Modifier.fillMaxSize().padding(20.dp)) {
                    TopBrand()
                    X(if (dash.err != null) "Cannot load the dashboard" else "Loading…", 22, FontWeight.ExtraBold)
                    if (dash.err != null) X(dash.err!!, 14, FontWeight.Medium, V4.mute)
                }
            } else {
                val ro = Ro(d, { stack.add(it) }, { tab = it; stack.clear() })
                val back = { stack.removeAt(stack.lastIndex); Unit }
                when (val top = stack.lastOrNull()) {
                    null -> when (tab) {
                        "Command" -> Command(ro)
                        "Stations" -> Stations(ro)
                        "Today" -> Today(ro)
                        "People" -> People(ro)
                        else -> Me(ro)
                    }
                    is Sc.Station -> StationScreen(ro, top.name, back)
                    is Sc.Alert -> AlertScreen(ro, top.node, back)
                    is Sc.Person -> PersonScreen(ro, top.name, back)
                    is Sc.Report -> ReportScreen(ro, top.i, back)
                    is Sc.Nodes -> NodesScreen(ro, top.f, back)
                    Sc.Reports -> ReportsScreen(ro, back)
                }
            }
        }
        Nav(tab) { tab = it; stack.clear() }
    }
}

private fun worst(nodes: List<JSONObject>) = nodes.firstOrNull { it.getString("level") == "high" } ?: nodes.firstOrNull { it.getString("level") == "medium" }

@Composable
private fun StationCard(ro: Ro, k: JSONObject, withPeople: Boolean) {
    val name = k.s("name"); val n = k.getInt("nodes")
    val dots = k.optJSONArray("dots")?.let { a -> List(a.length()) { a.getString(it) } } ?: emptyList()
    val on = k.getInt("on_duty"); val total = k.getInt("people")
    val last = ro.reports.firstOrNull { it.s("office") == name }
    Surf(Modifier.fillMaxWidth(), border = if (k.getInt("high") > 0) V4.high else V4.line, click = { ro.push(Sc.Station(name)) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { X(name, 16, FontWeight.Bold); X("$n nodes", 12, FontWeight.Medium, V4.mute) }
            when {
                k.getInt("high") > 0 -> Chip4("${k.getInt("high")} High", V4.high)
                k.getInt("medium") > 0 -> Chip4("${k.getInt("medium")} Medium", V4.med)
                n > 0 && k.getInt("offline") == n -> Chip4("No signal", V4.off)
                else -> Chip4("All clear", V4.ok)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { dots.forEach { Box(Modifier.size(11.dp).clip(CircleShape).background(lc(it))) } }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(V4.surf2)) { Box(Modifier.fillMaxWidth(if (total > 0) on.toFloat() / total else 0f).fillMaxHeight().background(V4.acc)) }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { X("$on on duty", 12, FontWeight.SemiBold, V4.acc); X("${total - on} off duty", 12, FontWeight.Medium, V4.mute) }
        if (withPeople) {
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(V4.line))
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) { ro.people.filter { it.s("office") == name && it.optBoolean("on_duty") }.take(3).forEach { Av(it.getString("name"), 26) } }
                X(if (last != null) "Last report ${hhmm(last.getLong("at"))}" else "No report today", 12, FontWeight.Medium, V4.mute)
                Icon(Icons.Rounded.ChevronRight, null, tint = V4.mute, modifier = Modifier.size(18.dp))
            }
            if (last != null) X(reportLine(last), 12, FontWeight.Medium, V4.mute)
        } else if (last != null) { Spacer(Modifier.height(4.dp)); X("Last report ${hhmm(last.getLong("at"))} · ${reportLine(last)}", 12, FontWeight.Medium, V4.mute) }
    }
}

@Composable
private fun Command(ro: Ro) {
    val ctx = LocalContext.current
    val p = Store.profile.value
    val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val name = p?.name ?: ""
    val w = ro.nodes.firstOrNull { it.getString("level") == "high" }
    val live = ro.sum.getInt("nodes") - ro.sum.getInt("offline")
    val hi = ro.sum.getInt("high"); val me = ro.sum.getInt("medium")
    val alertC = if (hi > 0) V4.high else if (me > 0) V4.med else V4.ok
    Column(Modifier.fillMaxSize()) {
        TopBrand()
        Body {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    X(if (h < 12) "Good morning" else if (h < 17) "Good afternoon" else "Good evening", 13, FontWeight.Medium, V4.mute)
                    X(name, 26, FontWeight.ExtraBold)
                    X("Range Officer · ${ro.rangeName}", 13, FontWeight.Medium, V4.mute)
                }
                Av(name, 48)
            }
            if (w != null) {
                val office = w.s("office"); val phone = ro.kid(office)?.s("phone") ?: ""
                val rep = ro.reports.firstOrNull { it.getString("node") == w.getString("id") }
                Surf(Modifier.fillMaxWidth(), r = 22, border = V4.high, fill = V4.heroRed) {
                    X("NEEDS YOU NOW", 12, FontWeight.ExtraBold, V4.high)
                    Spacer(Modifier.height(6.dp))
                    X("High alert · ${w.getString("id")}", 22, FontWeight.ExtraBold)
                    X(listOf(w.s("place"), office).filter { it.isNotBlank() }.joinToString(" · "), 13, FontWeight.Medium, V4.heroText)
                    val t = ro.sec(w)
                    X((if (t != null) "Last signal ${hhmm(t)}. " else "") + (if (rep != null) "${rep.s("by")} saw ${herdText(rep)} at ${hhmm(rep.getLong("at"))}." else ""), 13, FontWeight.Medium, V4.heroText)
                    val rs = ro.resp.filter { it.getString("node") == w.getString("id") }
                    X(if (rs.isEmpty()) "Nobody has accepted yet." else rs.joinToString(" · ") { it.s("name").substringBefore(" ") + " " + stepWord(it.s("state")).lowercase() }, 13, FontWeight.Bold, V4.heroText)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (phone.isNotBlank()) PBtn("Call station", V4.high, Color.White, Modifier.weight(1f), Icons.Rounded.Call) { dial(ctx, phone) }
                        PBtn("Open node", V4.surf2, V4.text, Modifier.weight(1f)) { ro.push(Sc.Alert(w.getString("id"))) }
                    }
                }
            } else {
                Surf(Modifier.fillMaxWidth(), r = 22, border = V4.ok) {
                    X("ALL CALM", 12, FontWeight.ExtraBold, V4.ok); Spacer(Modifier.height(6.dp))
                    X("Nothing needs you now", 22, FontWeight.ExtraBold)
                    X(if (me > 0) "$me Medium alert${if (me > 1) "s" else ""} in ${ro.rangeName}; none is High." else "No High alert in ${ro.rangeName}.", 13, FontWeight.Medium, V4.mute)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat("${ro.sum.getInt("nodes")}", "Nodes", "$live live", V4.text, Modifier.weight(1f)) { ro.push(Sc.Nodes("All")) }
                Stat("${ro.sum.getInt("reports24")}", "Reports", "today", V4.text, Modifier.weight(1f)) { ro.push(Sc.Reports) }
                Stat("${ro.sum.getInt("on_duty")}", "On duty", "of ${ro.sum.getInt("people")}", V4.text, Modifier.weight(1f)) { ro.tab("People") }
                Stat("${hi + me}", "Alerts", if (hi > 0) "$hi High" else if (me > 0) "Medium" else "none", alertC, Modifier.weight(1f), border = alertC) { ro.push(Sc.Nodes(if (hi > 0) "High" else if (me > 0) "Medium" else "All")) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                X("Your stations", 15, FontWeight.Bold, m = Modifier.weight(1f))
                X("See all", 12, FontWeight.SemiBold, V4.acc, Modifier.clickable { ro.tab("Stations") })
            }
            ro.kids.forEach { StationCard(ro, it, false) }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun Stations(ro: Ro) {
    var f by remember { mutableStateOf("All") }
    fun cat(k: JSONObject) = if (k.getInt("high") > 0) "High" else if (k.getInt("medium") > 0) "Medium" else "Clear"
    val shown = ro.kids.filter { f == "All" || cat(it) == f }
    Column(Modifier.fillMaxSize()) {
        TopBrand()
        Body {
            Column { X("Stations", 28, FontWeight.ExtraBold); X("${ro.rangeName} · ${ro.kids.size} offices · ${ro.sum.getInt("nodes")} nodes", 13, FontWeight.Medium, V4.mute) }
            FilterRow(listOf("All", "High", "Medium", "Clear").map { l -> if (l == "All") "All ${ro.kids.size}" else "$l ${ro.kids.count { cat(it) == l }}" }, (if (f == "All") "All ${ro.kids.size}" else "$f ${ro.kids.count { cat(it) == f }}")) { f = it.substringBefore(" ") }
            if (shown.isEmpty()) X("Nothing here.", 14, FontWeight.Medium, V4.mute)
            shown.forEach { StationCard(ro, it, true) }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun NodeRow(n: JSONObject, ro: Ro, showOffice: Boolean = false) {
    val l = n.getString("level"); val c = lc(l)
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(V4.surf).border(BorderStroke(1.dp, V4.line), RoundedCornerShape(16.dp)).clickable { ro.push(Sc.Alert(n.getString("id"))) }.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(c)); Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) { X(n.getString("id"), 14, FontWeight.Bold); val pl = listOf(n.s("place"), if (showOffice) n.s("office") else "").filter { it.isNotBlank() }.joinToString(" · "); if (pl.isNotBlank()) X(pl, 12, FontWeight.Medium, V4.mute) }
        Column(horizontalAlignment = Alignment.End) { X(lw(l), 13, FontWeight.Bold, c); ro.sec(n)?.let { X(hhmm(it), 11, FontWeight.Medium, V4.mute) } }
    }
}

@Composable
private fun PersonRow(ro: Ro, p: JSONObject) {
    val duty = p.optBoolean("duty", true); val on = duty && p.getBoolean("on_duty")
    Row(Modifier.fillMaxWidth().clickable { ro.push(Sc.Person(p.getString("name"))) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Av(p.getString("name"), 40, if (on) V4.ok else null); Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) { X(p.getString("name"), 14, FontWeight.SemiBold); X(postWord(p.s("post")) + if (duty && !on) " · off duty" else "", 12, FontWeight.Medium, V4.mute) }
        CallBtn(p.getString("phone"), on)
    }
}

@Composable
private fun StationScreen(ro: Ro, name: String, back: () -> Unit) {
    val ctx = LocalContext.current
    var seg by remember { mutableIntStateOf(0) }
    val k = ro.kid(name)
    val nodes = ro.nodes.filter { it.s("office") == name }
    val reps = ro.reports.filter { it.s("office") == name }
    val team = ro.people.filter { it.s("office") == name }
    val head = k?.optJSONObject("head")
    val hi = k?.getInt("high") ?: 0; val me = k?.getInt("medium") ?: 0
    val frame = if (hi > 0) V4.high else if (me > 0) V4.med else if (nodes.isNotEmpty() && nodes.all { it.getString("level") == "offline" }) V4.off else V4.ok
    val phone = k?.s("phone") ?: ""
    Column(Modifier.fillMaxSize()) {
        BackTop(name, ro.rangeName, { Chip4(if (hi > 0) "$hi High" else if (me > 0) "$me Medium" else if (frame == V4.off) "No signal" else "All clear", frame) }, back)
        Body {
            Surf(Modifier.fillMaxWidth(), r = 22, border = frame, fill = if (hi > 0) V4.heroRed else V4.surf) {
                X("OFFICER IN CHARGE", 11, FontWeight.ExtraBold, V4.mute)
                Spacer(Modifier.height(8.dp))
                if (head != null) {
                    val pr = ro.people.firstOrNull { it.getString("name") == head.s("name") }
                    Row(Modifier.fillMaxWidth().clickable(enabled = pr != null) { ro.push(Sc.Person(head.s("name"))) }, verticalAlignment = Alignment.CenterVertically) {
                        Av(head.s("name"), 40); Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) { X(head.s("name"), 15, FontWeight.Bold); X(postWord(head.s("post")), 12, FontWeight.Medium, V4.mute) }
                        if (head.s("phone").isNotBlank()) CallBtn(head.s("phone"), true)
                    }
                } else X("No officer in charge is set for this office yet.", 13, FontWeight.Medium, V4.mute)
                if (phone.isNotBlank()) { Spacer(Modifier.height(12.dp)); PBtn("Call station", if (hi > 0) V4.high else V4.acc, if (hi > 0) Color.White else V4.onAcc, Modifier.fillMaxWidth(), Icons.Rounded.Call, 48) { dial(ctx, phone) } }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat("${nodes.size}", "Nodes", "${nodes.count { it.getString("level") == "high" }} High", if (hi > 0) V4.high else V4.text, Modifier.weight(1f), if (hi > 0) V4.high else V4.line) { seg = 0 }
                Stat("${reps.size}", "Reports", "recent", V4.text, Modifier.weight(1f)) { seg = 1 }
                Stat("${k?.getInt("on_duty") ?: 0}", "On duty", "of ${k?.getInt("people") ?: 0}", V4.text, Modifier.weight(1f)) { seg = 2 }
            }
            Segmented(listOf("Nodes", "Reports", "Team"), seg) { seg = it }
            when (seg) {
                0 -> { if (nodes.isEmpty()) X("No nodes are set for this office yet.", 14, FontWeight.Medium, V4.mute); nodes.forEach { NodeRow(it, ro) } }
                1 -> { if (reps.isEmpty()) X("No reports yet.", 14, FontWeight.Medium, V4.mute); reps.forEach { r -> ReportRow(ro, r) } }
                else -> { if (team.isEmpty()) X("No one is registered under this office yet.", 14, FontWeight.Medium, V4.mute); Surf(Modifier.fillMaxWidth()) { team.forEach { PersonRow(ro, it) } } }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ReportRow(ro: Ro, r: JSONObject) {
    val i = ro.reports.indexOf(r)
    Surf(Modifier.fillMaxWidth(), r = 16, click = { ro.push(Sc.Report(i)) }) {
        Row { X(r.getString("node"), 14, FontWeight.Bold, m = Modifier.weight(1f)); X(hhmm(r.getLong("at")), 12, FontWeight.Medium, V4.mute) }
        X(reportLine(r).replaceFirstChar { it.uppercase() }, 13, FontWeight.Medium)
        val by = r.s("by"); if (by.isNotBlank()) X(by + if (r.s("office").isNotBlank()) " · " + r.s("office") else "", 12, FontWeight.Medium, V4.mute)
    }
}

private class Ev(val t: Long, val label: String, val c: String, val title: String, val sub: String, val office: String, val click: Sc?)

@Composable
private fun Today(ro: Ro) {
    var f by remember { mutableStateOf("All") }
    val evs = remember(ro.d) {
        val l = mutableListOf<Ev>()
        fun sub(vararg a: String) = a.filter { it.isNotBlank() }.joinToString(" · ")
        ro.reports.forEachIndexed { i, r -> l.add(Ev(r.getLong("at"), "REPORT", "acc", "${r.s("by").ifBlank { "A report" }} saw ${herdText(r)}", sub(r.s("note"), r.s("office")), r.s("office"), Sc.Report(i))) }
        val ev = ro.d.optJSONArray("events")?.let { a -> List(a.length()) { a.getJSONObject(it) } } ?: emptyList()
        val place = ro.nodes.associate { it.getString("id") to it.s("place") }
        ev.forEach { e ->
            val lv = e.getString("level"); val id = e.getString("node")
            val (lab, col) = when (lv) { "high" -> "HIGH ALERT" to "high"; "medium" -> "MEDIUM" to "medium"; "offline" -> "NO SIGNAL" to "off"; else -> "BACK TO ${lw(lv).uppercase()}" to "low" }
            l.add(Ev(e.getLong("at"), lab, col, if (lv == "offline") "$id stopped reporting" else "$id is ${lw(lv)}", sub(place[id] ?: "", e.s("office")), e.s("office"), Sc.Alert(id)))
        }
        ro.resp.forEach { r ->
            val who = r.s("name"); val node = r.getString("node"); val off = r.s("office")
            listOf("acc" to "accepted", "way" to "is on the way to", "reached" to "reached").forEach { (k, w) ->
                if (!r.isNull(k)) l.add(Ev(r.getLong(k), "RRT", "acc", "$who $w $node", off, off, Sc.Alert(node)))
            }
        }
        // nodes already at High or Medium before the first recorded change still belong on the list
        ro.nodes.filter { n -> (n.getString("level") == "high" || n.getString("level") == "medium") && ev.none { it.getString("node") == n.getString("id") } }.forEach { n ->
            val lv = n.getString("level"); val t = if (n.isNull("since")) ro.sec(n) else n.getLong("since")
            l.add(Ev(t ?: 0, if (lv == "high") "HIGH ALERT" else "MEDIUM", lv, "${n.getString("id")} is ${lw(lv)}", sub(n.s("place"), n.s("office")), n.s("office"), Sc.Alert(n.getString("id"))))
        }
        l.sortedByDescending { it.t }
    }
    val shown = evs.filter { f == "All" || it.office == f }
    val accC = V4.acc
    val alerts = evs.count { it.label == "HIGH ALERT" || it.label == "MEDIUM" }
    Column(Modifier.fillMaxSize()) {
        TopBrand()
        Body {
            Column { X("Today", 28, FontWeight.ExtraBold); X(SimpleDateFormat("EEEE d MMMM", Locale.getDefault()).format(Date()) + " · ${ro.sum.getInt("reports24")} reports · $alerts alerts", 13, FontWeight.Medium, V4.mute) }
            FilterRow(listOf("All") + ro.kids.map { it.s("name") }, f) { f = it }
            if (shown.isEmpty()) X("Nothing yet today.", 14, FontWeight.Medium, V4.mute)
            shown.forEach { e ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    X(if (e.t > 0) hhmm(e.t) else "--:--", 12, FontWeight.Bold, V4.mute, Modifier.width(44.dp))
                    val ec = if (e.c == "acc") accC else lc(e.c)
                    Box(Modifier.padding(top = 4.dp, end = 10.dp).size(10.dp).clip(CircleShape).background(ec))
                    Surf(Modifier.weight(1f), r = 14, click = e.click?.let { c -> { ro.push(c) } }) { X(e.label, 10, FontWeight.ExtraBold, ec); X(e.title, 14, FontWeight.Bold); if (e.sub.isNotBlank()) X(e.sub, 12, FontWeight.Medium, V4.mute) }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun People(ro: Ro) {
    var seg by remember { mutableIntStateOf(0) }
    var team by remember { mutableIntStateOf(0) }   // 0 stations, 1 the RRT units
    val all = ro.people.filter { it.optBoolean("rrt") == (team == 1) }
    val duty = all.filter { it.optBoolean("duty", true) }
    val on = duty.count { it.getBoolean("on_duty") }
    val list = all.filter { p -> val d = p.optBoolean("duty", true); when (seg) { 0 -> d && p.getBoolean("on_duty"); 1 -> d && !p.getBoolean("on_duty"); else -> true } }
    val groups = list.groupBy { it.s("office").ifBlank { ro.rangeName } }
    Column(Modifier.fillMaxSize()) {
        TopBrand()
        Body {
            Column { X("People", 28, FontWeight.ExtraBold); X("$on on duty · ${duty.size - on} off duty", 13, FontWeight.Medium, V4.mute) }
            Segmented(listOf("Stations", "RRT"), team) { team = it }
            Segmented(listOf("On duty", "Off duty", "All"), seg) { seg = it }
            if (list.isEmpty()) Surf(Modifier.fillMaxWidth(), border = V4.med) {
                X("Nobody is on duty", 18, FontWeight.ExtraBold, V4.med)
                X(if (seg == 0) (if (team == 1) "No RRT member is on duty. Call the RRT unit office." else "No one in ${ro.rangeName} has gone on duty. Call the station offices.") else "Nothing to show here.", 13, FontWeight.Medium, V4.mute)
            }
            groups.forEach { (office, ps) ->
                val d = ps.filter { it.optBoolean("duty", true) }; val o = d.count { it.getBoolean("on_duty") }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row { X(office, 14, FontWeight.Bold, m = Modifier.weight(1f)); X("$o of ${d.size} on duty", 12, FontWeight.Medium, V4.mute) }
                    Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(V4.surf2)) { Box(Modifier.fillMaxWidth(if (d.isNotEmpty()) o.toFloat() / d.size else 0f).fillMaxHeight().background(V4.acc)) }
                    Surf(Modifier.fillMaxWidth()) { ps.forEach { PersonRow(ro, it) } }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun PersonScreen(ro: Ro, name: String, back: () -> Unit) {
    val ctx = LocalContext.current
    val p = ro.people.firstOrNull { it.getString("name") == name }
    Column(Modifier.fillMaxSize()) {
        BackTop("Person", "", null, back)
        if (p == null) { Body { X("This person is no longer in your list.", 14, FontWeight.Medium, V4.mute) }; return@Column }
        val duty = p.optBoolean("duty", true); val on = duty && p.getBoolean("on_duty"); val office = p.s("office")
        Body {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Av(name, 72, if (on) V4.ok else null)
                X(name, 22, FontWeight.ExtraBold)
                X(listOf(postWord(p.s("post")), office).filter { it.isNotBlank() }.joinToString(" · "), 13, FontWeight.Medium, V4.mute)
                if (duty) Chip4(if (on) "On duty" else "Off duty", if (on) V4.ok else V4.off)
            }
            Surf(Modifier.fillMaxWidth()) {
                listOf("Post" to postWord(p.s("post")), "Office" to office.ifBlank { "-" }, "Mobile" to "+91 ${p.s("phone")}").forEach { (a, b) ->
                    Row(Modifier.padding(vertical = 4.dp)) { X(a, 13, FontWeight.Medium, V4.mute, Modifier.weight(1f)); X(b, 14, FontWeight.SemiBold) }
                }
            }
            PBtn("Call $name", V4.acc, V4.onAcc, Modifier.fillMaxWidth(), Icons.Rounded.Call, 58) { dial(ctx, p.getString("phone")) }
            if (office.isNotBlank() && ro.kids.any { it.s("name") == office }) PBtn("Open $office", null, V4.text, Modifier.fillMaxWidth(), null, 52) { ro.push(Sc.Station(office)) }
        }
    }
}

@Composable
private fun AlertScreen(ro: Ro, id: String, back: () -> Unit) {
    val ctx = LocalContext.current
    val n = ro.nodes.firstOrNull { it.getString("id") == id }
    var sig by remember(id) { mutableStateOf<JSONObject?>(null) }
    var tried by remember(id) { mutableStateOf(false) }
    LaunchedEffect(id, ro.now) { Api.node(id) { sig = it ?: sig; tried = true } }
    Column(Modifier.fillMaxSize()) {
        if (n == null) { BackTop("Node", "", null, back); Body { X("This node is not in your range.", 14, FontWeight.Medium, V4.mute) }; return@Column }
        val l = n.getString("level"); val c = lc(l); val office = n.s("office")
        val k = ro.kid(office); val phone = k?.s("phone") ?: ""; val head = k?.optJSONObject("head")
        val reps = ro.reports.filter { it.getString("node") == id }
        val reporter = reps.firstOrNull()?.s("by")?.let { by -> ro.people.firstOrNull { it.getString("name") == by } }
        val t = ro.sec(n)
        BackTop(if (l == "offline") "No signal" else "${lw(l)} alert", office, null, back)
        Column(Modifier.weight(1f)) {
            Body(Modifier.weight(1f)) {
                Surf(Modifier.fillMaxWidth(), r = 22, border = c, fill = if (l == "high") V4.heroRed else V4.surf) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { X(id, 24, FontWeight.ExtraBold); X(listOf(n.s("place"), office).filter { it.isNotBlank() }.joinToString(" · "), 12, FontWeight.Medium, if (l == "high") V4.heroText else V4.mute) }
                        Column(horizontalAlignment = Alignment.End) { X(lw(l), 22, FontWeight.ExtraBold, c); if (t != null) X("last signal ${hhmm(t)}", 11, FontWeight.Medium, if (l == "high") V4.heroText else V4.mute) }
                    }
                }
                Surf(Modifier.fillMaxWidth()) {
                    X("SIGNAL", 11, FontWeight.ExtraBold, V4.mute); Spacer(Modifier.height(8.dp))
                    val sl = sig?.optJSONArray("slices")
                    if (sl != null && sl.length() > 0) {
                        Row(Modifier.fillMaxWidth().height(54.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
                            for (i in 0 until sl.length()) {
                                val lv = sl.getJSONObject(i).getString("level")
                                val hgt = when (lv) { "high" -> 1f; "medium" -> .72f; "low" -> .5f; "safe" -> .3f; else -> .12f }
                                Box(Modifier.weight(1f).fillMaxHeight(hgt).clip(RoundedCornerShape(3.dp)).background(lc(lv)))
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Row { X(hhmm(sl.getJSONObject(0).getLong("t")), 10, FontWeight.Medium, V4.mute, Modifier.weight(1f)); X("now", 10, FontWeight.Medium, V4.mute) }
                        Spacer(Modifier.height(8.dp))
                        val pk = sig?.takeIf { !it.isNull("peak_at") }?.getLong("peak_at"); val sn = sig?.takeIf { !it.isNull("since") }?.getLong("since")
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            X("Strength ${lw(l)}", 12, FontWeight.SemiBold, c)
                            if (pk != null) X("Strongest at ${hhmm(pk)}", 12, FontWeight.Medium, V4.mute)
                            if (sn != null) X("${lw(l)} since ${hhmm(sn)}", 12, FontWeight.Medium, V4.mute)
                        }
                    } else X(if (tried) "The signal strip is not available yet." else "Loading…", 13, FontWeight.Medium, V4.mute)
                }
                val rs = ro.resp.filter { it.getString("node") == id }
                if (rs.isNotEmpty() || l == "high") {
                    X("Responding", 15, FontWeight.Bold)
                    if (rs.isEmpty()) Surf(Modifier.fillMaxWidth(), border = V4.high) { X("Nobody has accepted yet", 14, FontWeight.Bold, V4.high); X("The station and you are reminded after 3 and 6 minutes.", 12, FontWeight.Medium, V4.mute) }
                    rs.forEach { r ->
                        val lat = if (r.isNull("lat")) null else r.getDouble("lat"); val lon = if (r.isNull("lon")) null else r.getDouble("lon")
                        Surf(Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Av(r.s("name"), 40); Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) { X(r.s("name"), 14, FontWeight.SemiBold); X(postWord(r.s("post")) + " · " + stepWord(r.s("state")), 12, FontWeight.Bold, V4.ok) }
                                CallBtn(r.getString("phone"), true)
                            }
                            Spacer(Modifier.height(6.dp))
                            X("Accepted ${hhmm(r.getLong("acc"))}" + (if (!r.isNull("way")) " · On the way ${hhmm(r.getLong("way"))}" else "") + (if (!r.isNull("reached")) " · Reached ${hhmm(r.getLong("reached"))}" else ""), 12, FontWeight.Medium, V4.mute)
                            if (lat != null && lon != null) { Spacer(Modifier.height(8.dp)); PBtn("Show on map · ${hhmm(r.getLong("pos"))}", null, V4.text, Modifier.fillMaxWidth(), Icons.Rounded.Place, 44) { openMap(ctx, lat, lon, r.s("name")) } }
                        }
                    }
                }
                X("What happened", 15, FontWeight.Bold)
                if (reps.isEmpty() && t == null) X("Nothing recorded for this node yet.", 13, FontWeight.Medium, V4.mute)
                reps.forEach { r -> Row { X(hhmm(r.getLong("at")), 12, FontWeight.Bold, V4.mute, Modifier.width(44.dp)); X("${r.s("by").ifBlank { "A report" }} reported ${herdText(r)}${if (r.s("note").isNotBlank()) ", " + r.s("note") else ""}", 13, FontWeight.Medium) } }
                if (t != null) Row { X(hhmm(t), 12, FontWeight.Bold, V4.mute, Modifier.width(44.dp)); X("Last signal from the node (${lw(l)})", 13, FontWeight.Medium) }
                val headP = head?.let { h -> ro.people.firstOrNull { it.getString("name") == h.s("name") } }
                if (headP != null || reporter != null) {
                    X("Call", 15, FontWeight.Bold)
                    Surf(Modifier.fillMaxWidth()) {
                        if (headP != null) PersonRowSub(ro, headP, "Officer in charge · ${postWord(headP.s("post"))}")
                        if (reporter != null && reporter.getString("name") != headP?.getString("name")) PersonRowSub(ro, reporter, "Reported ${hhmm(reps.first().getLong("at"))} · ${postWord(reporter.s("post"))}")
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            if (phone.isNotBlank()) Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) { PBtn("Call station", if (l == "high") V4.high else V4.acc, if (l == "high") Color.White else V4.onAcc, Modifier.fillMaxWidth(), Icons.Rounded.Call, 52) { dial(ctx, phone) } }
        }
    }
}

@Composable
private fun PersonRowSub(ro: Ro, p: JSONObject, sub: String) {
    val on = p.optBoolean("duty", true) && p.getBoolean("on_duty")
    Row(Modifier.fillMaxWidth().clickable { ro.push(Sc.Person(p.getString("name"))) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Av(p.getString("name"), 40, if (on) V4.ok else null); Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) { X(p.getString("name"), 14, FontWeight.SemiBold); X(sub, 12, FontWeight.Medium, V4.mute) }
        CallBtn(p.getString("phone"), on)
    }
}

@Composable
private fun NodesScreen(ro: Ro, start: String, back: () -> Unit) {
    var f by remember { mutableStateOf(start) }
    fun want(l: String) = when (f) { "High" -> l == "high"; "Medium" -> l == "medium"; "No signal" -> l == "offline"; else -> true }
    val shown = ro.nodes.filter { want(it.getString("level")) }
    val groups = shown.groupBy { it.s("office").ifBlank { ro.rangeName } }
    Column(Modifier.fillMaxSize()) {
        BackTop("Nodes", "Across ${ro.rangeName}", null, back)
        Body {
            fun cnt(l: String?) = ro.nodes.count { l == null || it.getString("level") == l }
            val labels = listOf("All" to cnt(null), "High" to cnt("high"), "Medium" to cnt("medium"), "No signal" to cnt("offline")).map { (a, n) -> "$a $n" }
            FilterRow(labels, labels.first { it.substringBeforeLast(" ") == f }) { f = it.substringBeforeLast(" ") }
            if (shown.isEmpty()) X("Nothing here.", 14, FontWeight.Medium, V4.mute)
            groups.forEach { (office, ns) ->
                X("$office · ${ns.size} nodes", 13, FontWeight.Bold, V4.mute)
                ns.forEach { NodeRow(it, ro) }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ReportsScreen(ro: Ro, back: () -> Unit) {
    val groups = ro.reports.groupBy { it.s("office").ifBlank { ro.rangeName } }
    Column(Modifier.fillMaxSize()) {
        BackTop("Reports", "${ro.sum.getInt("reports24")} today", null, back)
        Body {
            if (ro.reports.isEmpty()) X("No reports yet.", 14, FontWeight.Medium, V4.mute)
            groups.forEach { (office, rs) -> X("$office · ${rs.size} reports", 13, FontWeight.Bold, V4.mute); rs.forEach { ReportRow(ro, it) } }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ReportScreen(ro: Ro, i: Int, back: () -> Unit) {
    val ctx = LocalContext.current
    val r = ro.reports.getOrNull(i)
    Column(Modifier.fillMaxSize()) {
        BackTop("Report", r?.s("office") ?: "", null, back)
        if (r == null) { Body { X("Report not found.", 14, FontWeight.Medium, V4.mute) }; return@Column }
        val by = r.s("by"); val who = ro.people.firstOrNull { it.getString("name") == by }
        Body {
            Surf(Modifier.fillMaxWidth(), r = 22) {
                X(herdText(r).replaceFirstChar { it.uppercase() }, 20, FontWeight.ExtraBold)
                X(listOf(r.s("office"), hhmm(r.getLong("at"))).filter { it.isNotBlank() }.joinToString(" · "), 13, FontWeight.Medium, V4.mute)
            }
            Surf(Modifier.fillMaxWidth()) {
                listOf("Node" to r.getString("node"), "Sent by" to by.ifBlank { "-" }, "Count" to "${r.getInt("herd")}", "Seen" to r.s("seen").ifBlank { "-" }).forEach { (a, b) ->
                    Row(Modifier.padding(vertical = 4.dp)) { X(a, 13, FontWeight.Medium, V4.mute, Modifier.weight(1f)); X(b, 14, FontWeight.SemiBold) }
                }
            }
            if (r.s("note").isNotBlank()) Surf(Modifier.fillMaxWidth()) { X("NOTE", 11, FontWeight.ExtraBold, V4.mute); X(r.s("note"), 14) }
            if (who != null) PBtn("Call $by", V4.acc, V4.onAcc, Modifier.fillMaxWidth(), Icons.Rounded.Call, 58) { dial(ctx, who.getString("phone")) }
        }
    }
}

@Composable
private fun Me(ro: Ro) {
    val ctx = LocalContext.current
    val p = Store.profile.value
    var confirm by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        TopBrand()
        Body(Modifier.weight(1f)) {
            X("Me", 28, FontWeight.ExtraBold)
            if (p != null) Surf(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Av(p.name, 48); Spacer(Modifier.width(12.dp))
                    Column { X(p.name, 18, FontWeight.ExtraBold); X("Range Officer · ${ro.rangeName}", 13, FontWeight.Medium, V4.mute); X("+91 ${p.phone}", 13, FontWeight.Medium, V4.mute) }
                }
                Spacer(Modifier.height(8.dp))
                X("${p.range.division} · ${p.range.circle}", 12, FontWeight.Medium, V4.mute)
            }
            val mode by Store.themeMode
            Surf(Modifier.fillMaxWidth()) {
                X("Appearance", 13, FontWeight.SemiBold, V4.mute)
                Spacer(Modifier.height(8.dp))
                Segmented(listOf("Auto", "Light", "Dark"), mode) { Store.setTheme(it) }
                Spacer(Modifier.height(8.dp))
                X("Auto turns dark at sunset and light at sunrise for your station.", 12, FontWeight.Medium, V4.mute)
            }
            X("Your personal dashboard. No duty status and no alerts here.", 12, FontWeight.Medium, V4.mute)
            if (p != null && p.range.phone.isNotBlank()) Surf(Modifier.fillMaxWidth(), click = { dial(ctx, p.range.phone) }) {
                Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.Call, null, tint = V4.acc, modifier = Modifier.size(22.dp)); Spacer(Modifier.width(12.dp)); Column { X("Call your range office", 15, FontWeight.Bold); X("${p.range.name} · ${p.range.phone}", 12, FontWeight.Medium, V4.mute) } }
            }
        }
        Box(Modifier.fillMaxWidth().padding(20.dp)) { PBtn("Sign out", null, V4.high, Modifier.fillMaxWidth(), Icons.Rounded.Logout, 52) { confirm = true } }
        if (confirm) AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Sign out of DharmIS?") },
            text = { Text("You will need your mobile number and PIN to log in again.") },
            confirmButton = { TextButton(onClick = { confirm = false; Store.logout(); AlertService.sync(ctx.applicationContext) }) { Text("Sign out") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Stay logged in") } },
        )
    }
}
