package com.example.ui.overlay

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.LanguageCatalog
import kotlinx.coroutines.delay
import kotlin.math.min

@Composable
fun CircleOverlayScreen(
    state: OverlayUiState,
    onGestureCompleted: (List<Offset>) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onUpdateEditableText: (String) -> Unit,
    onSelectSourceLang: (String) -> Unit,
    onSelectTargetLang: (String) -> Unit,
    onSwapLanguages: () -> Unit,
    onRetranslate: () -> Unit,
    onDownloadMissingModels: (List<String>) -> Unit,
    onCopyText: (String) -> Unit,
    onRequestMediaProjectionFallback: () -> Unit,
    onOpenSheet: () -> Unit,
    onClose: () -> Unit
) {
    var copyBannerVisible by remember { mutableStateOf(false) }
    var copyBannerNonce by remember { mutableStateOf(0) }

    LaunchedEffect(copyBannerNonce) {
        if (copyBannerNonce > 0) {
            copyBannerVisible = true
            delay(1800L)
            copyBannerVisible = false
        }
    }

    val triggerCopy: (String) -> Unit = { text ->
        if (text.isNotBlank()) {
            onCopyText(text)
            copyBannerNonce++
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.58f))
    ) {
        // 1. Screenshot + Circle-to-Search Interactive Canvas
        ScreenshotInteractiveLayer(
            screenshot = state.screenshot,
            ocrStage = state.ocrStage,
            selectedWordIndices = state.selectedWordIndices,
            onGestureCompleted = onGestureCompleted,
            modifier = Modifier.fillMaxSize()
        )

        // 2. Top Floating Action Bar respecting safeDrawing (status bar + display cutout)
        TopOverlayControls(
            ocrStage = state.ocrStage,
            isRefining = state.isRefiningCroppedOcr,
            hasSelection = state.selectedWordIndices.isNotEmpty(),
            onSelectAll = onSelectAll,
            onClearSelection = onClearSelection,
            onClose = onClose,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
                )
                .padding(horizontal = 12.dp, vertical = 10.dp)
        )

        // 3. Copy confirmation toast pill
        AnimatedVisibility(
            visible = copyBannerVisible,
            enter = fadeIn(tween(150)) + slideInVertically { -it / 2 },
            exit = fadeOut(tween(200)) + slideOutVertically { -it / 2 },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
                )
                .padding(top = 64.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.inverseSurface,
                tonalElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.inversePrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(R.string.overlay_copied_toast),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.inverseOnSurface
                    )
                }
            }
        }

        // 4. Bottom Floating Pill (when text exists & sheet is minimized) OR Rounded Translation Sheet
        AnimatedVisibility(
            visible = !state.isSheetExpanded && state.ocrStage is OcrStage.Ready,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
                )
                .padding(bottom = 16.dp, start = 16.dp, end = 16.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
                modifier = Modifier.clickable { onOpenSheet() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = if (state.selectedWordIndices.isEmpty()) {
                            "روی متن دایره بکشید یا برای پنل ترجمه لمس کنید"
                        } else {
                            "باز کردن پنل ترجمه (${state.selectedWordIndices.size} واژه)"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowUp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = state.isSheetExpanded,
            enter = fadeIn(tween(180)) + slideInVertically(spring(stiffness = 450f)) { it / 2 },
            exit = fadeOut(tween(150)) + slideOutVertically { it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            TranslationBottomSheet(
                state = state,
                onUpdateEditableText = onUpdateEditableText,
                onSelectSourceLang = onSelectSourceLang,
                onSelectTargetLang = onSelectTargetLang,
                onSwapLanguages = onSwapLanguages,
                onRetranslate = onRetranslate,
                onDownloadMissingModels = onDownloadMissingModels,
                onCopyOriginal = { triggerCopy(state.editableSourceText) },
                onCopyTranslation = {
                    val tr = (state.translationStage as? TranslationStage.Success)?.translatedText.orEmpty()
                    triggerCopy(tr)
                },
                onCopyBoth = {
                    val tr = (state.translationStage as? TranslationStage.Success)?.translatedText.orEmpty()
                    val combined = buildString {
                        if (state.editableSourceText.isNotBlank()) append(state.editableSourceText)
                        if (tr.isNotBlank()) {
                            if (isNotEmpty()) append("\n\n")
                            append(tr)
                        }
                    }
                    triggerCopy(combined)
                },
                onMinimize = onClearSelection,
                onRequestMediaProjectionFallback = onRequestMediaProjectionFallback,
                onClose = onClose
            )
        }
    }
}

@Composable
private fun ScreenshotInteractiveLayer(
    screenshot: Bitmap?,
    ocrStage: OcrStage,
    selectedWordIndices: Set<Int>,
    onGestureCompleted: (List<Offset>) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeStroke = remember { mutableStateListOf<Offset>() }
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowPulse"
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary

    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val boxW = with(density) { maxWidth.toPx() }
        val boxH = with(density) { maxHeight.toPx() }

        val imageRect = remember(screenshot, boxW, boxH) {
            if (screenshot == null || screenshot.width <= 0 || screenshot.height <= 0) {
                Rect(0f, 0f, boxW, boxH)
            } else {
                val scale = min(boxW / screenshot.width.toFloat(), boxH / screenshot.height.toFloat())
                val drawnW = screenshot.width * scale
                val drawnH = screenshot.height * scale
                val left = (boxW - drawnW) / 2f
                val top = (boxH - drawnH) / 2f
                Rect(left, top, left + drawnW, top + drawnH)
            }
        }

        if (screenshot != null && !screenshot.isRecycled) {
            val imageBitmap = remember(screenshot) { screenshot.asImageBitmap() }
            Image(
                bitmap = imageBitmap,
                contentDescription = "تصویر صفحه فعلی",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }

        val readyResult = (ocrStage as? OcrStage.Ready)?.result

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(imageRect, readyResult) {
                    detectTapGestures { tapOffset ->
                        val nx = ((tapOffset.x - imageRect.left) / imageRect.width.coerceAtLeast(1f))
                            .coerceIn(0f, 1f)
                        val ny = ((tapOffset.y - imageRect.top) / imageRect.height.coerceAtLeast(1f))
                            .coerceIn(0f, 1f)
                        onGestureCompleted(listOf(Offset(nx, ny)))
                    }
                }
                .pointerInput(imageRect, readyResult) {
                    detectDragGestures(
                        onDragStart = { startOffset ->
                            activeStroke.clear()
                            activeStroke.add(startOffset)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            activeStroke.add(change.position)
                        },
                        onDragEnd = {
                            val normalized = activeStroke.map { p ->
                                Offset(
                                    ((p.x - imageRect.left) / imageRect.width.coerceAtLeast(1f))
                                        .coerceIn(0f, 1f),
                                    ((p.y - imageRect.top) / imageRect.height.coerceAtLeast(1f))
                                        .coerceIn(0f, 1f)
                                )
                            }
                            activeStroke.clear()
                            if (normalized.isNotEmpty()) {
                                onGestureCompleted(normalized)
                            }
                        },
                        onDragCancel = {
                            activeStroke.clear()
                        }
                    )
                }
        ) {
            val edgeStrokeWidth = if (ocrStage is OcrStage.Scanning) 14f else 6f
            val edgeAlpha = if (ocrStage is OcrStage.Scanning) glowPulse else 0.45f
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = edgeAlpha),
                        tertiaryColor.copy(alpha = edgeAlpha * 0.85f),
                        primaryColor.copy(alpha = edgeAlpha)
                    )
                ),
                style = Stroke(width = edgeStrokeWidth)
            )

            if (readyResult != null) {
                for (word in readyResult.words) {
                    val wb = word.normalizedBounds
                    val left = imageRect.left + wb.left * imageRect.width
                    val top = imageRect.top + wb.top * imageRect.height
                    val w = (wb.width() * imageRect.width).coerceAtLeast(6f)
                    val h = (wb.height() * imageRect.height).coerceAtLeast(6f)
                    val isSelected = word.index in selectedWordIndices

                    if (isSelected) {
                        drawRoundRect(
                            color = primaryColor.copy(alpha = 0.36f),
                            topLeft = Offset(left - 3f, top - 2f),
                            size = Size(w + 6f, h + 4f),
                            cornerRadius = CornerRadius(10f, 10f)
                        )
                        drawRoundRect(
                            color = primaryColor.copy(alpha = 0.92f),
                            topLeft = Offset(left - 3f, top - 2f),
                            size = Size(w + 6f, h + 4f),
                            cornerRadius = CornerRadius(10f, 10f),
                            style = Stroke(width = 2.5f)
                        )
                    } else {
                        drawRoundRect(
                            color = Color.White.copy(alpha = 0.14f),
                            topLeft = Offset(left - 2f, top - 1f),
                            size = Size(w + 4f, h + 2f),
                            cornerRadius = CornerRadius(8f, 8f)
                        )
                        drawRoundRect(
                            color = Color.White.copy(alpha = 0.38f),
                            topLeft = Offset(left - 2f, top - 1f),
                            size = Size(w + 4f, h + 2f),
                            cornerRadius = CornerRadius(8f, 8f),
                            style = Stroke(width = 1.2f)
                        )
                    }
                }
            }

            if (activeStroke.size > 1) {
                val path = Path().apply {
                    moveTo(activeStroke.first().x, activeStroke.first().y)
                    for (i in 1 until activeStroke.size) {
                        lineTo(activeStroke[i].x, activeStroke[i].y)
                    }
                }
                drawPath(
                    path = path,
                    color = primaryColor.copy(alpha = 0.35f),
                    style = Stroke(width = 26f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
                drawPath(
                    path = path,
                    brush = Brush.linearGradient(listOf(primaryColor, tertiaryColor)),
                    style = Stroke(width = 10f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
        }
    }
}

@Composable
private fun TopOverlayControls(
    ocrStage: OcrStage,
    isRefining: Boolean,
    hasSelection: Boolean,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        FilledTonalIconButton(
            onClick = onClose,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f)
            ),
            modifier = Modifier
                .size(48.dp)
                .testTag("overlay_close_button")
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.overlay_close)
            )
        }

        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.90f),
            tonalElevation = 4.dp,
            modifier = Modifier
                .weight(1f, fill = false)
                .padding(horizontal = 8.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (ocrStage is OcrStage.Scanning || isRefining) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Text(
                        text = if (isRefining) {
                            "بازخوانی دقیق ناحیه دایره…"
                        } else {
                            "در حال تشخیص متن صفحه…"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = stringResource(R.string.overlay_hint_circle),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (hasSelection) {
                FilledTonalIconButton(
                    onClick = onClearSelection,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f)
                    ),
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("overlay_clear_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Clear,
                        contentDescription = stringResource(R.string.overlay_clear_selection)
                    )
                }
            }
            if (ocrStage is OcrStage.Ready) {
                FilledTonalIconButton(
                    onClick = onSelectAll,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.94f)
                    ),
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("overlay_select_all_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.SelectAll,
                        contentDescription = stringResource(R.string.overlay_select_all),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun TranslationBottomSheet(
    state: OverlayUiState,
    onUpdateEditableText: (String) -> Unit,
    onSelectSourceLang: (String) -> Unit,
    onSelectTargetLang: (String) -> Unit,
    onSwapLanguages: () -> Unit,
    onRetranslate: () -> Unit,
    onDownloadMissingModels: (List<String>) -> Unit,
    onCopyOriginal: () -> Unit,
    onCopyTranslation: () -> Unit,
    onCopyBoth: () -> Unit,
    onMinimize: () -> Unit,
    onRequestMediaProjectionFallback: () -> Unit,
    onClose: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 12.dp,
        shadowElevation = 16.dp,
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 720.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
                )
                .imePadding()
                .heightIn(max = 520.dp)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = stringResource(R.string.overlay_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (state.screenshot != null) {
                        TextButton(onClick = onMinimize) {
                            Icon(
                                imageVector = Icons.Filled.KeyboardArrowDown,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("نمایش صفحه")
                        }
                    }
                    TextButton(onClick = onClose) {
                        Text(stringResource(R.string.overlay_close))
                    }
                }
            }

            if (state.ocrStage is OcrStage.UnavailableOrProtected) {
                UnavailableScreenBanner(
                    onRequestMediaProjectionFallback = onRequestMediaProjectionFallback,
                    onClose = onClose
                )
            } else if (state.ocrStage is OcrStage.Empty && state.editableSourceText.isBlank()) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "متنی به صورت خودکار یافت نشد؛ روی بخش موردنظر دایره بکشید.",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "همچنین می‌توانید متن را در کادر زیر تایپ یا جای‌گذاری کنید.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (state.ocrStage !is OcrStage.UnavailableOrProtected) {
                LanguageSelectorBar(
                    sourceLangCode = state.sourceLangCode,
                    detectedSourceCode = state.detectedSourceCode,
                    targetLangCode = state.targetLangCode,
                    onSelectSourceLang = onSelectSourceLang,
                    onSelectTargetLang = onSelectTargetLang,
                    onSwapLanguages = onSwapLanguages
                )

                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val isWide = maxWidth >= 540.dp
                    if (isWide) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OriginalEditableCard(
                                text = state.editableSourceText,
                                isRefining = state.isRefiningCroppedOcr,
                                detectedOrSelectedLang = if (state.sourceLangCode == LanguageCatalog.AUTO_DETECT_CODE) {
                                    state.detectedSourceCode ?: "fa"
                                } else {
                                    state.sourceLangCode
                                },
                                onTextChange = onUpdateEditableText,
                                onRetranslate = onRetranslate,
                                modifier = Modifier.weight(1f)
                            )
                            TranslatedOutputCard(
                                translationStage = state.translationStage,
                                targetLangCode = state.targetLangCode,
                                onDownloadMissingModels = onDownloadMissingModels,
                                onRetry = onRetranslate,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OriginalEditableCard(
                                text = state.editableSourceText,
                                isRefining = state.isRefiningCroppedOcr,
                                detectedOrSelectedLang = if (state.sourceLangCode == LanguageCatalog.AUTO_DETECT_CODE) {
                                    state.detectedSourceCode ?: "fa"
                                } else {
                                    state.sourceLangCode
                                },
                                onTextChange = onUpdateEditableText,
                                onRetranslate = onRetranslate,
                                modifier = Modifier.fillMaxWidth()
                            )
                            TranslatedOutputCard(
                                translationStage = state.translationStage,
                                targetLangCode = state.targetLangCode,
                                onDownloadMissingModels = onDownloadMissingModels,
                                onRetry = onRetranslate,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                val hasOriginal = state.editableSourceText.isNotBlank()
                val hasTranslation = state.translationStage is TranslationStage.Success &&
                    state.translationStage.translatedText.isNotBlank()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onCopyTranslation,
                        enabled = hasTranslation,
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("copy_translation_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.overlay_copy_translation),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    FilledTonalButton(
                        onClick = onCopyOriginal,
                        enabled = hasOriginal,
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("copy_original_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.overlay_copy_original),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    OutlinedButton(
                        onClick = onCopyBoth,
                        enabled = hasOriginal || hasTranslation,
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp),
                        modifier = Modifier.testTag("copy_both_button")
                    ) {
                        Text(
                            text = stringResource(R.string.overlay_copy_both),
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UnavailableScreenBanner(
    onRequestMediaProjectionFallback: () -> Unit,
    onClose: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.overlay_protected_screen),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onRequestMediaProjectionFallback,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Screenshot,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.overlay_capture_fallback_btn),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                OutlinedButton(
                    onClick = onClose,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(stringResource(R.string.overlay_close))
                }
            }
        }
    }
}

@Composable
private fun LanguageSelectorBar(
    sourceLangCode: String,
    detectedSourceCode: String?,
    targetLangCode: String,
    onSelectSourceLang: (String) -> Unit,
    onSelectTargetLang: (String) -> Unit,
    onSwapLanguages: () -> Unit
) {
    var showSourceMenu by remember { mutableStateOf(false) }
    var showTargetMenu by remember { mutableStateOf(false) }

    val sourceLabel = if (sourceLangCode == LanguageCatalog.AUTO_DETECT_CODE) {
        if (detectedSourceCode != null) {
            "تشخیص خودکار (${LanguageCatalog.displayNameForCode(detectedSourceCode)})"
        } else {
            "تشخیص خودکار"
        }
    } else {
        LanguageCatalog.displayNameForCode(sourceLangCode)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(modifier = Modifier.weight(1f)) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showSourceMenu = true }
                    .testTag("source_language_dropdown")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = sourceLabel,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            DropdownMenu(
                expanded = showSourceMenu,
                onDismissRequest = { showSourceMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("تشخیص خودکار زبان") },
                    onClick = {
                        showSourceMenu = false
                        onSelectSourceLang(LanguageCatalog.AUTO_DETECT_CODE)
                    }
                )
                LanguageCatalog.supportedLanguages.forEach { lang ->
                    DropdownMenuItem(
                        text = { Text("${lang.persianName} (${lang.nativeName})") },
                        onClick = {
                            showSourceMenu = false
                            onSelectSourceLang(lang.mlKitCode)
                        }
                    )
                }
            }
        }

        FilledTonalIconButton(
            onClick = onSwapLanguages,
            modifier = Modifier
                .size(48.dp)
                .testTag("overlay_swap_lang_button")
        ) {
            Icon(
                imageVector = Icons.Filled.SwapHoriz,
                contentDescription = "جابه‌جایی زبان مبدأ و مقصد"
            )
        }

        Box(modifier = Modifier.weight(1f)) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showTargetMenu = true }
                    .testTag("target_language_dropdown")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = LanguageCatalog.displayNameForCode(targetLangCode),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            DropdownMenu(
                expanded = showTargetMenu,
                onDismissRequest = { showTargetMenu = false }
            ) {
                LanguageCatalog.supportedLanguages.forEach { lang ->
                    DropdownMenuItem(
                        text = { Text("${lang.persianName} (${lang.nativeName})") },
                        onClick = {
                            showTargetMenu = false
                            onSelectTargetLang(lang.mlKitCode)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun OriginalEditableCard(
    text: String,
    isRefining: Boolean,
    detectedOrSelectedLang: String,
    onTextChange: (String) -> Unit,
    onRetranslate: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isRtlText = LanguageCatalog.isRtlLanguage(detectedOrSelectedLang) ||
        text.any { it in '\u0600'..'\u06FF' }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (isRefining) "در حال خوانش دقیق ناحیه…" else "متن استخراج‌شده (قابل ویرایش)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FilledTonalButton(
                    onClick = onRetranslate,
                    enabled = text.isNotBlank(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.testTag("retranslate_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.overlay_retranslate),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            if (isRefining) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            CompositionLocalProvider(
                LocalLayoutDirection provides if (isRtlText) LayoutDirection.Rtl else LayoutDirection.Ltr
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChange,
                    placeholder = { Text("متن انتخاب‌شده اینجا نمایش داده می‌شود…") },
                    minLines = 2,
                    maxLines = 5,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("editable_extracted_text")
                )
            }
        }
    }
}

@Composable
private fun TranslatedOutputCard(
    translationStage: TranslationStage,
    targetLangCode: String,
    onDownloadMissingModels: (List<String>) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isTargetRtl = LanguageCatalog.isRtlLanguage(targetLangCode)

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "ترجمه (${LanguageCatalog.displayNameForCode(targetLangCode)})",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )

            when (translationStage) {
                is TranslationStage.Idle -> {
                    Text(
                        text = "برای مشاهده ترجمه فوری، بخشی از متن را انتخاب یا ویرایش کنید.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                is TranslationStage.Translating -> {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(
                        text = "در حال ترجمه آفلاین…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                is TranslationStage.DownloadingModels -> {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(
                        text = "در حال دریافت مدل آفلاین زبان…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                is TranslationStage.Success -> {
                    CompositionLocalProvider(
                        LocalLayoutDirection provides if (isTargetRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
                    ) {
                        SelectionContainer {
                            Text(
                                text = translationStage.translatedText,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("translated_result_text")
                            )
                        }
                    }
                    if (translationStage.wasSameLanguage) {
                        Text(
                            text = "زبان مبدأ و مقصد یکسان تشخیص داده شد؛ زبان مقصد را تغییر دهید.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                is TranslationStage.MissingModel -> {
                    Text(
                        text = translationStage.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Button(
                        onClick = { onDownloadMissingModels(translationStage.missingCodes) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("download_missing_model_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Download,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("دانلود مدل آفلاین و ترجمه")
                    }
                }
                is TranslationStage.Error -> {
                    Text(
                        text = translationStage.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    OutlinedButton(onClick = onRetry, shape = RoundedCornerShape(12.dp)) {
                        Text("تلاش مجدد")
                    }
                }
            }
        }
    }
}
