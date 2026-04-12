package com.crowdpulse.camera.utils

import android.graphics.*
import android.media.Image
import java.io.ByteArrayOutputStream
import kotlin.math.abs

object ImageUtils {

    /**
     * Converts YUV_420_888 Image to JPEG byte array.
     * Includes compression quality parameter (e.g., 30-40) and resize target limits.
     */
    fun yuv420ToJpeg(image: Image, targetWidth: Int, targetHeight: Int, quality: Int): ByteArray {
        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer

        yBuffer.rewind()
        uBuffer.rewind()
        vBuffer.rewind()

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val width = image.width
        val height = image.height

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, width, height), 100, out)
        val imageBytes = out.toByteArray()

        // Decode bounds to calculate resize ratio
        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = true
        BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, options)

        var scale = 1
        while (options.outWidth / scale / 2 >= targetWidth &&
            options.outHeight / scale / 2 >= targetHeight) {
            scale *= 2
        }

        options.inJustDecodeBounds = false
        options.inSampleSize = scale
        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, options)

        // Compress final resized frame with specified quality parameter (e.g., 30-40%)
        val finalOut = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, finalOut)
        bitmap.recycle()
        
        return finalOut.toByteArray()
    }

    /**
     * Quick pixel-based motion detection (Edge Processing).
     * Extracts only Y (Luminance) plane to compute difference against previous frame.
     * Average absolute difference > threshold triggers a motion event.
     */
    fun hasMotion(currentImage: Image, previousYPlane: ByteArray?, threshold: Int = 15): Pair<Boolean, ByteArray> {
        val yBuffer = currentImage.planes[0].buffer
        yBuffer.rewind()
        val ySize = yBuffer.remaining()
        val currentYPlane = ByteArray(ySize)
        yBuffer.get(currentYPlane, 0, ySize)
        yBuffer.rewind()

        if (previousYPlane == null || previousYPlane.size != currentYPlane.size) {
            return Pair(true, currentYPlane)
        }

        var diffSum = 0L
        // Subsampling step to speed up comparison (compare every 16th pixel)
        val step = 16
        var count = 0
        for (i in 0 until ySize step step) {
            val p1 = currentYPlane[i].toInt() and 0xFF
            val p2 = previousYPlane[i].toInt() and 0xFF
            diffSum += abs(p1 - p2)
            count++
        }

        val avgDiff = diffSum / count
        return Pair(avgDiff > threshold, currentYPlane)
    }
}
