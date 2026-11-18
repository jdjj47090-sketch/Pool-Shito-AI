package com.mahdi.poolshito

import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.os.Bundle
import android.view.Gravity
import android.view.Surface
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.mahdi.poolshito.camera.CameraFrameAnalyzer
import com.mahdi.poolshito.core.analysis.AnalysisSnapshot
import com.mahdi.poolshito.core.analysis.CalibrationStatus
import com.mahdi.poolshito.core.calibration.ManualCalibrationInput
import com.mahdi.poolshito.ui.AnalysisOverlayView
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var previewView: PreviewView
    private lateinit var overlay: AnalysisOverlayView
    private lateinit var cameraMessage: TextView
    private lateinit var calibrateButton: MaterialButton
    private lateinit var resetButton: MaterialButton
    private lateinit var lensButton: MaterialButton
    private lateinit var fpsButton: MaterialButton
    private lateinit var resolutionButton: MaterialButton
    private val analyzerExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val frameAnalyzer = CameraFrameAnalyzer(::onFrameResult)
    private val calibrationInput = ManualCalibrationInput()
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var processingFps = 8
    private var processingWidth = 1280
    private var cameraProviderFutureStarted = false

    private val permissionRequest = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) { cameraMessage.visibility = View.GONE; bindCameraIfReady() }
        else showPermissionHelp()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(8, 6, 16)
        window.navigationBarColor = android.graphics.Color.rgb(8, 6, 16)
        setContentView(createScreen())
        overlay.onCalibrationPoint = { point, count ->
            val selectedCorners = calibrationInput.add(point)
            if (count == 4 && selectedCorners != null) {
                try {
                    frameAnalyzer.setManualCalibration(selectedCorners)
                    calibrateButton.text = "Recalibrate table"
                    cameraMessage.visibility = View.GONE
                } catch (error: IllegalArgumentException) {
                    calibrationInput.cancel(); overlay.setCalibrationMode(false)
                    showCameraMessage(error.message ?: "Those corners could not calibrate the table")
                }
            }
        }
        if (hasCameraPermission()) bindCameraIfReady() else requestCameraPermission()
    }

    override fun onStart() {
        super.onStart()
        if (hasCameraPermission()) bindCameraIfReady()
    }

    override fun onStop() {
        cameraProvider?.unbindAll()
        imageAnalysis?.clearAnalyzer()
        imageAnalysis = null
        super.onStop()
    }

    override fun onDestroy() {
        imageAnalysis?.clearAnalyzer()
        cameraProvider?.unbindAll()
        analyzerExecutor.execute { frameAnalyzer.close() }
        analyzerExecutor.shutdown()
        super.onDestroy()
    }

    private fun createScreen(): View {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(android.graphics.Color.rgb(8, 6, 16))
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(10))
        }
        header.addView(TextView(this).apply {
            text = "POOL SHITO AI"; textSize = 20f; setTextColor(android.graphics.Color.WHITE)
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
        })
        header.addView(TextView(this).apply {
            text = "Live table analysis · camera frames stay on this device"
            textSize = 12f; setTextColor(0xFFBDA8D8.toInt()); setPadding(0, dp(3), 0, 0)
        })
        page.addView(header)

        val cameraLayer = FrameLayout(this).apply { setBackgroundColor(android.graphics.Color.rgb(12, 17, 24)) }
        previewView = PreviewView(this).apply {
            scaleType = PreviewView.ScaleType.FIT_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
        cameraLayer.addView(previewView, FrameLayout.LayoutParams(-1, -1))
        overlay = AnalysisOverlayView(this)
        cameraLayer.addView(overlay, FrameLayout.LayoutParams(-1, -1))
        cameraMessage = TextView(this).apply {
            text = "Waiting for camera permission"; textSize = 15f; gravity = Gravity.CENTER
            setTextColor(android.graphics.Color.WHITE); setPadding(dp(28), dp(18), dp(28), dp(18))
            setBackgroundColor(0xCC080610.toInt())
        }
        cameraLayer.addView(cameraMessage, FrameLayout.LayoutParams(-1, -1))
        page.addView(cameraLayer, LinearLayout.LayoutParams(-1, 0, 1f))

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(8), dp(12), dp(10))
            setBackgroundColor(android.graphics.Color.rgb(12, 9, 22))
        }
        val firstRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        calibrateButton = actionButton("Calibrate table") { toggleCalibration() }
        resetButton = actionButton("Reset calibration") {
            calibrationInput.cancel(); overlay.setCalibrationMode(false); frameAnalyzer.clearCalibration()
            calibrateButton.text = "Calibrate table"
        }
        firstRow.addView(calibrateButton, weightedButtonParams())
        firstRow.addView(resetButton, weightedButtonParams())
        controls.addView(firstRow)
        val secondRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(6), 0, 0) }
        lensButton = actionButton("Use front camera") { toggleLens() }
        fpsButton = actionButton("Analysis 8 fps") {
            processingFps = if (processingFps == 8) 5 else 8
            frameAnalyzer.setTargetFps(processingFps)
            fpsButton.text = "Analysis $processingFps fps"
        }
        secondRow.addView(lensButton, weightedButtonParams())
        secondRow.addView(fpsButton, weightedButtonParams())
        controls.addView(secondRow)
        resolutionButton = actionButton("Resolution 720p") {
            processingWidth = if (processingWidth == 1280) 640 else 1280
            resolutionButton.text = if (processingWidth == 1280) "Resolution 720p" else "Resolution 360p"
            bindCameraIfReady()
        }
        controls.addView(resolutionButton, LinearLayout.LayoutParams(-1, dp(42)).apply { setMargins(dp(4), dp(6), dp(4), 0) })
        page.addView(controls)
        return page
    }

    private fun actionButton(label: String, action: () -> Unit) = MaterialButton(this).apply {
        text = label; textSize = 12f; isAllCaps = false; cornerRadius = dp(12)
        setOnClickListener { action() }
    }

    private fun weightedButtonParams() = LinearLayout.LayoutParams(0, dp(46), 1f).apply {
        marginStart = dp(4); marginEnd = dp(4)
    }

    private fun toggleCalibration() {
        if (overlay.isCalibrationActive()) {
            overlay.setCalibrationMode(false); calibrationInput.cancel(); calibrateButton.text = "Calibrate table"
            return
        }
        calibrationInput.begin()
        overlay.setCalibrationMode(true)
        calibrateButton.text = "Cancel calibration"
    }

    private fun toggleLens() {
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
        frameAnalyzer.setMirrorHorizontally(lensFacing == CameraSelector.LENS_FACING_FRONT)
        lensButton.text = if (lensFacing == CameraSelector.LENS_FACING_BACK) "Use front camera" else "Use rear camera"
        bindCameraIfReady()
    }

    private fun hasCameraPermission() = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun requestCameraPermission() {
        permissionRequest.launch(Manifest.permission.CAMERA)
    }

    private fun showPermissionHelp() {
        cameraMessage.text = if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA))
            "Camera permission is needed to show and analyze the pool table. Grant it to continue."
        else "Camera access is disabled. Allow it in Android Settings to use live table analysis."
        cameraMessage.visibility = View.VISIBLE
        cameraMessage.setOnClickListener {
            if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) requestCameraPermission()
            else startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }
    }

    private fun showCameraMessage(message: String) {
        cameraMessage.text = message; cameraMessage.visibility = View.VISIBLE
        cameraMessage.setOnClickListener(null)
    }

    private fun bindCameraIfReady() {
        if (!::previewView.isInitialized || !hasCameraPermission()) return
        cameraProvider?.let { bindUseCases(); return }
        if (cameraProviderFutureStarted) return
        cameraProviderFutureStarted = true
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                cameraProvider = future.get()
                bindUseCases()
            } catch (error: Exception) {
                showCameraMessage("Could not open camera: ${error.cause?.message ?: error.message ?: "unknown camera error"}")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindUseCases() {
        val provider = cameraProvider ?: return
        try {
            provider.unbindAll()
            val rotation = previewView.display?.rotation ?: Surface.ROTATION_0
            val preview = Preview.Builder().setTargetAspectRatio(AspectRatio.RATIO_16_9).setTargetRotation(rotation).build()
            preview.setSurfaceProvider(previewView.surfaceProvider)
            val analysis = ImageAnalysis.Builder()
                .setTargetResolution(android.util.Size(processingWidth, processingWidth * 9 / 16))
                .setTargetRotation(rotation)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .build()
            analysis.setAnalyzer(analyzerExecutor, frameAnalyzer)
            val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
            provider.bindToLifecycle(this, selector, preview, analysis)
            imageAnalysis = analysis
            cameraMessage.visibility = View.GONE
        } catch (error: Exception) {
            showCameraMessage("Camera unavailable: ${error.cause?.message ?: error.message ?: "unsupported camera configuration"}")
        }
    }

    private fun onFrameResult(snapshot: AnalysisSnapshot?, error: String?) {
        runOnUiThread {
            if (isFinishing || isDestroyed) return@runOnUiThread
            if (snapshot != null) {
                overlay.update(snapshot)
                if (cameraMessage.visibility != View.GONE) cameraMessage.visibility = View.GONE
                if (snapshot.calibrationStatus == CalibrationStatus.CALIBRATED && !overlay.isCalibrationActive()) calibrateButton.text = "Recalibrate table"
            } else if (error != null) showCameraMessage(error)
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
