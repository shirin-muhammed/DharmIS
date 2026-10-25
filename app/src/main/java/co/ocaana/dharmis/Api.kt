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
    fun signUp(p: Profile, pin: String, place: Place, done: (String?) -> Unit) {
        thread {
            val r = call("register", JSONObject().put("name", p.name).put("phone", p.phone).put("role", p.role).put("pin", pin).put("post", place.post)
                .put("division_id", place.divisionId).put("range_id", place.rangeId).put("unit_id", place.unitId).put("rrt_unit_id", place.rrtUnitId).put("rrt_unit", place.rrtUnitId > 0), "")
            val t = r?.json?.optString("token") ?: ""
            if (r != null && r.code == 200 && t.isNotBlank()) { main.post { Store.setToken(t); Store.saveProfile(p); done(null) } }
            else main.post { done(failText(r)) }
        }
    }

    /** The divisions, ranges, offices and RRT units the control room has built; registration offers only these. */
    fun units(done: (List<DivisionPick>?, String) -> Unit) {
        thread {
            val r = call("units", null, "")
            val arr = r?.json?.optJSONArray("divisions")
            if (r == null || r.code != 200 || arr == null) { main.post { done(null, failText(r)) }; return@thread }
            fun picks(a: org.json.JSONArray?) = List(a?.length() ?: 0) { i -> a!!.getJSONObject(i).let { Pick(it.getInt("id"), it.getString("name"), it.optString("kind")) } }
            val out = List(arr.length()) { i ->
                val d = arr.getJSONObject(i); val rs = d.getJSONArray("ranges")
                DivisionPick(d.getInt("id"), d.getString("name"), List(rs.length()) { j -> rs.getJSONObject(j).let { RangePick(it.getInt("id"), it.getString("name"), picks(it.optJSONArray("offices"))) } }, picks(d.optJSONArray("rrt")))
            }
            main.post { done(out, "") }
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
            val office = range ?: if (rank in setOf("section", "dro", "range")) RangeOffice(m.optString("range"), "", emptyList(), m.optString("division"), m.optString("circle")) else null   // officers are set up by staff and may not be in the registration list
            if (office == null) { main.post { done("Your range office is not in this app's list. Call your control room.") }; return@thread }
            main.post { Store.setRank(rank); Store.setToken(t); Store.saveProfile(Profile(m.optString("name"), m.optString("phone"), m.optString("role"), m.optString("station"), office)); done(null) }
        }
    }

    /** Officer dashboard for one office (0 = their own). done gets the JSON, or null and a message. */
    fun dash(unit: Int, have: String = "", done: (JSONObject?, String?) -> Unit) {
        thread {
            val r = call((if (unit > 0) "dash&unit=$unit" else "dash") + if (have.isNotBlank()) "&h=$have" else "")
            if (r != null && r.code == 401) { main.post { Store.signedOut() }; return@thread }
            if (r != null && r.code == 200) main.post { done(r.json, null) } else main.post { done(null, failText(r)) }
        }
    }

    /** Accepted, on_the_way, reached or done on one alert. lat and lon are sent only while accepted or on the way. */
    fun respond(node: String, state: String, lat: Double? = null, lon: Double? = null, done: () -> Unit = {}) {
        thread {
            val b = JSONObject().put("node", node).put("state", state)
            if (lat != null && lon != null) b.put("lat", lat).put("lon", lon)
            call("respond", b)
            main.post { done() }
            poll()
        }
    }

    /** Signal strip of one node for the last hour (RO alert detail). Null when the server is older or the node is out of reach. */
    fun node(id: String, done: (JSONObject?) -> Unit) {
        thread {
            val r = call("node&id=" + java.net.URLEncoder.encode(id, "UTF-8"))
            main.post { done(if (r != null && r.code == 200) r.json else null) }
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
                sendPushToken()   // officers get the escalation notices too, so every login registers
                var r = call("alerts")
                if (r != null && r.code == 401) { main.post { Store.signedOut() }; return@thread }
                if (r != null && r.json.has("receives")) { val rv = r.json.optBoolean("receives", true); main.post { Store.receives.value = rv } }
                if (r != null && r.json.has("in_rrt")) { val iv = r.json.optBoolean("in_rrt"); main.post { Store.inRrt.value = iv } }
                if (r != null && r.json.has("rank")) { val rk = r.json.optString("rank"); main.post { Store.setRank(rk) } }
                if (Store.alertUser && r != null && r.json.has("on_duty") && r.json.optBoolean("on_duty") != duty) {
                    call("duty", JSONObject().put("on_duty", duty))
                    r = call("alerts")
                }
                if (r == null || r.code !in 200..299 && r.code != 403) { main.post { Store.online.value = false }; return@thread }
                val status = r.json.optString("status", "blocked")
                val now = System.currentTimeMillis()
                val list = r.json.optJSONArray("alerts")
                val alerts = List(list?.length() ?: 0) { i ->
                    val a = list!!.getJSONObject(i)
                    val rs = a.optJSONArray("resp")
                    Alert(a.getString("node"), a.optString("place"), a.getString("level").uppercase(), now - a.optLong("age") * 1000,
                        if (a.isNull("lat")) Double.NaN else a.getDouble("lat"), if (a.isNull("lon")) Double.NaN else a.getDouble("lon"),
                        List(rs?.length() ?: 0) { j -> rs!!.getJSONObject(j).let { Resp(it.optString("who"), it.optString("state"), it.optLong("at") * 1000, it.optBoolean("mine")) } })
                }
                main.post { Store.serverStatus.value = status; Store.online.value = true; Store.lastOk.value = System.currentTimeMillis(); Store.alerts.clear(); Store.alerts.addAll(alerts) }
                if (Store.onDuty.value && Store.alertUser) Notify.check(Store.app, alerts)
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
