package com.mahdi.poolshito.core.camera

import java.nio.ByteBuffer

/** Packs camera YUV_420_888 planes into the NV21 byte order expected by OpenCV. */
class Yuv420Nv21Packer {
    private var output = ByteArray(0)

    fun pack(
        width: Int, height: Int,
        y: ByteBuffer, yRowStride: Int,
        u: ByteBuffer, uRowStride: Int, uPixelStride: Int,
        v: ByteBuffer, vRowStride: Int, vPixelStride: Int,
    ): ByteArray {
        require(width > 0 && height > 0 && width % 2 == 0 && height % 2 == 0) { "YUV420 frame dimensions must be positive and even" }
        val ySize = width * height
        val size = ySize + ySize / 2
        if (output.size != size) output = ByteArray(size)
        copyPlane(y, width, height, yRowStride, 1, output, 0, 1)
        copyInterleavedChroma(v, width / 2, height / 2, vRowStride, vPixelStride, output, ySize)
        copyInterleavedChroma(u, width / 2, height / 2, uRowStride, uPixelStride, output, ySize + 1)
        return output
    }

    private fun copyPlane(source: ByteBuffer, width: Int, height: Int, rowStride: Int, pixelStride: Int, target: ByteArray, offset: Int, targetPixelStride: Int) {
        val buffer = source.duplicate(); val base = buffer.position()
        for (row in 0 until height) for (column in 0 until width) {
            target[offset + row * width * targetPixelStride + column * targetPixelStride] = buffer.get(base + row * rowStride + column * pixelStride)
        }
    }

    private fun copyInterleavedChroma(source: ByteBuffer, width: Int, height: Int, rowStride: Int, pixelStride: Int, target: ByteArray, offset: Int) {
        val buffer = source.duplicate(); val base = buffer.position()
        for (row in 0 until height) for (column in 0 until width) {
            target[offset + row * width * 2 + column * 2] = buffer.get(base + row * rowStride + column * pixelStride)
        }
    }
}
