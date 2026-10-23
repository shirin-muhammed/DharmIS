package co.ocaana.dharmis

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/** Google push: wakes the phone for a High alert even when the app is closed and the phone is idle. */
class Push : FirebaseMessagingService() {
    override fun onMessageReceived(m: RemoteMessage) {
        val d = m.data
        val node = d["node"] ?: return
        Store.init(applicationContext)
        if (!Store.onDuty.value) return
        Notify.channels(applicationContext)
        Notify.push(applicationContext, Alert(node, d["place"] ?: "", (d["level"] ?: "HIGH").uppercase(), System.currentTimeMillis(),
            d["lat"]?.toDoubleOrNull() ?: Double.NaN, d["lon"]?.toDoubleOrNull() ?: Double.NaN))
    }

    override fun onNewToken(token: String) { Store.init(applicationContext); Api.sendPushToken(token) }
}
