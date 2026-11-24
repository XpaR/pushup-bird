package com.pushupbird

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import com.google.android.material.button.MaterialButtonToggleGroup
import com.pushupbird.camera.HeadTracker
import com.pushupbird.camera.TrackerError
import com.pushupbird.databinding.ActivityMainBinding
import com.pushupbird.logic.GameWorld
import com.pushupbird.logic.HeadSignal
import com.pushupbird.logic.RunState
import com.pushupbird.logic.Tick
import kotlin.math.abs

/**
 * Pushup Bird. The front camera is the controller: head height maps to the
 * bird, and the bars scroll like a one-lane flyer. Nothing is recorded.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var settings: GameSettings
    private lateinit var signal: HeadSignal
    private lateinit var world: GameWorld
    private lateinit var tracker: HeadTracker

    private var tone: ToneGenerator? = null
    private var lastFaceAt = 0L
    private var stickyError: String? = null
    private var pauseMessage: String = ""
    private var newBestThisRun = false
    private var syncingTravel = false

    private val requestCamera = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            stickyError = null
            tracker.start()
            renderChrome()
        } else {
            showPermissionDenied()
        }
    }

    private val backCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (hasCameraPermission() && world.state == RunState.PLAYING) {
                world.pause()
                pauseMessage = getString(R.string.pause_back)
                renderChrome()
            } else {
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prepareWindow()

        settings = GameSettings(this)
        signal = HeadSignal(initialHalfRange = settings.halfRange)
        world = GameWorld()
        binding.preview.scaleType = PreviewView.ScaleType.FILL_CENTER
        // TextureView, so the transparent game canvas composites over the preview.
        // SurfaceView mode punches a hole and fights overlays on some devices.
        binding.preview.implementationMode = PreviewView.ImplementationMode.COMPATIBLE

        tracker = HeadTracker(
            context = this,
            lifecycleOwner = this,
            previewView = binding.preview,
            onFaceY = { normY ->
                stickyError = null
                signal.push(normY)
                lastFaceAt = SystemClock.uptimeMillis()
            },
            onError = { error ->
                stickyError = when (error) {
                    TrackerError.NO_FRONT_CAMERA -> getString(R.string.camera_missing)
                    TrackerError.FAILED -> getString(R.string.camera_failed)
                }
                updateStatus()
            },
        )

        binding.game.world = world
        binding.game.control = { signal.control() }
        binding.game.onTick = ::onTick

        binding.enableCameraButton.setOnClickListener { onEnableCamera() }
        binding.openSettingsButton.setOnClickListener { openAppSettings() }
        binding.startButton.setOnClickListener { onStartClicked() }
        binding.retryButton.setOnClickListener { onRetryClicked() }
        binding.resumeButton.setOnClickListener { onResumeClicked() }
        wireTravel(binding.travelToggle)
        wireTravel(binding.overTravelToggle)
        syncTravel(signal.halfRange)

        try {
            tone = ToneGenerator(AudioManager.STREAM_MUSIC, 70)
        } catch (_: RuntimeException) {
            tone = null
        }

        onBackPressedDispatcher.addCallback(this, backCallback)
        pauseMessage = getString(R.string.pause_left)
        if (!hasCameraPermission()) {
            if (settings.cameraAsked) showPermissionDenied() else showPermissionRationale()
        }
        renderChrome()
    }

    override fun onStart() {
        super.onStart()
        if (hasCameraPermission()) {
            tracker.start()
            renderChrome()
        } else {
            tracker.stop()
            if (settings.cameraAsked) showPermissionDenied() else showPermissionRationale()
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        binding.game.setLooping(true)
        if (hasCameraPermission()) tracker.start()
        renderChrome()
    }

    override fun onPause() {
        binding.game.setLooping(false)
        if (world.state == RunState.PLAYING) {
            world.pause()
            pauseMessage = getString(R.string.pause_left)
        }
        super.onPause()
    }

    override fun onStop() {
        tracker.stop()
        super.onStop()
    }

    override fun onDestroy() {
        tracker.release()
        tone?.release()
        tone = null
        super.onDestroy()
    }

    private fun onTick(tick: Tick) {
        if (tick.scored) {
            blip(ToneGenerator.TONE_PROP_ACK, 70)
            binding.game.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            binding.game.contentDescription = getString(R.string.score_cd, tick.score)
        }
        if (tick.died) {
            blip(ToneGenerator.TONE_PROP_NACK, 180)
            binding.game.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            newBestThisRun = settings.recordScore(tick.score)
            renderChrome()
        } else {
            updateStatus()
        }
    }

    private fun onEnableCamera() {
        if (hasCameraPermission()) {
            tracker.start()
            renderChrome()
            return
        }
        val permanentlyDenied = settings.cameraAsked &&
            !shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)
        if (permanentlyDenied) {
            openAppSettings()
        } else {
            settings.cameraAsked = true
            requestCamera.launch(Manifest.permission.CAMERA)
        }
    }

    private fun onStartClicked() {
        if (!world.start()) return
        newBestThisRun = false
        renderChrome()
    }

    private fun onRetryClicked() {
        if (!world.start()) return
        newBestThisRun = false
        renderChrome()
    }

    private fun onResumeClicked() {
        world.resume()
        renderChrome()
    }

    private fun renderChrome() {
        val granted = hasCameraPermission()
        binding.permissionScreen.isVisible = !granted
        val state = if (granted) world.state else null
        binding.readyGroup.isVisible = state == RunState.READY
        binding.pausedPanel.isVisible = state == RunState.PAUSED
        binding.gameOverPanel.isVisible = state == RunState.DEAD
        if (state == RunState.PAUSED) binding.pauseBody.text = pauseMessage
        if (state == RunState.READY) binding.bestReady.text = bestLabel()
        if (state == RunState.DEAD) {
            binding.scoreValue.text = world.score.toString()
            binding.bestOver.text = bestLabel()
            binding.newBest.isVisible = newBestThisRun
        }
        updateStatus()
    }

    private fun updateStatus() {
        if (!::binding.isInitialized) return
        val message = currentMessage()
        val readyText = message ?: getString(R.string.face_ready)
        binding.readyStatus.text = readyText
        val urgent = message != null
        binding.readyStatus.setTextColor(
            ContextCompat.getColor(this, if (urgent) R.color.brass else R.color.cream_dim),
        )
        binding.game.banner = if (world.state == RunState.PLAYING) message else null
    }

    private fun currentMessage(): String? {
        stickyError?.let { return it }
        val missing = !faceVisible()
        return when (world.state) {
            RunState.PLAYING -> if (missing) getString(R.string.face_lost) else null
            else -> if (missing) getString(R.string.looking_for_face) else null
        }
    }

    private fun faceVisible(): Boolean {
        return lastFaceAt != 0L && SystemClock.uptimeMillis() - lastFaceAt < FACE_HOLD_MS
    }

    private fun bestLabel(): String {
        return if (settings.best == 0) {
            getString(R.string.best_none)
        } else {
            getString(R.string.best_fmt, settings.best)
        }
    }

    private fun wireTravel(group: MaterialButtonToggleGroup) {
        group.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked || syncingTravel) return@addOnButtonCheckedListener
            val range = rangeFor(checkedId) ?: return@addOnButtonCheckedListener
            signal.halfRange = range
            settings.halfRange = range
            syncTravel(range)
        }
    }

    private fun syncTravel(range: Float) {
        syncingTravel = true
        binding.travelToggle.check(travelButton(binding.travelToggle, range))
        binding.overTravelToggle.check(travelButton(binding.overTravelToggle, range))
        syncingTravel = false
    }

    private fun rangeFor(id: Int): Float? = when (id) {
        R.id.travel_small, R.id.over_travel_small -> HeadSignal.SMALL_RANGE
        R.id.travel_medium, R.id.over_travel_medium -> HeadSignal.MEDIUM_RANGE
        R.id.travel_large, R.id.over_travel_large -> HeadSignal.LARGE_RANGE
        else -> null
    }

    private fun travelButton(group: MaterialButtonToggleGroup, range: Float): Int {
        val small = abs(range - HeadSignal.SMALL_RANGE)
        val medium = abs(range - HeadSignal.MEDIUM_RANGE)
        val large = abs(range - HeadSignal.LARGE_RANGE)
        val index = when {
            small < medium && small <= large -> 0
            large < medium -> 2
            else -> 1
        }
        val readyIds = intArrayOf(R.id.travel_small, R.id.travel_medium, R.id.travel_large)
        val overIds = intArrayOf(R.id.over_travel_small, R.id.over_travel_medium, R.id.over_travel_large)
        return if (group.id == R.id.travel_toggle) readyIds[index] else overIds[index]
    }

    private fun showPermissionRationale() {
        binding.permissionBody.setText(R.string.permission_body)
        binding.enableCameraButton.setText(R.string.permission_enable)
        binding.openSettingsButton.isVisible = false
        renderChrome()
    }

    private fun showPermissionDenied() {
        binding.permissionBody.setText(R.string.permission_denied)
        binding.enableCameraButton.setText(R.string.permission_try_again)
        binding.openSettingsButton.isVisible = true
        renderChrome()
    }

    private fun openAppSettings() {
        startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", packageName, null),
            ),
        )
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun blip(toneType: Int, durationMs: Int) {
        try {
            tone?.startTone(toneType, durationMs)
        } catch (_: RuntimeException) {
            // Some devices reject the tone generator. The set still plays.
        }
    }

    private fun prepareWindow() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        hideSystemBars()
    }

    private fun hideSystemBars() {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    private companion object {
        const val FACE_HOLD_MS = 500L
    }
}
