package dev.persistentrings

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class RingsListener : NotificationListenerService() {
    private val runtime get() = (application as RingsApp).runtime
    private val main = Handler(Looper.getMainLooper())
    private val snapshot = Runnable { refreshSnapshot() }
    override fun onListenerConnected() {
        super.onListenerConnected()
        runtime.listener = this
        refreshSnapshot()
        runtime.connected.value = true
        runtime.refreshNotificationAccess()
        runtime.refreshBridge()
    }
    override fun onNotificationPosted(sbn: StatusBarNotification?) { resnapshot() }
    override fun onNotificationRemoved(sbn: StatusBarNotification?) { resnapshot() }
    private fun resnapshot() {
        // One authoritative snapshot after a burst covers group churn and removal races.
        main.removeCallbacks(snapshot)
        main.post(snapshot)
    }
    private fun refreshSnapshot() {
        runCatching {
            (activeNotifications ?: emptyArray()).map {
                Notice(it.key, it.packageName,
                    it.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0,
                    it.isOngoing, it.postTime)
            }
        }.onSuccess { runtime.notices.value = it }
            .onFailure {
                runtime.notices.value = emptyList()
                runtime.message.value = "Notification snapshot failed: ${it.message}"
            }
    }
    override fun onListenerDisconnected() {
        detach()
        // Request a framework rebind, rather than starting a background service.
        runCatching { requestRebind(ComponentName(this, RingsListener::class.java)) }
        super.onListenerDisconnected()
    }
    private fun detach() {
        main.removeCallbacks(snapshot)
        runtime.connected.value = false
        runtime.refreshNotificationAccess()
        runtime.notices.value = emptyList()
        if (runtime.listener === this) runtime.listener = null
        endForeground()
    }
    override fun onDestroy() { detach(); super.onDestroy() }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // A session may have been cancelled before this queued start was delivered.
        if (!runtime.foregroundActive.value) { endForeground() }
        return START_NOT_STICKY // Framework binding, not a sticky restart, restores snapshots.
    }
    fun beginForeground() {
        // A bound service must also enter the started state before promotion.
        // May be rejected by Android/HyperOS; the runtime catches this and stops LEDs.
        startForegroundService(Intent(this, RingsListener::class.java))
        val manager = requireNotNull(getSystemService(NotificationManager::class.java))
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Active LED reminders", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getActivity(this, 1, Intent(this, MainActivity::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_ring)
            .setContentTitle("Persistent Rings is reminding you")
            .setContentText("Selected notifications remain. Tap to manage reminders.")
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
            .addAction(Notification.Action.Builder(null, "Disable reminders", stop).build()).build()
        startForeground(101, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
    }
    fun endForeground() { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
    companion object {
        private const val CHANNEL = "reminders"
        const val ACTION_STOP = "dev.persistentrings.STOP"
    }
}
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            runCatching { NotificationListenerService.requestRebind(ComponentName(context, RingsListener::class.java)) }
        }
    }
}
