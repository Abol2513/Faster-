package com.example.ui.setup

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assistant
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.OfflinePin
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.DashboardCustomize
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.LanguageCatalog
import com.example.domain.model.LanguagePackStatus
import com.example.domain.model.PrewarmStatus
import com.example.domain.model.TranslationEngineMode

@Composable
fun SetupScreen(
    state: SetupUiState,
    snackbarHostState: SnackbarHostState,
    onRequestAssistantRole: () -> Unit,
    onOpenAssistantSettings: () -> Unit,
    onOpenXiaomiPopupPermissions: () -> Unit,
    onOpenXiaomiAutostart: () -> Unit,
    onAddQuickTile: () -> Unit,
    onTestScreenCapture: () -> Unit,
    onPickTestPhoto: () -> Unit,
    onSelectTargetLanguage: (String) -> Unit,
    onSelectTranslationMode: (TranslationEngineMode) -> Unit,
    onSaveGeminiApiKey: (String) -> Unit,
    onPausePrewarm: () -> Unit,
    onResumePrewarm: () -> Unit,
    onRetryCleanPrewarm: () -> Unit,
    onToggleOcrLanguage: (String, Boolean) -> Unit,
    onToggleAutoTranslate: (Boolean) -> Unit,
    onToggleAllowCellular: (Boolean) -> Unit,
    onDownloadOcr: (String) -> Unit,
    onDeleteOcr: (String) -> Unit,
    onDownloadTranslate: (String) -> Unit,
    onDeleteTranslate: (String) -> Unit
) {
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 680.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    HeroAssistantCard(
                        isDefaultAssistant = state.isDefaultAssistant,
                        onRequestAssistantRole = onRequestAssistantRole,
                        onOpenAssistantSettings = onOpenAssistantSettings,
                        onAddQuickTile = onAddQuickTile
                    )
                }

                item {
                    XiaomiHyperOsHelperCard(
                        isXiaomiDevice = state.isXiaomiDevice,
                        onOpenXiaomiPopupPermissions = onOpenXiaomiPopupPermissions,
                        onOpenXiaomiAutostart = onOpenXiaomiAutostart,
                        onOpenAssistantSettings = onOpenAssistantSettings
                    )
                }

                item {
                    TranslationEngineAndApiCard(
                        selectedMode = state.translationEngineMode,
                        savedApiKey = state.geminiApiKey,
                        onSelectMode = onSelectTranslationMode,
                        onSaveGeminiApiKey = onSaveGeminiApiKey
                    )
                }

                item {
                    QuickToolsCard(
                        autoTranslate = state.autoTranslateOnSelect,
                        allowCellularDownloads = state.allowCellularDownloads,
                        onToggleAutoTranslate = onToggleAutoTranslate,
                        onToggleAllowCellular = onToggleAllowCellular,
                        onTestScreenCapture = onTestScreenCapture,
                        onPickTestPhoto = onPickTestPhoto
                    )
                }

                item {
                    DefaultTargetLanguageCard(
                        selectedCode = state.targetLanguageCode,
                        onSelectTargetLanguage = onSelectTargetLanguage
                    )
                }

                item {
                    Text(
                        text = "پایگاه داده آفلاین زبان‌ها (OCR و ترجمه)",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                    )
                    Text(
                        text = "تشخیص متن (OCR) برای ۱۰ زبان اصلی دنیا به‌صورت داخلی داخل برنامه آماده است. مدل‌های ترجمه آفلاین نیز به‌صورت نوبتی و بدون تداخل در پس‌زمینه دریافت می‌شوند.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                    )
                }

                item {
                    PrewarmStatusCard(
                        status = state.prewarmStatus,
                        onPause = onPausePrewarm,
                        onResume = onResumePrewarm,
                        onRetryClean = onRetryCleanPrewarm
                    )
                }

                if (state.isLoadingPacks) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                } else {
                    items(
                        items = state.languagePacks,
                        key = { it.language.mlKitCode }
                    ) { pack ->
                        LanguagePackItemCard(
                            pack = pack,
                            isOcrActive = pack.language.tessCode != null &&
                                pack.language.tessCode in state.activeOcrLanguages,
                            onToggleOcr = { checked ->
                                pack.language.tessCode?.let { onToggleOcrLanguage(it, checked) }
                            },
                            onDownloadOcr = {
                                pack.language.tessCode?.let(onDownloadOcr)
                            },
                            onDeleteOcr = {
                                pack.language.tessCode?.let(onDeleteOcr)
                            },
                            onDownloadTranslate = {
                                onDownloadTranslate(pack.language.mlKitCode)
                            },
                            onDeleteTranslate = {
                                onDeleteTranslate(pack.language.mlKitCode)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroAssistantCard(
    isDefaultAssistant: Boolean,
    onRequestAssistantRole: () -> Unit,
    onOpenAssistantSettings: () -> Unit,
    onAddQuickTile: () -> Unit
) {
    ElevatedCard(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(176.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_assistant_hero),
                    contentDescription = stringResource(R.string.app_name),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.15f),
                                    Color.Black.copy(alpha = 0.72f)
                                )
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (isDefaultAssistant) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.errorContainer
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = if (isDefaultAssistant) {
                                    Icons.Filled.CheckCircle
                                } else {
                                    Icons.Filled.TouchApp
                                },
                                contentDescription = null,
                                tint = if (isDefaultAssistant) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onErrorContainer
                                },
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (isDefaultAssistant) {
                                    stringResource(R.string.assistant_status_active)
                                } else {
                                    stringResource(R.string.assistant_status_inactive)
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isDefaultAssistant) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onErrorContainer
                                }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "بدون دکمه شناور؛ با نگهداشتن دکمه Home فعال می‌شود",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.88f)
                    )
                }
            }

            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "برای فعال‌سازی با نگهداشتن دکمه مرکزی Home (یا کشیدن از گوشه پایین صفحه)، برنامه را به‌عنوان دستیار دیجیتال پیش‌فرض اندروید تنظیم کنید. در حالت عادی هیچ آیکون یا دکمه شناوری نمایش داده نمی‌شود.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Button(
                    onClick = onRequestAssistantRole,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("set_assistant_role_button"),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(vertical = 14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Assistant,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isDefaultAssistant) {
                            "دستیار پیش‌فرض فعال است (تغییر)"
                        } else {
                            stringResource(R.string.assistant_set_default_btn)
                        }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilledTonalButton(
                        onClick = onAddQuickTile,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("add_quick_tile_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DashboardCustomize,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.quick_tile_add_btn),
                            maxLines = 1
                        )
                    }

                    OutlinedButton(
                        onClick = onOpenAssistantSettings,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("open_assistant_settings_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.assistant_open_settings_btn),
                            maxLines = 1
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "اگر گوشی شما دریافت اسکرین‌شات مستقیم دستیار را مسدود کند، هنگام نگهداشتن دکمه Home یا لمس کاشی تنظیمات سریع، ثبت صفحه به‌صورت خودکار با MediaProjection انجام می‌شود.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun XiaomiHyperOsHelperCard(
    isXiaomiDevice: Boolean,
    onOpenXiaomiPopupPermissions: () -> Unit,
    onOpenXiaomiAutostart: () -> Unit,
    onOpenAssistantSettings: () -> Unit
) {
    var expanded by rememberSaveable(isXiaomiDevice) { mutableStateOf(isXiaomiDevice) }

    ElevatedCard(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (isXiaomiDevice) {
                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            }
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 16.dp, vertical = 14.dp)
                    .testTag("xiaomi_helper_toggle"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Filled.PhoneAndroid,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Text(
                            text = if (isXiaomiDevice) {
                                "راهنمای تنظیمات Xiaomi HyperOS / MIUI (پیشنهادی)"
                            } else {
                                "راهنمای اختصاصی گوشی‌های شیائومی (HyperOS / MIUI)"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (expanded) "برای بستن لمس کنید" else "برای مشاهده تنظیمات مجوزهای پس‌زمینه لمس کنید",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "در رابط کاربری HyperOS و MIUI برای اجرای سریع روکش با نگهداشتن دکمه Home، این ۳ مورد را بررسی کنید:\n" +
                            "۱. در بخش «سایر مجوزها»، گزینه Display pop-up windows while running in the background را فعال کنید.\n" +
                            "۲. «شروع خودکار» (Autostart) را برای سرویس دستیار فعال نگه دارید.\n" +
                            "۳. در تنظیمات اضافی > میانبرهای اشاره/دکمه، نگهداشتن دکمه Home را روی دستیار دیجیتال تنظیم کنید.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = onOpenXiaomiPopupPermissions,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("xiaomi_popup_perm_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Layers,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("پنجره بازشو", maxLines = 1)
                        }

                        FilledTonalButton(
                            onClick = onOpenXiaomiAutostart,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("xiaomi_autostart_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayCircleOutline,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("شروع خودکار", maxLines = 1)
                        }
                    }

                    OutlinedButton(
                        onClick = onOpenAssistantSettings,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("باز کردن تنظیمات دستیار پیش‌فرض")
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickToolsCard(
    autoTranslate: Boolean,
    allowCellularDownloads: Boolean,
    onToggleAutoTranslate: (Boolean) -> Unit,
    onToggleAllowCellular: (Boolean) -> Unit,
    onTestScreenCapture: () -> Unit,
    onPickTestPhoto: () -> Unit
) {
    ElevatedCard(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "تنظیمات ترجمه و آزمایش سریع",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        text = "ترجمه خودکار بلافاصله پس از انتخاب متن",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "پس از کشیدن دایره روی متن، ترجمه به‌صورت آنی نمایش داده می‌شود.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = autoTranslate,
                    onCheckedChange = onToggleAutoTranslate,
                    modifier = Modifier.testTag("auto_translate_switch")
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        text = "دانلود مدل‌های آفلاین با اینترنت همراه",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "در صورت خاموش بودن، مدل‌های زبان ML Kit فقط از طریق وای‌فای دریافت می‌شوند.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = allowCellularDownloads,
                    onCheckedChange = onToggleAllowCellular,
                    modifier = Modifier.testTag("allow_cellular_switch")
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilledTonalButton(
                    onClick = onTestScreenCapture,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("test_screen_capture_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Screenshot,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = stringResource(R.string.test_capture_btn), maxLines = 1)
                }

                OutlinedButton(
                    onClick = onPickTestPhoto,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("pick_test_photo_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.PhotoLibrary,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = stringResource(R.string.test_photo_btn), maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun DefaultTargetLanguageCard(
    selectedCode: String,
    onSelectTargetLanguage: (String) -> Unit
) {
    ElevatedCard(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Translate,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "زبان مقصد پیش‌فرض برای ترجمه فوری",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(LanguageCatalog.supportedLanguages, key = { it.mlKitCode }) { lang ->
                    FilterChip(
                        selected = lang.mlKitCode == selectedCode,
                        onClick = { onSelectTargetLanguage(lang.mlKitCode) },
                        label = { Text(lang.persianName) },
                        modifier = Modifier.testTag("target_lang_chip_${lang.mlKitCode}")
                    )
                }
            }
        }
    }
}

@Composable
private fun TranslationEngineAndApiCard(
    selectedMode: TranslationEngineMode,
    savedApiKey: String,
    onSelectMode: (TranslationEngineMode) -> Unit,
    onSaveGeminiApiKey: (String) -> Unit
) {
    var apiInput by rememberSaveable { mutableStateOf(savedApiKey) }
    var showKeyText by rememberSaveable { mutableStateOf(false) }
    var showApiSection by rememberSaveable { mutableStateOf(savedApiKey.isNotBlank()) }

    LaunchedEffect(savedApiKey) {
        if (savedApiKey != apiInput && apiInput.isBlank()) {
            apiInput = savedApiKey
        }
    }

    ElevatedCard(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column {
                    Text(
                        text = "موتور ترجمه (آنلاین و آفلاین)",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "در حالت هوشمند هنگام دسترسی به اینترنت از ترجمه دقیق آنلاین استفاده می‌شود و در نبود اینترنت به‌طور خودکار به موتور آفلاین می‌رود.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(TranslationEngineMode.entries, key = { it.key }) { mode ->
                    val icon = when (mode) {
                        TranslationEngineMode.SMART_HYBRID -> Icons.Filled.AutoAwesome
                        TranslationEngineMode.ONLINE_ONLY -> Icons.Filled.Cloud
                        TranslationEngineMode.OFFLINE_ONLY -> Icons.Filled.OfflinePin
                    }
                    FilterChip(
                        selected = selectedMode == mode,
                        onClick = { onSelectMode(mode) },
                        leadingIcon = {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        label = { Text(mode.persianLabel) },
                        modifier = Modifier.testTag("engine_mode_chip_${mode.key}")
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showApiSection = !showApiSection }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Key,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Column {
                        Text(
                            text = "کلید Gemini API (اختیاری)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = if (savedApiKey.isNotBlank()) {
                                "فعال و ذخیره‌شده روی دستگاه"
                            } else {
                                "بدون کلید هم سرویس رایگان آنلاین فعال است"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Icon(
                    imageVector = if (showApiSection) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null
                )
            }

            AnimatedVisibility(visible = showApiSection) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        OutlinedTextField(
                            value = apiInput,
                            onValueChange = { apiInput = it },
                            label = { Text("Gemini API Key") },
                            placeholder = { Text("AIza...") },
                            singleLine = true,
                            visualTransformation = if (showKeyText) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            trailingIcon = {
                                IconButton(onClick = { showKeyText = !showKeyText }) {
                                    Icon(
                                        imageVector = if (showKeyText) {
                                            Icons.Filled.VisibilityOff
                                        } else {
                                            Icons.Filled.Visibility
                                        },
                                        contentDescription = "نمایش یا مخفی کردن کلید"
                                    )
                                }
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("gemini_api_key_input")
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = { onSaveGeminiApiKey(apiInput) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("save_gemini_api_key_button")
                        ) {
                            Text("ذخیره کلید روی دستگاه")
                        }
                        if (savedApiKey.isNotBlank() || apiInput.isNotBlank()) {
                            OutlinedButton(
                                onClick = {
                                    apiInput = ""
                                    onSaveGeminiApiKey("")
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.testTag("clear_gemini_api_key_button")
                            ) {
                                Text("پاک کردن")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PrewarmStatusCard(
    status: PrewarmStatus,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRetryClean: () -> Unit
) {
    val allDone = !status.isRunning && status.lastError == null && status.completedCount >= status.totalCount
    val progress = if (status.totalCount > 0) {
        (status.completedCount.toFloat() / status.totalCount.toFloat()).coerceIn(0f, 1f)
    } else 0f

    ElevatedCard(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = when {
                status.lastError != null -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
                allDone -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                else -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
            }
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when {
                            allDone -> "تمام مدل‌های ترجمه آفلاین زبان‌های اصلی آماده هستند"
                            status.isRunning && status.currentLanguageCode != null ->
                                "در حال آماده‌سازی مدل آفلاین: ${LanguageCatalog.displayNameForCode(status.currentLanguageCode)}"
                            status.isPaused -> "آماده‌سازی خودکار مدل‌های آفلاین متوقف شد"
                            else -> "وضعیت دریافت نوبتی مدل‌های ترجمه آفلاین"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${status.completedCount} از ${status.totalCount} زبان اصلی",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (allDone) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (!allDone) {
                if (status.isRunning && progress <= 0f) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            if (!status.lastError.isNullOrBlank()) {
                Text(
                    text = status.lastError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (!allDone) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (status.isRunning) {
                        OutlinedButton(
                            onClick = onPause,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("pause_prewarm_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Pause,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("توقف موقت")
                        }
                    } else {
                        FilledTonalButton(
                            onClick = onResume,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("resume_prewarm_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("ادامه دریافت خودکار")
                        }
                    }

                    TextButton(
                        onClick = onRetryClean,
                        modifier = Modifier.testTag("retry_clean_prewarm_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("پاکسازی و تلاش مجدد", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun LanguagePackItemCard(
    pack: LanguagePackStatus,
    isOcrActive: Boolean,
    onToggleOcr: (Boolean) -> Unit,
    onDownloadOcr: () -> Unit,
    onDeleteOcr: () -> Unit,
    onDownloadTranslate: () -> Unit,
    onDeleteTranslate: () -> Unit
) {
    val lang = pack.language
    ElevatedCard(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${lang.persianName} (${lang.nativeName})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = buildString {
                            append("OCR: ")
                            append(
                                when {
                                    lang.isOcrBundled -> "آماده در برنامه"
                                    pack.isOcrInstalled -> "نصب‌شده"
                                    else -> "${lang.ocrPackSizeMb} MB"
                                }
                            )
                            append("  •  ترجمه: ")
                            append(
                                when {
                                    pack.isTranslateBuiltIn -> "داخلی"
                                    pack.isTranslateModelInstalled -> "نصب‌شده"
                                    else -> "~${lang.translateModelSizeMb} MB"
                                }
                            )
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (pack.isOcrInstalled && lang.tessCode != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "تشخیص فعال",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Checkbox(
                            checked = isOcrActive,
                            onCheckedChange = onToggleOcr,
                            modifier = Modifier.testTag("ocr_active_checkbox_${lang.mlKitCode}")
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (lang.tessCode != null) {
                    if (pack.isDownloadingOcr) {
                        AssistChip(
                            onClick = {},
                            enabled = false,
                            leadingIcon = {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                            },
                            label = { Text("در حال دانلود OCR…") }
                        )
                    } else if (!pack.isOcrInstalled) {
                        AssistChip(
                            onClick = onDownloadOcr,
                            leadingIcon = {
                                Icon(
                                    Icons.Filled.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            label = { Text("دانلود OCR") },
                            modifier = Modifier.testTag("download_ocr_${lang.mlKitCode}")
                        )
                    } else if (lang.isOcrBundled) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                        ) {
                            Text(
                                text = "OCR داخلی آماده",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                            )
                        }
                    } else {
                        AssistChip(
                            onClick = onDeleteOcr,
                            leadingIcon = {
                                Icon(
                                    Icons.Filled.DeleteOutline,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            label = { Text("حذف OCR") }
                        )
                    }
                }

                if (!pack.isTranslateBuiltIn) {
                    if (pack.isDownloadingTranslate) {
                        AssistChip(
                            onClick = {},
                            enabled = false,
                            leadingIcon = {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                            },
                            label = { Text("در حال دانلود ترجمه…") }
                        )
                    } else if (!pack.isTranslateModelInstalled) {
                        AssistChip(
                            onClick = onDownloadTranslate,
                            leadingIcon = {
                                Icon(
                                    Icons.Filled.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            label = { Text("دانلود مدل ترجمه") },
                            modifier = Modifier.testTag("download_translate_${lang.mlKitCode}")
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                            ) {
                                Text(
                                    text = "ترجمه آفلاین آماده",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                )
                            }
                            IconButton(
                                onClick = onDeleteTranslate,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.DeleteOutline,
                                    contentDescription = "حذف مدل ترجمه",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
