package com.example.capture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.R
import com.example.data.session.OverlaySessionStore
import com.example.domain.model.CaptureSource
import com.example.ui.overlay.OverlayActivity
import java.util.concurrent.atomic.AtomicBoolean

class ScreenCaptureService : Service() {

    companion object {
        private const val TAG = "ScreenCaptureService"
        private const val CHANNEL_ID = "screen_capture_once"
        private const val NOTIFICATION_ID = 4102
        private const val EXTRA_RESULT_CODE = "extra_result_code"
        private const val EXTRA_RESULT_DATA = "extra_result_data"

        fun createStartIntent(context: Context, resultCode: Int, resultData: Intent): Intent {
            return Intent(context, ScreenCaptureService::class.java).apply {
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, resultData)
            }
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private val captured = AtomicBoolean(false)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureNotificationChannel()
        val notification = buildNotification()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, -1) ?: -1
        val resultData: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_RESULT_DATA)
        }

        if (resultCode == -1 || resultData == null) {
            stopAndCleanup()
            return START_NOT_STICKY
        }

        val projectionManager = getSystemService(MediaProjectionManager::class.java)
        val projection = projectionManager?.getMediaProjection(resultCode, resultData)
        if (projection == null) {
            stopAndCleanup()
            return START_NOT_STICKY
        }

        mediaProjection = projection
        projection.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                super.onStop()
                releaseProjectionResources()
                stopSelf()
            }
        }, mainHandler)

        // Short delay so the system projection consent dialog/QS shade finishes closing cleanly
        mainHandler.postDelayed({ captureSingleFrame(projection) }, 240L)

        // Safety timeout in case no frame arrives
        mainHandler.postDelayed({
            if (!captured.get()) {
                OverlaySessionStore.publish(
                    bitmap = null,
                    source = CaptureSource.MEDIA_PROJECTION,
                    isProtectedOrUnavailable = true
                )
                startActivity(OverlayActivity.createIntent(this))
                stopAndCleanup()
            }
        }, 3000L)

        return START_NOT_STICKY
    }

    private fun captureSingleFrame(projection: MediaProjection) {
        val (width, height, densityDpi) = getDisplayMetrics()
        val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        imageReader = reader

        reader.setOnImageAvailableListener({ r ->
            if (captured.get()) return@setOnImageAvailableListener
            val image = try {
                r.acquireLatestImage()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to acquire image", e)
                null
            } ?: return@setOnImageAvailableListener

            if (!captured.compareAndSet(false, true)) {
                image.close()
                return@setOnImageAvailableListener
            }

            try {
                val plane = image.planes[0]
                val buffer = plane.buffer
                val pixelStride = plane.pixelStride
                val rowStride = plane.rowStride
                val rowPadding = rowStride - pixelStride * width

                val tempBitmap = Bitmap.createBitmap(
                    width + rowPadding / pixelStride,
                    height,
                    Bitmap.Config.ARGB_8888
                )
                tempBitmap.copyPixelsFromBuffer(buffer)

                val croppedBitmap = Bitmap.createBitmap(tempBitmap, 0, 0, width, height)
                if (tempBitmap !== croppedBitmap) {
                    tempBitmap.recycle()
                }

                OverlaySessionStore.publish(
                    bitmap = croppedBitmap,
                    source = CaptureSource.MEDIA_PROJECTION,
                    isProtectedOrUnavailable = false
                )
                startActivity(OverlayActivity.createIntent(this))
            } catch (e: Exception) {
                Log.e(TAG, "Error converting captured frame", e)
                OverlaySessionStore.publish(
                    bitmap = null,
                    source = CaptureSource.MEDIA_PROJECTION,
                    isProtectedOrUnavailable = true
                )
                startActivity(OverlayActivity.createIntent(this))
            } finally {
                image.close()
                stopAndCleanup()
            }
        }, mainHandler)

        virtualDisplay = projection.createVirtualDisplay(
            "CircleTranslateCapture",
            width,
            height,
            densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface,
            object : VirtualDisplay.Callback() {},
            mainHandler
        )
    }

    private fun getDisplayMetrics(): Triple<Int, Int, Int> {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val densityDpi = resources.displayMetrics.densityDpi
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = wm.maximumWindowMetrics.bounds
            Triple(bounds.width().coerceAtLeast(1), bounds.height().coerceAtLeast(1), densityDpi)
        } else {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(metrics)
            Triple(metrics.widthPixels.coerceAtLeast(1), metrics.heightPixels.coerceAtLeast(1), metrics.densityDpi)
        }
    }

    private fun stopAndCleanup() {
        mainHandler.removeCallbacksAndMessages(null)
        releaseProjectionResources()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun releaseProjectionResources() {
        try {
            virtualDisplay?.release()
        } catch (_: Exception) {
        }
        virtualDisplay = null

        try {
            imageReader?.close()
        } catch (_: Exception) {
        }
        imageReader = null

        try {
            mediaProjection?.stop()
        } catch (_: Exception) {
        }
        mediaProjection = null
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_capture_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_capture_desc)
                setShowBadge(false)
            }
            nm?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tile_circle_search)
            .setContentTitle(getString(R.string.notification_capture_title))
            .setContentText(getString(R.string.notification_capture_text))
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        releaseProjectionResources()
        super.onDestroy()
    }
}
