package co.ocaana.dharmis

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

class Draft {
    var seen by mutableStateOf<String?>(null)
    var adults by mutableIntStateOf(0)
    var calves by mutableIntStateOf(0)
    var tuskers by mutableIntStateOf(0)
    var direction by mutableStateOf<String?>(null)
    var distance by mutableStateOf<Int?>(null)
    var distanceLabel by mutableStateOf("")
    var activity by mutableStateOf<Set<String>>(emptySet())
    var photoId by mutableStateOf<String?>(null)
    var photoKb by mutableIntStateOf(0)
    var note by mutableStateOf("")
    val herd get() = adults + calves + tuskers
    fun reset() { seen = null; adults = 0; calves = 0; tuskers = 0; direction = null; distance = null; distanceLabel = ""; activity = emptySet(); photoId = null; photoKb = 0; note = "" }
}

val SEEN = listOf(
    Triple("I can see them", "Right now, with my own eyes", Icons.Rounded.Sensors),
    Triple("Tracks or dung", "Fresh signs only", Icons.Rounded.Place),
    Triple("Camera image", "From a camera or phone", Icons.Rounded.CameraAlt),
    Triple("Someone told me", "Not seen by me", Icons.Rounded.Person),
    Triple("No elephants here", "False alarm", Icons.Rounded.Close),
)
val ACTIVITIES = listOf("Standing in forest", "Walking to village", "In crop field", "Crossing road", "Calves with herd", "Aggressive")
val DIST_BANDS = listOf("Under 100 m" to 50, "100–300 m" to 200, "300 m – 1 km" to 650, "Over 1 km" to 1500)

fun compressPhoto(src: File, dst: File): Boolean {
    return try {
        val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(src.path, b)
        var sample = 1
        while (maxOf(b.outWidth, b.outHeight) / sample > 2048) sample *= 2
        var bmp = BitmapFactory.decodeFile(src.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return false
        val rot = when (ExifInterface(src.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)) { 6 -> 90f; 3 -> 180f; 8 -> 270f; else -> 0f }
        val scale = 1024f / maxOf(bmp.width, bmp.height)
        val m = Matrix().apply { if (scale < 1f) postScale(scale, scale); if (rot != 0f) postRotate(rot) }
        bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        dst.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 60, it) }
        src.delete()
        true
    } catch (_: Exception) { false }
}

@Composable
fun Bubble(text: String, mine: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        val shape = RoundedCornerShape(topStart = if (mine) 18.dp else 4.dp, topEnd = if (mine) 4.dp else 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp)
        Box(
            Modifier.widthIn(max = 280.dp).clip(shape).background(if (mine) pal.pri else pal.card)
                .let { if (mine) it else it.border(BorderStroke(1.dp, pal.line), shape) }.padding(horizontal = 16.dp, vertical = 10.dp),
        ) { T(text, 15, if (mine) FontWeight.Bold else FontWeight.SemiBold, if (mine) pal.onPri else pal.ink) }
    }
}

