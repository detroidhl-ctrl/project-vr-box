package com.vrarengine.core.panel

/**
 * Utilitário mínimo para rotacionar vetores 3D por quaternion.
 * Convenção: quaternion representado como FloatArray [x, y, z, w].
 */
object Quaternion {

    /** Rotaciona o vetor v pelo quaternion q. */
    fun rotateVector(q: FloatArray, v: FloatArray): FloatArray {
        val x = q[0]; val y = q[1]; val z = q[2]; val w = q[3]
        val vx = v[0]; val vy = v[1]; val vz = v[2]

        // Fórmula otimizada: t = 2 * cross(q.xyz, v); v' = v + w*t + cross(q.xyz, t)
        val tx = 2f * (y * vz - z * vy)
        val ty = 2f * (z * vx - x * vz)
        val tz = 2f * (x * vy - y * vx)

        return floatArrayOf(
            vx + w * tx + (y * tz - z * ty),
            vy + w * ty + (z * tx - x * tz),
            vz + w * tz + (x * ty - y * tx)
        )
    }

    /** Rotaciona o vetor v pelo INVERSO do quaternion q (q é assumido unitário). */
    fun rotateVectorByInverse(q: FloatArray, v: FloatArray): FloatArray {
        val conjugate = floatArrayOf(-q[0], -q[1], -q[2], q[3])
        return rotateVector(conjugate, v)
    }
}
