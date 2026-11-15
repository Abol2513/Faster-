package com.example.assistant

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.example.capture.ScreenCaptureRequestActivity

/**
 * Fallback Activity for OEM launchers or system shortcuts that fire Intent.ACTION_ASSIST directly.
 * Launches the single-frame MediaProjection capture flow seamlessly and finishes immediately.
 */
class AssistFallbackActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(0, 0)
        if (intent?.action == Intent.ACTION_ASSIST || intent?.action == Intent.ACTION_SEARCH_LONG_PRESS) {
            startActivity(ScreenCaptureRequestActivity.createIntent(this, fromQuickTile = false))
        }
        finish()
        overridePendingTransition(0, 0)
    }
}
