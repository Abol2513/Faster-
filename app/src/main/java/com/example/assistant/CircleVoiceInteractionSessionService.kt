package com.example.assistant

import android.app.assist.AssistContent
import android.app.assist.AssistStructure
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import android.util.DisplayMetrics
import android.util.Log
import android.view.View
import android.view.WindowManager
import com.example.capture.ScreenCaptureRequestActivity
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
        private const val SCREENSHOT_WAIT_TIMEOUT_MS = 700L
    }

    private val sessionScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var fallbackJob: Job? = null
    private var dispatched = false
    private var latestAssistLines: List<OcrLine> = emptyList()

    override fun onCreateContentView(): View {
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
        dispatched = false
        latestAssistLines = emptyList()
        fallbackJob?.cancel()
        fallbackJob = sessionScope.launch {
            delay(SCREENSHOT_WAIT_TIMEOUT_MS)
            if (!dispatched) {
                // OEM (e.g., HyperOS) did not deliver onHandleScreenshot within window:
                // Seamlessly fall back to MediaProjection capture flow.
                launchMediaProjectionFallback()
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
        if (dispatched) return

        val safeCopy = screenshot?.let { bmp ->
            val copyConfig = Bitmap.Config.ARGB_8888
            bmp.copy(copyConfig, false)
        }

        if (safeCopy != null) {
            launchOverlayWithBitmap(safeCopy)
        } else {
            // OEM blocked assist screenshot or returned null -> seamlessly launch MediaProjection flow
            launchMediaProjectionFallback()
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

    private fun launchOverlayWithBitmap(bitmap: Bitmap) {
        if (dispatched) return
        dispatched = true
        fallbackJob?.cancel()

        OverlaySessionStore.publish(
            bitmap = bitmap,
            assistLines = latestAssistLines,
            source = CaptureSource.ASSISTANT_SCREENSHOT,
            isProtectedOrUnavailable = false
        )
        startAssistantUi(OverlayActivity.createIntent(serviceContext))
    }

    private fun launchMediaProjectionFallback() {
        if (dispatched) return
        dispatched = true
        fallbackJob?.cancel()

        if (latestAssistLines.isNotEmpty()) {
            OverlaySessionStore.setPendingAssistLines(latestAssistLines)
        }
        startAssistantUi(
            ScreenCaptureRequestActivity.createIntent(
                context = serviceContext,
                fromQuickTile = false
            )
        )
    }

    private fun startAssistantUi(intent: Intent) {
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
