package com.crowdpulse.camera.camera

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log

class CameraDeviceManager(
    private val context: Context,
    private val frameListener: (android.media.Image) -> Unit
) {
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var previewSurface: android.view.Surface? = null
    
    var isFrontCamera = false
        private set
    var isFlashEnabled = false
        private set
    
    fun setPreviewSurface(surface: android.view.Surface?) {
        this.previewSurface = surface
    }
    
    // We request 720p maximum threshold handling, falling back to lower if not available
    private var imageReader = ImageReader.newInstance(1280, 720, ImageFormat.YUV_420_888, 2)
    
    private val backgroundThread = HandlerThread("CameraBackground").apply { start() }
    private val backgroundHandler = Handler(backgroundThread.looper)

    @SuppressLint("MissingPermission")
    fun startCamera() {
        val targetFacing = if (isFrontCamera) CameraCharacteristics.LENS_FACING_FRONT else CameraCharacteristics.LENS_FACING_BACK
        val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
            val characteristics = cameraManager.getCameraCharacteristics(id)
            val lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING)
            lensFacing == targetFacing
        } ?: cameraManager.cameraIdList.firstOrNull() ?: return

        cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                cameraDevice = camera
                startCaptureSession()
            }
            override fun onDisconnected(camera: CameraDevice) {
                camera.close()
            }
            override fun onError(camera: CameraDevice, error: Int) {
                camera.close()
            }
        }, backgroundHandler)
    }

    private fun startCaptureSession() {
        val device = cameraDevice ?: return
        
        imageReader.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage()
            if (image != null) {
                frameListener(image)
            }
        }, backgroundHandler)

        try {
            val surfaces = mutableListOf<android.view.Surface>(imageReader.surface)
            previewSurface?.let { surfaces.add(it) }

            device.createCaptureSession(
                surfaces,
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        captureSession = session
                        val requestBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
                        requestBuilder.addTarget(imageReader.surface)
                        previewSurface?.let { requestBuilder.addTarget(it) }

                        // 1. Auto Focus
                        requestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
                        // Flash
                        if (isFlashEnabled && !isFrontCamera) {
                            requestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
                        } else {
                            requestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
                        }
                        
                        // 2. Adaptive FPS throttling is handled downstream in SmartController. 
                        // Do NOT enforce a hardcoded 10-15 FPS range as it crashes most devices (IllegalArgumentException).
                        
                        try {
                            session.setRepeatingRequest(requestBuilder.build(), null, backgroundHandler)
                            Log.d("CameraManager", "Camera streaming started")
                        } catch (e: Exception) {
                            Log.e("CameraManager", "Error setting repeating request", e)
                        }
                    }
                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        Log.e("CameraManager", "Capture session configuration failed!")
                    }
                },
                backgroundHandler
            )
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    fun stopCamera() {
        captureSession?.close()
        captureSession = null
        cameraDevice?.close()
        cameraDevice = null
    }

    fun flipCamera() {
        isFrontCamera = !isFrontCamera
        if (cameraDevice != null) {
            stopCamera()
            startCamera()
        }
    }

    fun toggleFlash() {
        isFlashEnabled = !isFlashEnabled
        val device = cameraDevice ?: return
        val session = captureSession ?: return
        try {
            val requestBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
            requestBuilder.addTarget(imageReader.surface)
            previewSurface?.let { requestBuilder.addTarget(it) }
            
            requestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
            if (isFlashEnabled && !isFrontCamera) {
                requestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
            } else {
                requestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
            }
            session.setRepeatingRequest(requestBuilder.build(), null, backgroundHandler)
        } catch (e: Exception) {
            Log.e("CameraManager", "Failed to update flash", e)
        }
    }

    fun destroy() {
        stopCamera()
        backgroundThread.quitSafely()
    }
}
