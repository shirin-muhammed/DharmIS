package co.ocaana.dharmis

import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/** Sends waiting reports to the OCAANA server. API_BASE is empty until the server endpoints exist, so nothing leaves the phone yet. */
object Api {
    val enabled get() = BuildConfig.API_BASE.isNotBlank()

    fun syncPending(onDone: () -> Unit = {}) {
        if (!enabled) return
        val p = Store.profile.value ?: return
        val waiting = Store.reports.filter { !it.sent }
        if (waiting.isEmpty()) return
        thread {
            for (r in waiting) {
                try {
                    val body = Store.toJson(r).put("station", p.station).put("range", p.range.name).put("by", p.name).put("phone", p.phone).toString()
                    val c = URL(BuildConfig.API_BASE + "rrt_api.php?action=report").openConnection() as HttpURLConnection
                    c.requestMethod = "POST"; c.doOutput = true; c.connectTimeout = 8000; c.readTimeout = 8000
                    c.setRequestProperty("Content-Type", "application/json")
                    c.outputStream.use { it.write(body.toByteArray()) }
                    if (c.responseCode in 200..299) android.os.Handler(android.os.Looper.getMainLooper()).post { Store.markSent(r.id); onDone() }
                    c.disconnect()
                } catch (_: Exception) { break }
            }
        }
    }
}
