package co.ocaana.dharmis

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

val ROLES = listOf("RRT member", "Forest Watcher", "Beat Forest Officer", "Section Forest Officer (SFO)", "Deputy Range Officer (station in charge)", "Range Officer (RO)", "Office phone (station or section)")
private val POST_KEY = mapOf(ROLES[0] to "rrt", ROLES[1] to "watcher", ROLES[2] to "beat", ROLES[3] to "section", ROLES[4] to "dro", ROLES[5] to "range", ROLES[6] to "office")

/** What the person chose from the control room's lists (division, range, office, RRT unit). Staff still approve it under Users. */
class Place(val post: String, val divisionId: Int, val rangeId: Int, val unitId: Int, val rrtUnitId: Int)

class Pick(val id: Int, val name: String, val kind: String = "")
class RangePick(val id: Int, val name: String, val offices: List<Pick>)
class DivisionPick(val id: Int, val name: String, val ranges: List<RangePick>, val rrt: List<Pick>)

@Composable
fun Register(onLogin: () -> Unit, onDone: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("") }
    var inRrt by remember { mutableStateOf<Boolean?>(null) }
    var division by remember { mutableStateOf<DivisionPick?>(null) }
    var range by remember { mutableStateOf<RangePick?>(null) }
    var office by remember { mutableStateOf<Pick?>(null) }
    var rrtUnit by remember { mutableStateOf<Pick?>(null) }
    var lists by remember { mutableStateOf<List<DivisionPick>?>(null) }
    var loadError by remember { mutableStateOf("") }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(reload) { loadError = ""; Api.units { l, e -> lists = l; loadError = e } }
    val post = POST_KEY[role] ?: ""
    // The questions depend on the post: a range officer picks a range, a station phone picks a station, and so on.
    val steps = buildList {
        add("you"); add("division")
        if (post != "rrt") add("range")
        if (post in setOf("watcher", "beat", "section", "dro", "office")) add("office")
        if (post == "rrt" || post == "watcher" || post == "beat") add("rrt")
        add("pin")
    }
    val key = steps[step.coerceAtMost(steps.size - 1)]
    var pin by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    BackHandler(enabled = step > 0) { step-- }
    fun next() { step++ }
    val titles = mapOf("you" to "About you", "division" to "Your division", "range" to "Your range office", "office" to "Your station or section", "rrt" to "RRT unit", "pin" to "Choose your PIN")

    Page {
        BackBar(titles[key] ?: "", "Step ${step + 1} of ${steps.size}", onBack = { if (step > 0) step-- })
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (i in steps.indices) Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(if (i <= step) pal.pri else pal.line))
        }
        Spacer(Modifier.height(14.dp))
        when (key) {
            "you" -> {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Field("Your name", name, "Full name") { name = it }
                    Field("Mobile number", phone, "10-digit number", KeyboardType.Phone) { phone = it.filter(Char::isDigit).take(10) }
                    T("Your post", 14, FontWeight.Bold, pal.ink2)
                    Chips(ROLES, setOf(role)) { role = it; office = null; inRrt = null; rrtUnit = null }
                    Row(Modifier.clickable(onClick = onLogin).padding(vertical = 6.dp)) { T("Already registered? ", 15, FontWeight.Medium, pal.ink2); T("Log in", 15, FontWeight.ExtraBold, pal.pri) }
                }
                Column(Modifier.padding(20.dp)) {
                    BigButton("Continue", Icons.Rounded.Check, modifier = Modifier.fillMaxWidth(), enabled = name.isNotBlank() && phone.length == 10 && post.isNotEmpty()) { next() }
                }
            }
            "division" -> PickList(lists?.map { Pick(it.id, it.name) }, loadError, "No division has been set up yet. Ask your control room.", { reload++ }) { p ->
                if (division?.id != p.id) { range = null; office = null; rrtUnit = null }
                division = lists!!.first { it.id == p.id }; next()
            }
            "range" -> PickList(division!!.ranges.map { Pick(it.id, it.name) }, "", "This division has no range office set up yet. Ask your control room.", {}) { p ->
                if (range?.id != p.id) office = null
                range = division!!.ranges.first { it.id == p.id }; next()
            }
            "office" -> PickList(range!!.offices.filter { o -> when (post) { "section" -> o.kind == "section"; "dro" -> o.kind == "station"; else -> true } }, "", "Nothing is set up here yet. Ask your control room.", {}) { p -> office = p; next() }
            "rrt" -> Column(Modifier.weight(1f).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (post != "rrt") {
                    T("Are you part of the RRT unit?", 18, FontWeight.ExtraBold)
                    T("Only the RRT unit gets elephant alerts. If you work for a station or section, answer No.", 14, FontWeight.Medium, pal.ink2)
                    Chips(listOf("Yes", "No"), setOf(if (inRrt == true) "Yes" else if (inRrt == false) "No" else "")) { inRrt = it == "Yes"; if (!inRrt!!) rrtUnit = null }
                }
                if (post == "rrt" || inRrt == true) {
                    val units = division!!.rrt
                    if (units.isEmpty()) T("No RRT unit has been set up for this division yet. Ask your control room.", 14, FontWeight.Medium, pal.ink3)
                    units.forEach { u -> OptionRow(u.name, null, Icons.Rounded.Home, rrtUnit?.id == u.id) { rrtUnit = u } }
                }
                Spacer(Modifier.weight(1f))
                val ok = if (post == "rrt" || inRrt == true) rrtUnit != null else inRrt == false
                BigButton("Continue", Icons.Rounded.Check, modifier = Modifier.fillMaxWidth(), enabled = ok) { next() }
                Spacer(Modifier.height(20.dp))
            }
            else -> {
                var first by remember { mutableStateOf("") }
                var again by remember { mutableStateOf(false) }
                var mismatch by remember { mutableStateOf(false) }
                val shown = if (again) pin else first
                fun finish(p: String) {
                    busy = true; error = ""
                    Api.signUp(Profile(name.trim(), phone, role, office?.name ?: rrtUnit?.name ?: "", RangeOffice(range?.name ?: "", "", emptyList(), division!!.name, "")), p, Place(post, division!!.id, range?.id ?: 0, office?.id ?: 0, if (post == "rrt" || inRrt == true) rrtUnit?.id ?: 0 else 0)) { e -> busy = false; if (e == null) onDone() else { error = e; again = false; first = ""; pin = "" } }
                }
                fun press(d: Char) {
                    if (busy) return
                    mismatch = false
                    val cur = if (again) pin else first
                    if (cur.length >= 4) return
                    val n = cur + d
                    if (!again) { first = n; if (n.length == 4) again = true; return }
                    pin = n
                    if (n.length == 4) { if (n == first) finish(n) else { mismatch = true; pin = ""; first = ""; again = false } }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(18.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                    T(if (again) "Enter it again" else "Choose a 4-digit PIN", 24, FontWeight.ExtraBold)
                    T("With your mobile number, it lets you log back in if you change or reinstall the phone. Do not share it.", 14, FontWeight.Medium, pal.ink2)
                    PinDots(shown, mismatch)
                    if (mismatch) T("The two PINs did not match. Start again.", 14, FontWeight.Bold, pal.red)
                    if (error.isNotEmpty()) T(error, 14, FontWeight.Bold, pal.red)
                    if (busy) T("Registering…", 14, FontWeight.Bold, pal.ink3)
                    NumPad(::press) { if (!busy) { if (again) pin = pin.dropLast(1) else first = first.dropLast(1) } }
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}

/** A list of things the control room has built. items null means still loading. */
@Composable
private fun ColumnScope.PickList(items: List<Pick>?, error: String, empty: String, retry: () -> Unit, onPick: (Pick) -> Unit) {
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when {
            error.isNotEmpty() -> { T(error, 15, FontWeight.Bold, pal.red); BigButton("Try again", null, 1, Modifier.fillMaxWidth(), onClick = retry) }
            items == null -> T("Loading…", 15, FontWeight.Medium, pal.ink3)
            items.isEmpty() -> T(empty, 15, FontWeight.Medium, pal.ink3)
            else -> items.forEach { p -> OptionRow(p.name, if (p.kind.isNotEmpty()) p.kind.replaceFirstChar { it.uppercase() } else null, Icons.Rounded.Place, false, chevron = true) { onPick(p) } }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
fun Welcome(onLogin: () -> Unit, onRegister: () -> Unit) {
    Page {
        Column(Modifier.weight(1f).padding(horizontal = 24.dp), verticalArrangement = Arrangement.Center) {
            T("Elephant alerts for your response team", 30, FontWeight.ExtraBold)
            Spacer(Modifier.height(10.dp))
            T("Get an alert the moment a node raises one, and report what you see in a few taps.", 16, FontWeight.Medium, pal.ink2)
        }
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            BigButton("Log in", Icons.Rounded.Check, modifier = Modifier.fillMaxWidth(), onClick = onLogin)
            BigButton("I am new, register", null, 1, Modifier.fillMaxWidth(), onClick = onRegister)
        }
    }
}

@Composable
fun Login(onBack: () -> Unit, onDone: () -> Unit) {
    var phone by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    BackHandler(onBack = onBack)
    fun go() {
        busy = true; error = ""
        Api.logIn(phone, pin) { e -> busy = false; if (e == null) onDone() else { error = e; pin = "" } }
    }
    Page {
        BackBar("Log in", null, onBack = onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
            Column(Modifier.fillMaxWidth()) { Field("Mobile number", phone, "10-digit number", KeyboardType.Phone) { phone = it.filter(Char::isDigit).take(10) } }
            T("Your PIN", 14, FontWeight.Bold, pal.ink2, Modifier.fillMaxWidth())
            PinDots(pin, error.isNotEmpty())
            if (error.isNotEmpty()) T(error, 14, FontWeight.Bold, pal.red)
            if (busy) T("Logging in…", 14, FontWeight.Bold, pal.ink3)
            NumPad({ d -> if (!busy && pin.length < 4) { pin += d; error = ""; if (pin.length == 4 && phone.length == 10) go() } }) { if (!busy) pin = pin.dropLast(1) }
            T("Forgot your PIN? Ask your control room to reset it.", 13, FontWeight.Medium, pal.ink3)
            Spacer(Modifier.height(12.dp))
        }
    }
}
