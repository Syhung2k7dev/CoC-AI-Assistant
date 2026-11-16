package dev.syhung.cocassistant

import android.graphics.Bitmap
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/** Tạo mã nhận dạng hình ảnh bằng OpenCV native. Lớp này không tải ảnh nguồn lên mạng. */
object VisionProcessor {
    @Synchronized
    fun perceptualHash(bitmap: Bitmap): String {
        check(OpenCVLoader.initLocal()) { "Không thể khởi tạo bộ xử lý OpenCV native" }
        val source = Mat()
        val gray = Mat()
        val resized = Mat()
        val floating = Mat()
        val transformed = Mat()
        try {
            Utils.bitmapToMat(bitmap, source)
            when (source.channels()) {
                4 -> Imgproc.cvtColor(source, gray, Imgproc.COLOR_RGBA2GRAY)
                3 -> Imgproc.cvtColor(source, gray, Imgproc.COLOR_RGB2GRAY)
                else -> source.copyTo(gray)
            }
            Imgproc.resize(gray, resized, Size(32.0, 32.0), 0.0, 0.0, Imgproc.INTER_AREA)
            resized.convertTo(floating, CvType.CV_32F)
            Core.dct(floating, transformed)
            val values = ArrayList<Double>(63)
            for (y in 0 until 8) for (x in 0 until 8) {
                if (x != 0 || y != 0) values.add(transformed.get(y, x)[0])
            }
            val sorted = values.sorted()
            val median = sorted[sorted.size / 2]
            var bits = 0L
            var index = 0
            for (y in 0 until 8) for (x in 0 until 8) {
                val value = transformed.get(y, x)[0]
                if (value > median) bits = bits or (1L shl index)
                index++
            }
            return bits.toULong().toString(16).padStart(16, '0')
        } finally {
            source.release(); gray.release(); resized.release(); floating.release(); transformed.release()
        }
    }

    fun hammingDistance(first: String, second: String): Int {
        val a = first.toULongOrNull(16) ?: return Int.MAX_VALUE
        val b = second.toULongOrNull(16) ?: return Int.MAX_VALUE
        return (a xor b).countOneBits()
    }
}
