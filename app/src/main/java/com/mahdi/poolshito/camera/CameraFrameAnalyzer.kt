package com.mahdi.poolshito.camera

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.mahdi.poolshito.core.analysis.AnalysisSnapshot
import com.mahdi.poolshito.core.analysis.FrameThrottle
import com.mahdi.poolshito.core.calibration.TableCalibration
import com.mahdi.poolshito.geometry.Point2
import com.mahdi.poolshito.vision.PoolAnalysisEngine
import org.opencv.android.OpenCVLoader
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** CameraX adapter. CameraX uses a single executor and KEEP_ONLY_LATEST; every image closes in finally. */
class CameraFrameAnalyzer(
    private val onResult: (AnalysisSnapshot?, String?) -> Unit,
) : ImageAnalysis.Analyzer, AutoCloseable {
    private val converter = ImageProxyMatConverter()
    private val throttle = FrameThrottle(125_000_000L)
    private val targetFps = AtomicInteger(8)
    private val mirrorHorizontally = AtomicBoolean(false)
    private val manualCorners = AtomicReference<List<Point2>?>(null)
    private val resetRequested = AtomicBoolean(false)
    private var engine: PoolAnalysisEngine? = null
    @Volatile private var closed = false
    private var initializationFailed = false

    fun setMirrorHorizontally(enabled: Boolean) { mirrorHorizontally.set(enabled) }
    fun setTargetFps(fps: Int) { targetFps.set(fps.coerceIn(2, 15)) }
    fun setManualCalibration(corners: List<Point2>) {
        require(TableCalibration.fromCorners(corners) != null) { "Selected table corners are degenerate; tap the four cushion corners clockwise" }
        manualCorners.set(corners.toList())
    }
    fun clearCalibration() { resetRequested.set(true) }

    override fun analyze(image: ImageProxy) {
        try {
            if (closed || initializationFailed) return
            throttle.setIntervalNs(1_000_000_000L / targetFps.get())
            if (!throttle.accept(image.imageInfo.timestamp)) return
            val started = android.os.SystemClock.elapsedRealtimeNanos()
            val activeEngine = engine ?: run {
                if (!OpenCVLoader.initLocal()) {
                    initializationFailed = true
                    onResult(null, "OpenCV could not load. Check device ABI support and reinstall the app.")
                    return
                }
                PoolAnalysisEngine().also { engine = it }
            }
            if (resetRequested.getAndSet(false)) activeEngine.clearCalibration()
            manualCorners.getAndSet(null)?.let { activeEngine.setManualCalibration(it) }
            val rgb = converter.convert(image, mirrorHorizontally.get())
            val result = activeEngine.process(rgb, image.imageInfo.timestamp)
            val elapsed = ((android.os.SystemClock.elapsedRealtimeNanos() - started) / 1_000_000L).coerceAtLeast(0L)
            onResult(result.copy(latencyMs = elapsed, statusMessage = "${result.statusMessage} · ${elapsed} ms"), null)
        } catch (error: Exception) {
            onResult(null, error.message?.let { "Analysis error: $it" } ?: "Analysis failed on this frame")
        } finally {
            image.close()
        }
    }

    override fun close() {
        closed = true
        engine?.close(); engine = null
        converter.close()
    }
}
