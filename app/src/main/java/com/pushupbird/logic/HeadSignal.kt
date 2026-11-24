package com.pushupbird.logic

/**
 * Turns a noisy face-box vertical position into a calm bird control.
 *
 * [normY] is 0 at the top of the upright camera frame and 1 at the bottom.
 * Lowering into a pushup increases [normY], the control value goes positive,
 * and the playfield maps that to a lower bird. Pushing back up does the reverse.
 *
 * There is no calibration tap. The neutral pose is the middle of the camera
 * frame, and that baseline drifts toward the head only very slowly, so a real
 * pushup still moves the bird while camera jitter does not.
 *
 * [halfRange] is how far the head must travel, as a fraction of the frame,
 * to send the bird from the center to the ceiling or the mat. Larger is calmer.
 */
class HeadSignal(
    private val alpha: Float = 0.15f,
    initialHalfRange: Float = MEDIUM_RANGE,
    private val maxStep: Float = 0.028f,
    private val baselineAlpha: Float = 0.01f,
) {
    var smoothed: Float? = null
        private set

    /** Frame position that maps to the vertical center of the playfield. */
    var baseline: Float = FRAME_MID
        private set

    var halfRange: Float = initialHalfRange.coerceIn(MIN_RANGE, MAX_RANGE)
        set(value) {
            field = value.coerceIn(MIN_RANGE, MAX_RANGE)
        }

    fun push(normY: Float) {
        val sample = normY.coerceIn(0f, 1f)
        val previous = smoothed
        val next = if (previous == null) {
            sample
        } else {
            val stepped = previous + (sample - previous).coerceIn(-maxStep, maxStep)
            alpha * stepped + (1f - alpha) * previous
        }
        smoothed = next
        baseline += baselineAlpha * (next - baseline)
    }

    /**
     * -1 when the head is one full [halfRange] above the baseline (bird at the top),
     * +1 when it is one full range below (bird at the bottom).
     * Null until the first face sample arrives.
     */
    fun control(): Float? {
        val current = smoothed ?: return null
        return ((current - baseline) / halfRange).coerceIn(-1f, 1f)
    }

    companion object {
        const val FRAME_MID = 0.5f

        /** Livelier. Use when the phone is far and the head barely moves in frame. */
        const val SMALL_RANGE = 0.18f

        /** Calm default. A real pushup still covers the playfield. */
        const val MEDIUM_RANGE = 0.26f

        /** Gentler. Use when the phone is close and the head sweeps the frame. */
        const val LARGE_RANGE = 0.36f

        private const val MIN_RANGE = 0.12f
        private const val MAX_RANGE = 0.5f
    }
}
