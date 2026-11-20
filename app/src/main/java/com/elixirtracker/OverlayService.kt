package com.elixirtracker

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

class OverlayService : Service() {

    companion object {
        const val CHANNEL_ID = "elixir_overlay"
        const val NOTIF_ID = 1002
    }

    private var wm: WindowManager? = null
    private var view: View? = null
    private var scope: CoroutineScope? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            startForeground(NOTIF_ID, buildNotification())
            if (view == null) {
                show()
            }
        } catch (e: Exception) {
            // تجاهل الخطأ بدل ما يكرش التطبيق
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "Elixir Overlay", NotificationManager.IMPORTANCE_LOW)
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(ch)
        }
    }

    private fun show() {
        try {
            wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 30; y = 300
            }

            val v = View.inflate(this, R.layout.overlay_elixir, null)
            val enemyText = v.findViewById<TextView>(R.id.enemyElixir)
            val myText = v.findViewById<TextView>(R.id.myElixir)
            val unitsText = v.findViewById<TextView>(R.id.unitsText)
            val timeText = v.findViewById<TextView>(R.id.timeText)

            v.setOnTouchListener(object : View.OnTouchListener {
                private var ix = 0; private var iy = 0
                private var tx = 0f; private var ty = 0f
                override fun onTouch(vv: View?, e: MotionEvent): Boolean {
                    when (e.action) {
                        MotionEvent.ACTION_DOWN -> {
                            ix = params.x; iy = params.y
                            tx = e.rawX; ty = e.rawY; return true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            params.x = ix + (e.rawX - tx).toInt()
                            params.y = iy + (e.rawY - ty).toInt()
                            wm?.updateViewLayout(view, params)
                            return true
                        }
                    }
                    return false
                }
            })

            view = v
            wm?.addView(v, params)

            scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
            scope?.launch {
                launch { ElixirState.enemyElixir.collect { enemyText.text = String.format("%.1f", it) } }
                launch { ElixirState.myElixir.collect { myText.text = String.format("%.1f", it) } }
                launch { ElixirState.unitsSeen.collect { unitsText.text = "وحدات: $it" } }
                launch {
                    ElixirState.elapsed.collect {
                        val sec = it / 1000
                        val m = sec / 60; val s = sec % 60
                        timeText.text = String.format("%02d:%02d", m, s)
                    }
                }
            }
        } catch (e: Exception) {
            // إذا فشل الـ Overlay، لا نكرش التطبيق
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ElixirTracker")
            .setContentText("Overlay نشط")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        scope?.cancel()
        try {
            view?.let { wm?.removeView(it) }
        } catch (e: Exception) {}
        view = null
        super.onDestroy()
    }
}
