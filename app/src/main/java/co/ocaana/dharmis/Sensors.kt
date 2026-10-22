package co.ocaana.dharmis

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext

class Alert(val node: String, val place: String, val level: String, val since: Long, val lat: Double, val lon: Double)

/** Placeholder until the server sends real alerts: clearly labelled as a demo in the UI. */
fun demoAlert() = Alert("TX_NODE_03", "Near Thathengalam colony", "HIGH", System.currentTimeMillis() - 9 * 60_000, 10.9917, 76.4631)

object Loc {
    val last = mutableStateOf<Location?>(null)
    private var listener: LocationListener? = null

    @SuppressLint("MissingPermission")
    fun start(ctx: Context) {
        if (listener != null) return
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val l = object : LocationListener {
            override fun onLocationChanged(location: Location) { last.value = location }
        }
        listener = l
        try {
            for (p in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
                if (lm.isProviderEnabled(p)) {
                    lm.requestLocationUpdates(p, 5000L, 3f, l, Looper.getMainLooper())
                    if (last.value == null) last.value = lm.getLastKnownLocation(p)
                }
            }
        } catch (_: SecurityException) { listener = null }
    }
}

fun cardinal(deg: Double): String = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")[(((deg % 360) + 360) % 360 / 45 + .5).toInt() % 8]

fun kmText(m: Float): String = if (m < 1000) "${(m / 10).toInt() * 10} m" else "%.1f km".format(m / 1000)

/** Live heading (degrees, 0 = north) of the direction the back camera points. Null until the sensor reports. */
@Composable
fun rememberHeading(active: Boolean): State<Float?> {
    val ctx = LocalContext.current
    val state = remember { mutableStateOf<Float?>(null) }
    DisposableEffect(active) {
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val l = object : SensorEventListener {
            val r = FloatArray(9); val r2 = FloatArray(9); val o = FloatArray(3)
            override fun onSensorChanged(e: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(r, e.values)
                SensorManager.remapCoordinateSystem(r, SensorManager.AXIS_X, SensorManager.AXIS_Z, r2)
                SensorManager.getOrientation(r2, o)
                state.value = ((Math.toDegrees(o[0].toDouble()) + 360) % 360).toFloat()
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        if (active && sensor != null) sm.registerListener(l, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { sm.unregisterListener(l) }
    }
    return state
}
