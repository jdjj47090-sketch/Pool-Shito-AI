package com.mahdi.poolshito

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private val captureRequest = 4101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 64, 48, 48)
            setBackgroundColor(android.graphics.Color.rgb(8, 6, 16))
        }
        val title = TextView(this).apply {
            text = "POOL SHITO AI"
            textSize = 28f
            setTextColor(android.graphics.Color.WHITE)
        }
        val subtitle = TextView(this).apply {
            text = "Screen analysis • Shot paths • Bank-shot geometry"
            textSize = 14f
            setTextColor(0xFFBDA8D8.toInt())
            setPadding(0, 12, 0, 36)
        }
        val overlayButton = Button(this).apply {
            text = "Grant overlay permission"
            setOnClickListener {
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                } else {
                    Toast.makeText(this@MainActivity, "Overlay permission is enabled", Toast.LENGTH_SHORT).show()
                }
            }
        }
        val captureButton = Button(this).apply {
            text = "Start screen analysis"
            setOnClickListener { requestScreenCapture() }
        }
        val note = TextView(this).apply {
            text = "This starter requests screen capture only after your approval. Live ball detection and trajectory calculations are the next development milestone."
            textSize = 13f
            setTextColor(0xFFE0D7EA.toInt())
            setPadding(0, 28, 0, 0)
        }
        layout.addView(title)
        layout.addView(subtitle)
        layout.addView(overlayButton)
        layout.addView(captureButton)
        layout.addView(note)
        setContentView(layout)
    }

    private fun requestScreenCapture() {
        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(manager.createScreenCaptureIntent(), captureRequest)
    }

    @Deprecated("Deprecated in Android API; kept for broad starter compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == captureRequest && resultCode == Activity.RESULT_OK && data != null) {
            val serviceIntent = Intent(this, CaptureService::class.java).apply {
                putExtra(CaptureService.EXTRA_RESULT_CODE, resultCode)
                putExtra(CaptureService.EXTRA_RESULT_DATA, data)
            }
            androidx.core.content.ContextCompat.startForegroundService(this, serviceIntent)
            Toast.makeText(this, "Screen capture permission granted", Toast.LENGTH_SHORT).show()
        }
    }
}
