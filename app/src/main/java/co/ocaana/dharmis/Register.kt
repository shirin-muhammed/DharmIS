package co.ocaana.dharmis

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

val ROLES = listOf("Range Officer", "Deputy Range Forest Officer", "Section Forest Officer", "Beat Forest Officer", "Forest Watcher", "Office Phone")
private val POST_KEY = mapOf(ROLES[0] to "range", ROLES[1] to "dro", ROLES[2] to "section", ROLES[3] to "beat", ROLES[4] to "watcher", ROLES[5] to "office")

/** What the person chose from the control room's lists (division, range, office, RRT unit). Staff still approve it under Users. */
class Place(val post: String, val divisionId: Int, val rangeId: Int, val unitId: Int, val rrtUnitId: Int)

class Pick(val id: Int, val name: String, val kind: String = "")
class RangePick(val id: Int, val name: String, val offices: List<Pick>)
class DivisionPick(val id: Int, val name: String, val ranges: List<RangePick>, val rrt: List<Pick>)

@Composable
fun Register(onBack: () -> Unit, onDone: () -> Unit) {
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
        add("post"); add("division")
        if (post != "rrt") add("range")
        if (post in setOf("watcher", "beat", "section", "dro", "office")) add("office")
        if (post in setOf("dro", "section", "beat", "watcher")) add("rrt")
        add("you"); add("pin")
    }
    val key = steps[step.coerceAtMost(steps.size - 1)]
    var pin by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    BackHandler { if (step > 0) step-- else onBack() }
    fun next() { step++ }
    val titles = mapOf(
        "post" to ("Who are you?" to "Choose your post. Your control room confirms you before alerts start."),
        "division" to ("Your division" to "Pick the division you work in."),
        "range" to ("Your range" to "Only ranges your control room has built are listed."),
        "office" to (if (post == "office") "Which office is this phone?" to "The phone is the office. It gets alerts like an RRT member and reports to the officer in charge." else "Your station or section" to "Only offices built under Users appear here."),
        "rrt" to ("Are you in the RRT unit?" to "Members of the RRT unit get alerts on this phone."),
        "you" to (if (post == "office") "Office phone number" to "Your control room confirms the phone before alerts start." else "About you" to "Your control room confirms you before alerts start."),
        "pin" to ("Choose your PIN" to "With your mobile number, it lets you log back in."),
    )

    Page(header = false) {
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (i in steps.indices) Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(if (i <= step) pal.pri else pal.line))
        }
        Spacer(Modifier.height(14.dp))
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            T(titles[key]!!.first, 30, FontWeight.ExtraBold, lh = 36)
            T(titles[key]!!.second, 15, FontWeight.Medium, pal.ink2, lh = 22)
        }
        Spacer(Modifier.height(14.dp))
        @Composable fun NextButton(enabled: Boolean, label: String = "Next") {
            Column(Modifier.padding(20.dp)) { BigButton(label, null, modifier = Modifier.fillMaxWidth(), enabled = enabled) { next() } }
        }
        when (key) {
            "post" -> {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ROLES.forEach { r -> OptionRow(r, null, null, role == r, chevron = true) { if (role != r) { office = null; inRrt = null; rrtUnit = null }; role = r } }
                }
                NextButton(role.isNotEmpty())
            }
            "you" -> {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (post != "office") Field("Full name", name, "Full name") { name = it }
                    Field(if (post == "office") "Office mobile number" else "Mobile number", phone, "10-digit number", KeyboardType.Phone) { phone = it.filter(Char::isDigit).take(10) }
                    Card(Modifier.fillMaxWidth(), bg = pal.pri2, border = null) {
                        T("You will report from", 13, FontWeight.Bold, pal.pri)
                        T(office?.name ?: range?.name ?: division?.name ?: "", 17, FontWeight.ExtraBold)
                        T(listOfNotNull(if (office != null) range?.name else null, division?.name).joinToString(" · "), 13, FontWeight.Medium, pal.ink2, lh = 19)
                    }
                }
                NextButton((post == "office" || name.isNotBlank()) && phone.length == 10)
            }
            "division" -> {
                PickList(lists?.map { Pick(it.id, it.name) }, loadError, "No division has been set up yet. Ask your control room.", { reload++ }, division?.id) { p ->
                    if (division?.id != p.id) { range = null; office = null; rrtUnit = null }
                    division = lists!!.first { it.id == p.id }
                }
                NextButton(division != null)
            }
            "range" -> {
                PickList(division!!.ranges.map { Pick(it.id, it.name, division!!.name) }, "", "This division has no range office set up yet. Ask your control room.", {}, range?.id) { p ->
                    if (range?.id != p.id) office = null
                    range = division!!.ranges.first { it.id == p.id }
                }
                NextButton(range != null)
            }
            "office" -> {
                PickList(range!!.offices.filter { o -> when (post) { "section" -> o.kind == "section"; "dro" -> o.kind == "station"; else -> true } }.map { Pick(it.id, it.name, range!!.name) }, "", "Nothing is set up here yet. Ask your control room.", {}, office?.id) { p -> office = p }
                NextButton(office != null)
            }
            "rrt" -> {
                val units = division!!.rrt
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // one RRT unit per division is the usual case: Yes joins it without asking
                    OptionRow("Yes, I am in the RRT unit", null, null, inRrt == true, chevron = true) { inRrt = true; rrtUnit = units.singleOrNull() }
                    OptionRow("No, I am not", null, null, inRrt == false, chevron = true) { inRrt = false; rrtUnit = null }
                    if (inRrt == true && units.isEmpty()) T("No RRT unit has been set up for this division yet. Ask your control room.", 14, FontWeight.Medium, pal.ink3)
                    if (inRrt == true && units.size > 1) { T("Which RRT unit?", 14, FontWeight.Bold, pal.ink2); units.forEach { u -> OptionRow(u.name, null, null, rrtUnit?.id == u.id) { rrtUnit = u } } }
                    if (inRrt == true && units.size == 1) Card(Modifier.fillMaxWidth(), bg = pal.pri2, border = null) {
                        T("You will join", 13, FontWeight.Bold, pal.pri)
                        T(units[0].name, 17, FontWeight.ExtraBold)
                        T("Your division has one RRT unit, so you join it automatically.", 13, FontWeight.Medium, pal.ink2, lh = 19)
                    }
                }
                NextButton(inRrt == false || (inRrt == true && rrtUnit != null))
            }
            else -> {
                var first by remember { mutableStateOf("") }
                var again by remember { mutableStateOf(false) }
                var mismatch by remember { mutableStateOf(false) }
                val shown = if (again) pin else first
                fun finish(p: String) {
                    busy = true; error = ""
                    Api.signUp(Profile(if (post == "office") office!!.name else name.trim(), phone, role, office?.name ?: rrtUnit?.name ?: "", RangeOffice(range?.name ?: "", "", emptyList(), division!!.name, "")), p, Place(post, division!!.id, range?.id ?: 0, office?.id ?: 0, if (inRrt == true) rrtUnit?.id ?: 0 else 0)) { e -> busy = false; if (e == null) onDone() else { error = e; again = false; first = ""; pin = "" } }
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
                    T(if (again) "Enter it again" else "4-digit PIN", 14, FontWeight.Bold, pal.ink2, Modifier.fillMaxWidth())
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
private fun ColumnScope.PickList(items: List<Pick>?, error: String, empty: String, retry: () -> Unit, selected: Int?, onPick: (Pick) -> Unit) {
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        when {
            error.isNotEmpty() -> { T(error, 15, FontWeight.Bold, pal.red); BigButton("Try again", null, 1, Modifier.fillMaxWidth(), onClick = retry) }
            items == null -> T("Loading…", 15, FontWeight.Medium, pal.ink3)
            items.isEmpty() -> T(empty, 15, FontWeight.Medium, pal.ink3)
            else -> items.forEach { p -> OptionRow(p.name, p.kind.ifEmpty { null }, null, selected == p.id, chevron = true) { onPick(p) } }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
fun Welcome(onLogin: () -> Unit, onRegister: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val logo = remember { android.graphics.BitmapFactory.decodeStream(ctx.assets.open("logo.png"), null, android.graphics.BitmapFactory.Options().apply { inSampleSize = 2 })!!.asImageBitmap() }
    val deep = androidx.compose.ui.graphics.Color(0xFF11201A); val gold = androidx.compose.ui.graphics.Color(0xFFD9B95B); val soft = androidx.compose.ui.graphics.Color(0xFF9AA8A0)
    Column(Modifier.fillMaxSize().background(deep).statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
        Column(Modifier.weight(1f), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            androidx.compose.foundation.Image(logo, "OCAANA", Modifier.width(150.dp).aspectRatio(logo.width.toFloat() / logo.height))
            Spacer(Modifier.height(8.dp))
            T("DharmIS", 40, FontWeight.ExtraBold, androidx.compose.ui.graphics.Color.White)
            T("Tap. Report. Protect.", 16, FontWeight.Medium, gold)
            Spacer(Modifier.height(32.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                WelcomeButton("Log in", gold, deep, null, onLogin)
                WelcomeButton("Register", androidx.compose.ui.graphics.Color.White, androidx.compose.ui.graphics.Color(0xFF17261E), androidx.compose.ui.graphics.Color(0xFFDCD6C8), onRegister)
            }
        }
        T("OCAANA by ONTAGO · Kerala Forest Department", 12, FontWeight.Medium, soft, Modifier.padding(vertical = 20.dp))
    }
}

@Composable
private fun WelcomeButton(text: String, bg: androidx.compose.ui.graphics.Color, fg: androidx.compose.ui.graphics.Color, border: androidx.compose.ui.graphics.Color?, onClick: () -> Unit) {
    val shape = RoundedCornerShape(30.dp)
    Row(Modifier.fillMaxWidth().height(60.dp).clip(shape).background(bg).let { if (border != null) it.border(androidx.compose.foundation.BorderStroke(2.dp, border), shape) else it }.clickable(onClick = onClick), horizontalArrangement = Arrangement.Center, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { T(text, 17, FontWeight.Bold, fg) }
}

@Composable
fun Login(onBack: () -> Unit, onDone: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val logo = remember { android.graphics.BitmapFactory.decodeStream(ctx.assets.open("logo.png"), null, android.graphics.BitmapFactory.Options().apply { inSampleSize = 4 })!!.asImageBitmap() }
    var phone by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    BackHandler(onBack = onBack)
    fun go() {
        busy = true; error = ""
        Api.logIn(phone, pin) { e -> busy = false; if (e == null) onDone() else { error = e; pin = "" } }
    }
    Page(header = false) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
            androidx.compose.foundation.Image(logo, "OCAANA", Modifier.padding(top = 16.dp).width(90.dp).aspectRatio(logo.width.toFloat() / logo.height))
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                T("Log in", 30, FontWeight.ExtraBold, lh = 36)
                T("Use the mobile number and PIN you registered with.", 15, FontWeight.Medium, pal.ink2, lh = 22)
            }
            Column(Modifier.fillMaxWidth()) { Field("Mobile number", phone, "10-digit number", KeyboardType.Phone) { phone = it.filter(Char::isDigit).take(10) } }
            T("PIN", 14, Bold, pal.ink2, Modifier.fillMaxWidth())
            PinDots(pin, error.isNotEmpty())
            if (error.isNotEmpty()) T(error, 14, Bold, pal.red)
            if (busy) T("Logging in…", 14, Bold, pal.ink3)
            NumPad({ d -> if (!busy && pin.length < 4) { pin += d; error = ""; if (pin.length == 4 && phone.length == 10) go() } }) { if (!busy) pin = pin.dropLast(1) }
            T("Forgot your PIN? Ask your control room to reset it.", 13, FontWeight.Medium, pal.ink3)
            Spacer(Modifier.height(12.dp))
        }
    }
}
