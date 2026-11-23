package com.pushupbird.camera

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

enum class TrackerError {
    NO_FRONT_CAMERA,
    FAILED,
}

/**
 * Front camera plus on-device face detection.
 *
 * Reports the largest face's vertical center as a fraction of the upright
 * frame: 0 is the top, 1 is the bottom. Horizontal mirroring of the preview
 * does not affect this, because a pushup moves the head up and down.
 */
class HeadTracker(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val previewView: PreviewView,
    private val onFaceY: (Float) -> Unit,
    private val onError: (TrackerError) -> Unit,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val session = AtomicInteger(0)
    private var cameraExecutor: ExecutorService? = null
    private var provider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var analysis: ImageAnalysis? = null
    private var detector: FaceDetector? = null
    private var running = false
    private var rotationListener = false

    private val onLayout = android.view.View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
        val next = previewView.display?.rotation ?: return@OnLayoutChangeListener
        preview?.targetRotation = next
        analysis?.targetRotation = next
    }

    fun start() {
        if (running) return
        running = true
        val generation = session.get()
        if (cameraExecutor == null || cameraExecutor?.isShutdown == true) {
            cameraExecutor = Executors.newSingleThreadExecutor()
        }
        if (detector == null) {
            detector = FaceDetection.getClient(
                FaceDetectorOptions.Builder()
                    .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                    .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                    .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                    .setMinFaceSize(0.12f)
                    .enableTracking()
                    .build(),
            )
        }
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener(
            {
                if (generation != session.get()) return@addListener
                try {
                    val cameraProvider = future.get()
                    provider = cameraProvider
                    bind(cameraProvider)
                } catch (error: Exception) {
                    Log.e(TAG, "Camera provider failed", error)
                    running = false
                    onError(TrackerError.FAILED)
                }
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    fun stop() {
        running = false
        session.incrementAndGet()
        try {
            provider?.unbindAll()
        } catch (error: Exception) {
            Log.w(TAG, "Unbind failed", error)
        }
        preview = null
        analysis = null
    }

    fun release() {
        stop()
        if (rotationListener) {
            previewView.removeOnLayoutChangeListener(onLayout)
            rotationListener = false
        }
        try {
            detector?.close()
        } catch (error: RuntimeException) {
            Log.w(TAG, "Detector close failed", error)
        }
        detector = null
        cameraExecutor?.shutdown()
        cameraExecutor = null
    }

    private fun bind(cameraProvider: ProcessCameraProvider) {
        if (!cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
            running = false
            onError(TrackerError.NO_FRONT_CAMERA)
            return
        }
        val rotation = previewView.display?.rotation ?: android.view.Surface.ROTATION_0
        val previewUseCase = Preview.Builder()
            .setTargetRotation(rotation)
            .build()
            .also { it.surfaceProvider = previewView.surfaceProvider }
        val analysisUseCase = ImageAnalysis.Builder()
            .setTargetRotation(rotation)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .setResolutionSelector(analysisResolution())
            .build()
            .also { useCase ->
                val executor = cameraExecutor ?: return@also
                useCase.setAnalyzer(executor) { imageProxy -> analyze(imageProxy) }
            }
        preview = previewUseCase
        analysis = analysisUseCase
        if (!rotationListener) {
            previewView.addOnLayoutChangeListener(onLayout)
            rotationListener = true
        }
        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_FRONT_CAMERA,
                previewUseCase,
                analysisUseCase,
            )
        } catch (error: Exception) {
            Log.e(TAG, "Unable to bind the front camera", error)
            running = false
            onError(TrackerError.FAILED)
        }
    }

    private fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        val faceDetector = detector
        val executor = cameraExecutor
        if (mediaImage == null || faceDetector == null || executor == null || executor.isShutdown) {
            imageProxy.close()
            return
        }
        val generation = session.get()
        val rotation = imageProxy.imageInfo.rotationDegrees
        // ML Kit returns boxes in the upright frame, so a 90/270 buffer swaps axes.
        val uprightHeight = (if (rotation % 180 == 0) imageProxy.height else imageProxy.width)
            .coerceAtLeast(1)
        val input = InputImage.fromMediaImage(mediaImage, rotation)
        try {
            faceDetector.process(input)
                .addOnSuccessListener(executor) { faces ->
                    val face = faces.maxByOrNull { found ->
                        found.boundingBox.width() * found.boundingBox.height()
                    } ?: return@addOnSuccessListener
                    val normY = (face.boundingBox.exactCenterY() / uprightHeight.toFloat())
                        .coerceIn(0f, 1f)
                    mainHandler.post {
                        if (generation == session.get()) onFaceY(normY)
                    }
                }
                .addOnFailureListener(executor) { error ->
                    Log.w(TAG, "Face detector frame failed", error)
                }
                .addOnCompleteListener(executor) {
                    imageProxy.close()
                }
        } catch (error: RuntimeException) {
            Log.w(TAG, "Dropping a camera frame", error)
            imageProxy.close()
        }
    }

    private fun analysisResolution(): ResolutionSelector {
        return ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
            .setResolutionStrategy(
                ResolutionStrategy(
                    Size(640, 480),
                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                ),
            )
            .build()
    }

    private companion object {
        const val TAG = "HeadTracker"
    }
}
