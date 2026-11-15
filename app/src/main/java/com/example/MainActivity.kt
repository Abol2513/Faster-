package com.example

import android.app.StatusBarManager
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.example.capture.CircleQuickTileService
import com.example.capture.ScreenCaptureRequestActivity
import com.example.ui.overlay.OverlayActivity
import com.example.ui.setup.SetupScreen
import com.example.ui.setup.SetupViewModel
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: SetupViewModel by viewModels()

    private val assistantRoleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.refreshSystemStatus()
    }

    private val photoPickerLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            lifecycleScope.launch {
                val ok = viewModel.loadPhotoAndPublishSession(uri)
                if (ok) {
                    startActivity(OverlayActivity.createIntent(this@MainActivity))
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                val snackbarHostState = remember { SnackbarHostState() }
                val scope = rememberCoroutineScope()

                LaunchedEffect(Unit) {
                    viewModel.messages.collect { msg ->
                        snackbarHostState.showSnackbar(msg)
                    }
                }

                SetupScreen(
                    state = uiState,
                    snackbarHostState = snackbarHostState,
                    onRequestAssistantRole = ::requestAssistantRole,
                    onOpenAssistantSettings = ::openAssistantSystemSettings,
                    onAddQuickTile = {
                        requestAddQuickSettingsTile { message ->
                            scope.launch { snackbarHostState.showSnackbar(message) }
                        }
                    },
                    onTestScreenCapture = {
                        startActivity(ScreenCaptureRequestActivity.createIntent(this@MainActivity))
                    },
                    onPickTestPhoto = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onSelectTargetLanguage = viewModel::setTargetLanguage,
                    onToggleOcrLanguage = viewModel::toggleOcrLanguage,
                    onToggleAutoTranslate = viewModel::setAutoTranslate,
                    onDownloadOcr = viewModel::downloadOcrPack,
                    onDeleteOcr = viewModel::deleteOcrPack,
                    onDownloadTranslate = viewModel::downloadTranslateModel,
                    onDeleteTranslate = viewModel::deleteTranslateModel
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshSystemStatus()
        viewModel.refreshLanguagePacks()
    }

    private fun requestAssistantRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_ASSISTANT)) {
                if (!roleManager.isRoleHeld(RoleManager.ROLE_ASSISTANT)) {
                    assistantRoleLauncher.launch(
                        roleManager.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT)
                    )
                    return
                }
            }
        }
        openAssistantSystemSettings()
    }

    private fun openAssistantSystemSettings() {
        val intent = Intent(Settings.ACTION_VOICE_INPUT_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { startActivity(intent) }
            .onFailure {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            }
    }

    private fun requestAddQuickSettingsTile(onResultMessage: (String) -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val statusBarManager = getSystemService(StatusBarManager::class.java)
            if (statusBarManager != null) {
                statusBarManager.requestAddTileService(
                    ComponentName(this, CircleQuickTileService::class.java),
                    getString(R.string.tile_label),
                    Icon.createWithResource(this, R.drawable.ic_tile_circle_search),
                    mainExecutor
                ) { resultCode ->
                    val message = when (resultCode) {
                        StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ->
                            "کاشی «ترجمه صفحه» به تنظیمات سریع اضافه شد."
                        StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED ->
                            "کاشی «ترجمه صفحه» از قبل در تنظیمات سریع وجود دارد."
                        else ->
                            "می‌توانید از بخش ویرایش نوار اعلان سریع، کاشی «ترجمه صفحه» را اضافه کنید."
                    }
                    onResultMessage(message)
                }
                return
            }
        }
        onResultMessage("نوار اعلان سریع را پایین بکشید، دکمه ویرایش را بزنید و کاشی «ترجمه صفحه» را اضافه کنید.")
    }
}
