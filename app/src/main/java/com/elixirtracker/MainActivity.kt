package com.elixirtracker

import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var status: TextView
    private lateinit var btnCapture: Button
    private lateinit var btnOverlay: Button
    private lateinit var btnReset: Button

    private val captureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val i = Intent(this, ScreenCaptureService::class.java).apply {
                action = ScreenCaptureService.ACTION_START
                putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, result.data)
            }
            ContextCompat.startForegroundService(this, i)
            status.text = "التقاط الشاشة: يعمل"
        } else {
            status.text = "التقاط الشاشة: رُفض"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        status = findViewById(R.id.status)
        btnCapture = findViewById(R.id.btnCapture)
        btnOverlay = findViewById(R.id.btnOverlay)
        btnReset = findViewById(R.id.btnReset)

        btnCapture.setOnClickListener {
            val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            captureLauncher.launch(mgr.createScreenCaptureIntent())
        }

        btnOverlay.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                ))
            } else {
                startService(Intent(this, OverlayService::class.java))
                status.text = "Overlay: يعمل"
            }
        }

        btnReset.setOnClickListener {
            stopService(Intent(this, ScreenCaptureService::class.java))
            ElixirState.enemyElixir.value = 5f
            ElixirState.myElixir.value = 5f
            ElixirState.unitsSeen.value = 0
            ElixirState.elapsed.value = 0
            status.text = "تم التصفير — أعد بدء الالتقاط"
        }
    }
}
