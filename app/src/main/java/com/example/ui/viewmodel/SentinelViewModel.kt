package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.LocationPing
import com.example.data.model.TrackedDevice
import com.example.data.remote.unwrapFirestoreException
import com.example.data.repository.TrackingRepository
import com.example.service.DeviceIdentityStore
import com.example.service.LiveServiceState
import com.example.service.TrackingStatusHolder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

data class GuardConfigUiState(
    val deviceId: String,
    val deviceName: String,
    val updateIntervalSec: Long,
    val theftAlertEnabled: Boolean,
    val firebaseProjectId: String,
    val firestoreDatabaseId: String,
    val currentUserId: String,
    val actionMessage: String? = null,
)

class SentinelViewModel(
    private val repository: TrackingRepository,
    private val identityStore: DeviceIdentityStore,
    private val currentUserId: String,
    private val firebaseProjectId: String,
    private val firestoreDatabaseId: String,
) : ViewModel() {

    val localDeviceId: String = identityStore.getOrCreateDeviceId()

    private val _guardConfig = MutableStateFlow(
        GuardConfigUiState(
            deviceId = localDeviceId,
            deviceName = identityStore.getDeviceName(),
            updateIntervalSec = identityStore.getUpdateIntervalSec(),
            theftAlertEnabled = identityStore.isTheftAlertEnabled(),
            firebaseProjectId = firebaseProjectId,
            firestoreDatabaseId = firestoreDatabaseId,
            currentUserId = currentUserId,
        )
    )
    val guardConfig: StateFlow<GuardConfigUiState> = _guardConfig.asStateFlow()

    val serviceState: StateFlow<LiveServiceState> = TrackingStatusHolder.state

    private val _selectedDeviceId = MutableStateFlow(localDeviceId)
    val selectedDeviceId: StateFlow<String> = _selectedDeviceId.asStateFlow()

    val devicesState: StateFlow<UiState<List<TrackedDevice>>> =
        repository.observeDevices(currentUserId)
            .map<List<TrackedDevice>, UiState<List<TrackedDevice>>> { devices ->
                if (devices.isNotEmpty() && devices.none { it.id == _selectedDeviceId.value }) {
                    _selectedDeviceId.value = devices.first().id
                }
                UiState.Success(devices)
            }
            .catch { error ->
                val firestoreError = error.unwrapFirestoreException()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing devices", rootError)
                emit(UiState.Error(rootError.message ?: "تعذر تحميل الأجهزة المتصلة"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    @OptIn(ExperimentalCoroutinesApi::class)
    val pingsState: StateFlow<UiState<List<LocationPing>>> =
        _selectedDeviceId
            .flatMapLatest { deviceId ->
                if (deviceId.isBlank()) {
                    flowOf(UiState.Success(emptyList()))
                } else {
                    repository.observePings(currentUserId, deviceId)
                        .map<List<LocationPing>, UiState<List<LocationPing>>> { UiState.Success(it) }
                        .catch { error ->
                            val firestoreError = error.unwrapFirestoreException()
                            if (firestoreError == null && error is CancellationException) throw error
                            val rootError = firestoreError ?: error
                            Log.w(TAG, "Error observing pings", rootError)
                            emit(UiState.Error(rootError.message ?: "تعذر تحميل سجل المواقع"))
                        }
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    init {
        viewModelScope.launch {
            repository.ensureDeviceRegistered(
                deviceId = localDeviceId,
                deviceName = identityStore.getDeviceName(),
                isTracking = TrackingStatusHolder.state.value.isRunning,
                status = when {
                    identityStore.isTheftAlertEnabled() -> TrackedDevice.STATUS_ALERT
                    TrackingStatusHolder.state.value.isRunning -> TrackedDevice.STATUS_ACTIVE
                    else -> TrackedDevice.STATUS_PAUSED
                },
                updateIntervalSec = identityStore.getUpdateIntervalSec(),
            )
        }
    }

    fun selectDevice(deviceId: String) {
        _selectedDeviceId.value = deviceId
    }

    fun updateDeviceName(newName: String) {
        val clean = newName.trim().take(60)
        if (clean.isBlank()) return
        identityStore.setDeviceName(clean)
        _guardConfig.value = _guardConfig.value.copy(deviceName = clean, actionMessage = "تم تحديث اسم الجهاز")
        syncSettingsToCloud()
    }

    fun updateInterval(seconds: Long) {
        val safe = seconds.coerceIn(5L, 300L)
        identityStore.setUpdateIntervalSec(safe)
        _guardConfig.value = _guardConfig.value.copy(updateIntervalSec = safe)
        syncSettingsToCloud()
    }

    fun toggleTheftAlert(enabled: Boolean) {
        identityStore.setTheftAlertEnabled(enabled)
        _guardConfig.value = _guardConfig.value.copy(
            theftAlertEnabled = enabled,
            actionMessage = if (enabled) {
                "تم تفعيل وضع طوارئ السرقة (تحديث كل 5 ثوانٍ)"
            } else {
                "تم إيقاف وضع طوارئ السرقة"
            }
        )
        syncSettingsToCloud()
    }

    fun clearActionMessage() {
        _guardConfig.value = _guardConfig.value.copy(actionMessage = null)
    }

    private fun syncSettingsToCloud() {
        val running = TrackingStatusHolder.state.value.isRunning
        val isAlert = identityStore.isTheftAlertEnabled()
        val status = when {
            isAlert -> TrackedDevice.STATUS_ALERT
            running -> TrackedDevice.STATUS_ACTIVE
            else -> TrackedDevice.STATUS_PAUSED
        }
        viewModelScope.launch {
            repository.updateDeviceSettings(
                deviceId = localDeviceId,
                deviceName = identityStore.getDeviceName(),
                isTracking = running,
                status = status,
                updateIntervalSec = if (isAlert) 5L else identityStore.getUpdateIntervalSec(),
            )
        }
    }

    fun deleteDevice(deviceId: String) {
        if (deviceId == localDeviceId) return
        viewModelScope.launch {
            repository.deleteDevice(deviceId)
                .onSuccess {
                    _guardConfig.value = _guardConfig.value.copy(actionMessage = "تمت إزالة الجهاز من القائمة")
                }
        }
    }

    fun buildFirestoreConsoleUrl(): String {
        return "https://console.firebase.google.com/project/$firebaseProjectId/firestore/databases/$firestoreDatabaseId/data/~2Fusers~2F$currentUserId~2Fdevices~2F$localDeviceId"
    }

    fun buildGoogleMapsUrl(lat: Double, lng: Double): String {
        return "https://www.google.com/maps?q=$lat,$lng"
    }

    private companion object {
        const val TAG = "SentinelVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
