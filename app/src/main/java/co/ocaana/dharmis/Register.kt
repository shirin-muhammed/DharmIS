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

/** Where the person works, sent with the registration. Staff approve it under Users before it gives any access. */
class Place(val post: String, val rrtUnit: Boolean, val section: String, val officeKind: String)

@Composable
fun Register(onLogin: () -> Unit, onDone: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    var q by remember { mutableStateOf("") }
    var range by remember { mutableStateOf<RangeOffice?>(null) }
    var station by remember { mutableStateOf<String?>(null) }
    var typedStation by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("") }
    var inRrt by remember { mutableStateOf<Boolean?>(null) }
    var sectionName by remember { mutableStateOf("") }
    var officeKind by remember { mutableStateOf("Forest station") }
    val post = POST_KEY[role] ?: ""
    val needsPlace = post in setOf("watcher", "beat", "section", "office")
    var pin by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    fun back() { step = if (step == 4 && !needsPlace) 2 else step - 1 }
    BackHandler(enabled = step > 0) { back() }

    Page {
        BackBar(
            when (step) { 0 -> "Your range office"; 1 -> "Your station"; 2 -> "About you"; 3 -> "Your office"; else -> "Choose your PIN" },
            "Step ${step + 1} of 5",
            onBack = { if (step > 0) back() },
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (i in 0..4) Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(if (i <= step) pal.pri else pal.line))
        }
        Spacer(Modifier.height(14.dp))
        when (step) {
            0 -> {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    TextBox(q, "Search range office, division or circle", Icons.Rounded.Search) { q = it }
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.clickable(onClick = onLogin).padding(vertical = 6.dp)) { T("Already registered? ", 15, FontWeight.Medium, pal.ink2); T("Log in", 15, FontWeight.ExtraBold, pal.pri) }
                }
                Spacer(Modifier.height(8.dp))
                val list = remember(q) { Directory.search(q) }
                if (list.isEmpty()) T("No range office matches “$q”.", 15, FontWeight.Medium, pal.ink3, Modifier.padding(20.dp))
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(list) { r ->
                        OptionRow(r.name, r.where, Icons.Rounded.Place, range === r, chevron = true) { range = r; station = null; typedStation = ""; step = 1 }
                    }
                }
            }
            1 -> {
                val r = range!!
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    T(r.name, 15, FontWeight.Bold, pal.ink2)
                    r.stations.forEach { s -> OptionRow(s, null, Icons.Rounded.Home, station == s) { station = s; typedStation = ""; step = 2 } }
                    if (r.stations.isEmpty()) T("This range office has no stations listed. Type yours below.", 14, FontWeight.Medium, pal.ink3)
                    Spacer(Modifier.height(4.dp))
                    T("Not in the list?", 14, FontWeight.Bold, pal.ink2)
                    TextBox(typedStation, "Type your station name") { typedStation = it; station = null }
                    BigButton("Continue", modifier = Modifier.fillMaxWidth(), enabled = typedStation.isNotBlank()) { step = 2 }
                    Spacer(Modifier.height(12.dp))
                }
            }
            2 -> {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Card(Modifier.fillMaxWidth(), bg = pal.pri2, border = null) {
                        T(station ?: typedStation, 16, FontWeight.ExtraBold, pal.pri)
                        T("${range!!.name} · ${range!!.division}", 13, FontWeight.Medium, pal.ink2)
                    }
                    Field("Your name", name, "Full name") { name = it }
                    Field("Mobile number", phone, "10-digit number", KeyboardType.Phone) { phone = it.filter(Char::isDigit).take(10) }
                    T("Your post", 14, FontWeight.Bold, pal.ink2)
                    Chips(ROLES, setOf(role)) { role = it }
                }
                Column(Modifier.padding(20.dp)) {
                    BigButton("Continue", Icons.Rounded.Check, modifier = Modifier.fillMaxWidth(), enabled = name.isNotBlank() && phone.length == 10 && post.isNotEmpty()) { step = if (needsPlace) 3 else 4 }
                }
            }
            3 -> {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (post == "watcher" || post == "beat") {
                        T("Are you part of the RRT unit?", 18, FontWeight.ExtraBold)
                        T("Only the RRT unit gets elephant alerts. If you work for a station or section, answer No.", 14, FontWeight.Medium, pal.ink2)
                        Chips(listOf("Yes", "No"), setOf(if (inRrt == true) "Yes" else if (inRrt == false) "No" else "")) { inRrt = it == "Yes" }
                    }
                    if (post == "office") {
                        T("This phone belongs to a", 18, FontWeight.ExtraBold)
                        Chips(listOf("Forest station", "Section office"), setOf(officeKind)) { officeKind = it }
                    }
                    if (post == "section" || (post == "office" && officeKind == "Section office")) {
                        Field("Name of your section office", sectionName, "e.g. Kuttampuzha section") { sectionName = it }
                    }
                }
                Column(Modifier.padding(20.dp)) {
                    val ok = when (post) { "watcher", "beat" -> inRrt != null; "section" -> sectionName.isNotBlank(); else -> officeKind == "Forest station" || sectionName.isNotBlank() }
                    BigButton("Continue", Icons.Rounded.Check, modifier = Modifier.fillMaxWidth(), enabled = ok) { step = 4 }
                }
            }
            else -> {
                var first by remember { mutableStateOf("") }
                var again by remember { mutableStateOf(false) }
                var mismatch by remember { mutableStateOf(false) }
                val shown = if (again) pin else first
                fun finish(p: String) {
                    busy = true; error = ""
                    Api.signUp(Profile(name.trim(), phone, role, (station ?: typedStation).trim(), range!!), p, Place(post, inRrt == true, sectionName.trim(), if (officeKind == "Section office") "section" else "station")) { e -> busy = false; if (e == null) onDone() else { error = e; again = false; first = ""; pin = "" } }
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
