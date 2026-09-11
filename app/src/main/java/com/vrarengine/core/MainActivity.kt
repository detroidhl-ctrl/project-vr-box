package com.vrarengine.core

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.PointF
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.view.Choreographer
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.vrarengine.core.camera.CameraPassthroughManager
import com.vrarengine.core.gesture.HandGestureDetector
import com.vrarengine.core.gesture.PinchState
import com.vrarengine.core.orientation.OrientationTracker
import com.vrarengine.core.panel.WorldLockedPanel
import com.vrarengine.core.render.PassthroughRenderer

class MainActivity : AppCompatActivity(), Choreographer.FrameCallback {

    private lateinit var glSurfaceView: GLSurfaceView
    private lateinit var panelView: FrameLayout

    private lateinit var orientationTracker: OrientationTracker
    private lateinit var gestureDetector: HandGestureDetector
    private lateinit var cameraManager: CameraPassthroughManager
    private lateinit var renderer: PassthroughRenderer
    private lateinit var panel: WorldLockedPanel

    private var isDraggingPanel = false
    private var dragStartScreen = PointF()
    private var dragStartPanelWorldDir = floatArrayOf(0f, 0f, -1f)

    companion object {
        private const val CAMERA_PERMISSION_CODE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupImmersiveMode()
        setContentView(R.layout.activity_main)

        glSurfaceView = findViewById(R.id.gl_surface_view)
        panelView = findViewById(R.id.panel_container)

        // ORDEM IMPORTA: cada objeto abaixo é capturado por lambda do próximo,
        // então precisa já estar atribuído antes que a thread de GL comece a
        // rodar (o que acontece assim que setRenderer() é chamado).
        orientationTracker = OrientationTracker(this)

        gestureDetector = HandGestureDetector(this) { pinchState ->
            runOnUiThread { handlePinch(pinchState) }
        }
        gestureDetector.setScreenSize(
            resources.displayMetrics.widthPixels,
            resources.displayMetrics.heightPixels
        )

        cameraManager = CameraPassthroughManager(this) { bitmap, timestampMs ->
            gestureDetector.detectAsync(bitmap, timestampMs)
        }

        renderer = PassthroughRenderer { surfaceTexture ->
            cameraManager.attachPreviewTexture(surfaceTexture)
        }
        glSurfaceView.setEGLContextClientVersion(2)
        glSurfaceView.setRenderer(renderer)
        glSurfaceView.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY

        panel = WorldLockedPanel(
            screenWidthPx = resources.displayMetrics.widthPixels,
            screenHeightPx = resources.displayMetrics.heightPixels,
            horizontalFovDegrees = 90f // calibrar conforme a lente da VR Box usada
        )

        if (hasCameraPermission()) {
            startPipeline()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_CODE)
        }
    }

    private fun handlePinch(state: PinchState) {
        val currentRotation = orientationTracker.getRotationQuaternion()
        val panelScreenPos = panel.getScreenPosition(currentRotation)

        if (state.isPinching) {
            if (!isDraggingPanel && panelScreenPos != null &&
                panel.isPointInsidePanel(state.pinchScreenPos, panelScreenPos)
            ) {
                // Pinça começou em cima do painel -> inicia arraste
                isDraggingPanel = true
                dragStartScreen = state.pinchScreenPos
                dragStartPanelWorldDir = panel.worldDirection.copyOf()
            }
            if (isDraggingPanel) {
                panel.dragTo(state.pinchScreenPos, dragStartScreen, dragStartPanelWorldDir, currentRotation)
            }
        } else {
            isDraggingPanel = false
        }
    }

    override fun doFrame(frameTimeNanos: Long) {
        val rotation = orientationTracker.getRotationQuaternion()
        val screenPos = panel.getScreenPosition(rotation)

        if (screenPos == null) {
            panelView.visibility = View.INVISIBLE
        } else {
            panelView.visibility = View.VISIBLE
            panelView.translationX = screenPos.x - panelView.width / 2f
            panelView.translationY = screenPos.y - panelView.height / 2f
        }

        Choreographer.getInstance().postFrameCallback(this)
    }

    private fun setupImmersiveMode() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    private fun hasCameraPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_CODE && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            startPipeline()
        }
    }

    private fun startPipeline() {
        cameraManager.start()
        Choreographer.getInstance().postFrameCallback(this)
    }

    override fun onResume() {
        super.onResume()
        orientationTracker.start()
    }

    override fun onPause() {
        super.onPause()
        orientationTracker.stop()
        cameraManager.stop()
        Choreographer.getInstance().removeFrameCallback(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        gestureDetector.close()
    }
}
