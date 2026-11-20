package com.elixirtracker

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
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
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

class ScreenCaptureService : Service() {

    companion object {
        const val ACTION_START = "start"
        const val ACTION_STOP = "stop"
        const val EXTRA_RESULT_CODE = "rc"
        const val EXTRA_RESULT_DATA = "rd"
        const val CHANNEL_ID = "elixir_capture"
        const val NOTIF_ID = 1001

        @Volatile var latestBitmap: Bitmap? = null
        @Volatile var screenW: Int = 0
        @Volatile var screenH: Int = 0
        @Volatile var running: Boolean = false

        fun getLatest(): Bitmap? = latestBitmap
    }

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private val handler = Handler(Looper.getMainLooper())
    private val engine = ElixirEngine()
    private val detector = UnitDetector()
    private var loopScope: CoroutineScope? = null
    private var lastDetectorAt = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val rc = intent.getIntExtra(EXTRA_RESULT_CODE, -1)
                val rd = intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
                if (rc != -1 && rd != null) startCapture(rc, rd)
            }
            ACTION_STOP -> stopCapture()
        }
        return START_NOT_STICKY
    }

    private fun startCapture(resultCode: Int, data: Intent) {
        if (running) return
        startForeground(NOTIF_ID, buildNotification())

        val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = mgr.getMediaProjection(resultCode, data)

        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        (getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(metrics)
        screenW = metrics.widthPixels
        screenH = metrics.heightPixels

        imageReader = ImageReader.newInstance(
            screenW, screenH, android.graphics.PixelFormat.RGBA_8888, 2
        )

        virtualDisplay = projection?.createVirtualDisplay(
            "ElixirCapture", screenW, screenH, metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, handler
        )

        imageReader?.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                val plane = image.planes[0]
                val buffer = plane.buffer
                val pixelStride = plane.pixelStride
                val rowStride = plane.rowStride
                val rowPadding = rowStride - pixelStride * screenW
                val bmp = Bitmap.createBitmap(
                    screenW + rowPadding / pixelStride,
                    screenH, Bitmap.Config.ARGB_8888
                )
                bmp.copyPixelsFromBuffer(buffer)
                latestBitmap = if (rowPadding != 0) {
                    val cropped = Bitmap.createBitmap(bmp, 0, 0, screenW, screenH)
                    bmp.recycle()
                    cropped
                } else bmp
            } catch (_: Exception) {
            } finally {
                image.close()
            }
        }, handler)

        running = true
        engine.start()
        detector.reset()

        loopScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        loopScope?.launch {
            while (running) {
                try {
                    engine.tick()
                    val now = System.currentTimeMillis()
                    if (now - lastDetectorAt > 150) {
                        lastDetectorAt = now
                        val bmp = latestBitmap
                        if (bmp != null) {
                            val dets = detector.detect(bmp)
                            for (_d in dets) {
                                engine.onEnemyUnitDeployed(ElixirEngine.DEFAULT_UNIT_COST)
                            }
                        }
                    }
                } catch (_: Exception) {}
                delay(100)
            }
        }
    }

    private fun stopCapture() {
        running = false
        loopScope?.cancel()
        loopScope = null
        engine.stop()
        virtualDisplay?.release(); virtualDisplay = null
        imageReader?.close(); imageReader = null
        projection?.stop(); projection = null
        latestBitmap = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "Elixir Capture", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ElixirTracker")
            .setContentText("يقرأ الشاشة")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        stopCapture()
        super.onDestroy()
    }
}
