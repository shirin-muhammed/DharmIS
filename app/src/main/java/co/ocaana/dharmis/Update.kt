package co.ocaana.dharmis

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

// Asks GitHub for the newest build-N release; when N is above this app's build, the app offers the APK.
object Updater {
    class Found(val build: Int, val apkUrl: String)
    val found = mutableStateOf<Found?>(null)
    private var lastCheck = 0L

    fun check() {
        val now = System.currentTimeMillis()
        if (now - lastCheck < 30 * 60_000) return
        lastCheck = now
        Thread {
            try {
                val c = URL("https://api.github.com/repos/shirin-muhammed/DharmIS/releases?per_page=1").openConnection() as HttpURLConnection
                c.connectTimeout = 8000; c.readTimeout = 8000
                c.setRequestProperty("Accept", "application/vnd.github+json")
                val rel = JSONArray(c.inputStream.bufferedReader().readText()).optJSONObject(0) ?: return@Thread
                val n = rel.getString("tag_name").removePrefix("build-").toIntOrNull() ?: return@Thread
                val assets = rel.getJSONArray("assets")
                if (n > BuildConfig.BUILD_NUMBER && assets.length() > 0) found.value = Found(n, assets.getJSONObject(0).getString("browser_download_url"))
            } catch (_: Exception) { } // offline or rate-limited: try again next time the app opens
        }.start()
    }

    fun install(ctx: Context, f: Found) {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(f.apkUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

@Composable
fun UpdateDialog(ctx: Context) {
    val f = Updater.found.value ?: return
    AlertDialog(
        onDismissRequest = { Updater.found.value = null },
        title = { Text("Update available") },
        text = { Text("DharmIS build ${f.build} is ready. Your phone downloads it; open it and tap Install. Your login and reports stay.") },
        confirmButton = { TextButton(onClick = { Updater.install(ctx, f); Updater.found.value = null }) { Text("Update") } },
        dismissButton = { TextButton(onClick = { Updater.found.value = null }) { Text("Later") } },
    )
}
