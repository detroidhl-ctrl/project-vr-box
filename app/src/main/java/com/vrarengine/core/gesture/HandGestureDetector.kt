package com.vrarengine.core.gesture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import kotlin.math.sqrt

/**
 * Detecta o gesto de pinça (polegar + indicador) a partir dos landmarks de
 * mão do MediaPipe HandLandmarker, rodando 100% on-device com delegate de GPU.
 *
 * Índices de landmark usados (padrão MediaPipe Hands):
 *   0 = wrist | 4 = thumb_tip | 8 = index_finger_tip | 9 = middle_finger_mcp
 */
class HandGestureDetector(
    context: Context,
    private val onPinchUpdate: (PinchState) -> Unit
) {
    // Histerese: threshold de entrada mais apertado que o de saída, evita flicker.
    private val PINCH_ENTER_THRESHOLD = 0.35f
    private val PINCH_EXIT_THRESHOLD = 0.5f
    private var currentlyPinching = false

    private var screenWidth = 1
    private var screenHeight = 1

    fun setScreenSize(width: Int, height: Int) {
        screenWidth = width
        screenHeight = height
    }

    private val handLandmarker: HandLandmarker = HandLandmarker.createFromOptions(
        context,
        HandLandmarker.HandLandmarkerOptions.builder()
            .setBaseOptions(
                BaseOptions.builder()
                    .setModelAssetPath("hand_landmarker.task")
                    .setDelegate(Delegate.GPU) // latência mínima
                    .build()
            )
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumHands(1) // 1 mão = mais rápido; subir para 2 quando necessário
            .setMinHandDetectionConfidence(0.6f)
            .setMinTrackingConfidence(0.5f)
            .setResultListener(this::onResult)
            .setErrorListener(this::onError)
            .build()
    )

    fun detectAsync(bitmap: Bitmap, timestampMs: Long) {
        val mpImage = BitmapImageBuilder(bitmap).build()
        handLandmarker.detectAsync(mpImage, timestampMs)
    }

    private fun onResult(result: HandLandmarkerResult, input: MPImage) {
        val landmarksList = result.landmarks()
        if (landmarksList.isEmpty()) {
            if (currentlyPinching) {
                currentlyPinching = false
                onPinchUpdate(PinchState(false, PointF(0f, 0f), 0f))
            }
            return
        }

        val landmarks = landmarksList[0]
        val wrist = landmarks[0]
        val thumbTip = landmarks[4]
        val indexTip = landmarks[8]
        val middleMcp = landmarks[9]

        val pinchDistance = distance(thumbTip.x(), thumbTip.y(), indexTip.x(), indexTip.y())
        val handScale = distance(wrist.x(), wrist.y(), middleMcp.x(), middleMcp.y())
        val normalizedDistance = if (handScale > 1e-5f) pinchDistance / handScale else Float.MAX_VALUE

        val threshold = if (currentlyPinching) PINCH_EXIT_THRESHOLD else PINCH_ENTER_THRESHOLD
        currentlyPinching = normalizedDistance < threshold

        // Coordenadas do MediaPipe são normalizadas (0..1) relativas à imagem de entrada.
        // TODO: validar rotação/aspect ratio do sensor da câmera no aparelho de teste
        // antes de confiar 100% neste mapeamento direto para a tela (ver README).
        val midX = (thumbTip.x() + indexTip.x()) / 2f
        val midY = (thumbTip.y() + indexTip.y()) / 2f
        val screenPos = PointF(midX * screenWidth, midY * screenHeight)

        val confidence = (1f - (normalizedDistance / PINCH_EXIT_THRESHOLD)).coerceIn(0f, 1f)
        onPinchUpdate(PinchState(currentlyPinching, screenPos, confidence))
    }

    private fun onError(error: RuntimeException) {
        // TODO: plugar num sistema de log real; por enquanto só evita crash silencioso.
        error.printStackTrace()
    }

    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x2 - x1
        val dy = y2 - y1
        return sqrt(dx * dx + dy * dy)
    }

    fun close() {
        handLandmarker.close()
    }
}
