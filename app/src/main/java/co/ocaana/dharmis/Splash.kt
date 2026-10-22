package co.ocaana.dharmis

import android.graphics.BitmapFactory
import android.graphics.Paint as NativePaint
import android.graphics.Typeface
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext

private const val TOTAL = 3500f
private const val LW = 600f
private const val LH = 545f
private val GOLD = Color(0xFFD7B557)
private val BLACK = Color.Black
private val CLEAR = Color.Transparent

private fun st(t: Float, a: Float, b: Float) = ((t - a) / (b - a)).coerceIn(0f, 1f)
private fun eio(x: Float) = if (x < .5f) 4 * x * x * x else 1 - Math.pow((-2 * x + 2).toDouble(), 3.0).toFloat() / 2
private fun eo(x: Float) = 1 - (1 - x) * (1 - x) * (1 - x)

/** Draws one logo layer in the 600x545 logo space: image, then an optional bright edge (SrcAtop), then a soft reveal mask (DstIn). */
private fun DrawScope.layer(img: ImageBitmap, glow: (DrawScope.() -> Unit)?, mask: DrawScope.() -> Unit) {
    val c = drawContext.canvas
    c.saveLayer(Rect(0f, 0f, LW, LH), Paint())
    drawImage(img)
    glow?.invoke(this)
    mask()
    c.restore()
}

