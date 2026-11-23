package com.pushupbird.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HeadSignalTest {

    @Test
    fun firstSampleBecomesThePreviewMidpoint() {
        val signal = HeadSignal(alpha = 1f, maxStep = 1f)
        assertFalse(signal.calibrate())
        assertNull(signal.control())

        signal.push(0.42f)
        assertEquals(0.42f, signal.smoothed!!, 0.0001f)
        assertEquals(0f, signal.control()!!, 0.0001f)
    }

    @Test
    fun loweringTheHeadSendsTheBirdDown() {
        val signal = HeadSignal(alpha = 1f, initialHalfRange = 0.1f, maxStep = 1f)
        signal.push(0.50f)
        assertTrue(signal.calibrate())
        assertEquals(0f, signal.control()!!, 0.0001f)

        // Smaller Y is higher in the frame: the player pushed up.
        signal.push(0.40f)
        assertEquals(-1f, signal.control()!!, 0.0001f)

        // Larger Y is lower in the frame: the player dropped into the pushup.
        signal.push(0.60f)
        assertEquals(1f, signal.control()!!, 0.0001f)

        signal.push(0.55f)
        assertEquals(0.5f, signal.control()!!, 0.0001f)
    }

    @Test
    fun samplesOutsideTheFrameAreClamped() {
        val signal = HeadSignal(alpha = 1f, maxStep = 1f)
        signal.push(-2f)
        assertEquals(0f, signal.smoothed!!, 0.0001f)
        signal.push(4f)
        assertEquals(1f, signal.smoothed!!, 0.0001f)
    }

    @Test
    fun exponentialAverageMovesPartway() {
        val signal = HeadSignal(alpha = 0.5f, maxStep = 1f)
        signal.push(0.40f)
        signal.push(0.80f)
        assertEquals(0.60f, signal.smoothed!!, 0.0001f)
    }

    @Test
    fun aSingleSpikeCannotTeleportTheSignal() {
        val signal = HeadSignal(alpha = 1f, maxStep = 0.06f)
        signal.push(0.40f)
        signal.calibrate()
        signal.push(0.90f)
        assertEquals(0.46f, signal.smoothed!!, 0.0001f)
        assertEquals(0.06f / HeadSignal.MEDIUM_RANGE, signal.control()!!, 0.001f)
    }

    @Test
    fun halfRangeChangesHowFarTheBirdMoves() {
        val signal = HeadSignal(alpha = 1f, initialHalfRange = 0.1f, maxStep = 1f)
        signal.push(0.50f)
        signal.calibrate()
        signal.push(0.55f)
        assertEquals(0.5f, signal.control()!!, 0.0001f)
        signal.halfRange = HeadSignal.LARGE_RANGE
        assertEquals(0.05f / HeadSignal.LARGE_RANGE, signal.control()!!, 0.0001f)
    }
}
