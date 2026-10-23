package co.ocaana.dharmis

import android.os.Handler
import android.os.Looper
import android.util.Base64
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/** Talks to the OCAANA control site (rrt_api.php). The phone holds a token made at registration; staff approve it and link the member's customer account. */
object Api {
    val enabled get() = BuildConfig.API_BASE.isNotBlank()
    /** True while the app is on screen: alerts are only fetched then. */
    @Volatile var active = false
    @Volatile private var busy = false
    private val main = Handler(Looper.getMainLooper())

    private class Reply(val code: Int, val json: JSONObject)

    /** Returns null when the server cannot be reached at all. */
    private fun call(action: String, body: JSONObject? = null, token: String = Store.token): Reply? = try {
        val c = URL(BuildConfig.API_BASE + "rrt_api.php?action=" + action).openConnection() as HttpURLConnection
        c.connectTimeout = 8000; c.readTimeout = 15000
        c.setRequestProperty("X-RRT-Token", token)
        if (body != null) {
            c.requestMethod = "POST"; c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.outputStream.use { it.write(body.toString().toByteArray()) }
        }
        val code = c.responseCode
        val txt = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
        c.disconnect()
        Reply(code, try { JSONObject(txt) } catch (_: Exception) { JSONObject() })
    } catch (_: Exception) { null }

    private fun register(): Boolean {
        val p = Store.profile.value ?: return false
        val r = call("register", JSONObject().put("name", p.name).put("phone", p.phone).put("role", p.role).put("station", p.station)
            .put("range", p.range.name).put("division", p.range.division).put("circle", p.range.circle), "") ?: return false
        val t = r.json.optString("token")
        if (r.code != 200 || t.isBlank()) return false
        Store.setToken(t)
        return true
    }

    /** One round: make sure we are registered, fetch alerts, then send anything waiting. Safe to call often. */
    fun poll() {
        if (!enabled || busy || Store.profile.value == null) return
        busy = true
        thread {
            try {
                if (Store.token.isBlank() && !register()) { main.post { Store.online.value = false }; return@thread }
                val duty = Store.onDuty.value
                var r = call("alerts")
                if (r != null && r.code == 401) { Store.setToken(""); main.post { Store.serverStatus.value = "" }; return@thread }
                if (r != null && r.json.has("on_duty") && r.json.optBoolean("on_duty") != duty) {
                    call("duty", JSONObject().put("on_duty", duty))
                    r = call("alerts")
                }
                if (r == null || r.code !in 200..299 && r.code != 403) { main.post { Store.online.value = false }; return@thread }
                val status = r.json.optString("status", "blocked")
                val now = System.currentTimeMillis()
                val list = r.json.optJSONArray("alerts")
                val alerts = List(list?.length() ?: 0) { i ->
                    val a = list!!.getJSONObject(i)
                    Alert(a.getString("node"), a.optString("place"), a.getString("level").uppercase(), now - a.optLong("age") * 1000,
                        if (a.isNull("lat")) Double.NaN else a.getDouble("lat"), if (a.isNull("lon")) Double.NaN else a.getDouble("lon"))
                }
                main.post { Store.serverStatus.value = status; Store.online.value = true; Store.alerts.clear(); Store.alerts.addAll(alerts) }
                if (status == "active") send()
            } finally { busy = false }
        }
    }

    fun syncPending() { if (enabled && Store.token.isNotBlank()) thread { send() } }

    /** Runs on a background thread. Stops at the first network failure; the next round tries again. */
    private fun send() {
        for (r in Store.reports.filter { !it.sent }) {
            val photo = r.photo?.let { File(Store.photoDir, "$it.jpg") }?.takeIf { it.exists() }?.let { Base64.encodeToString(it.readBytes(), Base64.NO_WRAP) }
            val body = JSONObject().put("id", r.id).put("at", r.createdAt).put("node", r.nodeId).put("seen", r.seen)
                .put("adults", r.adults).put("calves", r.calves).put("tuskers", r.tuskers).put("direction", r.direction)
                .put("bearing", r.bearing ?: JSONObject.NULL).put("activity", org.json.JSONArray(r.activity)).put("dist", r.distanceM ?: JSONObject.NULL)
                .put("note", r.note).put("photo", photo ?: JSONObject.NULL)
            val x = call("report", body) ?: return
            if (x.code in 200..299) main.post { Store.markSent(r.id) } else if (x.code == 403 || x.code == 401) return
        }
        for (r in Store.reports.filter { it.sent }) for (c in r.corrections.filter { !it.synced }) {
            val x = call("correction", JSONObject().put("report", r.id).put("at", c.at).put("text", c.text)) ?: return
            if (x.code in 200..299) main.post { Store.markCorrectionSynced(r.id, c.at) }
        }
    }
}
