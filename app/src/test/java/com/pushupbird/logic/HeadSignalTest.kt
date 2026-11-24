package com.pushupbird.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HeadSignalTest {

    @Test
    fun noFaceMeansNoControl() {
        val signal = HeadSignal()
        assertNull(signal.control())
        assertEquals(HeadSignal.FRAME_MID, signal.baseline, 0.0001f)
    }

    @Test
    fun faceAtFrameCenterHoldsTheBird() {
        val signal = calm()
        signal.push(0.5f)
        assertEquals(0.5f, signal.smoothed!!, 0.0001f)
        assertEquals(0f, signal.control()!!, 0.0001f)
    }

    @Test
    fun loweringTheHeadSendsTheBirdDownWithoutACalibrationTap() {
        val signal = calm(halfRange = 0.25f)
        signal.push(0.50f)
        assertEquals(0f, signal.control()!!, 0.0001f)

        // Smaller Y is higher in the frame: the player pushed up.
        signal.push(0.25f)
        assertEquals(-1f, signal.control()!!, 0.0001f)

        // Larger Y is lower in the frame: the player dropped into the pushup.
        signal.push(0.75f)
        assertEquals(1f, signal.control()!!, 0.0001f)

        signal.push(0.625f)
        assertEquals(0.5f, signal.control()!!, 0.0001f)
    }

    @Test
    fun aSmallHeadMotionIsOnlyAFractionOfThePlayfield() {
        val signal = calm(halfRange = HeadSignal.MEDIUM_RANGE)
        signal.push(0.50f)
        signal.push(0.53f)
        val control = signal.control()!!
        assertTrue(control > 0f)
        assertTrue(control < 0.2f)
    }

    @Test
    fun samplesOutsideTheFrameAreClamped() {
        val signal = calm()
        signal.push(-2f)
        assertEquals(0f, signal.smoothed!!, 0.0001f)
        signal.push(4f)
        assertEquals(1f, signal.smoothed!!, 0.0001f)
    }

    @Test
    fun exponentialAverageMovesPartway() {
        val signal = HeadSignal(alpha = 0.5f, maxStep = 1f, baselineAlpha = 0f)
        signal.push(0.40f)
        signal.push(0.80f)
        assertEquals(0.60f, signal.smoothed!!, 0.0001f)
    }

    @Test
    fun aSingleSpikeCannotTeleportTheSignal() {
        val signal = HeadSignal(alpha = 1f, maxStep = 0.028f, baselineAlpha = 0f)
        signal.push(0.40f)
        signal.push(0.90f)
        assertEquals(0.428f, signal.smoothed!!, 0.0001f)
    }

    @Test
    fun halfRangeChangesHowFarTheBirdMoves() {
        val signal = calm(halfRange = 0.2f)
        signal.push(0.60f)
        assertEquals(0.5f, signal.control()!!, 0.0001f)
        signal.halfRange = HeadSignal.LARGE_RANGE
        assertEquals(0.10f / HeadSignal.LARGE_RANGE, signal.control()!!, 0.0001f)
    }

    @Test
    fun baselineDriftsSlowlyAndDoesNotEraseAPushup() {
        val signal = HeadSignal(
            alpha = 1f,
            initialHalfRange = 0.25f,
            maxStep = 1f,
            baselineAlpha = 0.05f,
        )
        signal.push(0.75f)
        val duringRep = signal.control()!!
        assertTrue(duringRep > 0.8f)
        repeat(8) { signal.push(0.75f) }
        assertTrue(signal.control()!! < duringRep)
        assertTrue(signal.baseline > HeadSignal.FRAME_MID)
        assertTrue(signal.baseline < 0.75f)
    }

    private fun calm(halfRange: Float = HeadSignal.MEDIUM_RANGE): HeadSignal {
        return HeadSignal(alpha = 1f, initialHalfRange = halfRange, maxStep = 1f, baselineAlpha = 0f)
    }
}
