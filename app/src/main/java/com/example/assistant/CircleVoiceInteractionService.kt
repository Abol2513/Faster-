package com.example.assistant

import android.service.voice.VoiceInteractionService

class CircleVoiceInteractionService : VoiceInteractionService() {
    override fun onReady() {
        super.onReady()
        // Lightweight system-bound service; stays completely idle until invoked by the user.
    }
}
