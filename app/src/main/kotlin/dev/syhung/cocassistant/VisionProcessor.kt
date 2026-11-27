package dev.syhung.cocassistant

import android.graphics.Bitmap
import android.graphics.Point
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.*
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

    fun findAnchor(frame: Bitmap, templateBitmap: Bitmap): Point? {
        check(OpenCVLoader.initLocal()) { "OpenCV not initialized" }
        if (frame.isRecycled || templateBitmap.isRecycled ||
            templateBitmap.width > frame.width || templateBitmap.height > frame.height) return null

        val frameMat = Mat()
        val templateMat = Mat()
        val frameGray = Mat()
        val templateGray = Mat()
        val result = Mat()
        try {
            Utils.bitmapToMat(frame, frameMat)
            Utils.bitmapToMat(templateBitmap, templateMat)
            val frameCode = when (frameMat.channels()) {
                4 -> Imgproc.COLOR_RGBA2GRAY
                3 -> Imgproc.COLOR_RGB2GRAY
                else -> -1
            }
            val templateCode = when (templateMat.channels()) {
                4 -> Imgproc.COLOR_RGBA2GRAY
                3 -> Imgproc.COLOR_RGB2GRAY
                else -> -1
            }
            if (frameCode < 0 || templateCode < 0) return null
            Imgproc.cvtColor(frameMat, frameGray, frameCode)
            Imgproc.cvtColor(templateMat, templateGray, templateCode)
            if (templateGray.cols() > frameGray.cols() || templateGray.rows() > frameGray.rows()) return null
            Imgproc.matchTemplate(frameGray, templateGray, result, Imgproc.TM_CCOEFF_NORMED)
            val match = Core.minMaxLoc(result)
            if (!match.maxVal.isFinite() || match.maxVal < 0.80) return null
            return Point(
                match.maxLoc.x.toInt() + templateMat.cols() / 2,
                match.maxLoc.y.toInt() + templateMat.rows() / 2
            )
        } finally {
            frameMat.release()
            templateMat.release()
            frameGray.release()
            templateGray.release()
            result.release()
        }
    }}
