package dev.rilandev.personalaiassistant

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat

/**
 * Persist owner choice after reboot/package replacement, without attempting
 * to start a microphone FGS from a restricted background broadcast.
 * The notification opens the visible app, which may resume the owner-armed
 * wake session when permissions and Android's current rules allow it.
 */
class NexaWakeBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED &&
            intent?.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        if (!NexaWakeWordService.enabled(context) ||
            !NexaWakeWordModule.modelReady(context)) return
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26)
            manager.createNotificationChannel(NotificationChannel(
                "nexa_wake_restore_v1", "Nexa wake restoration",
                NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(context, 20,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        manager.notify(4925, NotificationCompat.Builder(context, "nexa_wake_restore_v1")
            .setSmallIcon(R.drawable.ic_nexa_assistant)
            .setContentTitle("Nexa wake setting remembered")
            .setContentText("Tap to resume Hey Nexa safely after restart")
            .setAutoCancel(true)
            .setContentIntent(open)
            .build())
        // NO startForegroundService(), startService(), AudioRecord, or hidden mic.
    }
}
