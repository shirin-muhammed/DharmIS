package co.ocaana.dharmis

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class Profile(val name: String, val phone: String, val role: String, val station: String, val range: RangeOffice)

class Correction(val at: Long, val text: String, var synced: Boolean = false)

class Report(
    val id: String, val createdAt: Long, val nodeId: String, val seen: String,
    val adults: Int, val calves: Int, val tuskers: Int, val direction: String, val activity: List<String>,
    val distanceM: Int?, val photo: String?, val note: String,
    var sent: Boolean, val corrections: MutableList<Correction>,
    val bearing: Int? = null,
) {
    val herd get() = adults + calves + tuskers
}

/** Everything lives on the phone. There is deliberately no function that deletes or edits a saved report. */
object Store {
    private lateinit var sp: SharedPreferences
    private lateinit var file: File
    lateinit var photoDir: File
        private set

    val profile = mutableStateOf<Profile?>(null)
    val onDuty = mutableStateOf(true)
    val themeMode = mutableStateOf(0) // 0 auto, 1 light, 2 dark
    val reports = mutableStateListOf<Report>()
    /** Bumped on every report change so screens re-read corrections and sent flags. */
    val version = mutableStateOf(0)
    /** Server state: "" not known yet, pending (waiting for staff), active, blocked. */
    val serverStatus = mutableStateOf("")
    /** False when the last attempt to reach the server failed. */
    val online = mutableStateOf(true)
    val alerts = mutableStateListOf<Alert>()
    /** When the control room last answered, and a counter bumped each time the app comes back to the screen. */
    val lastOk = mutableStateOf(0L)
    val resumeTick = mutableStateOf(0)
    val token get() = sp.getString("token", "")!!
    fun setToken(t: String) { sp.edit().putString("token", t).apply() }

    lateinit var app: Context
        private set

    private var ready = false

    /** Safe to call again: the alert service can start the process without the activity. */
    fun init(ctx: Context) {
        if (ready) return
        ready = true
        app = ctx
        sp = ctx.getSharedPreferences("dharmis", Context.MODE_PRIVATE)
        file = File(ctx.filesDir, "reports.json")
        photoDir = File(ctx.filesDir, "photos").apply { mkdirs() }
        Directory.load(ctx)
        onDuty.value = sp.getBoolean("duty", true)
        themeMode.value = sp.getInt("theme", 0)
        sp.getString("profile", null)?.let { s ->
            val o = JSONObject(s)
            Directory.ranges.firstOrNull { it.name == o.getString("range") }?.let {
                profile.value = Profile(o.getString("name"), o.getString("phone"), o.optString("role"), o.getString("station"), it)
            }
        }
        if (file.exists()) {
            val a = JSONArray(file.readText())
            for (i in 0 until a.length()) reports.add(fromJson(a.getJSONObject(i)))
        }
    }

    fun saveProfile(p: Profile) {
        profile.value = p
        sp.edit().putString("profile", JSONObject().put("name", p.name).put("phone", p.phone).put("role", p.role).put("station", p.station).put("range", p.range.name).toString()).apply()
    }

    fun setDuty(on: Boolean) {
        onDuty.value = on; sp.edit().putBoolean("duty", on).apply()
        if (!on) alerts.clear()
        AlertService.sync(app)
        Api.poll()
    }
    fun setTheme(m: Int) { themeMode.value = m; sp.edit().putInt("theme", m).apply() }

    /** Language the voice note listens for: ml-IN or en-IN. */
    val voiceLang get() = sp.getString("voiceLang", "ml-IN")!!
    fun setVoiceLang(tag: String) { sp.edit().putString("voiceLang", tag).apply() }

    fun add(r: Report) { reports.add(0, r); persist() }

    /** A correction is added under the report; the original text is never changed. */
    fun addCorrection(id: String, text: String) {
        reports.firstOrNull { it.id == id }?.corrections?.add(Correction(System.currentTimeMillis(), text))
        persist()
    }

    /** Signs this phone out: alerts stop and the member is forgotten here. Saved reports stay on the phone; they are never deleted. */
    fun logout() {
        Api.signOut()
        setToken(""); sp.edit().remove("profile").apply()
        profile.value = null; serverStatus.value = ""; alerts.clear(); Notify.forget()
    }

    /** The server no longer knows this phone (for example the PIN was used to log in elsewhere): back to the log in screen. Reports stay. */
    fun signedOut() { setToken(""); sp.edit().remove("profile").apply(); profile.value = null; serverStatus.value = ""; alerts.clear(); Notify.forget(); AlertService.sync(app) }

    fun markSent(id: String) { reports.firstOrNull { it.id == id }?.sent = true; persist() }
    fun markCorrectionSynced(id: String, at: Long) { reports.firstOrNull { it.id == id }?.corrections?.firstOrNull { it.at == at }?.synced = true; persist() }

    private fun persist() { file.writeText(JSONArray(reports.map { toJson(it) }).toString()); version.value++ }

    fun toJson(r: Report): JSONObject = JSONObject()
        .put("id", r.id).put("at", r.createdAt).put("node", r.nodeId).put("seen", r.seen)
        .put("adults", r.adults).put("calves", r.calves).put("tuskers", r.tuskers).put("dir", r.direction).put("bearing", r.bearing ?: JSONObject.NULL)
        .put("activity", JSONArray(r.activity)).put("dist", r.distanceM ?: JSONObject.NULL)
        .put("photo", r.photo ?: JSONObject.NULL).put("note", r.note).put("sent", r.sent)
        .put("corr", JSONArray(r.corrections.map { JSONObject().put("at", it.at).put("text", it.text).put("sy", it.synced) }))

    private fun fromJson(o: JSONObject): Report {
        val act = o.getJSONArray("activity"); val cr = o.getJSONArray("corr")
        return Report(
            o.getString("id"), o.getLong("at"), o.getString("node"), o.getString("seen"),
            o.getInt("adults"), o.getInt("calves"), o.getInt("tuskers"), o.getString("dir"),
            List(act.length()) { act.getString(it) },
            if (o.isNull("dist")) null else o.getInt("dist"),
            if (o.isNull("photo")) null else o.getString("photo"), o.getString("note"),
            o.getBoolean("sent"), MutableList(cr.length()) { Correction(cr.getJSONObject(it).getLong("at"), cr.getJSONObject(it).getString("text"), cr.getJSONObject(it).optBoolean("sy")) },
            if (o.isNull("bearing")) null else o.getInt("bearing"),
        )
    }
}
