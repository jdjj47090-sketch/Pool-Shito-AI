package com.mahdi.poolshito.core.camera

import java.nio.ByteBuffer
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class Yuv420Nv21PackerTest {
    @Test fun handlesPaddedRowsAndInterleavesVThenU() {
        val packed = Yuv420Nv21Packer().pack(
            4, 2,
            ByteBuffer.wrap(byteArrayOf(1,2,3,4,99,99,5,6,7,8)), 6,
            ByteBuffer.wrap(byteArrayOf(11,99,12)), 4, 2,
            ByteBuffer.wrap(byteArrayOf(21,99,22)), 4, 2,
        )
        assertArrayEquals(byteArrayOf(1,2,3,4,5,6,7,8,21,11,22,12), packed)
    }
}
