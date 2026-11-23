package com.pushupbird.logic

import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

enum class RunState {
    READY,
    PLAYING,
    PAUSED,
    DEAD,
}

data class Pipe(
    var x: Float,
    val width: Float,
    val gapCenterY: Float,
    val gapHeight: Float,
    var scored: Boolean = false,
)

data class Tick(
    val scored: Boolean,
    val died: Boolean,
    val state: RunState,
    val score: Int,
)

/**
 * Portrait flyer. The bird's Y is the player's head, not a tap or a gravity
 * simulation. Bars scroll in from the right. Clearing a gap scores one.
 * Touching a bar, the ceiling, or the mat ends the set.
 *
 * All coordinates are pixels, y grows downward.
 */
class GameWorld(
    private val random: Random = Random.Default,
) {
    var width: Float = 0f
        private set
    var height: Float = 0f
        private set
    var birdX: Float = 0f
        private set
    var birdY: Float = 0f
        private set
    var birdRadius: Float = 1f
        private set
    var score: Int = 0
        private set
    var state: RunState = RunState.READY
        private set

    val pipes = ArrayList<Pipe>()

    /** Seconds until the next bar. Tests pin this so they can place their own bars. */
    internal var nextSpawnIn: Float = FIRST_SPAWN_SEC

    val ceilingThickness: Float
        get() = max(height * 0.055f, birdRadius * 1.3f)

    val floorThickness: Float
        get() = max(height * 0.09f, birdRadius * 1.8f)

    fun matTop(): Float = height - floorThickness

    fun centerY(): Float = (ceilingThickness + matTop()) / 2f

    internal val pipeWidth: Float
        get() = birdRadius * 3.15f

    internal val gapHeight: Float
        get() {
            val inner = (matTop() - ceilingThickness).coerceAtLeast(1f)
            val wanted = inner * 0.36f
            val maxGap = (inner - hitRadius() * 4f).coerceAtLeast(hitRadius() * 2f)
            return min(wanted, maxGap)
        }

    fun resize(w: Float, h: Float) {
        if (w < 2f || h < 2f) return
        val first = width < 2f
        width = w
        height = h
        birdRadius = min(w, h) * 0.052f
        birdX = w * 0.28f
        if (first) birdY = centerY()
    }

    fun start(): Boolean {
        if (width < 2f || height < 2f) return false
        pipes.clear()
        score = 0
        nextSpawnIn = FIRST_SPAWN_SEC
        state = RunState.PLAYING
        return true
    }

    fun pause() {
        if (state == RunState.PLAYING) state = RunState.PAUSED
    }

    fun resume() {
        if (state == RunState.PAUSED) state = RunState.PLAYING
    }

    fun update(dtRaw: Float, control: Float?): Tick {
        if (width < 2f || height < 2f) {
            return Tick(scored = false, died = false, state = state, score = score)
        }
        applyControl(control)
        if (state != RunState.PLAYING) {
            return Tick(scored = false, died = false, state = state, score = score)
        }

        val dt = dtRaw.coerceIn(0f, MAX_STEP_SEC)
        val speed = speedPxPerSec()
        for (pipe in pipes) {
            pipe.x -= speed * dt
        }
        nextSpawnIn -= dt
        if (nextSpawnIn <= 0f) {
            spawnPipe()
            nextSpawnIn = SPAWN_EVERY_SEC
        }
        pipes.removeAll { it.x + it.width < -80f }

        if (collided()) {
            state = RunState.DEAD
            return Tick(scored = false, died = true, state = state, score = score)
        }

        var scoredNow = false
        val clearX = birdX - hitRadius()
        for (pipe in pipes) {
            if (pipe.scored) continue
            val capRight = pipe.x + pipe.width + pipe.width * CAP_EXTRA_SCALE
            if (capRight < clearX) {
                pipe.scored = true
                score += 1
                scoredNow = true
            }
        }
        return Tick(scored = scoredNow, died = false, state = state, score = score)
    }

    internal fun speedPxPerSec(): Float {
        val ramp = 1f + (score * 0.035f).coerceAtMost(0.4f)
        return width * 0.205f * ramp
    }

    internal fun sampleGapCenter(): Float {
        val halfGap = gapHeight / 2f
        val radius = hitRadius()
        val safeTop = ceilingThickness + halfGap + radius
        val safeBottom = matTop() - halfGap - radius
        if (safeBottom <= safeTop + 1f) return centerY()
        val span = safeBottom - safeTop
        // The first bars stay near the middle so the opening set is readable.
        val ease = if (score < 2) 0.55f else 1f
        val usable = span * ease
        val start = safeTop + (span - usable) / 2f
        return start + random.nextFloat() * usable
    }

    private fun spawnPipe() {
        pipes.add(
            Pipe(
                x = width + pipeWidth * CAP_EXTRA_SCALE + 2f,
                width = pipeWidth,
                gapCenterY = sampleGapCenter(),
                gapHeight = gapHeight,
            ),
        )
    }

    private fun applyControl(control: Float?) {
        val value = control ?: return
        val halfTravel = (matTop() - ceilingThickness) / 2f + birdRadius * 0.5f
        birdY = centerY() + value.coerceIn(-1f, 1f) * halfTravel
    }

    private fun collided(): Boolean {
        val radius = hitRadius()
        if (birdY - radius < ceilingThickness) return true
        if (birdY + radius > matTop()) return true
        for (pipe in pipes) {
            if (hits(pipe, radius)) return true
        }
        return false
    }

    private fun hits(pipe: Pipe, radius: Float): Boolean {
        val gapTop = pipe.gapCenterY - pipe.gapHeight / 2f
        val gapBottom = pipe.gapCenterY + pipe.gapHeight / 2f
        val left = pipe.x
        val right = pipe.x + pipe.width
        if (circleHits(birdX, birdY, radius, left, 0f, right, gapTop)) return true
        if (circleHits(birdX, birdY, radius, left, gapBottom, right, height)) return true
        val extra = pipe.width * CAP_EXTRA_SCALE
        val capH = pipe.width * CAP_HEIGHT_SCALE
        if (circleHits(birdX, birdY, radius, left - extra, gapTop - capH, right + extra, gapTop)) {
            return true
        }
        if (circleHits(birdX, birdY, radius, left - extra, gapBottom, right + extra, gapBottom + capH)) {
            return true
        }
        return false
    }

    private fun hitRadius(): Float = birdRadius * HIT_SCALE

    companion object {
        const val HIT_SCALE = 0.78f
        const val CAP_EXTRA_SCALE = 0.22f
        const val CAP_HEIGHT_SCALE = 0.34f
        private const val MAX_STEP_SEC = 0.05f
        private const val SPAWN_EVERY_SEC = 3.4f
        private const val FIRST_SPAWN_SEC = 0.8f
    }
}

internal fun circleHits(
    cx: Float,
    cy: Float,
    r: Float,
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
): Boolean {
    if (right <= left || bottom <= top) return false
    val nearestX = cx.coerceIn(left, right)
    val nearestY = cy.coerceIn(top, bottom)
    val dx = cx - nearestX
    val dy = cy - nearestY
    return dx * dx + dy * dy <= r * r
}
