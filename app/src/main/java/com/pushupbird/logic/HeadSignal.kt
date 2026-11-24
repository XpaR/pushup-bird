package com.pushupbird.logic

import kotlin.math.abs

/**
 * Follows the head's on-screen vertical fraction. 0 is the top of the preview,
 * 1 is the bottom, and that number is where the bird is drawn.
 *
 * A 3-sample median drops a single-frame glitch. The value then eases toward
 * that median with no velocity, so it cannot overshoot or keep moving after
 * the head has stopped. Missed frames are not samples: [screenY] simply stays
 * where it was.
 */
class HeadSignal(
    private val alpha: Float = 0.65f,
) {
    private val recent = ArrayDeque<Float>()

    var screenY: Float? = null
        private set

    fun push(normY: Float) {
        val sample = normY.coerceIn(0f, 1f)
        recent.addLast(sample)
        while (recent.size > WINDOW) recent.removeFirst()
        val median = median(recent)
        val previous = screenY
        screenY = if (previous == null) {
            median
        } else {
            previous + alpha * (median - previous)
        }
    }

    private fun median(values: Collection<Float>): Float {
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) {
            sorted[mid]
        } else {
            (sorted[mid - 1] + sorted[mid]) / 2f
        }
    }

    companion object {
        private const val WINDOW = 3
    }
}

/**
 * FILL_CENTER mapping from an upright camera image into the preview.
 * Y is unchanged by the front-camera mirror.
 *
 * Returns the point's vertical fraction of the view, 0 at the top.
 */
fun fillCenterNormY(
    imageY: Float,
    imageWidth: Float,
    imageHeight: Float,
    viewWidth: Float,
    viewHeight: Float,
): Float {
    if (imageWidth <= 0f || imageHeight <= 0f || viewWidth <= 0f || viewHeight <= 0f) {
        return 0.5f
    }
    val scale = maxOf(viewWidth / imageWidth, viewHeight / imageHeight)
    val offsetY = (imageHeight * scale - viewHeight) / 2f
    val viewY = imageY * scale - offsetY
    return (viewY / viewHeight).coerceIn(0f, 1f)
}

data class FaceCandidate(
    val screenY: Float,
    val area: Int,
)

/** Largest face until one is being followed, then the face nearest that line. */
fun pickFaceScreenY(faces: List<FaceCandidate>, lastScreenY: Float?): Float? {
    if (faces.isEmpty()) return null
    if (lastScreenY == null) return faces.maxBy { it.area }.screenY
    return faces.minBy { abs(it.screenY - lastScreenY) }.screenY
}
