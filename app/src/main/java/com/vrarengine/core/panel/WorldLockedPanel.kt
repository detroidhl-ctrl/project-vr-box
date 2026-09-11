package com.vrarengine.core.panel

import android.graphics.PointF
import kotlin.math.tan

/**
 * Painel flutuante fixo no espaço do mundo real (world-locked), no estilo dos
 * painéis do Meta Quest 3: a POSIÇÃO NO MUNDO só muda quando o usuário arrasta
 * com a pinça; girar a cabeça (o celular) apenas muda de onde, na tela, esse
 * ponto fixo aparece projetado.
 */
class WorldLockedPanel(
    private val screenWidthPx: Int,
    private val screenHeightPx: Int,
    horizontalFovDegrees: Float,
    val panelWidthPx: Float = 480f,
    val panelHeightPx: Float = 320f
) {
    /**
     * Direção do painel no mundo (vetor unitário), no referencial da câmera
     * no instante inicial: -Z é "para frente", +X direita, +Y para cima.
     */
    var worldDirection: FloatArray = floatArrayOf(0f, 0f, -1f)
        private set

    private val focalLengthPx: Float =
        (screenWidthPx / 2f) / tan(Math.toRadians(horizontalFovDegrees / 2.0)).toFloat()

    /**
     * Projeta a direção do painel para coordenadas de tela dado o quaternion
     * atual do dispositivo. Retorna null se o painel estiver fora do campo
     * de visão (atrás do usuário ou muito nas bordas).
     */
    fun getScreenPosition(deviceQuaternion: FloatArray): PointF? {
        val dirCamera = Quaternion.rotateVectorByInverse(deviceQuaternion, worldDirection)

        // Câmera olha para -Z; se z >= 0 o alvo está atrás do usuário.
        if (dirCamera[2] >= -0.01f) return null

        val screenX = (dirCamera[0] / -dirCamera[2]) * focalLengthPx + screenWidthPx / 2f
        val screenY = -(dirCamera[1] / -dirCamera[2]) * focalLengthPx + screenHeightPx / 2f

        val margin = 200f
        if (screenX < -margin || screenX > screenWidthPx + margin ||
            screenY < -margin || screenY > screenHeightPx + margin
        ) {
            return null
        }
        return PointF(screenX, screenY)
    }

    fun isPointInsidePanel(point: PointF, panelCenter: PointF): Boolean {
        val halfW = panelWidthPx / 2f
        val halfH = panelHeightPx / 2f
        return point.x in (panelCenter.x - halfW)..(panelCenter.x + halfW) &&
            point.y in (panelCenter.y - halfH)..(panelCenter.y + halfH)
    }

    /**
     * Atualiza a direção-no-mundo do painel enquanto a pinça arrasta.
     * Chamar a cada frame durante o arraste (não só uma vez).
     */
    fun dragTo(
        currentScreenPos: PointF,
        dragStartScreenPos: PointF,
        dragStartWorldDir: FloatArray,
        deviceQuaternion: FloatArray
    ) {
        val deltaX = currentScreenPos.x - dragStartScreenPos.x
        val deltaY = currentScreenPos.y - dragStartScreenPos.y

        val dirCameraStart = Quaternion.rotateVectorByInverse(deviceQuaternion, dragStartWorldDir)
        val depth = -dirCameraStart[2]

        val newDirCamera = floatArrayOf(
            dirCameraStart[0] + deltaX / focalLengthPx * depth,
            dirCameraStart[1] - deltaY / focalLengthPx * depth,
            dirCameraStart[2]
        )
        normalize(newDirCamera)

        worldDirection = Quaternion.rotateVector(deviceQuaternion, newDirCamera)
    }

    private fun normalize(v: FloatArray) {
        val len = kotlin.math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
        if (len > 1e-6f) {
            v[0] /= len; v[1] /= len; v[2] /= len
        }
    }
}
