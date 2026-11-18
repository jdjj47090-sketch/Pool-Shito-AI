package com.mahdi.poolshito.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathEffect
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import com.mahdi.poolshito.core.analysis.AnalysisSnapshot
import com.mahdi.poolshito.core.analysis.BallKind
import com.mahdi.poolshito.core.analysis.TrajectoryKind
import com.mahdi.poolshito.geometry.Point2
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Draws only results supplied by the real frame analysis engine. */
class AnalysisOverlayView(context: Context) : View(context) {
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = dp(2f); strokeCap = Paint.Cap.ROUND }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = dp(12f); typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL) }
    private val dashed: PathEffect = android.graphics.DashPathEffect(floatArrayOf(dp(9f), dp(6f)), 0f)
    private var snapshot: AnalysisSnapshot? = null
    private var calibrationMode = false
    private val calibrationPoints = mutableListOf<Point2>()
    var onCalibrationPoint: ((Point2, Int) -> Unit)? = null

    fun update(value: AnalysisSnapshot) { snapshot = value; invalidate() }
    fun isCalibrationActive() = calibrationMode
    fun setCalibrationMode(active: Boolean) { calibrationMode = active; calibrationPoints.clear(); invalidate() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val current = snapshot ?: return
        if (current.imageWidth <= 0 || current.imageHeight <= 0) return
        val scale = min(width / current.imageWidth.toFloat(), height / current.imageHeight.toFloat())
        val left = (width - current.imageWidth * scale) / 2f
        val top = (height - current.imageHeight * scale) / 2f
        fun sx(p: Point2) = left + p.x.toFloat() * scale
        fun sy(p: Point2) = top + p.y.toFloat() * scale

        if (current.tableCorners.size == 4) {
            line.color = Color.rgb(50, 226, 165); line.strokeWidth = dp(2.5f); line.pathEffect = null
            canvas.drawPath(pathOf(current.tableCorners.map { sx(it) to sy(it) }, true), line)
        }
        current.trajectories.forEach { segment ->
            val points = segment.points
            if (points.size < 2) return@forEach
            line.color = if (segment.kind == TrajectoryKind.CUE_BALL) Color.rgb(90, 190, 255) else Color.rgb(255, 194, 84)
            line.strokeWidth = dp(2.2f); line.pathEffect = if (segment.kind == TrajectoryKind.CUE_BALL) dashed else null
            canvas.drawPath(pathOf(points.map { sx(it) to sy(it) }, false), line)
            val last = points.last(); val prev = points[points.lastIndex - 1]
            drawArrow(canvas, sx(prev), sy(prev), sx(last), sy(last), line.color)
        }
        line.pathEffect = null
        current.detections.forEach { ball ->
            val color = when (ball.kind) {
                BallKind.CUE -> Color.WHITE
                BallKind.SOLID -> Color.rgb(255, 167, 74)
                BallKind.STRIPE -> Color.rgb(95, 190, 255)
                BallKind.UNKNOWN -> Color.rgb(215, 120, 255)
            }
            line.color = color; line.strokeWidth = dp(1.7f)
            canvas.drawCircle(sx(ball.center), sy(ball.center), (ball.radiusPx * scale.toDouble()).toFloat().coerceAtLeast(dp(6f)), line)
            fill.color = color; fill.alpha = 230
            canvas.drawCircle(sx(ball.center), sy(ball.center), dp(2f), fill)
            text.color = color; text.textSize = dp(10f)
            val track = current.tracks.minByOrNull { (it.position - ball.center).length() }
            if (track != null && track.missedFrames == 0 && (track.position - ball.center).length() < ball.radiusPx * 2.0) {
                canvas.drawText("#${track.id} ${(ball.confidence * 100).toInt()}%", sx(ball.center) + dp(8f), sy(ball.center) - dp(8f), text)
                if (track.isMoving) {
                    val dx = (track.velocity.x * 0.12 * scale.toDouble()).toFloat().coerceIn(-dp(90f), dp(90f))
                    val dy = (track.velocity.y * 0.12 * scale.toDouble()).toFloat().coerceIn(-dp(90f), dp(90f))
                    drawArrow(canvas, sx(track.position), sy(track.position), sx(track.position) + dx, sy(track.position) + dy, color)
                }
            }
            text.color = Color.WHITE
        }
        if (calibrationMode) {
            fill.color = Color.argb(210, 8, 6, 16)
            canvas.drawRoundRect(RectF(dp(12f), height - dp(110f), width - dp(12f), height - dp(62f)), dp(12f), dp(12f), fill)
            text.textSize = dp(13f); text.color = Color.WHITE
            val labels = listOf("long-rail start", "long-rail end", "third corner", "final corner")
            canvas.drawText("Tap ${labels.getOrElse(calibrationPoints.size) { "corner" }} table corner (${calibrationPoints.size + 1}/4)", dp(24f), height - dp(82f), text)
            text.textSize = dp(11f); text.color = Color.LTGRAY
            canvas.drawText("Choose the longest rail, then tap its corners clockwise", dp(24f), height - dp(67f), text)
            calibrationPoints.forEachIndexed { i, p ->
                fill.color = Color.rgb(50, 226, 165); canvas.drawCircle(sx(p), sy(p), dp(6f), fill)
                text.color = Color.WHITE; text.textSize = dp(11f); canvas.drawText("${i + 1}", sx(p) + dp(8f), sy(p), text)
            }
        } else {
            fill.color = Color.argb(205, 8, 6, 16)
            canvas.drawRoundRect(RectF(dp(12f), dp(12f), width - dp(12f), dp(62f)), dp(12f), dp(12f), fill)
            text.color = Color.WHITE; text.textSize = dp(12f)
            canvas.drawText(fitText(current.statusMessage, width - dp(46f), text), dp(23f), dp(34f), text)
            val pathDescription = current.trajectories.firstOrNull()?.description
            if (pathDescription != null) {
                text.color = 0xFFFFD38B.toInt(); text.textSize = dp(10f)
                canvas.drawText(fitText(pathDescription, width - dp(46f), text), dp(23f), dp(51f), text)
            }
            text.color = if (current.calibrationConfidence > 0.7) Color.rgb(50, 226, 165) else Color.rgb(255, 194, 84)
            text.textSize = dp(10f)
            val confidence = if (current.calibrationConfidence > 0.0) "CAL ${(current.calibrationConfidence * 100).toInt()}%" else "CALIBRATE"
            canvas.drawText(confidence, width - dp(88f), dp(66f), text)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!calibrationMode || event.action != MotionEvent.ACTION_UP) return calibrationMode
        val frame = snapshot ?: return true
        if (frame.imageWidth <= 0 || frame.imageHeight <= 0) return true
        val scale = min(width / frame.imageWidth.toFloat(), height / frame.imageHeight.toFloat())
        val left = (width - frame.imageWidth * scale) / 2f
        val top = (height - frame.imageHeight * scale) / 2f
        val x = (event.x - left) / scale; val y = (event.y - top) / scale
        if (x in 0f..frame.imageWidth.toFloat() && y in 0f..frame.imageHeight.toFloat() && calibrationPoints.size < 4) {
            val point = Point2(x.toDouble(), y.toDouble())
            calibrationPoints += point
            onCalibrationPoint?.invoke(point, calibrationPoints.size)
            if (calibrationPoints.size == 4) calibrationMode = false
            invalidate()
        }
        return true
    }

    private fun pathOf(points: List<Pair<Float, Float>>, close: Boolean): Path = Path().apply {
        if (points.isNotEmpty()) { moveTo(points[0].first, points[0].second); points.drop(1).forEach { lineTo(it.first, it.second) }; if (close) close() }
    }

    private fun drawArrow(canvas: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, color: Int) {
        line.color = color; line.strokeWidth = dp(2f); line.pathEffect = null
        canvas.drawLine(x1, y1, x2, y2, line)
        val angle = atan2(y2 - y1, x2 - x1); val head = dp(8f)
        canvas.drawLine(x2, y2, x2 - head * cos(angle - 0.55f), y2 - head * sin(angle - 0.55f), line)
        canvas.drawLine(x2, y2, x2 - head * cos(angle + 0.55f), y2 - head * sin(angle + 0.55f), line)
    }

    private fun fitText(value: String, maxWidth: Float, paint: Paint): String {
        if (paint.measureText(value) <= maxWidth) return value
        var shortened = value
        while (shortened.isNotEmpty() && paint.measureText("$shortened…") > maxWidth) shortened = shortened.dropLast(1)
        return "$shortened…"
    }

    private fun dp(value: Float) = value * resources.displayMetrics.density
}
