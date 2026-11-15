package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.service.LiveServiceState
import com.example.ui.theme.AmberContainer
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.BorderSlate
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.CrimsonContainer
import com.example.ui.theme.CyanTelemetry
import com.example.ui.theme.ElevatedSurface
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldShield
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.TextMuted
import com.example.ui.viewmodel.GuardConfigUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GuardScreen(
    guardConfig: GuardConfigUiState,
    serviceState: LiveServiceState,
    hasLocationPermission: Boolean,
    hasNotificationPermission: Boolean,
    hasBackgroundLocationPermission: Boolean,
    onRequestPermissions: () -> Unit,
    onRequestBackgroundLocation: () -> Unit,
    onStartTracking: () -> Unit,
    onStopTracking: () -> Unit,
    onPingNow: () -> Unit,
    onToggleTheftAlert: (Boolean) -> Unit,
    onSelectInterval: (Long) -> Unit,
    onRenameDevice: (String) -> Unit,
) {
    val context = LocalContext.current
    var showRenameDialog by remember { mutableStateOf(false) }
    var pendingName by remember(guardConfig.deviceName) { mutableStateOf(guardConfig.deviceName) }

    val accentColor = when {
        !serviceState.isRunning -> CyanTelemetry
        guardConfig.theftAlertEnabled -> CrimsonAlert
        else -> EmeraldShield
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (!hasLocationPermission || !hasNotificationPermission) {
                item {
                    PermissionBannerCard(
                        hasLocationPermission = hasLocationPermission,
                        hasNotificationPermission = hasNotificationPermission,
                        onRequestPermissions = onRequestPermissions,
                    )
                }
            } else if (!hasBackgroundLocationPermission) {
                item {
                    BackgroundLocationBannerCard(
                        onRequestBackgroundLocation = onRequestBackgroundLocation
                    )
                }
            }

            item {
                RadarShieldControlCard(
                    isRunning = serviceState.isRunning,
                    isAlertMode = guardConfig.theftAlertEnabled,
                    accentColor = accentColor,
                    hasLocationPermission = hasLocationPermission,
                    deviceName = guardConfig.deviceName,
                    onEditName = {
                        pendingName = guardConfig.deviceName
                        showRenameDialog = true
                    },
                    onToggleTracking = {
                        if (serviceState.isRunning) {
                            onStopTracking()
                        } else if (hasLocationPermission) {
                            onStartTracking()
                        } else {
                            onRequestPermissions()
                        }
                    }
                )
            }

            if (serviceState.lastError != null) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CrimsonContainer),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = serviceState.lastError,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
            }

            item {
                LiveTelemetryCard(
                    serviceState = serviceState,
                    onPingNow = onPingNow,
                    onOpenInMaps = { lat, lng ->
                        val uri = Uri.parse("https://www.google.com/maps?q=$lat,$lng")
                        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                    }
                )
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (guardConfig.theftAlertEnabled) {
                            CrimsonContainer.copy(alpha = 0.7f)
                        } else {
                            ElevatedSurface
                        }
                    ),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            1.dp,
                            if (guardConfig.theftAlertEnabled) CrimsonAlert else BorderSlate,
                            RoundedCornerShape(20.dp)
                        )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (guardConfig.theftAlertEnabled) {
                                            CrimsonAlert.copy(alpha = 0.2f)
                                        } else {
                                            AmberContainer
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Warning,
                                    contentDescription = "وضع طوارئ السرقة",
                                    tint = if (guardConfig.theftAlertEnabled) CrimsonAlert else AmberWarn
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "وضع طوارئ السرقة",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "يرفع تردد الإرسال إلى كل 5 ثوانٍ ويُظهر تنبيهاً أحمر في شاشة المراقبة.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = guardConfig.theftAlertEnabled,
                            onCheckedChange = onToggleTheftAlert,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = CrimsonAlert
                            ),
                            modifier = Modifier.testTag("theft_alert_switch")
                        )
                    }
                }
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ElevatedSurface),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, BorderSlate, RoundedCornerShape(20.dp))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "معدل تحديث الموقع إلى السحابة",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (guardConfig.theftAlertEnabled) {
                                "مقفل على 5 ثوانٍ حالياً لأن وضع الطوارئ مفعّل."
                            } else {
                                "اختر الفاصل الزمني بين كل إرسال إحداثيات:"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val intervals = listOf(5L to "5 ثوانٍ", 15L to "15 ثانية", 30L to "30 ثانية", 60L to "دقيقة")
                            intervals.forEach { (sec, label) ->
                                FilterChip(
                                    selected = guardConfig.updateIntervalSec == sec && !guardConfig.theftAlertEnabled,
                                    enabled = !guardConfig.theftAlertEnabled,
                                    onClick = { onSelectInterval(sec) },
                                    label = { Text(label) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = EmeraldContainer,
                                        selectedLabelColor = EmeraldShield
                                    ),
                                    modifier = Modifier.testTag("interval_chip_$sec")
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("تسمية هذا الجهاز") },
            text = {
                OutlinedTextField(
                    value = pendingName,
                    onValueChange = { pendingName = it.take(60) },
                    label = { Text("اسم الهاتف (مثال: هاتفي الشخصي)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("device_name_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRenameDevice(pendingName)
                        showRenameDialog = false
                    },
                    modifier = Modifier.testTag("save_device_name_button")
                ) {
                    Text("حفظ")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }
}

@Composable
private fun PermissionBannerCard(
    hasLocationPermission: Boolean,
    hasNotificationPermission: Boolean,
    onRequestPermissions: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = AmberContainer),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, AmberWarn.copy(alpha = 0.7f), RoundedCornerShape(20.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (!hasLocationPermission) Icons.Filled.LocationOn else Icons.Filled.NotificationsActive,
                    contentDescription = null,
                    tint = AmberWarn
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (!hasLocationPermission) {
                        "صلاحية الموقع مطلوبة لحماية الهاتف"
                    } else {
                        "صلاحية الإشعارات مطلوبة لاستمرار الخدمة"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "لتحديد موقع الهاتف بدقة وبثه للكمبيوتر في الخلفية عند التحرك، يرجى السماح بصلاحية الموقع الدقيق والإشعارات.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.9f)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onRequestPermissions,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AmberWarn,
                    contentColor = Color(0xFF1F1400)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("request_location_permission_button")
            ) {
                Text(
                    text = "منح صلاحيات الموقع الآن",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun BackgroundLocationBannerCard(
    onRequestBackgroundLocation: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = ElevatedSurface),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CyanTelemetry.copy(alpha = 0.65f), RoundedCornerShape(20.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Shield,
                    contentDescription = null,
                    tint = CyanTelemetry
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "استمرار التحديث حتى بعد إغلاق التطبيق",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "لضمان قدرة الخدمة الأمامية (Foreground Service) على استئناف البث إلى Firestore تلقائياً إذا أعاد النظام تشغيلها بعد إغلاق التطبيق، اختر «السماح طوال الوقت».",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = onRequestBackgroundLocation,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("request_background_location_button")
            ) {
                Text(
                    text = "السماح بالموقع طوال الوقت",
                    fontWeight = FontWeight.Bold,
                    color = CyanTelemetry
                )
            }
        }
    }
}

@Composable
private fun RadarShieldControlCard(
    isRunning: Boolean,
    isAlertMode: Boolean,
    accentColor: Color,
    hasLocationPermission: Boolean,
    deviceName: String,
    onEditName: () -> Unit,
    onToggleTracking: () -> Unit,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "radarPulse")
    val pulseProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseProgress"
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = ElevatedSurface),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, accentColor.copy(alpha = 0.55f), RoundedCornerShape(24.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.radialGradient(
                        colors = listOf(accentColor.copy(alpha = 0.14f), Color.Transparent)
                    )
                )
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (isRunning) accentColor else TextMuted)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = deviceName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                IconButton(
                    onClick = onEditName,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("edit_device_name_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = "تعديل اسم الجهاز",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Box(
                modifier = Modifier.size(164.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val maxRadius = size.minDimension / 2f
                    if (isRunning) {
                        val r1 = maxRadius * (0.5f + 0.5f * pulseProgress)
                        val r2 = maxRadius * (0.5f + 0.5f * ((pulseProgress + 0.5f) % 1f))
                        drawCircle(
                            color = accentColor.copy(alpha = (1f - pulseProgress) * 0.45f),
                            radius = r1,
                            style = Stroke(width = 3.dp.toPx())
                        )
                        drawCircle(
                            color = accentColor.copy(alpha = (1f - ((pulseProgress + 0.5f) % 1f)) * 0.45f),
                            radius = r2,
                            style = Stroke(width = 2.dp.toPx())
                        )
                    } else {
                        drawCircle(
                            color = BorderSlate,
                            radius = maxRadius * 0.88f,
                            style = Stroke(width = 1.5.dp.toPx())
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .size(108.dp)
                        .clip(CircleShape)
                        .background(
                            if (isRunning) accentColor else Color(0xFF1A2F4E)
                        )
                        .border(2.dp, accentColor, CircleShape)
                        .clickable(onClick = onToggleTracking)
                        .testTag("toggle_tracking_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = if (isRunning) Icons.Filled.Shield else Icons.Filled.PowerSettingsNew,
                            contentDescription = if (isRunning) "إيقاف البث" else "تفعيل الحماية",
                            tint = if (isRunning) Color(0xFF031910) else accentColor,
                            modifier = Modifier.size(34.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isRunning) "نشط" else "تشغيل",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (isRunning) Color(0xFF031910) else Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = when {
                    !hasLocationPermission -> "في انتظار منح صلاحية الموقع"
                    isRunning && isAlertMode -> "وضع طوارئ السرقة نشط — بث كل 5 ثوانٍ"
                    isRunning -> "البث المباشر نشط ومؤمّن في الخلفية"
                    else -> "اضغط لتفعيل حماية وتتبع هذا الهاتف"
                },
                style = MaterialTheme.typography.titleMedium,
                color = if (isRunning) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun LiveTelemetryCard(
    serviceState: LiveServiceState,
    onPingNow: () -> Unit,
    onOpenInMaps: (Double, Double) -> Unit,
) {
    val timeFormatter = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }
    val lastSyncFormatted = serviceState.lastSyncEpochMs?.let { timeFormatter.format(Date(it)) } ?: "--:--:--"

    Card(
        colors = CardDefaults.cardColors(containerColor = ElevatedSurface),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BorderSlate, RoundedCornerShape(20.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.GpsFixed,
                        contentDescription = null,
                        tint = CyanTelemetry,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "القياس المباشر لهذا الهاتف",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = "آخر مزامنة: $lastSyncFormatted",
                    style = MaterialTheme.typography.labelSmall,
                    color = CyanTelemetry
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            val coordsText = if (serviceState.latitude != null && serviceState.longitude != null) {
                String.format(Locale.US, "%.6f, %.6f", serviceState.latitude, serviceState.longitude)
            } else {
                "في انتظار إشارة GPS..."
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF0A1628))
                    .border(1.dp, BorderSlate.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Text(
                    text = coordsText,
                    fontFamily = JetBrainsMonoFamily,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (serviceState.latitude != null) EmeraldShield else TextMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TelemetryBadge(
                    label = "الدقة",
                    value = serviceState.accuracyMeters?.let { String.format(Locale.US, "±%.0f م", it) } ?: "--",
                    modifier = Modifier.weight(1f)
                )
                TelemetryBadge(
                    label = "البطارية",
                    value = "${serviceState.batteryPercent}%",
                    icon = if (serviceState.isCharging) Icons.Filled.BatteryChargingFull else Icons.Filled.BatteryStd,
                    modifier = Modifier.weight(1f)
                )
                TelemetryBadge(
                    label = "النبضات",
                    value = "${serviceState.pingsSentCount}",
                    modifier = Modifier.weight(1f)
                )
            }

            if (serviceState.isRunning) {
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onPingNow,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("ping_now_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("تحديث فوري")
                    }

                    if (serviceState.latitude != null && serviceState.longitude != null) {
                        Button(
                            onClick = { onOpenInMaps(serviceState.latitude, serviceState.longitude) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("open_maps_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Map,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("الخريطة")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TelemetryBadge(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0D1B2F))
            .padding(vertical = 10.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = EmeraldShield,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
            }
            Text(
                text = value,
                fontFamily = JetBrainsMonoFamily,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
