package com.example.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return

        val identityStore = DeviceIdentityStore(context)
        if (!identityStore.isTrackingArmed()) return
        if (Firebase.auth.currentUser == null) return

        val hasForegroundLoc = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasForegroundLoc) return

        val hasBackgroundLoc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        if (hasBackgroundLoc && Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            try {
                LocationTrackingService.startTracking(context)
                return
            } catch (e: Exception) {
                Log.w(TAG, "Unable to auto-start LocationTrackingService on boot: ${e.message}")
            }
        }

        showResumeTrackingNotification(context)
    }

    private fun showResumeTrackingNotification(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                RECOVERY_CHANNEL_ID,
                "استئناف الحماية بعد إعادة التشغيل",
                NotificationManager.IMPORTANCE_HIGH
            )
            manager.createNotificationChannel(channel)
        }

        val resumeIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_AUTO_START_TRACKING, true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            4109,
            resumeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, RECOVERY_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("حارس الموقع: تمت إعادة تشغيل الهاتف")
            .setContentText("اضغط هنا لاستئناف بث الموقع المباشر إلى Firestore.")
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        manager.notify(RECOVERY_NOTIFICATION_ID, notification)
    }

    companion object {
        private const val TAG = "BootCompletedReceiver"
        private const val RECOVERY_CHANNEL_ID = "sentinel_boot_recovery_channel"
        private const val RECOVERY_NOTIFICATION_ID = 4109
        const val EXTRA_AUTO_START_TRACKING = "extra_auto_start_tracking"
    }
}
