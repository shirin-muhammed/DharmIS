package co.ocaana.dharmis

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.IntentSenderRequest
import androidx.core.content.ContextCompat
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(applicationContext)
        setContent { App() }
    }

    override fun onResume() {
        super.onResume()
        if (Loc.wanted) Loc.start(this)
        Api.syncPending()
    }

    // Location is only ever on while a report or the Go map is open, and not while the app is in the background.
    override fun onPause() {
        super.onPause()
        Loc.pause(this)
    }
}

@Composable
fun App() {
    val ctx = LocalContext.current
    val mode by Store.themeMode
    // Re-check every minute so the switch at sunset happens while the app is open.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(60_000); now = System.currentTimeMillis() } }
    val dark = when (mode) {
        1 -> false
        2 -> true
        else -> isNight(10.5, 76.5, now) // Kerala; the sunset time varies by only a few minutes across the state, so no GPS is needed
    }

    // Shows the system "Turn on location" dialog; phones without Google services get the Location settings page.
    val settingsDialog = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { Loc.start(ctx) }
    fun turnOnLocation() {
        Loc.start(ctx)
        if (Loc.enabled(ctx)) return
        val req = LocationSettingsRequest.Builder().addLocationRequest(LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L).build()).setAlwaysShow(true).build()
        try {
            LocationServices.getSettingsClient(ctx).checkLocationSettings(req)
                .addOnSuccessListener { Loc.start(ctx) }
                .addOnFailureListener { e ->
                    if (e is ResolvableApiException) settingsDialog.launch(IntentSenderRequest.Builder(e.resolution).build())
                    else ctx.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
        } catch (_: Exception) { ctx.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
    var fromButton by remember { mutableStateOf(false) }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r.values.any { it }) turnOnLocation()
        else if (fromButton) ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        fromButton = false
    }
    fun askLocation(button: Boolean) {
        fromButton = button
        val granted = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION).any { ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED }
        if (granted) turnOnLocation() else perm.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    DharmisTheme(dark) {
        var splash by remember { mutableStateOf(true) }
        var chat by remember { mutableStateOf<Alert?>(null) }
        var review by remember { mutableStateOf(false) }
        var registered by remember { mutableStateOf(Store.profile.value != null) }
        val draft = remember { Draft() }
        var startStep by remember { mutableIntStateOf(0) }
        var go by remember { mutableStateOf<Alert?>(null) }
        // GPS runs only while a report or the Go map is open.
        val needsLoc = chat != null || go != null
        LaunchedEffect(needsLoc) { if (needsLoc) askLocation(false) else Loc.stop(ctx) }
        when {
            splash -> Splash { splash = false }
            !registered -> Register { registered = true }
            go != null && chat == null -> GoScreen(go!!, onBack = { go = null }, onTurnOnLocation = { askLocation(true) }, onReport = { draft.reset(); startStep = 0; chat = go; go = null })
            chat != null && review -> Review(chat!!, draft, onChange = { review = false; startStep = 5 }, onBack = { review = false; startStep = 5 }) { chat = null; review = false }
            chat != null -> key(startStep) { Chat(chat!!, draft, startStep, onClose = { chat = null }, onReview = { review = true }, onTurnOnLocation = { askLocation(true) }) }
            else -> Home(onReport = { a -> draft.reset(); startStep = 0; chat = a }, onGo = { a -> go = a })
        }
    }
}
