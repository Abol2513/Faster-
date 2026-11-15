package com.example.capture

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

class ScreenCaptureRequestActivity : ComponentActivity() {

    companion object {
        private const val EXTRA_FROM_QUICK_TILE = "extra_from_quick_tile"

        fun createIntent(context: Context, fromQuickTile: Boolean = false): Intent {
            return Intent(context, ScreenCaptureRequestActivity::class.java).apply {
                putExtra(EXTRA_FROM_QUICK_TILE, fromQuickTile)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            }
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var launchedConsent = false

    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            val fromQuickTile = intent.getBooleanExtra(EXTRA_FROM_QUICK_TILE, false)
            val serviceIntent = ScreenCaptureService.createStartIntent(
                context = this,
                resultCode = result.resultCode,
                resultData = data,
                fromQuickTile = fromQuickTile
            )
            ContextCompat.startForegroundService(this, serviceIntent)
        }
        finish()
        overridePendingTransition(0, 0)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(0, 0)

        val projectionManager = getSystemService(MediaProjectionManager::class.java)
        if (projectionManager == null) {
            finish()
            return
        }

        val fromQuickTile = intent.getBooleanExtra(EXTRA_FROM_QUICK_TILE, false)
        val launchDelayMs = if (fromQuickTile) 220L else 0L

        handler.postDelayed({
            if (!isFinishing && !isDestroyed && !launchedConsent) {
                launchedConsent = true
                projectionLauncher.launch(projectionManager.createScreenCaptureIntent())
            }
        }, launchDelayMs)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
