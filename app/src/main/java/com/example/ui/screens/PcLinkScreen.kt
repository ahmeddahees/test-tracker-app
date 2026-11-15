package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.service.LiveServiceState
import com.example.ui.theme.AmberContainer
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.BorderSlate
import com.example.ui.theme.CyanTelemetry
import com.example.ui.theme.ElevatedSurface
import com.example.ui.theme.EmeraldShield
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.viewmodel.GuardConfigUiState
import java.util.Locale

@Composable
fun PcLinkScreen(
    guardConfig: GuardConfigUiState,
    serviceState: LiveServiceState,
    consoleUrl: String,
    mapsUrl: String?,
) {
    val context = LocalContext.current

    fun copyText(label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(context, "تم النسخ إلى الحافظة", Toast.LENGTH_SHORT).show()
    }

    fun shareText(subject: String, text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, "إرسال إلى الكمبيوتر"))
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ElevatedSurface),
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, CyanTelemetry.copy(alpha = 0.5f), RoundedCornerShape(22.dp))
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Computer,
                                contentDescription = null,
                                tint = CyanTelemetry,
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "متابعة الهاتف من الكمبيوتر لحظة بلحظة",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "يرسل هذا التطبيق موقع هاتفك تلقائياً إلى قاعدة بيانات Firebase السحابية المرتبطة بحساب Google الخاص بك. لفتح البث المباشر على جهاز الكمبيوتر:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        CodeInfoRow(label = "معرف الجهاز", value = guardConfig.deviceId)
                        Spacer(modifier = Modifier.height(6.dp))
                        CodeInfoRow(label = "مشروع Firebase", value = guardConfig.firebaseProjectId)
                        Spacer(modifier = Modifier.height(6.dp))
                        CodeInfoRow(label = "قاعدة البيانات", value = guardConfig.firestoreDatabaseId)

                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { copyText("Firebase Live Tracker", consoleUrl) },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("copy_console_url_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ContentCopy,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("نسخ رابط الكمبيوتر")
                            }
                            OutlinedButton(
                                onClick = {
                                    shareText(
                                        "رابط تتبع الهاتف المباشر - حارس الموقع",
                                        "افتح هذا الرابط من متصفح الكمبيوتر المسجّل بنفس حساب Google لمتابعة إحداثيات الهاتف لحظة بلحظة:\n$consoleUrl"
                                    )
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("share_console_url_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Share,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("إرسال للكمبيوتر")
                            }
                        }
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
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Map,
                                contentDescription = null,
                                tint = EmeraldShield
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "رابط خريطة Google Maps الفوري",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (mapsUrl != null) {
                                String.format(
                                    Locale.US,
                                    "آخر إحداثي محفوظ: %.5f, %.5f",
                                    serviceState.latitude ?: 0.0,
                                    serviceState.longitude ?: 0.0
                                )
                            } else {
                                "فعّل الحماية أولاً للحصول على رابط خريطة مباشر لآخر نقطة."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (mapsUrl != null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = { copyText("Google Maps Location", mapsUrl) },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("copy_maps_url_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.ContentCopy,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("نسخ رابط الخريطة")
                                }
                                OutlinedButton(
                                    onClick = { shareText("موقع هاتفي الآن", mapsUrl) },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("share_maps_url_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Share,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("مشاركة")
                                }
                            }
                        }
                    }
                }
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = AmberContainer.copy(alpha = 0.7f)),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, AmberWarn.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Security,
                                contentDescription = null,
                                tint = AmberWarn
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "إرشادات مهمة في حالات السرقة",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        AdviceBullet("اضبط قفل الشاشة برقم سري قوي حتى لا يتمكن السارق من إغلاق بيانات الهاتف أو GPS من الإعدادات السريعة.")
                        AdviceBullet("فعّل أيضاً خدمة «العثور على جهازي» (Find My Device) من إعدادات Google للحماية المزدوجة على مستوى النظام.")
                        AdviceBullet("تأكد من استثناء التطبيق من توفير البطارية في إعدادات النظام حتى لا تتوقف الخدمة الأمامية بعد ساعات طويلة.")
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                }
                                context.startActivity(intent)
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("open_app_settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("فتح إعدادات البطارية والأذونات للتطبيق")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CodeInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF0A1628))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = value,
            fontFamily = JetBrainsMonoFamily,
            style = MaterialTheme.typography.labelSmall,
            color = EmeraldShield,
            maxLines = 1
        )
    }
}

@Composable
private fun AdviceBullet(text: String) {
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = Icons.Filled.Lock,
            contentDescription = null,
            tint = AmberWarn,
            modifier = Modifier
                .padding(top = 3.dp)
                .size(14.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onTertiaryContainer
        )
    }
}
