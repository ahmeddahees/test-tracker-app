package com.example.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.model.TrackedDevice
import com.example.data.repository.TrackingRepository
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

data class LiveServiceState(
    val isRunning: Boolean = false,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracyMeters: Double? = null,
    val speedMps: Double? = null,
    val altitudeMeters: Double? = null,
    val batteryPercent: Long = 100L,
    val isCharging: Boolean = false,
    val lastSyncEpochMs: Long? = null,
    val pingsSentCount: Int = 0,
    val lastError: String? = null,
)

object TrackingStatusHolder {
    private val _state = MutableStateFlow(LiveServiceState())
    val state: StateFlow<LiveServiceState> = _state.asStateFlow()

    internal fun update(transform: (LiveServiceState) -> LiveServiceState) {
        _state.update(transform)
    }
}

class LocationTrackingService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var repository: TrackingRepository
    private lateinit var identityStore: DeviceIdentityStore
    private lateinit var powerManager: PowerManager

    private var lastPingLocation: Location? = null
    private var lastPingTimeMs: Long = 0L
    private var isLocationUpdatesActive = false

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            handleNewLocation(location)
        }
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        repository = TrackingRepository(applicationContext)
        identityStore = DeviceIdentityStore(applicationContext)
        powerManager = getSystemService(PowerManager::class.java)
        ensureNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                identityStore.setTrackingArmed(false)
                stopTrackingAndService()
                return START_NOT_STICKY
            }
            ACTION_UPDATE_CONFIG -> {
                if (TrackingStatusHolder.state.value.isRunning) {
                    restartLocationUpdates()
                    updateNotification(TrackingStatusHolder.state.value)
                }
                return START_STICKY
            }
            ACTION_PING_NOW -> {
                if (hasLocationPermission() && TrackingStatusHolder.state.value.isRunning) {
                    requestImmediateFix()
                }
                return START_STICKY
            }
            ACTION_START -> {
                identityStore.setTrackingArmed(true)
            }
            null -> {
                // System restarted service via START_STICKY after process termination
                if (!identityStore.isTrackingArmed()) {
                    stopSelf()
                    return START_NOT_STICKY
                }
            }
        }

        if (!hasLocationPermission()) {
            Log.w(TAG, "Cannot start LocationTrackingService without location permission.")
            identityStore.setTrackingArmed(false)
            TrackingStatusHolder.update {
                it.copy(
                    isRunning = false,
                    lastError = "يرجى منح صلاحية الموقع أولاً لتفعيل التتبع المباشر."
                )
            }
            stopSelf()
            return START_NOT_STICKY
        }

        if (Firebase.auth.currentUser == null) {
            Log.w(TAG, "Cannot start LocationTrackingService while unauthenticated.")
            identityStore.setTrackingArmed(false)
            TrackingStatusHolder.update {
                it.copy(
                    isRunning = false,
                    lastError = "يرجى تسجيل الدخول بحساب Google أولاً."
                )
            }
            stopSelf()
            return START_NOT_STICKY
        }

        if (!promoteToForeground()) {
            stopSelf()
            return START_NOT_STICKY
        }

        TrackingStatusHolder.update { it.copy(isRunning = true, lastError = null) }
        restartLocationUpdates()
        requestImmediateFix()
        return START_STICKY
    }

    private fun promoteToForeground(): Boolean {
        val notification = buildNotification(TrackingStatusHolder.state.value)
        return try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                } else {
                    0
                }
            )
            true
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing runtime permission for location foreground service", e)
            TrackingStatusHolder.update {
                it.copy(
                    isRunning = false,
                    lastError = "يتعذر تشغيل الخدمة في الخلفية بدون صلاحية الموقع طوال الوقت."
                )
            }
            false
        } catch (e: Exception) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                e is ForegroundServiceStartNotAllowedException
            ) {
                Log.e(TAG, "Foreground service start not allowed from background", e)
                TrackingStatusHolder.update {
                    it.copy(
                        isRunning = false,
                        lastError = "النظام منع تشغيل الخدمة من الخلفية. افتح التطبيق وأعد التشغيل."
                    )
                }
            } else {
                Log.e(TAG, "Failed to start foreground service", e)
                TrackingStatusHolder.update {
                    it.copy(
                        isRunning = false,
                        lastError = e.localizedMessage ?: "تعذر بدء خدمة التتبع الأمامية."
                    )
                }
            }
            false
        }
    }

    @SuppressLint("MissingPermission")
    private fun restartLocationUpdates() {
        if (!hasLocationPermission()) return
        if (isLocationUpdatesActive) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
            isLocationUpdatesActive = false
        }

        val intervalSec = if (identityStore.isTheftAlertEnabled()) {
            5L
        } else {
            identityStore.getUpdateIntervalSec()
        }
        val intervalMs = intervalSec * 1000L

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
            .setMinUpdateIntervalMillis((intervalMs / 2L).coerceAtLeast(3000L))
            .setMinUpdateDistanceMeters(0f)
            .setWaitForAccurateLocation(false)
            .build()

        fusedLocationClient.requestLocationUpdates(
            request,
            locationCallback,
            Looper.getMainLooper()
        )
        isLocationUpdatesActive = true
    }

    @SuppressLint("MissingPermission")
    private fun requestImmediateFix() {
        if (!hasLocationPermission()) return
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location ->
                if (location != null) {
                    handleNewLocation(location)
                }
            }
    }

    private fun handleNewLocation(location: Location) {
        val (batteryPercent, isCharging) = readBatteryTelemetry()
        val nowMs = System.currentTimeMillis()
        val distanceFromLastPing = lastPingLocation?.distanceTo(location) ?: Float.MAX_VALUE
        val elapsedSincePingMs = nowMs - lastPingTimeMs
        val shouldRecordPing =
            lastPingLocation == null || distanceFromLastPing >= 5f || elapsedSincePingMs >= 30_000L

        val isAlert = identityStore.isTheftAlertEnabled()
        val intervalSec = if (isAlert) 5L else identityStore.getUpdateIntervalSec()
        val status = if (isAlert) TrackedDevice.STATUS_ALERT else TrackedDevice.STATUS_ACTIVE

        TrackingStatusHolder.update { current ->
            current.copy(
                isRunning = true,
                latitude = location.latitude,
                longitude = location.longitude,
                accuracyMeters = location.accuracy.toDouble(),
                speedMps = if (location.hasSpeed()) location.speed.toDouble() else 0.0,
                altitudeMeters = if (location.hasAltitude()) location.altitude else 0.0,
                batteryPercent = batteryPercent,
                isCharging = isCharging,
                lastError = null,
            )
        }
        updateNotification(TrackingStatusHolder.state.value)

        if (Firebase.auth.currentUser == null) return

        serviceScope.launch {
            val wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "SentinelGPS:FirestoreUploadWakeLock"
            )
            wakeLock.setReferenceCounted(false)
            wakeLock.acquire(15_000L)
            try {
                val result = repository.publishLocationFix(
                    deviceId = identityStore.getOrCreateDeviceId(),
                    deviceName = identityStore.getDeviceName(),
                    latitude = location.latitude,
                    longitude = location.longitude,
                    accuracyMeters = location.accuracy.toDouble(),
                    speedMps = if (location.hasSpeed()) location.speed.toDouble() else 0.0,
                    altitudeMeters = if (location.hasAltitude()) location.altitude else 0.0,
                    batteryPercent = batteryPercent,
                    isCharging = isCharging,
                    status = status,
                    updateIntervalSec = intervalSec,
                    recordPing = shouldRecordPing,
                )

                result.onSuccess {
                    if (shouldRecordPing) {
                        lastPingLocation = location
                        lastPingTimeMs = nowMs
                    }
                    TrackingStatusHolder.update { current ->
                        current.copy(
                            lastSyncEpochMs = System.currentTimeMillis(),
                            pingsSentCount = if (shouldRecordPing) current.pingsSentCount + 1 else current.pingsSentCount,
                            lastError = null,
                        )
                    }
                }.onFailure { error ->
                    TrackingStatusHolder.update { current ->
                        current.copy(
                            lastError = error.localizedMessage ?: "تعذر إرسال التحديث إلى السحابة."
                        )
                    }
                }
            } finally {
                if (wakeLock.isHeld) {
                    wakeLock.release()
                }
            }
        }
    }

    private fun stopTrackingAndService() {
        if (isLocationUpdatesActive) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
            isLocationUpdatesActive = false
        }
        TrackingStatusHolder.update { it.copy(isRunning = false) }

        if (Firebase.auth.currentUser != null) {
            serviceScope.launch {
                repository.updateDeviceSettings(
                    deviceId = identityStore.getOrCreateDeviceId(),
                    deviceName = identityStore.getDeviceName(),
                    isTracking = false,
                    status = TrackedDevice.STATUS_PAUSED,
                    updateIntervalSec = identityStore.getUpdateIntervalSec(),
                )
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        } else {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Keep the foreground service running continuously even after the user closes/swipes the app UI
        if (identityStore.isTrackingArmed()) {
            Log.i(TAG, "App task removed from recents; keeping Foreground Service active.")
        }
        super.onTaskRemoved(rootIntent)
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    private fun readBatteryTelemetry(): Pair<Long, Boolean> {
        val intent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        val pct = if (level >= 0 && scale > 0) ((level * 100L) / scale).coerceIn(0L, 100L) else 100L
        return pct to isCharging
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun updateNotification(state: LiveServiceState) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(state))
    }

    private fun buildNotification(state: LiveServiceState): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpen = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pingNowPending = PendingIntent.getService(
            this,
            1,
            Intent(this, LocationTrackingService::class.java).apply { action = ACTION_PING_NOW },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopPending = PendingIntent.getService(
            this,
            2,
            Intent(this, LocationTrackingService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val isAlert = identityStore.isTheftAlertEnabled()
        val title = if (isAlert) {
            getString(R.string.notification_title_alert)
        } else {
            getString(R.string.notification_title_active)
        }
        val body = if (state.latitude != null && state.longitude != null) {
            String.format(
                Locale.US,
                "%.5f, %.5f • دقة %.0fم • بطارية %d%%",
                state.latitude,
                state.longitude,
                state.accuracyMeters ?: 0.0,
                state.batteryPercent
            )
        } else {
            getString(R.string.notification_body_waiting)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(pendingOpen)
            .addAction(0, "تحديث فوري", pingNowPending)
            .addAction(0, "إيقاف", stopPending)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        if (isLocationUpdatesActive) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
            isLocationUpdatesActive = false
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "LocationTrackingSvc"
        private const val CHANNEL_ID = "sentinel_live_location_channel"
        private const val NOTIFICATION_ID = 4102

        const val ACTION_START = "com.example.action.START_TRACKING"
        const val ACTION_STOP = "com.example.action.STOP_TRACKING"
        const val ACTION_UPDATE_CONFIG = "com.example.action.UPDATE_CONFIG"
        const val ACTION_PING_NOW = "com.example.action.PING_NOW"

        fun startTracking(context: Context) {
            val intent = Intent(context, LocationTrackingService::class.java).apply {
                action = ACTION_START
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stopTracking(context: Context) {
            val intent = Intent(context, LocationTrackingService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun notifyConfigUpdated(context: Context) {
            val intent = Intent(context, LocationTrackingService::class.java).apply {
                action = ACTION_UPDATE_CONFIG
            }
            context.startService(intent)
        }

        fun pingNow(context: Context) {
            val intent = Intent(context, LocationTrackingService::class.java).apply {
                action = ACTION_PING_NOW
            }
            context.startService(intent)
        }
    }
}