@Composable
fun Chat(alert: Alert, draft: Draft, startStep: Int, onClose: () -> Unit, onReview: () -> Unit) {
    val ctx = LocalContext.current
    var step by remember { mutableIntStateOf(startStep) }
    var distRight by remember { mutableStateOf<Boolean?>(null) }
    var measuring by remember { mutableStateOf(false) }
    val heading by rememberHeading(measuring)
    val loc by Loc.last
    val gpsDist: Float? = loc?.let { l -> val r = FloatArray(1); android.location.Location.distanceBetween(l.latitude, l.longitude, alert.lat, alert.lon, r); r[0] }
    BackHandler { if (step > 0) step-- else onClose() }

    val photoFile = remember { File(ctx.cacheDir, "shots").apply { mkdirs() }.let { File(it, "shot.jpg") } }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) {
            val id = UUID.randomUUID().toString().take(8)
            val dst = File(Store.photoDir, "$id.jpg")
            if (compressPhoto(photoFile, dst)) { draft.photoId = id; draft.photoKb = (dst.length() / 1024).toInt().coerceAtLeast(1) }
            else Toast.makeText(ctx, "Could not save the photo", Toast.LENGTH_SHORT).show()
        }
    }
    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            val t = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!t.isNullOrBlank()) draft.note = if (draft.note.isBlank()) t else draft.note + " " + t
        }
    }

    Page {
        BackBar("Report", "Saved on this phone", onBack = { if (step > 0) step-- else onClose() })
        Row(Modifier.fillMaxWidth().background(pal.red2).padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.clip(RoundedCornerShape(6.dp)).background(pal.red).padding(horizontal = 8.dp, vertical = 2.dp)) { T(alert.level, 11, FontWeight.ExtraBold, pal.onRed) }
            Spacer(Modifier.width(8.dp))
            T("${alert.node} · ${alert.place}", 13, FontWeight.Bold, pal.redText)
        }
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (i in 0..5) Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(if (i <= step) pal.pri else pal.line))
        }
        val scroll = rememberScrollState()
        LaunchedEffect(step) { scroll.animateScrollTo(scroll.maxValue) }
        Column(Modifier.weight(1f).verticalScroll(scroll).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (step >= 0) Bubble("Are elephants there right now?", false)
            if (step >= 1) Bubble(draft.seen ?: "", true)
            if (step >= 1) Bubble("Roughly how many?", false)
            if (step >= 2) Bubble("${draft.herd} · ${draft.calves} calves · ${draft.tuskers} tusker${if (draft.tuskers == 1) "" else "s"}", true)
            if (step >= 2) Bubble("Which way are they moving?", false)
            if (step >= 3) Bubble(draft.direction ?: "Not sure", true)
            if (step >= 3) Bubble(if (gpsDist != null && distRight != false) "You are about ${kmText(gpsDist)} from ${alert.node} (GPS). Right?" else "How far are they from ${alert.node}?", false)
            if (step >= 4) Bubble(draft.distanceLabel, true)
            if (step >= 4) Bubble("What are they doing?", false)
            if (step >= 5) Bubble(draft.activity.joinToString(", ").ifEmpty { "Not sure" }, true)
            if (step >= 5) Bubble("Anything to add? Both are optional.", false)
        }
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)).background(pal.card).border(BorderStroke(1.dp, pal.line), RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when (step) {
                0 -> {
                    T("Tap one", 13, FontWeight.Bold, pal.ink3)
                    SEEN.forEach { (t, s, ic) ->
                        OptionRow(t, s, ic, draft.seen == t) {
                            draft.seen = t
                            if (t == "No elephants here") { draft.adults = 0; draft.calves = 0; draft.tuskers = 0; draft.direction = "Not sure"; draft.distance = null; draft.distanceLabel = "Not applicable"; onReview() } else step = 1
                        }
                    }
                }
                1 -> {
                    T("Tap + or −, or pick a size", 13, FontWeight.Bold, pal.ink3)
                    val sizes = listOf("1" to 1, "2–5" to 3, "6–10" to 8, "10+" to 12)
                    Chips(sizes.map { it.first }, sizes.filter { it.second == draft.adults && draft.calves == 0 && draft.tuskers == 0 }.map { it.first }.toSet()) { s -> draft.adults = sizes.first { it.first == s }.second; draft.calves = 0; draft.tuskers = 0 }
                    Counter("Adults", "Grown elephants", draft.adults) { draft.adults = it }
                    Counter("Calves", "Young ones", draft.calves) { draft.calves = it }
                    Counter("Tuskers", "Bulls with tusks", draft.tuskers) { draft.tuskers = it }
                    BigButton("Next", enabled = draft.herd > 0, modifier = Modifier.fillMaxWidth()) { step = 2 }
                }
                2 -> {
                    T("Tap a direction, or use your compass", 13, FontWeight.Bold, pal.ink3)
                    val grid = listOf(listOf("NW", "N", "NE"), listOf("W", "Not sure", "E"), listOf("SW", "S", "SE"))
                    grid.forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { d ->
                                val on = draft.direction == d
                                Box(
                                    Modifier.weight(1f).height(64.dp).clip(RoundedCornerShape(22.dp)).background(if (on) pal.pri else pal.bg).border(BorderStroke(1.dp, if (on) pal.pri else pal.line), RoundedCornerShape(22.dp))
                                        .clickable { draft.direction = d; measuring = false }, contentAlignment = Alignment.Center,
                                ) { T(d, if (d == "Not sure") 13 else 20, FontWeight.ExtraBold, if (on) pal.onPri else pal.ink) }
                            }
                        }
                    }
                    if (measuring) {
                        val h = heading
                        Card(Modifier.fillMaxWidth(), bg = pal.pri2, border = pal.pri) {
                            T(if (h == null) "Hold the phone upright and point the back at the elephants…" else "${cardinal(h.toDouble())} · ${h.toInt()}°", if (h == null) 14 else 22, FontWeight.ExtraBold, pal.pri)
                        }
                        BigButton("Use this direction", Icons.Rounded.Check, modifier = Modifier.fillMaxWidth(), enabled = h != null) { draft.direction = cardinal(h!!.toDouble()); measuring = false }
                    } else BigButton("Aim phone at them to measure", Icons.Rounded.Explore, 1, Modifier.fillMaxWidth()) { measuring = true }
                    BigButton("Next", enabled = draft.direction != null, modifier = Modifier.fillMaxWidth()) { step = 3 }
                }
                3 -> {
                    if (gpsDist != null && distRight != false) {
                        T("From your phone GPS", 13, FontWeight.Bold, pal.ink3)
                        BigButton("Yes, about ${kmText(gpsDist)}", Icons.Rounded.Check, modifier = Modifier.fillMaxWidth()) { draft.distance = gpsDist.toInt(); draft.distanceLabel = "About ${kmText(gpsDist)} (GPS)"; step = 4 }
                        BigButton("Not right, I will pick", null, 1, Modifier.fillMaxWidth()) { distRight = false }
                    } else {
                        T(if (gpsDist == null) "No GPS yet. Pick how far they are" else "Pick how far they are", 13, FontWeight.Bold, pal.ink3)
                        DIST_BANDS.forEach { (l, m) -> OptionRow(l, null, Icons.Rounded.Place, draft.distanceLabel == l) { draft.distance = m; draft.distanceLabel = l; step = 4 } }
                    }
                }
                4 -> {
                    T("Pick all that apply", 13, FontWeight.Bold, pal.ink3)
                    Chips(ACTIVITIES, draft.activity) { a -> draft.activity = if (a in draft.activity) draft.activity - a else draft.activity + a }
                    BigButton("Next", modifier = Modifier.fillMaxWidth()) { step = 5 }
                }
                else -> {
                    OptionRow(if (draft.photoId == null) "Add a photo" else "Photo added · ${draft.photoKb} KB", "Saved small, about 40–150 KB", Icons.Rounded.CameraAlt, draft.photoId != null) {
                        try { camera.launch(FileProvider.getUriForFile(ctx, ctx.packageName + ".files", photoFile)) } catch (_: Exception) { Toast.makeText(ctx, "No camera available", Toast.LENGTH_SHORT).show() }
                    }
                    OptionRow(if (draft.note.isBlank()) "Add a voice note" else "Voice note added", "Spoken, turned into text for you to check", Icons.Rounded.Mic, draft.note.isNotBlank()) {
                        try {
                            speech.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM).putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true).putExtra(RecognizerIntent.EXTRA_PROMPT, "Say anything we should know"))
                        } catch (_: ActivityNotFoundException) { Toast.makeText(ctx, "Speech input is not available on this phone. Type the note instead.", Toast.LENGTH_LONG).show() }
                    }
                    T("Note (optional, tap to type or fix the text)", 13, FontWeight.Bold, pal.ink3)
                    TextBox(draft.note, "Anything else we should know") { draft.note = it }
                    BigButton(if (draft.photoId == null && draft.note.isBlank()) "Skip, review report" else "Review report", modifier = Modifier.fillMaxWidth()) { onReview() }
                }
            }
        }
    }
}

