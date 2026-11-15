package com.example.ui.setup

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assistant
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Translate
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.LanguageCatalog
import com.example.domain.model.LanguagePackStatus

@Composable
fun SetupScreen(
    state: SetupUiState,
    snackbarHostState: SnackbarHostState,
    onRequestAssistantRole: () -> Unit,
    onOpenAssistantSettings: () -> Unit,
    onAddQuickTile: () -> Unit,
    onTestScreenCapture: () -> Unit,
    onPickTestPhoto: () -> Unit,
    onSelectTargetLanguage: (String) -> Unit,
    onToggleOcrLanguage: (String, Boolean) -> Unit,
    onToggleAutoTranslate: (Boolean) -> Unit,
    onDownloadOcr: (String) -> Unit,
    onDeleteOcr: (String) -> Unit,
    onDownloadTranslate: (String) -> Unit,
    onDeleteTranslate: (String) -> Unit
) {
    Scaffold(
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
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
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
                    QuickToolsCard(
                        autoTranslate = state.autoTranslateOnSelect,
                        onToggleAutoTranslate = onToggleAutoTranslate,
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
                        text = "زبان‌های فارسی و انگلیسی از پیش داخل برنامه نصب هستند. برای سایر زبان‌ها می‌توانید بسته‌های کم‌حجم آفلاین را دانلود کنید.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, start = 4.dp)
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

                item {
                    Spacer(modifier = Modifier.height(16.dp))
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
                    text = "برای فعال‌سازی با نگهداشتن دکمه مرکزی Home (یا کشیدن از گوشه پایین صفحه)، کافی است یک بار برنامه را به‌عنوان دستیار پیش‌فرض اندروید انتخاب کنید. در حالت عادی برنامه کاملاً نامرئی است.",
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
                            text = "نکته سیستمی اندروید: در برخی گوشی‌ها ممکن است برای اسکرین‌شات خودکار دستیار نیاز به فعال بودن گزینه «استفاده از اسکرین‌شات» در تنظیمات دستیار سیستم باشد؛ در غیر این صورت کاشی تنظیمات سریع همیشه با MediaProjection تصویر صفحه را می‌گیرد.",
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
private fun QuickToolsCard(
    autoTranslate: Boolean,
    onToggleAutoTranslate: (Boolean) -> Unit,
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
                text = "آزمایش سریع و رفتار ترجمه",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
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

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

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
                            append(if (pack.isOcrInstalled) "نصب‌شده" else "${lang.ocrPackSizeMb} MB")
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
                // OCR pack action
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
                    } else if (!lang.isOcrBundled) {
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

                // Translation model action
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
                            AnimatedVisibility(visible = true) {
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
}
