package com.pushupbird.ui

import android.content.Context
import android.util.AttributeSet
import android.view.Choreographer
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.pushupbird.R
import com.pushupbird.logic.GameWorld
import com.pushupbird.logic.Tick
import kotlin.math.abs

/**
 * Transparent playfield over the camera preview. The bird, bars, and score
 * are drawn here; the preview shows through every pixel that is not painted.
 */
class GameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    var world: GameWorld? = null
    var control: () -> Float? = { null }
    var onTick: ((Tick) -> Unit)? = null
    var banner: String? = null

    private val renderer = GameRenderer(context)
    private val choreographer = Choreographer.getInstance()
    private var looping = false
    private var lastFrame = 0L
    private var animSeconds = 0f
    private var tilt = 0f
    private var lastBirdY = Float.NaN
    private var deathFlash = 0f
    private var scorePunch = 0f
    private var topInset = 0f

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!looping) return
            step(frameTimeNanos)
            if (looping) choreographer.postFrameCallback(this)
        }
    }

    init {
        setWillNotDraw(false)
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            val cutout = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
            )
            topInset = cutout.top.toFloat()
            insets
        }
    }

    fun setLooping(enabled: Boolean) {
        if (looping == enabled) return
        looping = enabled
        lastFrame = 0L
        if (enabled) {
            choreographer.postFrameCallback(frameCallback)
        } else {
            choreographer.removeFrameCallback(frameCallback)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ViewCompat.requestApplyInsets(this)
    }

    override fun onDetachedFromWindow() {
        setLooping(false)
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        world?.resize(w.toFloat(), h.toFloat())
    }

    private fun step(frameTimeNanos: Long) {
        val world = world ?: return
        val dt = if (lastFrame == 0L) 0f else (frameTimeNanos - lastFrame) / 1_000_000_000f
        lastFrame = frameTimeNanos
        animSeconds += dt.coerceAtLeast(0f)
        val tick = world.update(dt, control())
        if (tick.scored) scorePunch = 1f
        if (tick.died) deathFlash = 1f
        updateTilt(world.birdY, dt)
        scorePunch *= 0.88f
        deathFlash *= 0.92f
        onTick?.invoke(tick)
        invalidate()
    }

    private fun updateTilt(birdY: Float, dt: Float) {
        val previous = lastBirdY
        lastBirdY = birdY
        if (previous.isNaN() || dt <= 0.001f || world == null) return
        val pixelsPerSecond = (birdY - previous) / dt
        val target = (pixelsPerSecond / world!!.height * 160f).coerceIn(-32f, 32f)
        // Ease so a single noisy sample does not snap the beak.
        tilt += (target - tilt) * 0.18f
        if (abs(tilt) < 0.05f) tilt = 0f
    }

    override fun onDraw(canvas: android.graphics.Canvas) {
        val world = world ?: return
        if (world.width < 2f) world.resize(width.toFloat(), height.toFloat())
        renderer.draw(
            canvas,
            DrawFrame(
                world = world,
                seconds = animSeconds,
                tiltDegrees = tilt,
                deathFlash = deathFlash,
                scoreScale = 1f + 0.2f * scorePunch,
                topInset = topInset,
                density = resources.displayMetrics.density,
                banner = banner,
                centerLabel = resources.getString(R.string.center_line),
                wordmark = resources.getString(R.string.wordmark),
            ),
        )
    }
}
