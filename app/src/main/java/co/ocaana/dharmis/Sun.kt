package co.ocaana.dharmis

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.*

/** Sunrise and sunset in epoch millis for the local calendar day (NOAA almanac method), or null in polar day/night. */
fun sunTimes(lat: Double, lon: Double, nowMs: Long): Pair<Long, Long>? {
    val local = Calendar.getInstance().apply { timeInMillis = nowMs }
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear(); set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
    }
    val n = local.get(Calendar.DAY_OF_YEAR)
    fun rad(d: Double) = d * PI / 180
    fun deg(r: Double) = r * 180 / PI
    fun norm(v: Double, m: Double) = ((v % m) + m) % m
    fun calc(rise: Boolean): Double? {
        val lngHour = lon / 15
        val t = n + ((if (rise) 6 else 18) - lngHour) / 24
        val m = 0.9856 * t - 3.289
        val l = norm(m + 1.916 * sin(rad(m)) + 0.020 * sin(rad(2 * m)) + 282.634, 360.0)
        var ra = norm(deg(atan(0.91764 * tan(rad(l)))), 360.0)
        ra = (ra + (floor(l / 90) * 90 - floor(ra / 90) * 90)) / 15
        val sinDec = 0.39782 * sin(rad(l))
        val cosDec = cos(asin(sinDec))
        val cosH = (cos(rad(90.833)) - sinDec * sin(rad(lat))) / (cosDec * cos(rad(lat)))
        if (cosH > 1 || cosH < -1) return null
        val h = (if (rise) 360 - deg(acos(cosH)) else deg(acos(cosH))) / 15
        return norm(h + ra - 0.06571 * t - 6.622 - lngHour, 24.0)
    }
    val r = calc(true) ?: return null
    val s = calc(false) ?: return null
    val base = utc.timeInMillis
    val rise = base + (r * 3_600_000).toLong()
    var set = base + (s * 3_600_000).toLong()
    if (set < rise) set += 86_400_000
    return rise to set
}

/** Dark from sunset to sunrise. Falls back to 18:30-06:00 local if the sun times cannot be worked out. */
fun isNight(lat: Double, lon: Double, nowMs: Long): Boolean {
    val st = sunTimes(lat, lon, nowMs)
    if (st != null) {
        // sunrise/sunset are for today; night is before sunrise, or after sunset (also covers sunset spilling past UTC midnight)
        return nowMs < st.first || nowMs >= st.second
    }
    val h = Calendar.getInstance().apply { timeInMillis = nowMs }.get(Calendar.HOUR_OF_DAY)
    return h >= 18 || h < 6
}
