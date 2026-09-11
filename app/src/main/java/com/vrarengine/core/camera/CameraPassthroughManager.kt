package com.vrarengine.core.camera

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import android.view.Surface

/**
 * Controla a câmera traseira via Camera2 API com DOIS outputs simultâneos:
 *  - `previewSurface`: alta resolução, vai direto para a textura OpenGL (passthrough visual).
 *  - `gestureImageReader`: resolução menor, alimenta o detector de mão (mais rápido).
 *
 * Isso evita que o processamento de gesto (mais pesado) limite a taxa de
 * quadros do que o usuário vê na tela.
 */
class CameraPassthroughManager(
    private val context: Context,
    private val onGestureFrame: (Bitmap, Long) -> Unit
) {
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var previewSurfaceTexture: SurfaceTexture? = null
    private lateinit var gestureImageReader: ImageReader

    private val backgroundThread = HandlerThread("CameraBackground").apply { start() }
    private val backgroundHandler = Handler(backgroundThread.looper)

    private val previewSize = Size(1280, 720)  // qualidade visual do passthrough
    private val gestureSize = Size(640, 480)   // resolução menor e mais rápida p/ MediaPipe

    private var cameraId: String = "0"

    /** Chamado pelo Renderer assim que a textura OpenGL (OES) está pronta. */
    fun attachPreviewTexture(texture: SurfaceTexture) {
        previewSurfaceTexture = texture
        texture.setDefaultBufferSize(previewSize.width, previewSize.height)
    }

    @SuppressLint("MissingPermission")
    fun start() {
        cameraId = findBackCameraId()

        gestureImageReader = ImageReader.newInstance(
            gestureSize.width, gestureSize.height, ImageFormat.YUV_420_888, 2
        ).apply {
            setOnImageAvailableListener({ reader ->
                val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                try {
                    val nv21 = YuvConverter.imageToNv21(image)
                    val bitmap = YuvConverter.nv21ToBitmap(nv21, image.width, image.height)
                    val timestampMs = image.timestamp / 1_000_000
                    onGestureFrame(bitmap, timestampMs)
                } finally {
                    image.close()
                }
            }, backgroundHandler)
        }

        cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(device: CameraDevice) {
                cameraDevice = device
                createSession(device)
            }
            override fun onDisconnected(device: CameraDevice) { device.close() }
            override fun onError(device: CameraDevice, error: Int) { device.close() }
        }, backgroundHandler)
    }

    private fun createSession(device: CameraDevice) {
        val texture = previewSurfaceTexture ?: return
        val previewSurface = Surface(texture)
        val gestureSurface = gestureImageReader.surface

        device.createCaptureSession(
            listOf(previewSurface, gestureSurface),
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    captureSession = session
                    val request = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                        addTarget(previewSurface)
                        addTarget(gestureSurface)
                        set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
                        set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                    }
                    session.setRepeatingRequest(request.build(), null, backgroundHandler)
                }
                override fun onConfigureFailed(session: CameraCaptureSession) { /* logar erro */ }
            },
            backgroundHandler
        )
    }

    private fun findBackCameraId(): String {
        for (id in cameraManager.cameraIdList) {
            val chars = cameraManager.getCameraCharacteristics(id)
            if (chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK) {
                return id
            }
        }
        return cameraManager.cameraIdList.first()
    }

    fun stop() {
        captureSession?.close()
        cameraDevice?.close()
        captureSession = null
        cameraDevice = null
    }
}
