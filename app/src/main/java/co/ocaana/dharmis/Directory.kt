package co.ocaana.dharmis

import android.content.Context
import org.json.JSONArray

class RangeOffice(val name: String, val phone: String, val stations: List<String>, val division: String, val circle: String) {
    val where get() = "$division · $circle"
}

object Directory {
    var ranges: List<RangeOffice> = emptyList()
        private set

    fun load(ctx: Context) {
        if (ranges.isNotEmpty()) return
        val txt = ctx.assets.open("directory.json").bufferedReader().use { it.readText() }
        val out = ArrayList<RangeOffice>()
        val circles = JSONArray(txt)
        for (i in 0 until circles.length()) {
            val c = circles.getJSONObject(i)
            val divs = c.getJSONArray("divisions")
            for (j in 0 until divs.length()) {
                val d = divs.getJSONObject(j)
                val rs = d.getJSONArray("ranges")
                for (k in 0 until rs.length()) {
                    val r = rs.getJSONObject(k)
                    val st = r.getJSONArray("stations")
                    out.add(RangeOffice(r.getString("name"), r.optString("phone"), List(st.length()) { st.getString(it) }, d.getString("name"), c.getString("name")))
                }
            }
        }
        ranges = out
    }

    fun search(q: String): List<RangeOffice> {
        val t = q.trim().lowercase()
        if (t.isEmpty()) return ranges
        return ranges.filter { it.name.lowercase().contains(t) || it.division.lowercase().contains(t) || it.circle.lowercase().contains(t) }
    }
}
