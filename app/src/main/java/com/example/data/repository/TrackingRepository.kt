package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.LocationPing
import com.example.data.model.TrackedDevice
import com.example.data.remote.OperationType
import com.example.data.remote.handleFirestoreError
import com.example.data.remote.unwrapFirestoreException
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class TrackingRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
) {
    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        auth = Firebase.auth,
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observeDevices(userId: String): Flow<List<TrackedDevice>> = flow {
        val path = "users/$userId/devices"
        emitAll(
            db.collection("users").document(userId).collection("devices")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(TrackedDevice::class.java, ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.updatedAt ?: it.createdAt }
                }
                .catch { error ->
                    val firestoreError = error.unwrapFirestoreException()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path, auth)
                    }
                    throw error
                }
        )
    }

    fun observePings(
        userId: String,
        deviceId: String,
        limit: Long = 40L,
    ): Flow<List<LocationPing>> = flow {
        val path = "users/$userId/devices/$deviceId/pings"
        emitAll(
            db.collection("users").document(userId)
                .collection("devices").document(deviceId)
                .collection("pings")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(limit)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(LocationPing::class.java, ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    val firestoreError = error.unwrapFirestoreException()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path, auth)
                    }
                    throw error
                }
        )
    }

    suspend fun getDevice(deviceId: String): Result<TrackedDevice?> {
        val uid = runCatching { requireUserId() }.getOrElse { return Result.failure(it) }
        val docRef = db.collection("users").document(uid).collection("devices").document(deviceId)
        return try {
            val snap = docRef.get().await()
            val device = if (snap.exists()) {
                snap.toObject(TrackedDevice::class.java, ServerTimestampBehavior.ESTIMATE)
            } else {
                null
            }
            Result.success(device)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun getUserDevices(): Result<List<TrackedDevice>> {
        val uid = runCatching { requireUserId() }.getOrElse { return Result.failure(it) }
        val path = "users/$uid/devices"
        return try {
            val snapshot = db.collection("users").document(uid).collection("devices").get().await()
            val devices = snapshot.toObjects(TrackedDevice::class.java, ServerTimestampBehavior.ESTIMATE)
            Result.success(devices)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path, auth)
            Result.failure(e)
        }
    }

    suspend fun ensureDeviceRegistered(
        deviceId: String,
        deviceName: String,
        isTracking: Boolean = false,
        status: String = TrackedDevice.STATUS_PAUSED,
        latitude: Double = 0.0,
        longitude: Double = 0.0,
        accuracyMeters: Double = 0.0,
        speedMps: Double = 0.0,
        altitudeMeters: Double = 0.0,
        batteryPercent: Long = 100L,
        isCharging: Boolean = false,
        updateIntervalSec: Long = 10L,
    ): Result<Unit> {
        val uid = runCatching { requireUserId() }.getOrElse { return Result.failure(it) }
        val docRef = db.collection("users").document(uid).collection("devices").document(deviceId)
        return try {
            val existingSnap = docRef.get().await()
            if (!existingSnap.exists()) {
                val payload = mapOf(
                    "userId" to uid,
                    "deviceName" to deviceName.take(60).ifBlank { "Android Device" },
                    "isTracking" to isTracking,
                    "status" to status,
                    "latitude" to latitude.coerceIn(-90.0, 90.0),
                    "longitude" to longitude.coerceIn(-180.0, 180.0),
                    "accuracyMeters" to accuracyMeters.coerceIn(0.0, 100000.0),
                    "speedMps" to speedMps.coerceIn(0.0, 500.0),
                    "altitudeMeters" to altitudeMeters.coerceIn(-1000.0, 50000.0),
                    "batteryPercent" to batteryPercent.coerceIn(0L, 100L),
                    "isCharging" to isCharging,
                    "updateIntervalSec" to updateIntervalSec.coerceIn(3L, 300L),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
                docRef.set(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun publishLocationFix(
        deviceId: String,
        deviceName: String,
        latitude: Double,
        longitude: Double,
        accuracyMeters: Double,
        speedMps: Double,
        altitudeMeters: Double,
        batteryPercent: Long,
        isCharging: Boolean,
        status: String,
        updateIntervalSec: Long,
        recordPing: Boolean,
    ): Result<Unit> {
        val uid = runCatching { requireUserId() }.getOrElse { return Result.failure(it) }
        val docRef = db.collection("users").document(uid).collection("devices").document(deviceId)
        val safeLat = latitude.coerceIn(-90.0, 90.0)
        val safeLng = longitude.coerceIn(-180.0, 180.0)
        val safeAcc = accuracyMeters.coerceIn(0.0, 100000.0)
        val safeSpeed = speedMps.coerceIn(0.0, 500.0)
        val safeAlt = altitudeMeters.coerceIn(-1000.0, 50000.0)
        val safeBattery = batteryPercent.coerceIn(0L, 100L)
        val safeStatus = if (status in listOf(
                TrackedDevice.STATUS_ACTIVE,
                TrackedDevice.STATUS_PAUSED,
                TrackedDevice.STATUS_ALERT
            )
        ) status else TrackedDevice.STATUS_ACTIVE

        return try {
            val snap = docRef.get().await()
            if (!snap.exists()) {
                val createPayload = mapOf(
                    "userId" to uid,
                    "deviceName" to deviceName.take(60).ifBlank { "Android Device" },
                    "isTracking" to true,
                    "status" to safeStatus,
                    "latitude" to safeLat,
                    "longitude" to safeLng,
                    "accuracyMeters" to safeAcc,
                    "speedMps" to safeSpeed,
                    "altitudeMeters" to safeAlt,
                    "batteryPercent" to safeBattery,
                    "isCharging" to isCharging,
                    "updateIntervalSec" to updateIntervalSec.coerceIn(3L, 300L),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
                docRef.set(createPayload).await()
            } else {
                val telemetryUpdate = mapOf(
                    "latitude" to safeLat,
                    "longitude" to safeLng,
                    "accuracyMeters" to safeAcc,
                    "speedMps" to safeSpeed,
                    "altitudeMeters" to safeAlt,
                    "batteryPercent" to safeBattery,
                    "isCharging" to isCharging,
                    "isTracking" to true,
                    "status" to safeStatus,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
                docRef.update(telemetryUpdate).await()
            }

            if (recordPing) {
                val pingId = "ping_${UUID.randomUUID().toString().replace("-", "")}"
                val pingRef = docRef.collection("pings").document(pingId)
                val pingPayload = mapOf(
                    "userId" to uid,
                    "deviceId" to deviceId,
                    "latitude" to safeLat,
                    "longitude" to safeLng,
                    "accuracyMeters" to safeAcc,
                    "speedMps" to safeSpeed,
                    "batteryPercent" to safeBattery,
                    "createdAt" to FieldValue.serverTimestamp(),
                )
                pingRef.set(pingPayload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun updateDeviceSettings(
        deviceId: String,
        deviceName: String,
        isTracking: Boolean,
        status: String,
        updateIntervalSec: Long,
    ): Result<Unit> {
        val uid = runCatching { requireUserId() }.getOrElse { return Result.failure(it) }
        val docRef = db.collection("users").document(uid).collection("devices").document(deviceId)
        val safeName = deviceName.trim().take(60).ifBlank { "Android Device" }
        val safeInterval = updateIntervalSec.coerceIn(3L, 300L)
        val safeStatus = if (status in listOf(
                TrackedDevice.STATUS_ACTIVE,
                TrackedDevice.STATUS_PAUSED,
                TrackedDevice.STATUS_ALERT
            )
        ) status else TrackedDevice.STATUS_PAUSED

        return try {
            val snap = docRef.get().await()
            if (!snap.exists()) {
                val createPayload = mapOf(
                    "userId" to uid,
                    "deviceName" to safeName,
                    "isTracking" to isTracking,
                    "status" to safeStatus,
                    "latitude" to 0.0,
                    "longitude" to 0.0,
                    "accuracyMeters" to 0.0,
                    "speedMps" to 0.0,
                    "altitudeMeters" to 0.0,
                    "batteryPercent" to 100L,
                    "isCharging" to false,
                    "updateIntervalSec" to safeInterval,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
                docRef.set(createPayload).await()
            } else {
                val updatePayload = mapOf(
                    "deviceName" to safeName,
                    "isTracking" to isTracking,
                    "status" to safeStatus,
                    "updateIntervalSec" to safeInterval,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
                docRef.update(updatePayload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun deleteDevice(deviceId: String): Result<Unit> {
        val uid = runCatching { requireUserId() }.getOrElse { return Result.failure(it) }
        val docRef = db.collection("users").document(uid).collection("devices").document(deviceId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path, auth)
            Result.failure(e)
        }
    }
}
