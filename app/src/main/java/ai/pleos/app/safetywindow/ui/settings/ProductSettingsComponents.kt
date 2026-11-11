package ai.pleos.app.safetywindow.ui.settings

import ai.pleos.app.safetywindow.BuildConfig
import ai.pleos.app.safetywindow.core.settings.ProductSettings
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * ProductSettingsComponents.kt
 * Pleos Safety Window - Work Order #41
 * Minimal, driver-distraction-safe product settings entry point & dialog.
 * Numeric specifications:
 * - Anchor Touch Target: 48dp x 48dp
 * - Anchor Icon Size: 24dp (gear symbol)
 * - Margins: Bottom 16dp, End 16dp
 * - Dialog Max Dimensions: Width 320dp, Max Height 340dp
 * - Font Sizes: Title 16sp, Body 13sp, Caption 11sp
 * - Animation Duration: 200ms
 */

@Composable
fun ProductSettingsAnchor(
    isDrivingRestricted: Boolean,
    onOpenPanel: () -> Unit,
    onShowDrivingNotice: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .padding(bottom = 16.dp, end = 16.dp)
            .size(48.dp)
            .clickable {
                if (isDrivingRestricted) {
                    onShowDrivingNotice()
                } else {
                    onOpenPanel()
                }
            },
        shape = CircleShape,
        color = Color(0xFF1E293B).copy(alpha = 0.88f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.6f))
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(48.dp)
        ) {
            Text(
                text = "⚙",
                fontSize = 20.sp,
                color = Color(0xFFF8FAFC)
            )
        }
    }
}

@Composable
fun ProductSettingsDrivingNotice(
    message: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .padding(bottom = 70.dp, end = 16.dp),
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFFE11D48).copy(alpha = 0.95f)
    ) {
        Text(
            text = message,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
fun ProductSettingsDialog(
    isOpen: Boolean,
    settings: ProductSettings,
    isDrivingRestricted: Boolean,
    onToggleUserEnabled: (Boolean) -> Unit,
    onToggleVoiceMuted: (Boolean) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showUninstallDisclaimer by remember { mutableStateOf(false) }
    val context = LocalContext.current

    AnimatedVisibility(
        visible = isOpen,
        enter = fadeIn(animationSpec = tween(200)),
        exit = fadeOut(animationSpec = tween(200)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .padding(bottom = 72.dp, end = 16.dp)
                .width(320.dp)
                .background(Color(0xFF0F172A), RoundedCornerShape(12.dp))
                .border(1.dp, Color(0xFF38BDF8).copy(alpha = 0.8f), RoundedCornerShape(12.dp))
                .padding(16.dp)
        ) {
            if (showUninstallDisclaimer) {
                // Uninstall & App Info Sub-Panel (앱 정보 및 제거 설정)
                Column {
                    Text(
                        text = "앱 정보 및 제거 설정 안내",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFBBF24)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "• 안드로이드 시스템 앱 설정 화면으로 이동하여 앱 정보를 확인하거나 삭제할 수 있습니다.\n" +
                               "• 자체 임의 삭제가 아니며, 삭제 가능 여부는 OS 시스템 및 플랫폼 정책에 따릅니다.\n" +
                               "• 시스템 설정 화면에서 언제든 취소하거나 복귀할 수 있습니다.",
                        fontSize = 12.sp,
                        color = Color(0xFFE2E8F0),
                        lineHeight = 16.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            onClick = { showUninstallDisclaimer = false },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155))
                        ) {
                            Text("취소", fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                navigateToAppSystemSettings(context)
                                showUninstallDisclaimer = false
                                onClose()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE11D48))
                        ) {
                            Text("시스템 설정 이동", fontSize = 12.sp)
                        }
                    }
                }
            } else {
                // Main Settings Panel
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Pleos Safety Window 설정",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF38BDF8)
                        )
                        Text(
                            text = "✕",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF94A3B8),
                            modifier = Modifier
                                .clickable { onClose() }
                                .padding(4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 1. App Safety Service ON/OFF
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "앱 안전 안내 서비스",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                            Text(
                                text = if (settings.userEnabled) "백그라운드 차폐 안내 활성" else "안내 비활성 (침묵 L0 유지)",
                                fontSize = 11.sp,
                                color = if (settings.userEnabled) Color(0xFF4ADE80) else Color(0xFF94A3B8)
                            )
                        }
                        Switch(
                            checked = settings.userEnabled,
                            onCheckedChange = { onToggleUserEnabled(it) },
                            enabled = !isDrivingRestricted,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF38BDF8),
                                checkedTrackColor = Color(0xFF0369A1)
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 2. Voice Mute ON/OFF
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "위험 음성 안내 무음",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                            val voiceSubtitle = if (BuildConfig.DEBUG) {
                                if (settings.voiceMuted) "음성 TTS 억제 (개발 발화 차단)" else "음성 안내 활성 (개발 정상 발화)"
                            } else {
                                if (settings.voiceMuted) {
                                    "음성 무음 선택됨 · [차량 음향 연동 전] 현재 안전 침묵 유지"
                                } else {
                                    "음성 켜짐 선택됨 · [차량 음향 연동 전] 현재 안전 침묵 유지"
                                }
                            }
                            Text(
                                text = voiceSubtitle,
                                fontSize = 11.sp,
                                color = if (settings.voiceMuted) Color(0xFFFBBF24) else Color(0xFF94A3B8)
                            )
                        }
                        Switch(
                            checked = settings.voiceMuted,
                            onCheckedChange = { onToggleVoiceMuted(it) },
                            enabled = !isDrivingRestricted,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFFFBBF24),
                                checkedTrackColor = Color(0xFFB45309)
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 3. App Info & Uninstall Entry
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1E293B), RoundedCornerShape(6.dp))
                            .clickable { showUninstallDisclaimer = true }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "앱 정보 및 제거 설정",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFE2E8F0)
                        )
                        Text(
                            text = "▶",
                            fontSize = 11.sp,
                            color = Color(0xFF38BDF8)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Footer Version & Operational Mode
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "v1.0.0",
                            fontSize = 10.sp,
                            color = Color(0xFF64748B)
                        )
                        Text(
                            text = if (BuildConfig.DEBUG) "모드: 디버그 시연 호스트" else "모드: 상용 서비스",
                            fontSize = 10.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                }
            }
        }
    }
}

private fun navigateToAppSystemSettings(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        // Fallback to generic settings if platform restricts details
        try {
            val genericIntent = Intent(Settings.ACTION_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(genericIntent)
        } catch (_: Exception) {}
    }
}
