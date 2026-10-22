package co.ocaana.dharmis

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
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
        Api.syncPending()
    }
}

@Composable
fun App() {
    val ctx = LocalContext.current
    val loc by Loc.last
    val mode by Store.themeMode
    val system = isSystemInDarkTheme()
    // Re-check every minute so the switch at sunset happens while the app is open.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(60_000); now = System.currentTimeMillis() } }
    val dark = when (mode) {
        1 -> false
        2 -> true
        else -> loc?.let { isNight(it.latitude, it.longitude, now) } ?: system
    }

    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { Loc.start(ctx) }
    LaunchedEffect(Unit) { perm.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }

    DharmisTheme(dark) {
        var splash by remember { mutableStateOf(true) }
        var chat by remember { mutableStateOf<Alert?>(null) }
        var review by remember { mutableStateOf(false) }
        var registered by remember { mutableStateOf(Store.profile.value != null) }
        val draft = remember { Draft() }
        var startStep by remember { mutableIntStateOf(0) }
        when {
            splash -> Splash { splash = false }
            !registered -> Register { registered = true }
            chat != null && review -> Review(chat!!, draft, onChange = { review = false; startStep = 5 }, onBack = { review = false; startStep = 5 }) { chat = null; review = false }
            chat != null -> key(startStep) { Chat(chat!!, draft, startStep, onClose = { chat = null }, onReview = { review = true }) }
            else -> Home { a -> draft.reset(); startStep = 0; chat = a }
        }
    }
}
