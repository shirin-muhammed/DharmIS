package co.ocaana.dharmis

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/** Sirens once per alert. An alert sounds again only if its level goes up or it clears and comes back. */
object Notify {
    private const val SIREN = "alert_siren_v1"
    private const val MEDIUM = "alert_medium_v1"
    private const val WATCH = "watch_v1"
    private val sounded = HashMap<String, Int>()
    private val rank = mapOf("LOW" to 1, "MEDIUM" to 2, "HIGH" to 3)

    private fun open(ctx: Context) = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    fun channels(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        nm.createNotificationChannel(NotificationChannel(SIREN, "Elephant alerts", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "One siren when a node raises an alert"
            setSound(Uri.parse("android.resource://${ctx.packageName}/${R.raw.siren}"), attrs)
            enableVibration(true); vibrationPattern = longArrayOf(0, 600, 300, 600)
        })
        nm.createNotificationChannel(NotificationChannel(MEDIUM, "Medium alerts", NotificationManager.IMPORTANCE_DEFAULT).apply { description = "A normal notification when a node reaches Medium" })
        nm.createNotificationChannel(NotificationChannel(WATCH, "Watching for alerts", NotificationManager.IMPORTANCE_LOW).apply { description = "Shown while you are on duty"; setShowBadge(false) })
    }

    fun watching(ctx: Context): Notification = NotificationCompat.Builder(ctx, WATCH).setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle("DharmIS · On duty").setContentText("Watching for elephant alerts").setOngoing(true).setContentIntent(open(ctx)).build()

    /** Called after every successful fetch with the alerts now showing. */
    fun check(ctx: Context, alerts: List<Alert>) {
        sounded.keys.retainAll(alerts.map { it.node }.toSet())
        for (a in alerts) {
            val r = rank[a.level] ?: continue
            if (r < 2 || (sounded[a.node] ?: 0) >= r) continue
            sounded[a.node] = r
            ring(ctx, a)
        }
    }

    /** A push that arrived while the app was asleep. Same once-per-rise rule as the poll path. */
    fun push(ctx: Context, a: Alert) {
        val r = rank[a.level] ?: return
        if (r < 2 || (sounded[a.node] ?: 0) >= r) return
        sounded[a.node] = r
        ring(ctx, a)
    }

    private fun ring(ctx: Context, a: Alert) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val high = a.level == "HIGH"
        val text = "${a.level.lowercase().replaceFirstChar { it.uppercase() }} alert" + if (a.place.isNotBlank()) " · ${a.place}" else ""
        nm.cancel(a.node.hashCode())   // a Medium notification is replaced, not silently updated, when the node reaches High
        val b = NotificationCompat.Builder(ctx, if (high) SIREN else MEDIUM).setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(a.node).setContentText(text).setAutoCancel(true).setContentIntent(open(ctx))
        if (high) b.setCategory(NotificationCompat.CATEGORY_ALARM).setPriority(NotificationCompat.PRIORITY_MAX)
            .setSound(Uri.parse("android.resource://${ctx.packageName}/${R.raw.siren}")).setOnlyAlertOnce(true)
        else b.setPriority(NotificationCompat.PRIORITY_DEFAULT).setDefaults(NotificationCompat.DEFAULT_SOUND)
        nm.notify(a.node.hashCode(), b.build())
    }

    fun forget() { sounded.clear() }
}

/** Keeps the app alive while on duty so alerts arrive with the phone locked or in a pocket. */
class AlertService : Service() {
    @Volatile private var running = false
    private var lock: PowerManager.WakeLock? = null

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, flags: Int, startId: Int): Int {
        Store.init(applicationContext)
        Notify.channels(this)
        val n = Notify.watching(this)
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else startForeground(1, n)
        if (!running) {
            running = true
            lock = (getSystemService(POWER_SERVICE) as PowerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "dharmis:watch").apply { acquire() }
            Thread {
                while (running) { Api.poll(); try { Thread.sleep(15_000) } catch (_: InterruptedException) { break } }
            }.start()
        }
        return START_STICKY
    }

    override fun onDestroy() { running = false; lock?.let { if (it.isHeld) it.release() }; super.onDestroy() }

    companion object {
        fun sync(ctx: Context) {
            val want = Store.profile.value != null && Store.onDuty.value && !Store.isOfficer
            val i = Intent(ctx, AlertService::class.java)
            if (want) ContextCompat.startForegroundService(ctx, i) else ctx.stopService(i)
        }
    }
}