fun cardinalLabel(d: String?) = when (d) { null, "Not sure" -> "Not sure"; else -> d }

@Composable
fun Review(alert: Alert, draft: Draft, onChange: () -> Unit, onBack: () -> Unit, onSaved: (String) -> Unit) {
    BackHandler(onBack = onBack)
    Page {
        BackBar("Check your report", "Last step", Icons.Rounded.Close, onBack = onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(Modifier.fillMaxWidth(), pad = 18.dp) {
                T("${alert.node} · now", 17, FontWeight.ExtraBold)
                Spacer(Modifier.height(10.dp))
                val rows = buildList {
                    add("Seen" to (draft.seen ?: ""))
                    if (draft.herd > 0) add("Herd" to "${draft.herd}: ${draft.adults} adults, ${draft.calves} calves, ${draft.tuskers} tusker${if (draft.tuskers == 1) "" else "s"}")
                    add("Moving" to cardinalLabel(draft.direction))
                    add("Distance" to draft.distanceLabel)
                    if (draft.activity.isNotEmpty()) add("Doing" to draft.activity.joinToString(", "))
                    if (draft.photoId != null) add("Photo" to "1 photo · ${draft.photoKb} KB")
                    if (draft.note.isNotBlank()) add("Note" to draft.note)
                }
                rows.forEach { (k, v) -> Row(Modifier.padding(vertical = 5.dp)) { T(k, 13, FontWeight.Bold, pal.ink3, Modifier.width(70.dp)); T(v, 15, FontWeight.Bold, pal.ink, Modifier.weight(1f)) } }
            }
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(pal.amb2).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CloudOff, null, tint = pal.amb, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(10.dp))
                T(if (Api.enabled) "It sends as soon as there is signal." else "It is saved on this phone. Sending to the control room starts once your office switches the server link on.", 13, FontWeight.Bold, pal.amb)
            }
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(pal.pri2).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Lock, null, tint = pal.pri, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(10.dp))
                T("Once saved, a report cannot be deleted or edited. You can add a correction later.", 13, FontWeight.Bold, pal.pri)
            }
        }
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            BigButton("Save and send", Icons.Rounded.Send, modifier = Modifier.fillMaxWidth()) {
                val r = Report(UUID.randomUUID().toString(), System.currentTimeMillis(), alert.node, draft.seen ?: "", draft.adults, draft.calves, draft.tuskers, cardinalLabel(draft.direction), draft.activity.toList(), draft.distance, draft.photoId, draft.note.trim(), false, mutableListOf())
                Store.add(r); Api.syncPending(); onSaved(r.id)
            }
            BigButton("Change an answer", null, 1, Modifier.fillMaxWidth(), onClick = onChange)
        }
    }
}
