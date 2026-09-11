package com.vrarengine.core.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import java.io.ByteArrayOutputStream

/**
 * Conversão de YUV_420_888 (formato do ImageReader) para Bitmap.
 *
 * NOTA DE PERFORMANCE: esta implementação usa YuvImage + JPEG como caminho
 * rápido para ter o pipeline funcionando ponta a ponta. Ela tem overhead de
 * compressão JPEG a cada frame. Para latência mínima de verdade, substitua
 * por uma conversão YUV->RGB direta via RenderScript ou shader OpenGL
 * (ver YuvToRgbConverter no repositório oficial android/camera-samples).
 */
object YuvConverter {

    fun imageToNv21(image: Image): ByteArray {
        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)
        return nv21
    }

    fun nv21ToBitmap(nv21: ByteArray, width: Int, height: Int): Bitmap {
        val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, width, height), 90, out)
        val jpegBytes = out.toByteArray()
        return BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
    }
}
