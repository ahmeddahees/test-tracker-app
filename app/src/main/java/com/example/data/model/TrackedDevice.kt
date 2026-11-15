package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class TrackedDevice(
    @DocumentId val id: String = "",
    val userId: String = "",
    val deviceName: String = "",
    val isTracking: Boolean = false,
    val status: String = STATUS_PAUSED,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val accuracyMeters: Double = 0.0,
    val speedMps: Double = 0.0,
    val altitudeMeters: Double = 0.0,
    val batteryPercent: Long = 0L,
    val isCharging: Boolean = false,
    val updateIntervalSec: Long = 10L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    companion object {
        const val STATUS_ACTIVE = "active"
        const val STATUS_PAUSED = "paused"
        const val STATUS_ALERT = "alert"
    }
}

@IgnoreExtraProperties
data class LocationPing(
    @DocumentId val id: String = "",
    val userId: String = "",
    val deviceId: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val accuracyMeters: Double = 0.0,
    val speedMps: Double = 0.0,
    val batteryPercent: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
)
