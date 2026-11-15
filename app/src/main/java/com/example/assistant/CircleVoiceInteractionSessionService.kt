package com.example.assistant

import android.app.assist.AssistContent
import android.app.assist.AssistStructure
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import android.util.DisplayMetrics
import android.util.Log
import android.view.View
import android.view.WindowManager
import com.example.data.session.OverlaySessionStore
import com.example.domain.model.CaptureSource
import com.example.domain.model.OcrLine
import com.example.ui.overlay.OverlayActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class CircleVoiceInteractionSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession {
        return CircleVoiceInteractionSession(this)
    }
}

class CircleVoiceInteractionSession(
    private val serviceContext: Context
) : VoiceInteractionSession(serviceContext) {

    companion object {
        private const val TAG = "CircleAssistantSession"
        private const val SCREENSHOT_WAIT_TIMEOUT_MS = 750L
    }

    private val sessionScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var fallbackJob: Job? = null
    private var launchedOverlay = false
    private var latestAssistLines: List<OcrLine> = emptyList()

    override fun onCreateContentView(): View {
        // Return an invisible zero-intrusion view while awaiting onHandleScreenshot
        return View(serviceContext).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }
    }

    override fun onPrepareShow(args: Bundle?, showFlags: Int) {
        super.onPrepareShow(args, showFlags)
        window?.window?.setDimAmount(0f)
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        launchedOverlay = false
        latestAssistLines = emptyList()
        fallbackJob?.cancel()
        fallbackJob = sessionScope.launch {
            delay(SCREENSHOT_WAIT_TIMEOUT_MS)
            if (!launchedOverlay) {
                launchOverlay(
                    bitmap = null,
                    assistLines = latestAssistLines,
                    source = if (latestAssistLines.isNotEmpty()) {
                        CaptureSource.ASSIST_TEXT_ONLY
                    } else {
                        CaptureSource.ASSISTANT_SCREENSHOT
                    },
                    isUnavailable = latestAssistLines.isEmpty()
                )
            }
        }
    }

    override fun onHandleAssist(state: AssistState) {
        super.onHandleAssist(state)
        extractFromStructure(state.assistStructure)
    }

    @Deprecated("Deprecated in Java")
    override fun onHandleAssist(
        data: Bundle?,
        structure: AssistStructure?,
        content: AssistContent?
    ) {
        super.onHandleAssist(data, structure, content)
        extractFromStructure(structure)
    }

    override fun onHandleScreenshot(screenshot: Bitmap?) {
        super.onHandleScreenshot(screenshot)
        if (launchedOverlay) return

        val safeCopy = screenshot?.let { bmp ->
            val targetConfig = Bitmap.Config.ARGB_8888
            bmp.copy(targetConfig, false)
        }

        if (safeCopy != null) {
            launchOverlay(
                bitmap = safeCopy,
                assistLines = latestAssistLines,
                source = CaptureSource.ASSISTANT_SCREENSHOT,
                isUnavailable = false
            )
        } else {
            // Screenshot null -> could be FLAG_SECURE or OEM restriction; wait briefly for AssistStructure or open fallback prompt
            if (latestAssistLines.isNotEmpty()) {
                launchOverlay(
                    bitmap = null,
                    assistLines = latestAssistLines,
                    source = CaptureSource.ASSIST_TEXT_ONLY,
                    isUnavailable = false
                )
            }
        }
    }

    private fun extractFromStructure(structure: AssistStructure?) {
        if (structure == null) return
        val (w, h) = getScreenDimensions()
        val extracted = AssistStructureExtractor.extractLines(structure, w, h)
        if (extracted.isNotEmpty()) {
            latestAssistLines = extracted
        }
    }

    private fun getScreenDimensions(): Pair<Int, Int> {
        val wm = serviceContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = wm.maximumWindowMetrics.bounds
            bounds.width() to bounds.height()
        } else {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(metrics)
            metrics.widthPixels to metrics.heightPixels
        }
    }

    private fun launchOverlay(
        bitmap: Bitmap?,
        assistLines: List<OcrLine>,
        source: CaptureSource,
        isUnavailable: Boolean
    ) {
        if (launchedOverlay) return
        launchedOverlay = true
        fallbackJob?.cancel()

        OverlaySessionStore.publish(
            bitmap = bitmap,
            assistLines = assistLines,
            source = source,
            isProtectedOrUnavailable = isUnavailable
        )

        val intent = OverlayActivity.createIntent(serviceContext)
        try {
            startAssistantActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "startAssistantActivity failed, falling back to context.startActivity", e)
            serviceContext.startActivity(intent)
        }
        finish()
    }

    override fun onDestroy() {
        fallbackJob?.cancel()
        sessionScope.cancel()
        super.onDestroy()
    }
}
