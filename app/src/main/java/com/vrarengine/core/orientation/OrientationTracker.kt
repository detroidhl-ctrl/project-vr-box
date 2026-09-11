package com.vrarengine.core.orientation

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

/**
 * Expõe a orientação do dispositivo como quaternion [x, y, z, w], usando o
 * sensor de vetor de rotação (fusão de giroscópio + acelerômetro + magnetômetro,
 * já com correção de drift feita pelo próprio Android). É a base do
 * "world-locking" do painel.
 */
class OrientationTracker(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    private val rawQuaternion = FloatArray(4) // formato do Android: [w, x, y, z]

    @Volatile
    private var quaternion = floatArrayOf(0f, 0f, 0f, 1f) // formato usado no resto do app: [x, y, z, w]

    fun start() {
        rotationSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
            SensorManager.getQuaternionFromVector(rawQuaternion, event.values)
            // rawQuaternion vem como [w, x, y, z]; reordenamos para [x, y, z, w]
            quaternion = floatArrayOf(rawQuaternion[1], rawQuaternion[2], rawQuaternion[3], rawQuaternion[0])
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    fun getRotationQuaternion(): FloatArray = quaternion.copyOf()
}
