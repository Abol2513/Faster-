package com.example.ui.overlay

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.capture.ScreenCaptureRequestActivity
import com.example.data.session.OverlaySessionStore
import com.example.ui.theme.MyApplicationTheme

class OverlayActivity : ComponentActivity() {

    companion object {
        fun createIntent(context: Context): Intent {
            return Intent(context, OverlayActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION
                )
            }
        }
    }

    private val viewModel: OverlayViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        overridePendingTransition(0, 0)

        setContent {
            MyApplicationTheme {
                val state by viewModel.uiState.collectAsStateWithLifecycle()

                BackHandler {
                    closeAndReturnToPreviousApp()
                }

                CircleOverlayScreen(
                    state = state,
                    onGestureCompleted = viewModel::selectByGesture,
                    onSelectAll = viewModel::selectAll,
                    onClearSelection = viewModel::clearSelection,
                    onUpdateEditableText = viewModel::updateEditableText,
                    onSelectSourceLang = viewModel::setSourceLanguage,
                    onSelectTargetLang = viewModel::setTargetLanguage,
                    onSelectTranslationMode = viewModel::setTranslationEngineMode,
                    onSwapLanguages = viewModel::swapLanguages,
                    onRetranslate = viewModel::retranslate,
                    onDownloadMissingModels = viewModel::downloadMissingModelsAndRetranslate,
                    onCopyText = ::copyToClipboard,
                    onRequestMediaProjectionFallback = {
                        closeAndReturnToPreviousApp()
                        startActivity(ScreenCaptureRequestActivity.createIntent(this))
                    },
                    onOpenSheet = viewModel::openSheetManually,
                    onClose = ::closeAndReturnToPreviousApp
                )
            }
        }
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(ClipboardManager::class.java) ?: return
        val clip = ClipData.newPlainText("CircleTranslate", text)
        clipboard.setPrimaryClip(clip)
    }

    private fun closeAndReturnToPreviousApp() {
        OverlaySessionStore.clear()
        finishAndRemoveTask()
        overridePendingTransition(0, 0)
    }

    override fun onDestroy() {
        if (isFinishing) {
            OverlaySessionStore.clear()
        }
        super.onDestroy()
    }
}
