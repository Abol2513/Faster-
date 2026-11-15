package com.example.data.session

import android.graphics.Bitmap
import com.example.domain.model.CaptureSource
import com.example.domain.model.OcrLine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ScreenSessionPayload(
    val id: Long,
    val bitmap: Bitmap?,
    val assistLines: List<OcrLine> = emptyList(),
    val source: CaptureSource,
    val isProtectedOrUnavailable: Boolean = false
)

object OverlaySessionStore {
    private val _payload = MutableStateFlow<ScreenSessionPayload?>(null)
    val payload: StateFlow<ScreenSessionPayload?> = _payload.asStateFlow()

    @Synchronized
    fun publish(
        bitmap: Bitmap?,
        assistLines: List<OcrLine> = emptyList(),
        source: CaptureSource,
        isProtectedOrUnavailable: Boolean = false
    ): Long {
        val previous = _payload.value
        val id = System.nanoTime()
        _payload.value = ScreenSessionPayload(
            id = id,
            bitmap = bitmap,
            assistLines = assistLines,
            source = source,
            isProtectedOrUnavailable = isProtectedOrUnavailable
        )
        if (previous?.bitmap != null && previous.bitmap !== bitmap && !previous.bitmap.isRecycled) {
            previous.bitmap.recycle()
        }
        return id
    }

    @Synchronized
    fun clear() {
        val current = _payload.value
        _payload.value = null
        if (current?.bitmap != null && !current.bitmap.isRecycled) {
            current.bitmap.recycle()
        }
    }
}
