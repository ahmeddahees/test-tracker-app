package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.TrackedDevice
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class TrackingRepositoryRuleTest : FirestoreEmulatorTestBase() {

    private fun newDeviceId(): String = "dev_${UUID.randomUUID().toString().replace("-", "")}"

    @Test
    fun publishLocationFix_authenticatedOwner_createsDeviceAndPing() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = TrackingRepository(firestore, auth)
        val deviceId = newDeviceId()

        val result = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.publishLocationFix(
                deviceId = deviceId,
                deviceName = "Pixel 9",
                latitude = 30.0444,
                longitude = 31.2357,
                accuracyMeters = 6.5,
                speedMps = 2.0,
                altitudeMeters = 24.0,
                batteryPercent = 88L,
                isCharging = false,
                status = TrackedDevice.STATUS_ACTIVE,
                updateIntervalSec = 10L,
                recordPing = true,
            )
        }
        assertTrue(result.isSuccess)

        val fetched = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.getDevice(deviceId).getOrThrow()
        }
        assertNotNull(fetched)
        assertEquals(deviceId, fetched?.id)
        assertEquals(uid, fetched?.userId)
        assertEquals(30.0444, fetched?.latitude ?: 0.0, 0.0001)

        val pings = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observePings(uid, deviceId).first { it.isNotEmpty() }
        }
        assertEquals(1, pings.size)
        assertEquals(deviceId, pings.first().deviceId)
    }

    @Test
    fun observeDevices_authenticatedOwner_emitsRealtimeUpdates() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = TrackingRepository(firestore, auth)
        val deviceId = newDeviceId()

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.ensureDeviceRegistered(
                deviceId = deviceId,
                deviceName = "My Galaxy",
                isTracking = true,
                status = TrackedDevice.STATUS_ACTIVE,
            ).getOrThrow()
        }

        val devices = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeDevices(uid).first { list -> list.any { it.id == deviceId } }
        }
        assertTrue(devices.any { it.id == deviceId && it.deviceName == "My Galaxy" })

        val updateResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.updateDeviceSettings(
                deviceId = deviceId,
                deviceName = "My Galaxy Pro",
                isTracking = true,
                status = TrackedDevice.STATUS_ALERT,
                updateIntervalSec = 5L,
            )
        }
        assertTrue(updateResult.isSuccess)

        val updatedDevices = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeDevices(uid).first { list ->
                list.any { it.id == deviceId && it.status == TrackedDevice.STATUS_ALERT }
            }
        }
        assertEquals(5L, updatedDevices.first { it.id == deviceId }.updateIntervalSec)
    }

    @Test
    fun observeDevices_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = TrackingRepository(firestore, auth)
        val deviceId = newDeviceId()

        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.ensureDeviceRegistered(deviceId, "Alice Phone").getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        val bobRepo = TrackingRepository(firestore, auth)

        val failure = runCatching {
            withTimeout(FLOW_TIMEOUT_MS) {
                bobRepo.observeDevices(aliceUid).first()
            }
        }.exceptionOrNull()

        assertNotNull(failure)
        val firestoreException = checkNotNull(failure?.unwrapFirestoreException()) {
            "Expected nested FirebaseFirestoreException, got $failure"
        }
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException.code)
    }

    @Test
    fun observeDevices_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repository = TrackingRepository(firestore, auth)

        val failure = runCatching {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeDevices("some_uid_123").first()
            }
        }.exceptionOrNull()

        assertNotNull(failure)
        val firestoreException = checkNotNull(failure?.unwrapFirestoreException()) {
            "Expected nested FirebaseFirestoreException, got $failure"
        }
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException.code)
    }

    private companion object {
        const val ALICE_EMAIL = "alice@test.com"
        const val BOB_EMAIL = "bob@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
