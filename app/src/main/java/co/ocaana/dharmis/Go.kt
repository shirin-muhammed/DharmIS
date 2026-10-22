package co.ocaana.dharmis

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Color as AColor
import android.graphics.DashPathEffect
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.File

private val WORDS = mapOf("N" to "north", "NE" to "north-east", "E" to "east", "SE" to "south-east", "S" to "south", "SW" to "south-west", "W" to "west", "NW" to "north-west")

private fun dot(ctx: Context, color: Int, sizeDp: Int): GradientDrawable {
    val px = (sizeDp * ctx.resources.displayMetrics.density).toInt()
    return GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color); setStroke((3 * ctx.resources.displayMetrics.density).toInt(), AColor.WHITE); setSize(px, px) }
}

/** Opens the phone's navigation app on foot towards the node. */
private fun openMaps(ctx: Context, a: Alert) {
    val nav = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=${a.lat},${a.lon}&mode=w")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try { ctx.startActivity(nav) } catch (_: ActivityNotFoundException) {
        try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:${a.lat},${a.lon}?q=${a.lat},${a.lon}(${a.node})")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: ActivityNotFoundException) { }
    }
}

@Composable
fun GoScreen(alert: Alert, onBack: () -> Unit, onTurnOnLocation: () -> Unit, onReport: () -> Unit) {
    val ctx = LocalContext.current
    BackHandler(onBack = onBack)
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { Loc.on.value = Loc.enabled(ctx); tick++; kotlinx.coroutines.delay(2000) } }
    val me = run { tick; Loc.fresh() }
    val r = FloatArray(1)
    val dist: Float? = me?.let { android.location.Location.distanceBetween(it.latitude, it.longitude, alert.lat, alert.lon, r); r[0] }
    val bearing: Int? = me?.let { val l = android.location.Location("").apply { latitude = it.latitude; longitude = it.longitude }; val n = android.location.Location("").apply { latitude = alert.lat; longitude = alert.lon }; ((l.bearingTo(n) + 360) % 360).toInt() }

    Page {
        BackBar(alert.node, "${alert.level.lowercase().replaceFirstChar { it.uppercase() }} · since ${ago(alert.since)}", onBack = onBack)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(modifier = Modifier.fillMaxSize(), factory = { c ->
                Configuration.getInstance().apply {
                    load(c, c.getSharedPreferences("osm", Context.MODE_PRIVATE)); userAgentValue = c.packageName
                    osmdroidBasePath = File(c.cacheDir, "osm"); osmdroidTileCache = File(c.cacheDir, "osm/tiles")
                }
                MapView(c).apply { setTileSource(TileSourceFactory.MAPNIK); setMultiTouchControls(true); zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER); controller.setZoom(15.0); controller.setCenter(GeoPoint(alert.lat, alert.lon)) }
            }, update = { map ->
                map.overlays.clear()
                val node = GeoPoint(alert.lat, alert.lon)
                if (me != null) map.overlays.add(Polyline(map).apply { setPoints(listOf(GeoPoint(me.latitude, me.longitude), node)); outlinePaint.color = AColor.rgb(31, 90, 64); outlinePaint.strokeWidth = 8f; outlinePaint.pathEffect = DashPathEffect(floatArrayOf(24f, 16f), 0f) })
                map.overlays.add(Marker(map).apply { position = node; icon = dot(ctx, AColor.rgb(200, 40, 28), 26); setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER); title = alert.node })
                if (me != null) {
                    val p = GeoPoint(me.latitude, me.longitude)
                    map.overlays.add(Marker(map).apply { position = p; icon = dot(ctx, AColor.rgb(37, 99, 235), 20); setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER); title = "You" })
                    // Fit both points once, the first time a fix arrives; later updates leave the view where the user put it.
                    if (map.tag == null) { map.tag = true; map.post { map.zoomToBoundingBox(BoundingBox.fromGeoPoints(listOf(p, node)), false, 120) } }
                }
                map.invalidate()
            }, onRelease = { it.onDetach() })
        }
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(pal.card).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (dist != null && bearing != null) {
                T("Head ${WORDS[cardinal(bearing.toDouble())]} · $bearing°", 24, FontWeight.ExtraBold)
                T("${kmText(dist)} · about ${maxOf(1, Math.round(dist / 80f))} minutes on foot", 15, FontWeight.Medium, pal.ink2)
                Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.MyLocation, null, tint = pal.ink3, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); T("Your live location, updated every few seconds", 13, FontWeight.Medium, pal.ink3) }
            } else {
                T(if (Loc.on.value) "Finding your location…" else "Location is off", 22, FontWeight.ExtraBold)
                T("Switch on location to see your position and the way to ${alert.node}. It is only on while this map or a report is open.", 14, FontWeight.Medium, pal.ink2)
                BigButton(if (Loc.on.value) "Check again" else "Turn on location", Icons.Rounded.MyLocation, modifier = Modifier.fillMaxWidth(), onClick = onTurnOnLocation)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BigButton("Maps", Icons.Rounded.Navigation, 1, Modifier.weight(1f)) { openMaps(ctx, alert) }
                BigButton("Report", Icons.Rounded.Edit, 2, Modifier.weight(1f), onClick = onReport)
            }
        }
    }
}
