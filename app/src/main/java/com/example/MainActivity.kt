package com.example

import android.Manifest
import android.app.StatusBarManager
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.example.capture.CircleQuickTileService
import com.example.capture.ScreenCaptureRequestActivity
import com.example.ui.overlay.OverlayActivity
import com.example.ui.setup.SetupScreen
import com.example.ui.setup.SetupViewModel
import com.example.ui.theme.MyApplicationTheme
import com.example.util.DeviceCompatibilityHelper
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: SetupViewModel by viewModels()

    private val assistantRoleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.refreshSystemStatus()
    }

    private val notificationPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        startActivity(ScreenCaptureRequestActivity.createIntent(this, fromQuickTile = false))
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
                    onOpenAssistantSettings = {
                        DeviceCompatibilityHelper.openDefaultAssistantSettings(this@MainActivity)
                    },
                    onOpenXiaomiPopupPermissions = {
                        DeviceCompatibilityHelper.openMiuiOtherPermissions(this@MainActivity)
                    },
                    onOpenXiaomiAutostart = {
                        DeviceCompatibilityHelper.openMiuiAutostart(this@MainActivity)
                    },
                    onAddQuickTile = {
                        requestAddQuickSettingsTile { message ->
                            scope.launch { snackbarHostState.showSnackbar(message) }
                        }
                    },
                    onTestScreenCapture = ::launchScreenCaptureWithNotificationCheck,
                    onPickTestPhoto = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onSelectTargetLanguage = viewModel::setTargetLanguage,
                    onToggleOcrLanguage = viewModel::toggleOcrLanguage,
                    onToggleAutoTranslate = viewModel::setAutoTranslate,
                    onToggleAllowCellular = viewModel::setAllowCellularDownloads,
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

    private fun launchScreenCaptureWithNotificationCheck() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        startActivity(ScreenCaptureRequestActivity.createIntent(this, fromQuickTile = false))
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
        DeviceCompatibilityHelper.openDefaultAssistantSettings(this)
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
                            "کاشی «ترجمه صفحه» با موفقیت به تنظیمات سریع اضافه شد."
                        StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED ->
                            "کاشی «ترجمه صفحه» از قبل در نوار تنظیمات سریع شما موجود است."
                        StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED ->
                            "افزودن کاشی لغو شد؛ می‌توانید هر زمان از بخش ویرایش نوار اعلان سریع آن را اضافه کنید."
                        else ->
                            "می‌توانید از بخش ویرایش نوار اعلان سریع، کاشی «ترجمه صفحه» را به صورت دستی اضافه کنید."
                    }
                    onResultMessage(message)
                }
                return
            }
        }
        onResultMessage("نوار اعلان سریع را پایین بکشید، دکمه ویرایش را بزنید و کاشی «ترجمه صفحه» را اضافه کنید.")
    }
}
