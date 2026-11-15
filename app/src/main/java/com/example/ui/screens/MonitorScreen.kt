package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.model.LocationPing
import com.example.data.model.TrackedDevice
import com.example.ui.theme.BorderSlate
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.CrimsonContainer
import com.example.ui.theme.CyanTelemetry
import com.example.ui.theme.ElevatedSurface
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldShield
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.TextMuted
import com.example.ui.viewmodel.UiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

@Composable
fun MonitorScreen(
    devicesState: UiState<List<TrackedDevice>>,
    pingsState: UiState<List<LocationPing>>,
    selectedDeviceId: String,
    localDeviceId: String,
    onSelectDevice: (String) -> Unit,
    onDeleteDevice: (String) -> Unit,
) {
    val context = LocalContext.current

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        when (devicesState) {
            is UiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = EmeraldShield)
                }
            }
            is UiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = devicesState.message,
                        color = CrimsonAlert,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                }
            }
            is UiState.Success -> {
                val devices = devicesState.data
                val selectedDevice = devices.firstOrNull { it.id == selectedDeviceId } ?: devices.firstOrNull()
                val pings = (pingsState as? UiState.Success)?.data ?: emptyList()

                if (selectedDevice == null) {
                    EmptyMonitorState()
                } else {
                    val accentColor = when (selectedDevice.status) {
                        TrackedDevice.STATUS_ALERT -> CrimsonAlert
                        TrackedDevice.STATUS_ACTIVE -> EmeraldShield
                        else -> CyanTelemetry
                    }
                    LazyColumn(
                        modifier = Modifier
                            .widthIn(max = 680.dp)
                            .fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                devices.forEach { device ->
                                    val isLocal = device.id == localDeviceId
                                    FilterChip(
                                        selected = device.id == selectedDevice.id,
                                        onClick = { onSelectDevice(device.id) },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Filled.Smartphone,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        label = {
                                            Text(
                                                text = if (isLocal) "${device.deviceName} (هذا)" else device.deviceName
                                            )
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = EmeraldContainer,
                                            selectedLabelColor = EmeraldShield
                                        ),
                                        modifier = Modifier.testTag("device_chip_${device.id}")
                                    )
                                }
                            }
                        }

                        item {
                            DeviceRadarPlotCard(
                                device = selectedDevice,
                                pings = pings,
                                accentColor = accentColor,
                                canDelete = selectedDevice.id != localDeviceId,
                                onDelete = { onDeleteDevice(selectedDevice.id) },
                                onOpenGoogleMaps = {
                                    val uri = Uri.parse(
                                        "https://www.google.com/maps?q=${selectedDevice.latitude},${selectedDevice.longitude}"
                                    )
                                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                                },
                                onCopyCoords = {
                                    val coords = String.format(
                                        Locale.US,
                                        "%.6f, %.6f",
                                        selectedDevice.latitude,
                                        selectedDevice.longitude
                                    )
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("GPS Coordinates", coords))
                                    Toast.makeText(context, "تم نسخ الإحداثيات", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }

                        item {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Timeline,
                                    contentDescription = null,
                                    tint = CyanTelemetry,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "سجل التحركات الأخيرة (${pings.size})",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            }
                        }

                        if (pings.isEmpty()) {
                            item {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = ElevatedSurface),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "لم تُسجل نقاط حركة بعد لهذا الجهاز. فعّل التتبع من تبويب الحماية.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(16.dp)
                                    )
                                }
                            }
                        } else {
                            items(pings, key = { it.id }) { ping ->
                                PingTimelineRow(ping = ping)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyMonitorState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.MyLocation,
            contentDescription = null,
            tint = CyanTelemetry,
            modifier = Modifier.size(56.dp)
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "لا توجد أجهزة مسجلة بعد",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "ابدأ البث من تبويب الحماية ليظهر موقع جهازك ومسار حركته هنا لحظة بلحظة.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun DeviceRadarPlotCard(
    device: TrackedDevice,
    pings: List<LocationPing>,
    accentColor: Color,
    canDelete: Boolean,
    onDelete: () -> Unit,
    onOpenGoogleMaps: () -> Unit,
    onCopyCoords: () -> Unit,
) {
    val timeFormatter = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US) }
    val updatedAtText = (device.updatedAt ?: device.createdAt)?.toDate()?.let {
        timeFormatter.format(it)
    } ?: "الآن"

    val hasValidFix = device.latitude != 0.0 || device.longitude != 0.0

    Card(
        colors = CardDefaults.cardColors(containerColor = ElevatedSurface),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, accentColor.copy(alpha = 0.6f), RoundedCornerShape(22.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(accentColor)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = device.deviceName,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = when (device.status) {
                            TrackedDevice.STATUS_ALERT -> "وضع طوارئ السرقة نشط • آخر تحديث $updatedAtText"
                            TrackedDevice.STATUS_ACTIVE -> "متصل ويبث الموقع • آخر تحديث $updatedAtText"
                            else -> "متوقف مؤقتاً • آخر تحديث $updatedAtText"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (device.status == TrackedDevice.STATUS_ALERT) CrimsonAlert else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (canDelete) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "حذف الجهاز",
                            tint = TextMuted
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Interactive Radar & Trajectory Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF071120))
                    .border(1.dp, BorderSlate, RoundedCornerShape(16.dp))
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val center = Offset(w / 2f, h / 2f)

                    // Tactical grid lines
                    val gridStep = 36.dp.toPx()
                    var x = gridStep
                    while (x < w) {
                        drawLine(
                            color = Color(0xFF132642),
                            start = Offset(x, 0f),
                            end = Offset(x, h),
                            strokeWidth = 1f
                        )
                        x += gridStep
                    }
                    var y = gridStep
                    while (y < h) {
                        drawLine(
                            color = Color(0xFF132642),
                            start = Offset(0f, y),
                            end = Offset(w, y),
                            strokeWidth = 1f
                        )
                        y += gridStep
                    }

                    // Radar rings
                    listOf(0.28f, 0.56f, 0.84f).forEach { factor ->
                        drawCircle(
                            color = accentColor.copy(alpha = 0.22f),
                            radius = (minOf(w, h) / 2f) * factor,
                            center = center,
                            style = Stroke(width = 1.5f)
                        )
                    }

                    if (hasValidFix) {
                        val chronological = pings.reversed()
                        if (chronological.size >= 2) {
                            val allLats = chronological.map { it.latitude } + device.latitude
                            val allLngs = chronological.map { it.longitude } + device.longitude
                            val minLat = allLats.min()
                            val maxLat = allLats.max()
                            val minLng = allLngs.min()
                            val maxLng = allLngs.max()
                            val latSpan = max(maxLat - minLat, 0.0008)
                            val lngSpan = max(maxLng - minLng, 0.0008)
                            val pad = 28.dp.toPx()

                            fun toCanvasPoint(lat: Double, lng: Double): Offset {
                                val nx = ((lng - minLng) / lngSpan).toFloat()
                                val ny = 1f - ((lat - minLat) / latSpan).toFloat()
                                return Offset(
                                    x = pad + nx * (w - 2 * pad),
                                    y = pad + ny * (h - 2 * pad)
                                )
                            }

                            val trailPath = Path()
                            chronological.forEachIndexed { idx, ping ->
                                val pt = toCanvasPoint(ping.latitude, ping.longitude)
                                if (idx == 0) trailPath.moveTo(pt.x, pt.y) else trailPath.lineTo(pt.x, pt.y)
                            }
                            val livePt = toCanvasPoint(device.latitude, device.longitude)
                            trailPath.lineTo(livePt.x, livePt.y)

                            drawPath(
                                path = trailPath,
                                color = CyanTelemetry.copy(alpha = 0.85f),
                                style = Stroke(
                                    width = 3.dp.toPx(),
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )

                            chronological.forEach { ping ->
                                val pt = toCanvasPoint(ping.latitude, ping.longitude)
                                drawCircle(
                                    color = CyanTelemetry,
                                    radius = 3.5.dp.toPx(),
                                    center = pt
                                )
                            }

                            // Accuracy halo + live marker
                            drawCircle(
                                color = accentColor.copy(alpha = 0.25f),
                                radius = 22.dp.toPx(),
                                center = livePt
                            )
                            drawCircle(
                                color = accentColor,
                                radius = 7.dp.toPx(),
                                center = livePt
                            )
                            drawCircle(
                                color = Color.White,
                                radius = 3.dp.toPx(),
                                center = livePt
                            )
                        } else {
                            drawCircle(
                                color = accentColor.copy(alpha = 0.25f),
                                radius = 26.dp.toPx(),
                                center = center
                            )
                            drawCircle(
                                color = accentColor,
                                radius = 8.dp.toPx(),
                                center = center
                            )
                            drawCircle(
                                color = Color.White,
                                radius = 3.5.dp.toPx(),
                                center = center
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(10.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xCC091627))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = if (hasValidFix) {
                            String.format(Locale.US, "%.5f, %.5f", device.latitude, device.longitude)
                        } else {
                            "بانتظار أول إحداثي GPS"
                        },
                        fontFamily = JetBrainsMonoFamily,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (hasValidFix) accentColor else TextMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricBox(
                    title = "السرعة",
                    value = String.format(Locale.US, "%.1f كم/س", device.speedMps * 3.6),
                    modifier = Modifier.weight(1f)
                )
                MetricBox(
                    title = "الدقة",
                    value = String.format(Locale.US, "±%.0f م", device.accuracyMeters),
                    modifier = Modifier.weight(1f)
                )
                MetricBox(
                    title = "البطارية",
                    value = "${device.batteryPercent}%",
                    modifier = Modifier.weight(1f)
                )
            }

            if (hasValidFix) {
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onOpenGoogleMaps,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1.3f)
                            .height(48.dp)
                            .testTag("monitor_open_maps_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Map,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("فتح في خرائط Google")
                    }
                    OutlinedButton(
                        onClick = onCopyCoords,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("monitor_copy_coords_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("نسخ")
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricBox(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0A1628))
            .padding(vertical = 10.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            fontFamily = JetBrainsMonoFamily,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun PingTimelineRow(ping: LocationPing) {
    val timeFormatter = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }
    val timeLabel = ping.createdAt?.toDate()?.let { timeFormatter.format(it) } ?: "--:--:--"

    Card(
        colors = CardDefaults.cardColors(containerColor = ElevatedSurface),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BorderSlate.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = String.format(Locale.US, "%.6f, %.6f", ping.latitude, ping.longitude),
                    fontFamily = JetBrainsMonoFamily,
                    style = MaterialTheme.typography.bodyMedium,
                    color = EmeraldShield
                )
                Text(
                    text = String.format(
                        Locale.US,
                        "دقة ±%.0fم • سرعة %.1f كم/س • بطارية %d%%",
                        ping.accuracyMeters,
                        ping.speedMps * 3.6,
                        ping.batteryPercent
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = timeLabel,
                fontFamily = JetBrainsMonoFamily,
                style = MaterialTheme.typography.labelSmall,
                color = CyanTelemetry
            )
        }
    }
}
