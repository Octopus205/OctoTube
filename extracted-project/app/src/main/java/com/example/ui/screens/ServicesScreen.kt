package com.example.ui.screens

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.OctoSettings
import com.example.domain.model.ServiceInstanceHealth
import com.example.domain.model.ServiceProvider
import com.example.domain.model.SponsorCategory
import com.example.ui.theme.OctoCoral
import com.example.ui.theme.OctoTeal
import com.example.ui.theme.SponsorSegmentGreen

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ServicesScreen(
    settings: OctoSettings,
    healthList: List<ServiceInstanceHealth>,
    isPinging: Boolean,
    onSelectProvider: (ServiceProvider) -> Unit,
    onUpdateCustomUrl: (ServiceProvider, String) -> Unit,
    onPingAll: () -> Unit,
    onToggleSponsorBlock: (Boolean) -> Unit,
    onToggleAutoSkipSponsor: (Boolean) -> Unit,
    onToggleSponsorCategory: (String) -> Unit,
    onToggleBackgroundOnLeave: (Boolean) -> Unit,
    onToggleAutoPip: (Boolean) -> Unit,
    onToggleSubtitlesAuto: (Boolean) -> Unit,
    onSelectPreferredQuality: (String) -> Unit,
    onSelectPreferredSubLang: (String) -> Unit,
    onToggleDarkTheme: (Boolean) -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    var editingProvider by remember { mutableStateOf<ServiceProvider?>(null) }
    var editingUrlText by remember { mutableStateOf("") }

    editingProvider?.let { provider ->
        AlertDialog(
            onDismissRequest = { editingProvider = null },
            title = { Text("تعديل خادم ${provider.displayName}") },
            text = {
                OutlinedTextField(
                    value = editingUrlText,
                    onValueChange = { editingUrlText = it },
                    label = { Text("رابط الخادم (Instance URL)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateCustomUrl(provider, editingUrlText)
                        editingProvider = null
                    },
                    modifier = Modifier.testTag("save_instance_url_button")
                ) {
                    Text("حفظ")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingProvider = null }) {
                    Text("إلغاء")
                }
            }
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Banner
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 7.5f)
                    .clip(MaterialTheme.shapes.large)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_octopipe_banner),
                    contentDescription = "شعار معمارية OctoPipe",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color.Black.copy(alpha = 0.8f), Color.Black.copy(alpha = 0.25f))
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(18.dp)
                ) {
                    Text(
                        text = "OctoPipe Core",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "محرك استخراج مستقل متعدد الخدمات • ${settings.adsBlockedCount} إعلان محجوب • توفير ${settings.sponsorSecondsSaved}ث",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }
            }
        }

        // 1. Multi-Service Provider Switcher
        item {
            SectionHeader(
                icon = Icons.Filled.CloudQueue,
                title = "محركات الاستخراج والخدمات (Services)"
            )
            Spacer(modifier = Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ServiceProvider.entries.forEach { provider ->
                    val isSelected = settings.activeProvider == provider
                    val currentUrl = when (provider) {
                        ServiceProvider.PIPED -> settings.customPipedUrl
                        ServiceProvider.INVIDIOUS -> settings.customInvidiousUrl
                        ServiceProvider.PEERTUBE -> settings.customPeerTubeUrl
                    }
                    val health = healthList.firstOrNull { it.provider == provider }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = if (isSelected) 1.5.dp else 0.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                shape = MaterialTheme.shapes.medium
                            )
                            .testTag("service_card_${provider.id}")
                            .clickable { onSelectProvider(provider) },
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = provider.displayName,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Filled.CheckCircle,
                                            contentDescription = "المحرك النشط",
                                            tint = OctoTeal,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = provider.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = currentUrl,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                health?.let {
                                    Text(
                                        text = it.statusMessage,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (it.isReachable) SponsorSegmentGreen else OctoCoral
                                    )
                                }
                            }
                            IconButton(
                                onClick = {
                                    editingUrlText = currentUrl
                                    editingProvider = provider
                                },
                                modifier = Modifier.testTag("edit_url_${provider.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Edit,
                                    contentDescription = "تعديل رابط الخادم"
                                )
                            }
                        }
                    }
                }

                OutlinedButton(
                    onClick = onPingAll,
                    enabled = !isPinging,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ping_instances_button")
                ) {
                    if (isPinging) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("جاري فحص زمن الاستجابة…")
                    } else {
                        Icon(Icons.Filled.Speed, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("فحص استجابة جميع الخوادم (Ping)")
                    }
                }
            }
        }

        // 2. SponsorBlock Settings
        item {
            SectionHeader(
                icon = Icons.Filled.FastForward,
                title = "حاجب الرعايات (SponsorBlock API)"
            )
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SettingsSwitchRow(
                        title = "تفعيل SponsorBlock",
                        subtitle = "جلب مقاطع الرعاية والمقدمات من sponsor.ajay.app",
                        checked = settings.sponsorBlockEnabled,
                        onCheckedChange = onToggleSponsorBlock,
                        tag = "switch_sponsorblock"
                    )
                    SettingsSwitchRow(
                        title = "التخطي التلقائي",
                        subtitle = "تجاوز المقاطع الدعائية تلقائياً أثناء التشغيل",
                        checked = settings.autoSkipSponsor,
                        onCheckedChange = onToggleAutoSkipSponsor,
                        tag = "switch_autoskip"
                    )
                    Text(
                        text = "الفئات المفعلة:",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SponsorCategory.entries.forEach { cat ->
                            val selected = cat.apiKey in settings.enabledSponsorCategories
                            FilterChip(
                                selected = selected,
                                onClick = { onToggleSponsorCategory(cat.apiKey) },
                                label = { Text(cat.labelAr) },
                                modifier = Modifier.testTag("sponsor_cat_${cat.apiKey}")
                            )
                        }
                    }
                }
            }
        }

        // 3. Background Playback & Picture-in-Picture
        item {
            SectionHeader(
                icon = Icons.Filled.PictureInPictureAlt,
                title = "الخلفية ووضع صورة داخل صورة (PiP)"
            )
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SettingsSwitchRow(
                        title = "تشغيل بالخلفية عند مغادرة التطبيق",
                        subtitle = "استمرار الصوت عبر Media3 MediaSessionService",
                        checked = settings.backgroundPlayOnLeave,
                        onCheckedChange = onToggleBackgroundOnLeave,
                        tag = "switch_background_play"
                    )
                    SettingsSwitchRow(
                        title = "تفعيل PiP التلقائي",
                        subtitle = "تصغير الفيديو لنافذة عائمة عند السحب للخروج",
                        checked = settings.autoPipEnabled,
                        onCheckedChange = onToggleAutoPip,
                        tag = "switch_auto_pip"
                    )
                }
            }
        }

        // 4. Subtitles & Video Quality
        item {
            SectionHeader(
                icon = Icons.Filled.Subtitles,
                title = "الترجمة والجودة الافتراضية"
            )
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SettingsSwitchRow(
                        title = "تفعيل الترجمة تلقائياً (CC)",
                        subtitle = "عرض الترجمة النصية VTT/TTML عند توفرها",
                        checked = settings.subtitlesAutoEnable,
                        onCheckedChange = onToggleSubtitlesAuto,
                        tag = "switch_subtitles_auto"
                    )
                    Text(
                        text = "لغة الترجمة المفضلة:",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("ar" to "العربية", "en" to "English", "fr" to "Français").forEach { (code, name) ->
                            FilterChip(
                                selected = settings.preferredSubtitleLang == code,
                                onClick = { onSelectPreferredSubLang(code) },
                                label = { Text(name) },
                                modifier = Modifier.testTag("sub_lang_$code")
                            )
                        }
                    }
                    Text(
                        text = "الجودة الافتراضية:",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("1080p", "720p", "480p", "360p").forEach { q ->
                            FilterChip(
                                selected = settings.preferredQuality == q,
                                onClick = { onSelectPreferredQuality(q) },
                                label = { Text(q) },
                                modifier = Modifier.testTag("pref_quality_$q")
                            )
                        }
                    }
                    SettingsSwitchRow(
                        title = "الوضع الليلي الداكن (Nocturne Ink)",
                        subtitle = "استخدام الثيم الداكن المريح للعين",
                        checked = settings.darkTheme,
                        onCheckedChange = onToggleDarkTheme,
                        tag = "switch_dark_theme"
                    )
                }
            }
        }

        // 5. Privacy & History Management
        item {
            OutlinedButton(
                onClick = onClearHistory,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("clear_history_button")
            ) {
                Icon(Icons.Filled.CleaningServices, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("مسح سجل المشاهدة المحلي")
            }
        }
    }
}

@Composable
private fun SectionHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(30.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(tag)
        )
    }
}
