package co.ocaana.dharmis

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class Profile(val name: String, val phone: String, val role: String, val station: String, val range: RangeOffice)

class Correction(val at: Long, val text: String)

class Report(
    val id: String, val createdAt: Long, val nodeId: String, val seen: String,
    val adults: Int, val calves: Int, val tuskers: Int, val direction: String, val activity: List<String>,
    val distanceM: Int?, val photo: String?, val note: String,
    var sent: Boolean, val corrections: MutableList<Correction>,
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

    fun init(ctx: Context) {
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

    fun setDuty(on: Boolean) { onDuty.value = on; sp.edit().putBoolean("duty", on).apply() }
    fun setTheme(m: Int) { themeMode.value = m; sp.edit().putInt("theme", m).apply() }

    fun add(r: Report) { reports.add(0, r); persist() }

    /** A correction is added under the report; the original text is never changed. */
    fun addCorrection(id: String, text: String) {
        reports.firstOrNull { it.id == id }?.corrections?.add(Correction(System.currentTimeMillis(), text))
        persist()
    }

    fun markSent(id: String) { reports.firstOrNull { it.id == id }?.sent = true; persist() }

    private fun persist() { file.writeText(JSONArray(reports.map { toJson(it) }).toString()); version.value++ }

    fun toJson(r: Report): JSONObject = JSONObject()
        .put("id", r.id).put("at", r.createdAt).put("node", r.nodeId).put("seen", r.seen)
        .put("adults", r.adults).put("calves", r.calves).put("tuskers", r.tuskers).put("dir", r.direction)
        .put("activity", JSONArray(r.activity)).put("dist", r.distanceM ?: JSONObject.NULL)
        .put("photo", r.photo ?: JSONObject.NULL).put("note", r.note).put("sent", r.sent)
        .put("corr", JSONArray(r.corrections.map { JSONObject().put("at", it.at).put("text", it.text) }))

    private fun fromJson(o: JSONObject): Report {
        val act = o.getJSONArray("activity"); val cr = o.getJSONArray("corr")
        return Report(
            o.getString("id"), o.getLong("at"), o.getString("node"), o.getString("seen"),
            o.getInt("adults"), o.getInt("calves"), o.getInt("tuskers"), o.getString("dir"),
            List(act.length()) { act.getString(it) },
            if (o.isNull("dist")) null else o.getInt("dist"),
            if (o.isNull("photo")) null else o.getString("photo"), o.getString("note"),
            o.getBoolean("sent"), MutableList(cr.length()) { Correction(cr.getJSONObject(it).getLong("at"), cr.getJSONObject(it).getString("text")) },
        )
    }
}
