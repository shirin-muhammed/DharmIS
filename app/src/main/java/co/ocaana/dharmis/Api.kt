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

    private fun failText(r: Reply?) = if (r == null) "No signal. Try again." else r.json.optString("error").ifBlank { "Something went wrong. Try again." }

    /** Registers a new member. done gets null on success (token saved) or a message to show. */
    fun signUp(p: Profile, pin: String, done: (String?) -> Unit) {
        thread {
            val r = call("register", JSONObject().put("name", p.name).put("phone", p.phone).put("role", p.role).put("station", p.station)
                .put("range", p.range.name).put("division", p.range.division).put("circle", p.range.circle).put("pin", pin), "")
            val t = r?.json?.optString("token") ?: ""
            if (r != null && r.code == 200 && t.isNotBlank()) { main.post { Store.setToken(t); Store.saveProfile(p); done(null) } }
            else main.post { done(failText(r)) }
        }
    }

    /** Signs in an existing member on this phone (after a reinstall or a new phone). */
    fun logIn(phone: String, pin: String, done: (String?) -> Unit) {
        thread {
            val r = call("login", JSONObject().put("phone", phone).put("pin", pin), "")
            val m = r?.json?.optJSONObject("member")
            val t = r?.json?.optString("token") ?: ""
            if (r == null || r.code != 200 || m == null || t.isBlank()) { main.post { done(failText(r)) }; return@thread }
            val range = Directory.ranges.firstOrNull { it.name == m.optString("range") && it.division == m.optString("division") } ?: Directory.ranges.firstOrNull { it.name == m.optString("range") }
            val rank = m.optString("rank", "rrt")
            val office = range ?: if (rank in setOf("office", "section", "dro", "range")) RangeOffice(m.optString("range"), "", emptyList(), m.optString("division"), m.optString("circle")) else null   // officers are set up by staff and may not be in the registration list
            if (office == null) { main.post { done("Your range office is not in this app's list. Call your control room.") }; return@thread }
            main.post { Store.setRank(rank); Store.setToken(t); Store.saveProfile(Profile(m.optString("name"), m.optString("phone"), m.optString("role"), m.optString("station"), office)); done(null) }
        }
    }

    /** Officer dashboard for one office (0 = their own). done gets the JSON, or null and a message. */
    fun dash(unit: Int, done: (JSONObject?, String?) -> Unit) {
        thread {
            val r = call(if (unit > 0) "dash&unit=$unit" else "dash")
            if (r != null && r.code == 401) { main.post { Store.signedOut() }; return@thread }
            if (r != null && r.code == 200) main.post { done(r.json, null) } else main.post { done(null, failText(r)) }
        }
    }

    private var pushSent = ""

    /** Gives the server this phone's Google push address. Safe to call often; does nothing without Firebase set up. */
    fun sendPushToken(tok: String? = null) {
        if (!enabled || Store.token.isBlank()) return
        thread {
            try {
                val t = tok ?: com.google.android.gms.tasks.Tasks.await(com.google.firebase.messaging.FirebaseMessaging.getInstance().token)
                if (Store.token + t == pushSent) return@thread
                val r = call("push_token", JSONObject().put("token", t))
                if (r != null && r.code == 200) pushSent = Store.token + t
            } catch (_: Exception) {}
        }
    }

    /** One round: make sure we are registered, fetch alerts, then send anything waiting. Safe to call often. */
    fun poll() {
        if (!enabled || busy || Store.profile.value == null) return
        busy = true
        thread {
            try {
                if (Store.token.isBlank()) return@thread
                val duty = Store.onDuty.value
                if (!Store.isOfficer) sendPushToken()
                var r = call("alerts")
                if (r != null && r.code == 401) { main.post { Store.signedOut() }; return@thread }
                if (r != null && r.json.has("rank")) { val rk = r.json.optString("rank"); main.post { Store.setRank(rk) } }
                if (!Store.isOfficer && r != null && r.json.has("on_duty") && r.json.optBoolean("on_duty") != duty) {
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
                main.post { Store.serverStatus.value = status; Store.online.value = true; Store.lastOk.value = System.currentTimeMillis(); Store.alerts.clear(); Store.alerts.addAll(alerts) }
                if (Store.onDuty.value && !Store.isOfficer) Notify.check(Store.app, alerts)
                if (status == "active") send()
            } finally { busy = false }
        }
    }

    /** Tells the server this phone is done (no more alerts). Best effort. */
    fun signOut() { if (enabled && Store.token.isNotBlank()) { val t = Store.token; thread { call("duty", JSONObject().put("on_duty", false), t) } } }

    fun syncPending() { if (enabled && Store.token.isNotBlank()) thread { send() } }

    /** Runs on a background thread. Stops at the first network failure; the next round tries again. */
    private fun send() {
        for (r in Store.reports.filter { !it.sent }) {
            val photo = r.photo?.let { File(Store.photoDir, "$it.jpg") }?.takeIf { it.exists() }?.let { Base64.encodeToString(it.readBytes(), Base64.NO_WRAP) }
            val body = JSONObject().put("id", r.id).put("at", r.createdAt).put("node", r.nodeId).put("seen", r.seen)
                .put("adults", r.adults).put("calves", r.calves).put("tuskers", r.tuskers).put("direction", r.direction)
                .put("bearing", r.bearing ?: JSONObject.NULL).put("activity", org.json.JSONArray(r.activity)).put("dist", r.distanceM ?: JSONObject.NULL)
                .put("note", r.note).put("others", r.others).put("photo", photo ?: JSONObject.NULL)
            val x = call("report", body) ?: return
            if (x.code in 200..299) main.post { Store.markSent(r.id) } else if (x.code == 403 || x.code == 401) return
        }
        for (r in Store.reports.filter { it.sent }) for (c in r.corrections.filter { !it.synced }) {
            val x = call("correction", JSONObject().put("report", r.id).put("at", c.at).put("text", c.text)) ?: return
            if (x.code in 200..299) main.post { Store.markCorrectionSynced(r.id, c.at) }
        }
    }
}
