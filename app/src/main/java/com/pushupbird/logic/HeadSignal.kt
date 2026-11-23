package com.pushupbird.logic

/**
 * Turns a noisy face-box vertical position into a stable bird control.
 *
 * [normY] is 0 at the top of the upright camera frame and 1 at the bottom.
 * Lowering into a pushup increases [normY], the control value goes positive,
 * and the playfield maps that to a lower bird. Pushing back up does the reverse.
 *
 * The first sample becomes a preview midpoint so the bird starts centered on
 * whatever the camera sees. [calibrate] recaptures that midpoint from the
 * smoothed height — that is the "set height" action.
 */
class HeadSignal(
    private val alpha: Float = 0.32f,
    initialHalfRange: Float = MEDIUM_RANGE,
    private val maxStep: Float = 0.06f,
) {
    var smoothed: Float? = null
        private set

    /** Head height that maps to the vertical center of the playfield. */
    var mid: Float? = null
        private set

    var halfRange: Float = initialHalfRange.coerceIn(MIN_RANGE, MAX_RANGE)
        set(value) {
            field = value.coerceIn(MIN_RANGE, MAX_RANGE)
        }

    fun push(normY: Float) {
        val sample = normY.coerceIn(0f, 1f)
        val previous = smoothed
        if (previous == null) {
            smoothed = sample
            if (mid == null) mid = sample
            return
        }
        val stepped = previous + (sample - previous).coerceIn(-maxStep, maxStep)
        smoothed = alpha * stepped + (1f - alpha) * previous
    }

    /** Lock the midpoint to the current smoothed head height. */
    fun calibrate(): Boolean {
        val current = smoothed ?: return false
        mid = current
        return true
    }

    /**
     * -1 when the head is one full range above the midpoint (bird at the top),
     * +1 when it is one full range below (bird at the bottom).
     * Null until the first face sample arrives.
     */
    fun control(): Float? {
        val current = smoothed ?: return null
        val origin = mid ?: return null
        return ((current - origin) / halfRange).coerceIn(-1f, 1f)
    }

    companion object {
        const val SMALL_RANGE = 0.07f
        const val MEDIUM_RANGE = 0.11f
        const val LARGE_RANGE = 0.16f
        private const val MIN_RANGE = 0.04f
        private const val MAX_RANGE = 0.35f
    }
}
