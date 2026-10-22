package co.ocaana.dharmis

import android.graphics.BitmapFactory
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

private fun ease(p: Float, from: Float, to: Float): Float {
    val t = ((p - from) / (to - from)).coerceIn(0f, 1f)
    return 1f - (1f - t) * (1f - t) * (1f - t)
}

/** Startup: the OCAANA elephant draws itself once (left ear, right ear, head and trunk), settles up, then DharmIS appears. No scrolling. */
@Composable
fun Splash(onFinished: () -> Unit) {
    val ctx = LocalContext.current
    val img = remember { BitmapFactory.decodeStream(ctx.assets.open("logo.png")).asImageBitmap() }
    val reduce = remember { Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
    val prog = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!reduce) prog.animateTo(1f, tween(2200, easing = LinearEasing))
        kotlinx.coroutines.delay(if (reduce) 500 else 450)
        onFinished()
    }
    val p = prog.value
    val settle = ease(p, .72f, .95f)
    Box(Modifier.fillMaxSize().background(Color(0xFF0A110E)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Canvas(
                Modifier.width(240.dp).aspectRatio(img.width.toFloat() / img.height)
                    .graphicsLayer { val s = 1f - .30f * settle; scaleX = s; scaleY = s; translationY = -26.dp.toPx() * settle },
            ) {
                val w = size.width; val h = size.height
                val dst = IntSize(w.toInt(), h.toInt())
                fun part(l: Float, r: Float, reveal: Float, fade: Float) {
                    if (reveal <= 0f) return
                    clipRect(left = w * l, top = 0f, right = w * r, bottom = h * reveal) {
                        drawImage(img, srcOffset = IntOffset.Zero, srcSize = IntSize(img.width, img.height), dstOffset = IntOffset.Zero, dstSize = dst, alpha = fade)
                    }
                }
                if (reduce) { part(0f, 1f, 1f, 1f); return@Canvas }
                part(0f, .31f, ease(p, 0f, .38f), ease(p, 0f, .12f))
                part(.69f, 1f, ease(p, .16f, .55f), ease(p, .16f, .28f))
                part(.31f, .69f, ease(p, .42f, .76f), ease(p, .42f, .54f))
            }
            Spacer(Modifier.height(10.dp))
            Column(Modifier.alpha(ease(p, .78f, 1f)), horizontalAlignment = Alignment.CenterHorizontally) {
                T("DharmIS", 44, FontWeight.ExtraBold, Color.White)
                T("Tap. Report. Protect.", 16, FontWeight.Medium, Color(0xFFE0C070))
            }
        }
    }
}
