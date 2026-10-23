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

val ROLES = listOf("Forest Watcher", "Beat Forest Officer", "Section Forest Officer", "Range Officer", "RRT member")

@Composable
fun Register(onLogin: () -> Unit, onDone: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    var q by remember { mutableStateOf("") }
    var range by remember { mutableStateOf<RangeOffice?>(null) }
    var station by remember { mutableStateOf<String?>(null) }
    var typedStation by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("RRT member") }
    var pin by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    BackHandler(enabled = step > 0) { step-- }

    Page {
        BackBar(
            when (step) { 0 -> "Your range office"; 1 -> "Your station"; else -> "About you" },
            "Step ${step + 1} of 3",
            onBack = { if (step > 0) step-- },
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (i in 0..2) Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(if (i <= step) pal.pri else pal.line))
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
            else -> {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Card(Modifier.fillMaxWidth(), bg = pal.pri2, border = null) {
                        T(station ?: typedStation, 16, FontWeight.ExtraBold, pal.pri)
                        T("${range!!.name} · ${range!!.division}", 13, FontWeight.Medium, pal.ink2)
                    }
                    Field("Your name", name, "Full name") { name = it }
                    Field("Mobile number", phone, "10-digit number", KeyboardType.Phone) { phone = it.filter(Char::isDigit).take(10) }
                    T("Your role", 14, FontWeight.Bold, pal.ink2)
                    Chips(ROLES, setOf(role)) { role = it }
                    Field("Choose a 4-digit PIN", pin, "4 digits", KeyboardType.NumberPassword) { pin = it.filter(Char::isDigit).take(4) }
                    T("You will need this PIN, with your mobile number, to log in again if you change or reinstall the phone.", 12, FontWeight.Medium, pal.ink3)
                    if (error.isNotEmpty()) T(error, 14, FontWeight.Bold, pal.red)
                }
                Column(Modifier.padding(20.dp)) {
                    BigButton("Start using DharmIS", Icons.Rounded.Check, modifier = Modifier.fillMaxWidth(), enabled = !busy && name.isNotBlank() && phone.length == 10 && pin.length == 4) {
                        busy = true; error = ""
                        Api.signUp(Profile(name.trim(), phone, role, (station ?: typedStation).trim(), range!!), pin) { e -> busy = false; if (e == null) onDone() else error = e }
                    }
                }
            }
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
    Page {
        BackBar("Log in", "Use the number and PIN you registered with", onBack = onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Field("Mobile number", phone, "10-digit number", KeyboardType.Phone) { phone = it.filter(Char::isDigit).take(10) }
            Field("PIN", pin, "4 digits", KeyboardType.NumberPassword) { pin = it.filter(Char::isDigit).take(4) }
            if (error.isNotEmpty()) T(error, 14, FontWeight.Bold, pal.red)
            T("Forgot your PIN? Ask your control room to reset it.", 13, FontWeight.Medium, pal.ink3)
        }
        Column(Modifier.padding(20.dp)) {
            BigButton("Log in", Icons.Rounded.Check, modifier = Modifier.fillMaxWidth(), enabled = !busy && phone.length == 10 && pin.length == 4) {
                busy = true; error = ""
                Api.logIn(phone, pin) { e -> busy = false; if (e == null) onDone() else error = e }
            }
        }
    }
}
