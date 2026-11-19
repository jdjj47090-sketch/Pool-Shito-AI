package com.mahdi.poolshito.camera

import android.graphics.ImageFormat
import com.mahdi.poolshito.core.camera.Yuv420Nv21Packer
import androidx.camera.core.ImageProxy
import org.opencv.core.CvType
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc

/** Reuses the YUV/RGB Mats and byte storage across camera frames. Call only from one analyzer thread. */
class ImageProxyMatConverter : AutoCloseable {
    private val packer = Yuv420Nv21Packer()
    private var yuvBytes = ByteArray(0)
    private val yuv = Mat()
    private val rgb = Mat()
    private val rotated = Mat()
    private val flipped = Mat()

    fun convert(image: ImageProxy, mirrorHorizontally: Boolean = false): Mat {
        require(image.format == ImageFormat.YUV_420_888) { "Expected YUV_420_888 camera frame" }
        val width = image.width; val height = image.height
        val planes = image.planes
        yuvBytes = packer.pack(
            width, height,
            planes[0].buffer, planes[0].rowStride,
            planes[1].buffer, planes[1].rowStride, planes[1].pixelStride,
            planes[2].buffer, planes[2].rowStride, planes[2].pixelStride,
        )
        yuv.create(height + height / 2, width, CvType.CV_8UC1)
        yuv.put(0, 0, yuvBytes)
        Imgproc.cvtColor(yuv, rgb, Imgproc.COLOR_YUV2RGB_NV21)
        when (image.imageInfo.rotationDegrees) {
            90 -> Core.rotate(rgb, rotated, Core.ROTATE_90_CLOCKWISE)
            180 -> Core.rotate(rgb, rotated, Core.ROTATE_180)
            270 -> Core.rotate(rgb, rotated, Core.ROTATE_90_COUNTERCLOCKWISE)
            else -> rgb.copyTo(rotated)
        }
        if (mirrorHorizontally) {
            org.opencv.core.Core.flip(rotated, flipped, 1)
            return flipped
        }
        return rotated
    }

    override fun close() { yuv.release(); rgb.release(); rotated.release(); flipped.release() }
}
