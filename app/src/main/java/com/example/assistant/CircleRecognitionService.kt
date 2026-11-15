package com.example.assistant

import android.content.Intent
import android.speech.RecognitionService
import android.speech.SpeechRecognizer

/**
 * Minimal stub RecognitionService so Android 14-16 and Xiaomi HyperOS RoleManager
 * recognize this app as a fully qualified Default Digital Assistant (ROLE_ASSISTANT).
 */
class CircleRecognitionService : RecognitionService() {

    override fun onStartListening(recognizerIntent: Intent?, listener: Callback?) {
        // Screen-translation assistant does not capture microphone audio.
        listener?.error(SpeechRecognizer.ERROR_CLIENT)
    }

    override fun onCancel(listener: Callback?) {
        // No-op
    }

    override fun onStopListening(listener: Callback?) {
        // No-op
    }
}