/** Startup, played once: circuit ear powers on, fingerprint ear ripples out, gold face wipes down, a shine crosses, then DharmIS settles in. No scrolling. */
@Composable
fun Splash(onFinished: () -> Unit) {
    val ctx = LocalContext.current
    val face = remember { BitmapFactory.decodeStream(ctx.assets.open("logo_face.png")).asImageBitmap() }
    val left = remember { BitmapFactory.decodeStream(ctx.assets.open("logo_left.png")).asImageBitmap() }
    val right = remember { BitmapFactory.decodeStream(ctx.assets.open("logo_right.png")).asImageBitmap() }
    val reduce = remember { Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
    val prog = remember { Animatable(if (reduce) TOTAL else 0f) }
    LaunchedEffect(Unit) {
        if (!reduce) prog.animateTo(TOTAL, tween(TOTAL.toInt(), easing = LinearEasing))
        kotlinx.coroutines.delay(450)
        onFinished()
    }
    val t = prog.value
    val paint = remember { NativePaint(NativePaint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD } }

    Canvas(Modifier.fillMaxSize().background(Color(0xFF0A110E))) {
        val k = size.width / 720f
        val settle = eio(st(t, 2500f, 3200f))
        val scale = 1f - .32f * settle
        val lw = 480f * k * scale
        val lh = lw * LH / LW
        val cx = size.width / 2
        val cy = size.height / 2 - settle * 70f * k

        // soft gold glow behind the mark
        val glowA = eo(st(t, 0f, 900f)) * .32f * (1 - .45f * settle)
        drawRect(Brush.radialGradient(listOf(GOLD.copy(alpha = glowA), CLEAR), center = Offset(cx, cy), radius = 420f * k))

        withTransform({ translate(cx - lw / 2, cy - lh / 2); scale(lw / LW, lw / LW, pivot = Offset.Zero) }) {
            drawContext.canvas.saveLayer(Rect(0f, 0f, LW, LH), Paint())

            // left ear: circuit board powers on from the outer edge inward
            val l = eio(st(t, 150f, 1500f))
            val le = -20f + (300f + 110f) * l
            if (le > 1f) layer(left,
                glow = {
                    val a = Math.sin(Math.PI * l).toFloat() * .95f
                    drawRect(Brush.horizontalGradient(0f to Color(1f, .94f, .67f, 0f), .8f to Color(1f, .957f, .745f, a), 1f to Color(1f, .94f, .67f, 0f), startX = le - 70f, endX = le + 10f), size = Size(LW, LH), blendMode = BlendMode.SrcAtop)
                },
                mask = {
                    drawRect(Brush.horizontalGradient(0f to BLACK, ((le - 90f) / le).coerceIn(0f, 1f) to BLACK, 1f to CLEAR, startX = 0f, endX = le), size = Size(LW, LH), blendMode = BlendMode.DstIn)
                })

            // right ear: fingerprint ripples out from its centre
            val r = eo(st(t, 400f, 2000f))
            if (r > 0f) {
                val c = Offset(470f, 270f); val rad = (430f * r).coerceAtLeast(1f); val soft = 80f
                layer(right,
                    glow = {
                        val a = Math.sin(Math.PI * r).toFloat() * .9f
                        val s0 = ((rad - soft) / (rad + 4f)).coerceIn(0f, 1f)
                        val s1 = ((rad - soft + .85f * (soft + 4f)) / (rad + 4f)).coerceIn(s0, 1f)
                        drawRect(Brush.radialGradient(0f to CLEAR, s0 to Color(1f, .957f, .745f, 0f), s1 to Color(1f, .957f, .745f, a), 1f to Color(1f, .957f, .745f, 0f), center = c, radius = rad + 4f), size = Size(LW, LH), blendMode = BlendMode.SrcAtop)
                    },
                    mask = {
                        drawRect(Brush.radialGradient(0f to BLACK, ((rad - soft) / rad).coerceIn(0f, 1f) to BLACK, 1f to CLEAR, center = c, radius = rad), size = Size(LW, LH), blendMode = BlendMode.DstIn)
                    })
            }

            // face: gold wipes down from the forehead to the trunk tip
            val f = eio(st(t, 1000f, 2100f))
            val fe = -80f + (LH + 160f) * f
            if (fe > 1f) layer(face, glow = null, mask = {
                drawRect(Brush.verticalGradient(0f to BLACK, ((fe - 80f) / fe).coerceIn(0f, 1f) to BLACK, 1f to CLEAR, startY = 0f, endY = fe), size = Size(LW, LH), blendMode = BlendMode.DstIn)
            })

            // shine: one diagonal highlight across the finished mark
            val sh = st(t, 2150f, 2950f)
            if (sh > 0f && sh < 1f) {
                val p = -260f + (LW + 520f) * eio(sh)
                rotate(-20.05f, pivot = Offset(LW / 2, LH / 2)) {
                    drawRect(Brush.horizontalGradient(0f to Color(1f, .973f, .824f, 0f), .5f to Color(1f, .973f, .824f, .75f), 1f to Color(1f, .973f, .824f, 0f), startX = p - 90f, endX = p + 90f),
                        topLeft = Offset(-300f, -300f), size = Size(LW + 600f, LH + 600f), blendMode = BlendMode.SrcAtop)
                }
            }
            drawContext.canvas.restore()
        }

        // wordmark: Dharm in white, IS in the logo's gold
        val ta = eo(st(t, 2550f, 3400f))
        if (ta > 0f) drawIntoCanvasText(k, cx, cy + lh / 2, ta, paint)
    }
}

private fun DrawScope.drawIntoCanvasText(k: Float, cx: Float, logoBottom: Float, ta: Float, paint: NativePaint) {
    val c = drawContext.canvas.nativeCanvas
    val slide = (1 - ta) * 24f * k
    paint.textSize = 88f * k
    val w1 = paint.measureText("Dharm"); val w2 = paint.measureText("IS")
    val x0 = cx - (w1 + w2) / 2; val y = logoBottom + 120f * k + slide
    paint.color = android.graphics.Color.WHITE; paint.alpha = (ta * 255).toInt(); c.drawText("Dharm", x0, y, paint)
    paint.color = 0xFFD7B557.toInt(); paint.alpha = (ta * 255).toInt(); c.drawText("IS", x0 + w1, y, paint)
    paint.textSize = 32f * k; paint.typeface = Typeface.DEFAULT
    val tag = "Tap. Report. Protect."; paint.color = 0xFFE0C070.toInt(); paint.alpha = (ta * 255).toInt()
    c.drawText(tag, cx - paint.measureText(tag) / 2, logoBottom + 176f * k + slide, paint)
    paint.typeface = Typeface.DEFAULT_BOLD
}
